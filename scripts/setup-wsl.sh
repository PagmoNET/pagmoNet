#!/usr/bin/env bash
# One-shot toolchain setup for building the pagmoNet Linux packages inside WSL/Ubuntu.
# Run this ONCE (it needs your sudo password for the apt step):
#     bash /mnt/c/src/pagmoSharp/pagmoNet/scripts/setup-wsl.sh
# Then open a new shell (or `source ~/.bashrc`) and run build-linux-artifacts.sh.
#
# Everything except the apt packages installs into your home dir (no sudo), so it's easy to remove.
set -euo pipefail

echo "==> [1/5] System build tools (sudo)"
sudo apt-get update
sudo apt-get install -y \
  cmake build-essential swig git curl zip unzip tar pkg-config \
  autoconf autoconf-archive automake libtool patchelf \
  openjdk-21-jdk

echo "==> [2/5] .NET SDK 10 (user-local)"
if [ ! -x "$HOME/.dotnet/dotnet" ]; then
  curl -sSL https://dot.net/v1/dotnet-install.sh | bash -s -- --channel 10.0 --install-dir "$HOME/.dotnet"
fi
export PATH="$HOME/.dotnet:$HOME/.dotnet/tools:$PATH"

echo "==> [3/5] PowerShell 7 (as a dotnet global tool — the build scripts are .ps1)"
"$HOME/.dotnet/dotnet" tool list --global | grep -qi powershell || "$HOME/.dotnet/dotnet" tool install --global PowerShell

echo "==> [4/5] vcpkg (builds pagmo2 + deps; the slow part happens later on first build)"
if [ ! -d "$HOME/vcpkg" ]; then
  git clone https://github.com/microsoft/vcpkg "$HOME/vcpkg" --depth 1
fi
"$HOME/vcpkg/bootstrap-vcpkg.sh" -disableMetrics

echo "==> [5/5] micromamba (supplies the libipopt closure for the IPOPT companion payloads)"
if [ ! -x "$HOME/.local/bin/micromamba" ]; then
  mkdir -p "$HOME/.local/bin"
  curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xj -C "$HOME/.local" bin/micromamba
fi

# Persist the environment for future shells.
JAVA_HOME_GUESS="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
if ! grep -q 'pagmoNet WSL env' ~/.bashrc 2>/dev/null; then
  cat >> ~/.bashrc <<RC

# pagmoNet WSL env
export PATH="\$HOME/.dotnet:\$HOME/.dotnet/tools:\$HOME/.local/bin:\$PATH"
export VCPKG_ROOT="\$HOME/vcpkg"
export JAVA_HOME="${JAVA_HOME_GUESS}"
RC
fi

echo ""
echo "Setup complete. Open a NEW shell (or 'source ~/.bashrc'), then run:"
echo "    bash /mnt/c/src/pagmoSharp/pagmoNet/scripts/build-linux-artifacts.sh"
