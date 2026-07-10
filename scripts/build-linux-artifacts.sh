#!/usr/bin/env bash
# Build all four Linux packages (Pagmo.NET base+companion nupkgs, PagmoNet4j base+companion jars)
# inside WSL/Ubuntu, verify them with the clean-room consumers, and drop the results where Windows
# can see them: C:\src\pagmoSharp\local-packages-linux\ .
#
# Prereq: run scripts/setup-wsl.sh once first. Then:
#     bash /mnt/c/src/pagmoSharp/pagmoNet/scripts/build-linux-artifacts.sh
#
# IMPORTANT: this builds on the native ext4 filesystem ($HOME), NOT on /mnt/c. cmake's
# configure_file fails with "Operation not permitted" on the drvfs-mounted Windows drive, so we
# stage a source copy to ~/pagmoNet-build, build there, and copy the finished packages back.
#
# Reuses the repo's Linux-aware PowerShell build scripts via the user-local pwsh; packaging
# (dotnet pack / gradle) is plain bash. Version is 1.0.0-local.
set -euo pipefail

SRC="/mnt/c/src/pagmoSharp/pagmoNet"
BUILD="$HOME/pagmoNet-build"
OUT="/mnt/c/src/pagmoSharp/local-packages-linux"
VER="1.0.0-local"
export PATH="$HOME/.dotnet:$HOME/.dotnet/tools:$HOME/.local/bin:$PATH"
export VCPKG_ROOT="${VCPKG_ROOT:-$HOME/vcpkg}"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
PWSH="$(command -v pwsh)"
mkdir -p "$OUT/dotnet" "$OUT/java"

echo "==> Staging source onto ext4 ($BUILD) — cmake can't configure_file on /mnt/c"
mkdir -p "$BUILD"
tar -C "$SRC" \
  --exclude='./.git' --exclude='*/bin' --exclude='*/obj' \
  --exclude='*/build' --exclude='*/win-build' --exclude='*/.gradle' \
  --exclude='*/staged' --exclude='*/staged-natives' --exclude='*/staged-runtimes' \
  --exclude='*/cleanroom-repo' \
  -cf - . | tar -C "$BUILD" -xf -
cd "$BUILD"

# gradlew ships from a Windows checkout with CRLF line endings; its `#!/usr/bin/env sh` shebang then
# fails under Linux ("env: 'sh\r': No such file or directory"). Strip the carriage returns.
find . -name gradlew -exec sed -i 's/\r$//' {} +

echo "==> [0/6] conda libipopt closure (OpenBLAS, nomkl) for the companion payloads"
# Use a DEDICATED env name so a stale MKL ~/ipopt-env from an earlier run can't be reused: MKL
# bloats the closure to ~570 MB (216 MB packed) vs OpenBLAS ~42 MB. Only this nomkl code ever
# creates ipopt-ob-env, so its presence guarantees OpenBLAS.
[ -d "$HOME/ipopt-ob-env" ] || micromamba create -c conda-forge -p "$HOME/ipopt-ob-env" ipopt nomkl -y
IPOPT_LIB="$HOME/ipopt-ob-env/lib"

# Force a CLEAN pagmo (no ipopt feature) so the base .so links zero IPOPT and stays MPL-clean.
# The portfile disables find_package(IPOPT) when ipopt isn't a requested feature.
echo "==> Ensuring pagmo2 is built WITHOUT the ipopt feature"
"$VCPKG_ROOT/vcpkg" remove pagmo2 coin-or-ipopt --recurse 2>/dev/null || true

# ── C# base ────────────────────────────────────────────────────────────────────────────────────
echo "==> [1/6] C# base native (libPagmoWrapper.so) + nupkg"
"$PWSH" -NoProfile -File pagmo.NET/createSwigWrappersAndPlaceThem.ps1
"$PWSH" -NoProfile -File scripts/build-native.ps1 -Configuration Release
mkdir -p pagmo.NET/pagmoWrapper/build /tmp/pkgs
cp native/build/libPagmoWrapper.so pagmo.NET/pagmoWrapper/build/libPagmoWrapper.so
# Pack to an ext4 dir via the explicit property (the `-o` flag mis-translates to a bare MSBuild
# arg in some SDKs, and /mnt/c writes are slow), then copy the nupkgs out to the Windows drive.
dotnet pack pagmo.NET/Pagmo.NET/Pagmo.NET.csproj -c Release -p:Version="$VER" -p:Platform=x64 -p:PackageOutputPath=/tmp/pkgs

# ── C# companion ───────────────────────────────────────────────────────────────────────────────
echo "==> [2/6] C# companion payload (libipopt closure) + nupkg"
rm -rf /tmp/staged-cs
"$PWSH" -NoProfile -File scripts/bundle-native-deps.ps1 \
  -OutputDir /tmp/staged-cs/runtimes/linux-x64/native -SearchDir "$IPOPT_LIB"
dotnet pack pagmo.NET.ipopt/Pagmo.NET.Ipopt.csproj -c Release -p:Version="$VER" \
  -p:PagmoNativePackDir=/tmp/staged-cs -p:PackageOutputPath=/tmp/pkgs
