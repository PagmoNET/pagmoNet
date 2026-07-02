#Requires -Version 7.0
param(
    # Generate the IPOPT-enabled binding surface (adds -DPAGMO_WITH_IPOPT, which
    # emits ipopt.cs and the IPOPT P/Invokes). Used by the Pagmo.NET.Ipopt build;
    # the base Pagmo.NET build leaves this off.
    [switch]$WithIpopt
)

$ErrorActionPreference = "Stop"

# Resolve SWIG executable: SWIG_EXE env var, then SWIG_HOME, then PATH.
$swigExe = $env:SWIG_EXE
if (-not $swigExe) {
    if ($env:SWIG_HOME -and (Test-Path (Join-Path $env:SWIG_HOME "swig"))) {
        $swigExe = Join-Path $env:SWIG_HOME "swig"
    } elseif ($env:SWIG_HOME -and (Test-Path (Join-Path $env:SWIG_HOME "swig.exe"))) {
        $swigExe = Join-Path $env:SWIG_HOME "swig.exe"
    } else {
        $swigExe = (Get-Command "swig" -ErrorAction SilentlyContinue)?.Source
    }
}
if (-not $swigExe) {
    throw "SWIG executable not found. Set SWIG_EXE, set SWIG_HOME, or add swig to PATH."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot ".")    # pagmo.NET/
$monoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")   # pagmoNet/ root (monorepo)

# Prefer the monorepo root swig/native dirs; fall back to the legacy nested
# submodule path for standalone pagmo.NET checkouts.
$swigSrc = if (Test-Path (Join-Path $monoRoot "swig")) {
    Join-Path $monoRoot "swig"
} else {
    Join-Path $repoRoot "pagmoNet/swig"
}
$nativeSrc = if (Test-Path (Join-Path $monoRoot "native")) {
    Join-Path $monoRoot "native"
} else {
    Join-Path $repoRoot "pagmoNet/native"
}
$csOut    = Join-Path $repoRoot "Pagmo.NET/pygmoWrappers"
$wrapCxx  = Join-Path $nativeSrc "GeneratedWrappers.cxx"
$wrapH    = Join-Path $nativeSrc "PagmoNETSwigInterface_wrap.h"

# Ensure output directory exists
if (Test-Path $csOut) { Remove-Item -Recurse -Force $csOut }
New-Item -ItemType Directory -Path $csOut | Out-Null

# Run SWIG — outputs .cs to $csOut, wrap.cxx and wrap.h to pagmoNet/native/
$swigArgs = @(
    "-c++", "-csharp", "-namespace", "pagmo", "-dllimport", "PagmoWrapper",
    "-outdir", $csOut,
    "-o", $wrapCxx,
    "-oh", $wrapH
)
if ($WithIpopt) { $swigArgs += "-DPAGMO_WITH_IPOPT" }
$swigArgs += @("-I$swigSrc", "-I$nativeSrc", (Join-Path $swigSrc "PagmoNETSwigInterface.i"))

& $swigExe @swigArgs
if ($LASTEXITCODE -ne 0) { throw "SWIG failed ($LASTEXITCODE)." }
