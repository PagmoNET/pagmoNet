#Requires -Version 7.0
<#
.SYNOPSIS
    Stage the IPOPT runtime closure (libipopt + every non-system dependency it pulls in)
    into a single, self-contained output directory so the companion payload package works
    on an end-user machine with no conda, no dev tools, and no LD/DYLD hints.

.DESCRIPTION
    This is the shared "make the IPOPT payload actually work on a clean machine" step used
    by the C# and Java IPOPT *companion* publish pipelines. It is intentionally a
    binding/packaging tool, not a general-purpose one -- it only knows the platforms we ship
    and the one dependency-source convention we use: a conda-forge prefix created with just
    `ipopt nomkl`, which IS exactly IPOPT's runtime closure (libipopt + MUMPS + OpenBLAS +
    the gfortran/quadmath/gcc runtime) and nothing else.

    It does NOT bundle the base wrapper (PagmoWrapper / libpagmonet4j). That belongs to the
    base package. The companion drops this libipopt closure into the SAME runtimes/<rid>/native
    (C#) / natives/<rid> (Java) directory as the base wrapper, where deferred_ipopt's dlopen
    finds it (searching its own module directory first).

    Why copy-ALL rather than walk the import/otool/ldd closure: conda-forge's libblas/liblapack
    are thin forwarders that LOAD the real OpenBLAS at runtime, so the BLAS implementation never
    appears in any static import table. An import-table walk therefore produces a payload that
    fails on a clean machine ("Could not load IPOPT"). Because the env was created with only
    `ipopt nomkl`, copying every non-system shared library in it is complete by construction.

    Platform behaviour (each stages the same closure, differing only in how the copied libraries
    are made to resolve each other from the package directory):

      Windows  DLLs resolve from the directory of the loading module, so nothing needs
               rewriting or signing -- just copy every non-system DLL from -SearchDir.

      Linux    Copy every non-system .so*; set an RPATH of $ORIGIN on each (via patchelf) so a
               library finds its siblings in the same directory with no LD_LIBRARY_PATH.

      macOS    Copy every .dylib; rewrite each library's id and its references to bundled
               siblings to @loader_path/<file> so dyld resolves them from the package directory
               with no DYLD_LIBRARY_PATH; re-sign ad-hoc (rewriting invalidates signatures).

               Why @loader_path for every reference: dyld de-duplicates loaded images by install
               name, so a dependency referenced under two different names (an absolute conda path
               from one binary, @rpath from another) can load twice and crash. Normalising every
               reference to the same @loader_path/<file> string prevents that.

.PARAMETER OutputDir
    Directory to stage the self-contained closure into. Created if absent. All libraries are
    placed here flat; this is what gets packed into runtimes/<rid>/native (C#) or natives/<rid>
    (Java).

.PARAMETER SearchDir
    One or more directories to copy the closure from -- the conda-forge prefix's library dir
    (Library\bin on Windows, lib on Linux/macOS). Every non-system shared library found here is
    bundled.

.PARAMETER SkipCodeSign
    macOS only: skip the ad-hoc re-sign step (useful when the caller signs/notarizes separately).
    Has no effect on other platforms.
#>
param(
    [Parameter(Mandatory)][string]$OutputDir,
    [Parameter(Mandatory)][string[]]$SearchDir,
    [switch]$SkipCodeSign
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$OutputDir = (Resolve-Path $OutputDir).Path

# Normalise search dirs to absolute, existing paths.
$searchDirs = @()
foreach ($d in $SearchDir) {
    if (Test-Path $d) { $searchDirs += (Resolve-Path $d).Path }
    else { Write-Warning "Search dir does not exist, ignoring: $d" }
}
if ($searchDirs.Count -eq 0) { throw "No usable -SearchDir given; nothing to bundle." }

# Copy one shared library into OutputDir, resolving symlinks to real content but keeping the
# entry's own basename so a soname-named symlink (libipopt.so.3 -> libipopt.so.3.14) lands as a
# real file under the name that DT_NEEDED / install names reference.
function Copy-Lib([System.IO.FileInfo]$item) {
    $target = $item.ResolveLinkTarget($true)
    $src = if ($target) { $target.FullName } else { $item.FullName }
    Copy-Item $src (Join-Path $OutputDir $item.Name) -Force
}

# ──────────────────────────────────────────────────────────────────────────────
if ($IsLinux) {
    if (-not (Get-Command patchelf -ErrorAction SilentlyContinue)) {
        throw "patchelf is required on Linux to set RPATH=`$ORIGIN on the bundled closure; install it (apt-get install -y patchelf)."
    }
    $count = 0
    foreach ($dir in $searchDirs) {
        Get-ChildItem -Path $dir -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '\.so($|\.)' } | ForEach-Object {
                Copy-Lib $_
                $count++
            }
    }
    # Point every bundled ELF at its own directory so siblings resolve with no LD_LIBRARY_PATH.
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        & patchelf --set-rpath '$ORIGIN' $f.FullName 2>$null
    }
    Write-Host "Linux: staged $count shared libraries from the IPOPT env into $OutputDir (RPATH=`$ORIGIN)."
    return
}

# ──────────────────────────────────────────────────────────────────────────────
if ($IsMacOS) {
    $count = 0
    foreach ($dir in $searchDirs) {
        Get-ChildItem -Path $dir -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '\.dylib$' } | ForEach-Object {
                Copy-Lib $_
                $count++
            }
    }

    # The set of basenames we bundled -- any reference to one of these is redirected to
    # @loader_path so it resolves from the package directory.
    $bundled = @{}
    foreach ($f in Get-ChildItem -Path $OutputDir -File) { $bundled[$f.Name] = $true }

    function Get-OtoolDeps([string]$path) {
        $lines = & otool -L $path
        $deps = @()
        # Line 0 is "<path>:"; line 1 is the binary's own id; the rest are deps.
        for ($i = 2; $i -lt $lines.Count; $i++) {
            $m = [regex]::Match($lines[$i], '^\s*(\S+)\s*\(')
            if ($m.Success) { $deps += $m.Groups[1].Value }
        }
        return $deps
    }

    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        $path = $f.FullName
        & install_name_tool -id "@loader_path/$($f.Name)" $path 2>$null
        foreach ($ref in (Get-OtoolDeps $path)) {
            $refBase = [System.IO.Path]::GetFileName($ref)
            if ($bundled.ContainsKey($refBase) -and $ref -ne "@loader_path/$refBase") {
                & install_name_tool -change $ref "@loader_path/$refBase" $path
            }
        }
    }

    if (-not $SkipCodeSign) {
        foreach ($f in Get-ChildItem -Path $OutputDir -File) {
            & codesign --force --sign - $f.FullName
        }
    }
    Write-Host "macOS: staged $count dylibs into $OutputDir (@loader_path, ad-hoc signed)."
    return
}

