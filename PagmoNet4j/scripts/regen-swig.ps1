#Requires -Version 7.0
param()

$ErrorActionPreference = "Stop"

$swigExe = $env:SWIG_EXE
if (-not $swigExe) {
    if ($env:SWIG_HOME) {
        $swigExe = (Get-ChildItem $env:SWIG_HOME -Filter "swig*" | Select-Object -First 1)?.FullName
    }
    if (-not $swigExe) {
        $swigExe = (Get-Command "swig" -ErrorAction SilentlyContinue)?.Source
    }
}
if (-not $swigExe) {
    throw "SWIG not found. Set SWIG_EXE, SWIG_HOME, or add swig to PATH."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

# Locate pagmoNet: git submodule (./pagmoNet) or sibling repo (../pagmoNet)
$pagmoNetRoot = Join-Path $repoRoot "pagmoNet"
if (-not (Test-Path (Join-Path $pagmoNetRoot "native\CMakeLists.txt"))) {
    $pagmoNetRoot = Resolve-Path (Join-Path $repoRoot "..\pagmoNet")
}
$pagmoNetRoot = Resolve-Path $pagmoNetRoot

$nativeSrc = Join-Path $pagmoNetRoot "native"
$swigDir   = Join-Path $pagmoNetRoot "swig"
$javaOut   = Join-Path $repoRoot "core\src\generated\java\io\github\samthegliderpilot\pagmonet4j"
$wrapOut   = Join-Path $repoRoot "pagmoWrapper\generated\pagmonet4j_wrap.cxx"

New-Item -ItemType Directory -Force $javaOut | Out-Null

$swigArgs = @(
    "-c++", "-java",
    "-package", "io.github.samthegliderpilot.pagmonet4j",
    "-outdir",  $javaOut,
    "-o",       $wrapOut,
    "-I$nativeSrc",
    "-I$swigDir",
    (Join-Path $swigDir "Pagmo4jSwigInterface.i")
)

Write-Host "==> SWIG: $swigExe"
Write-Host "==> pagmoNet: $pagmoNetRoot"
& $swigExe @swigArgs
if ($LASTEXITCODE -ne 0) { throw "SWIG failed ($LASTEXITCODE)." }

Write-Host "==> Done. Wrap:  $wrapOut"
Write-Host "==> Done. Java:  $javaOut"
