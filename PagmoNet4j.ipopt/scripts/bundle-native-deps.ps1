#Requires -Version 7.0
<#
.SYNOPSIS
    Collect the dynamic-dependency closure of a freshly built pagmonet4j native
    library and stage it (the wrapper + every non-system dependency) into a single
    output directory so the result is self-contained on an end-user machine.

.DESCRIPTION
    This is the "make the native package actually work on a clean machine" step for
    the PagmoNet4j IPOPT publish pipeline. It is the Java twin of the identical script
    in the Pagmo.NET.Ipopt repo; both gather the same conda-forge IPOPT + MUMPS +
    OpenBLAS + gfortran/quadmath closure. It is intentionally a binding/packaging
    tool, not a general-purpose one — it only knows the three platforms we ship and
    the one dependency-source convention we use (a conda-forge prefix).

    Platform behaviour:

      Linux   No-op. IPOPT and everything else are statically linked into
              libpagmonet4j.so (x64-linux-static-pic), so there is no closure to
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
              the VC++ redistributable, the same assumption every native package makes.

.PARAMETER WrapperPath
    Path to the freshly built native library (libpagmonet4j.dylib / pagmonet4j.dll
    / libpagmonet4j.so).

.PARAMETER OutputDir
    Directory to stage the self-contained payload into. Created if absent. The wrapper
    and all bundled dependencies are placed here flat; this is what gets packed into
    natives/<rid>/.

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

# Locate dumpbin from the latest VS install (windows-latest runners ship VS 2022).
function Get-Dumpbin {
    $vsWhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vsWhere)) { return $null }
    $vsPath = & $vsWhere -latest -property installationPath
    if (-not $vsPath) { return $null }
    $hit = Get-ChildItem -Path (Join-Path $vsPath "VC\Tools\MSVC") -Recurse -Filter "dumpbin.exe" `
        -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match 'Hostx64\\x64' } | Select-Object -First 1
    if ($hit) { return $hit.FullName }
    return (Get-Command dumpbin.exe -ErrorAction SilentlyContinue)?.Source
}

$dumpbin = Get-Dumpbin
if (-not $dumpbin) { throw "dumpbin.exe not found (need Visual Studio Build Tools) - cannot walk the DLL dependency closure." }

# Imported DLL names of a PE binary, via dumpbin /dependents.
function Get-PeDeps([string]$path) {
    $out = & $dumpbin /dependents $path
    $deps = @()
    $inList = $false
    foreach ($line in $out) {
        if ($line -match 'Image has the following dependencies:') { $inList = $true; continue }
        if ($inList) {
            $t = $line.Trim()
            if ($t -match '(?i)^[\w\.\-\+]+\.dll$') { $deps += $t }
            elseif ($t -eq '' -and $deps.Count -gt 0) { break }
        }
    }
    return $deps
}

# BFS the closure; bundle only DLLs we can find in -SearchDir (everything else is a
# Windows system DLL or the VC++ runtime, provided by the OS / VC++ redist).
$bundled = @{}
$queue = [System.Collections.Queue]::new()
$queue.Enqueue($stagedWrapper)
while ($queue.Count -gt 0) {
    $current = $queue.Dequeue()
    foreach ($dep in (Get-PeDeps $current)) {
        if ($bundled.ContainsKey($dep.ToLower())) { continue }
        $src = Resolve-DepFile $dep
        if (-not $src) { continue }
        $staged = Join-Path $OutputDir $dep
        Copy-Item $src $staged -Force
        $bundled[$dep.ToLower()] = $staged
        $queue.Enqueue($staged)
        Write-Host "  bundled $dep"
    }
}

Write-Host "Windows: staged $wrapperName + $($bundled.Count) dependencies in $OutputDir"