# ──────────────────────────────────────────────────────────────────────────────
# Windows: DLLs resolve each other from the same directory at load time -- no rewriting/signing.
# The Windows system DLLs, API sets, the VC++ redistributable, and the UCRT are provided by the OS
# or the VC++ redistributable and must never be bundled -- even when the conda-forge prefix ships
# its own copies of them (it does: MSVCP140, VCRUNTIME140*, api-ms-win-crt-*, ...). Skip them
# regardless of -SearchDir, the same assumption every native NuGet makes.
function Test-IsWindowsSystemDll([string]$name) {
    return ($name -match '(?i)^(api-ms-win-|ext-ms-win-)') `
        -or ($name -match '(?i)^(msvcp\d+|vcruntime\d+|msvcr\d+|ucrtbase|concrt\d+|vcomp\d+|vccorlib\d+|vcamp\d+|mfc\d+)(_\w+)?\.dll$') `
        -or ($name -match '(?i)^(kernel32|kernelbase|user32|advapi32|ole32|oleaut32|ws2_32|shell32|gdi32|gdi32full|ntdll|combase|bcrypt|bcryptprimitives|crypt32|sechost|rpcrt4|shlwapi|setupapi|version|winmm|msvcrt|powrprof|dbghelp)\.dll$')
}

$count = 0
foreach ($dir in $searchDirs) {
    Get-ChildItem -Path $dir -Filter *.dll -File -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-IsWindowsSystemDll $_.Name) { return }
        Copy-Item $_.FullName (Join-Path $OutputDir $_.Name) -Force
        $count++
    }
}
Write-Host "Windows: staged $count non-system DLLs from the IPOPT env into $OutputDir"
