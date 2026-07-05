#Requires -Version 7.0
<#
.SYNOPSIS
    Collect the dynamic-dependency closure of a freshly built PagmoWrapper native
    library and stage it (the wrapper + every non-system dependency) into a single
    output directory so the result is self-contained on an end-user machine.

.DESCRIPTION
    This is the shared "make the native package actually work on a clean machine"
    step used by the C# and Java IPOPT publish pipelines. It is intentionally a
    binding/packaging tool, not a general-purpose one — it only knows the three
    platforms we ship and the one dependency-source convention we use (a conda-forge
    prefix that supplies IPOPT + MUMPS + OpenBLAS + the gfortran/quadmath runtime).

    Platform behaviour:

      Linux   No-op. IPOPT and everything else are statically linked into
              libPagmoWrapper.so (x64-linux-static-pic), so there is no closure to
              gather — we just copy the wrapper through unchanged.

      macOS   IPOPT is pulled from dynamic conda dylibs. We walk the otool -L
              closure, copy every dependency that lives in -SearchDir next to the
              wrapper, then rewrite all install names (ids and references) to
              @loader_path/<file> so dyld resolves them from the package directory
              with no DYLD_LIBRARY_PATH. Rewriting invalidates code signatures, so
              each file is re-signed ad-hoc (codesign --sign -) afterwards.

              Why @loader_path everywhere: dyld de-duplicates loaded images by
              install name, so a dependency referenced under two different names
              (e.g. an absolute conda path from one binary and @rpath from another)
              can load twice and crash. Normalising every reference to the same
              @loader_path/<file> string is what prevents that.

      Windows DLLs resolve from the directory of the loading module, so no install
              names need rewriting and nothing needs signing — we only have to copy
              the dependency closure next to the wrapper. Imports are read with
              dumpbin (located via vswhere); a DLL is "ours to bundle" iff a file of
              that name exists in -SearchDir. Anything not in -SearchDir (Windows
              system DLLs, the VC++ runtime) is assumed to be provided by the OS /
              the VC++ redistributable, the same assumption every native NuGet makes.

.PARAMETER WrapperPath
    Path to the freshly built native library (libPagmoWrapper.dylib / PagmoWrapper.dll
    / libPagmoWrapper.so).

.PARAMETER OutputDir
    Directory to stage the self-contained payload into. Created if absent. The wrapper
    and all bundled dependencies are placed here flat; this is what gets packed into
    runtimes/<rid>/native/.

.PARAMETER SearchDir
    One or more directories to resolve dependencies from (the conda-forge prefix's
    lib/ on macOS, Library\bin on Windows). Only dependencies found here are bundled.

.PARAMETER SkipCodeSign
    macOS only: skip the ad-hoc re-sign step (useful when the caller signs/notarizes
    separately). Has no effect on other platforms.
#>
param(
    [Parameter(Mandatory)][string]$WrapperPath,
    [Parameter(Mandatory)][string]$OutputDir,
    [string[]]$SearchDir = @(),
    [switch]$SkipCodeSign
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $WrapperPath)) { throw "Wrapper not found: $WrapperPath" }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$OutputDir = (Resolve-Path $OutputDir).Path

# Normalise search dirs to absolute, existing paths.
$searchDirs = @()
foreach ($d in $SearchDir) {
    if (Test-Path $d) { $searchDirs += (Resolve-Path $d).Path }
    else { Write-Warning "Search dir does not exist, ignoring: $d" }
}

# Resolve a dependency by base name against the search dirs, following symlinks so we
# copy a real file (conda ships versioned dylibs behind unversioned symlinks).
function Resolve-DepFile([string]$baseName) {
    foreach ($dir in $searchDirs) {
        $candidate = Join-Path $dir $baseName
        if (Test-Path $candidate) {
            $item = Get-Item $candidate
            $target = $item.ResolveLinkTarget($true)
            if ($target) { return $target.FullName } else { return $item.FullName }
        }
    }
    return $null
}

# ──────────────────────────────────────────────────────────────────────────────
if ($IsLinux) {
    # Fully static — nothing to gather. Just stage the wrapper.
    $dest = Join-Path $OutputDir ([System.IO.Path]::GetFileName($WrapperPath))
    Copy-Item $WrapperPath $dest -Force
    Write-Host "Linux: static wrapper staged at $dest (no dynamic deps to bundle)."
    return
}