cp /tmp/pkgs/*.nupkg "$OUT/dotnet/"

# ── Java base ──────────────────────────────────────────────────────────────────────────────────
echo "==> [3/6] Java base native (libpagmonet4j.so) + jar"
mkdir -p PagmoNet4j/core/src/generated/java/io/github/samthegliderpilot/pagmonet4j PagmoNet4j/pagmoWrapper/generated
swig -java -c++ -package "io.github.samthegliderpilot.pagmonet4j" \
  -outdir "PagmoNet4j/core/src/generated/java/io/github/samthegliderpilot/pagmonet4j" \
  -o "PagmoNet4j/pagmoWrapper/generated/pagmonet4j_wrap.cxx" \
  -Iswig -Inative swig/Pagmo4jSwigInterface.i
"$PWSH" -NoProfile -File PagmoNet4j/scripts/build-native.ps1 -Configuration Release
( cd PagmoNet4j && ./gradlew :core:jar :core:publishToMavenLocal -x test -Pversion="$VER" --console=plain )
cp PagmoNet4j/core/build/libs/core-"$VER".jar "$OUT/java/pagmonet4j-$VER.jar"

# ── Java companion ─────────────────────────────────────────────────────────────────────────────
echo "==> [4/6] Java companion payload (libipopt closure) + jar"
"$PWSH" -NoProfile -File scripts/bundle-native-deps.ps1 \
  -OutputDir PagmoNet4j.ipopt/staged-natives/linux-x64 -SearchDir "$IPOPT_LIB"
( cd PagmoNet4j.ipopt && ./gradlew jar -x test -Pversion="$VER" --console=plain )
cp PagmoNet4j.ipopt/build/libs/pagmonet4j-ipopt-"$VER".jar "$OUT/java/"

# ── Verify C# (real IPOPT solve, offline from the local feed) ────────────────────────────────────
echo "==> [5/6] verify C# pair (clean-room solve)"
CONS=/tmp/cs-consumer; rm -rf "$CONS"; mkdir -p "$CONS"
cp pagmo.NET.ipopt/cleanroom/CleanRoom.csproj pagmo.NET.ipopt/cleanroom/Program.cs "$CONS/"
cat > "$CONS/nuget.config" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<configuration><packageSources><clear />
  <add key="local" value="$OUT/dotnet" />
  <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
</packageSources></configuration>
EOF
# Clear the cached same-version packages so we test the freshly-packed nupkgs, not stale copies.
rm -rf "$HOME/.nuget/packages/pagmo.net" "$HOME/.nuget/packages/pagmo.net.ipopt"
echo "   -- negative control: base ONLY, IPOPT must be UNavailable (proves the room is clean) --"
( cd "$CONS" && dotnet add CleanRoom.csproj package Pagmo.NET --version "$VER" \
  && PAGMONET_CLEANROOM_EXPECT=absent dotnet run --project CleanRoom.csproj -c Release -r linux-x64 --self-contained false )
echo "   -- positive: add companion, IPOPT solve must succeed --"
( cd "$CONS" && dotnet add CleanRoom.csproj package Pagmo.NET.Ipopt --version "$VER" \
  && dotnet run --project CleanRoom.csproj -c Release -r linux-x64 --self-contained false )

# ── Verify Java (real IPOPT solve, offline from a merged local repo) ─────────────────────────────
echo "==> [6/6] verify Java pair (clean-room solve)"
REPO_M=/tmp/java-repo; rm -rf "$REPO_M"; mkdir -p "$REPO_M"
( cd PagmoNet4j && ./gradlew :core:publishMavenPublicationToBuildLocalRepository -x test -Pversion="$VER" --console=plain )
( cd PagmoNet4j.ipopt && ./gradlew publishMavenPublicationToBuildLocalRepository -x test -Pversion="$VER" --console=plain )
cp -r PagmoNet4j/core/build/localrepo/* "$REPO_M/"
cp -r PagmoNet4j.ipopt/build/localrepo/* "$REPO_M/"
# Drop cached same-version artifacts (mavenLocal + gradle module cache) so the clean-room resolves
# the freshly-built jars, not stale copies.
rm -rf "$HOME/.m2/repository/io/github/samthegliderpilot" \
       "$HOME/.gradle/caches/modules-2/files-2.1/io.github.samthegliderpilot" \
       "$HOME/.gradle/caches/modules-2/metadata-"*/descriptors/io.github.samthegliderpilot 2>/dev/null || true
echo "   -- negative control: base ONLY, IPOPT must be UNavailable --"
PAGMONET_CLEANROOM_EXPECT=absent ./PagmoNet4j.ipopt/gradlew -p PagmoNet4j.ipopt/cleanroom run \
  -PpagmoIpoptVersion="$VER" -PlocalRepo="$REPO_M" -PpagmoBaseOnly=true --console=plain
echo "   -- positive: companion, IPOPT solve must succeed --"
./PagmoNet4j.ipopt/gradlew -p PagmoNet4j.ipopt/cleanroom run -PpagmoIpoptVersion="$VER" -PlocalRepo="$REPO_M" --console=plain

echo ""
echo "DONE. Linux packages in: C:\\src\\pagmoSharp\\local-packages-linux\\"
ls -la "$OUT/dotnet" "$OUT/java"