# ──────────────────────────────────────────────────────────────────────────────
if ($IsMacOS) {
    $wrapperName = [System.IO.Path]::GetFileName($WrapperPath)
    $stagedWrapper = Join-Path $OutputDir $wrapperName
    Copy-Item $WrapperPath $stagedWrapper -Force

    # Return the install names a Mach-O references (skip the first line, which is the
    # binary's own id) using otool -L.
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

    # BFS the closure, copying every dependency resolvable from -SearchDir into
    # OutputDir. System libs (/usr/lib, /System) are never in -SearchDir, so they
    # are skipped naturally.
    $bundled = @{}                          # basename -> staged full path
    $queue = [System.Collections.Queue]::new()
    $queue.Enqueue($stagedWrapper)
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        foreach ($ref in (Get-OtoolDeps $current)) {
            $base = [System.IO.Path]::GetFileName($ref)
            if ($bundled.ContainsKey($base)) { continue }
            $src = Resolve-DepFile $base
            if (-not $src) { continue }      # system / not ours
            $staged = Join-Path $OutputDir $base
            Copy-Item $src $staged -Force
            $bundled[$base] = $staged
            $queue.Enqueue($staged)
            Write-Host "  bundled $base"
        }
    }

    # Rewrite install names so everything resolves via @loader_path. For each staged
    # file we set its own id, then redirect every reference that points at a bundled
    # file to @loader_path/<basename>. Re-deriving from each file's own otool output
    # handles the case where the same dep is referenced under different names.
    function Repair-InstallNames([string]$path) {
        $base = [System.IO.Path]::GetFileName($path)
        if ($bundled.ContainsKey($base)) {
            & install_name_tool -id "@loader_path/$base" $path 2>$null
        }
        foreach ($ref in (Get-OtoolDeps $path)) {
            $refBase = [System.IO.Path]::GetFileName($ref)
            if ($bundled.ContainsKey($refBase) -and $ref -ne "@loader_path/$refBase") {
                & install_name_tool -change $ref "@loader_path/$refBase" $path
            }
        }
    }

    Repair-InstallNames $stagedWrapper
    foreach ($p in $bundled.Values) { Repair-InstallNames $p }

    # Rewriting invalidated signatures; re-sign ad-hoc unless told not to.
    if (-not $SkipCodeSign) {
        & codesign --force --sign - $stagedWrapper
        foreach ($p in $bundled.Values) { & codesign --force --sign - $p }
    }

    Write-Host "macOS: staged $wrapperName + $($bundled.Count) dependencies in $OutputDir"
    return
}

# ──────────────────────────────────────────────────────────────────────────────
# Windows
$wrapperName = [System.IO.Path]::GetFileName($WrapperPath)
$stagedWrapper = Join-Path $OutputDir $wrapperName
Copy-Item $WrapperPath $stagedWrapper -Force

# Windows system DLLs, API sets, the VC++ redistributable, and the UCRT are provided by the OS
# or the VC++ redistributable and must never be bundled -- even when the conda-forge prefix ships
# its own copies of them (it does: MSVCP140, VCRUNTIME140*, api-ms-win-crt-*, ...). Skip them
# regardless of -SearchDir, the same assumption every native NuGet makes.
function Test-IsWindowsSystemDll([string]$name) {
    return ($name -match '(?i)^(api-ms-win-|ext-ms-win-)') `
        -or ($name -match '(?i)^(msvcp\d+|vcruntime\d+|msvcr\d+|ucrtbase|concrt\d+|vcomp\d+|vccorlib\d+|vcamp\d+|mfc\d+)(_\w+)?\.dll$') `
        -or ($name -match '(?i)^(kernel32|kernelbase|user32|advapi32|ole32|oleaut32|ws2_32|shell32|gdi32|gdi32full|ntdll|combase|bcrypt|bcryptprimitives|crypt32|sechost|rpcrt4|shlwapi|setupapi|version|winmm|msvcrt|powrprof|dbghelp)\.dll$')
}

# Copy every non-system DLL from the search dir(s). An import-table walk (dumpbin /dependents)
# is NOT sufficient for the conda-forge IPOPT env: its libblas/liblapack are thin forwarders
# that LOAD the real MKL (or OpenBLAS) implementation at runtime, so mkl_rt / mkl_core /
# mkl_intel_thread / libiomp5md never appear in any import table. The env -- created with just
# `ipopt` -- IS exactly IPOPT's runtime closure, so copying all of its non-system DLLs is
# complete by construction. On Windows the DLLs resolve each other from this one directory at
# load time, so nothing needs install-name/rpath rewriting.
$count = 0
foreach ($dir in $searchDirs) {
    Get-ChildItem -Path $dir -Filter *.dll -File -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-IsWindowsSystemDll $_.Name) { return }
        Copy-Item $_.FullName (Join-Path $OutputDir $_.Name) -Force
        $count++
    }
}
Write-Host "Windows: staged $count non-system DLLs from the IPOPT env into $OutputDir"
