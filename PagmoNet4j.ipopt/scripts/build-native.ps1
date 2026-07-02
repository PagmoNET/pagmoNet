<#
.SYNOPSIS
    Builds the pagmonet4j JNI native library with IPOPT enabled.
.DESCRIPTION
    Wraps PagmoNet4j's build-native.ps1, adding the IPOPT vcpkg overlay port
    from this repo's ports/ directory. The output DLL is the same pagmonet4j
    native library, rebuilt with coin-or-ipopt statically linked.

    Requires PagmoNet4j to be cloned alongside this repo (default: ../PagmoNet4j).
    Set PAGMONET4J_ROOT to override the path.
.PARAMETER Configuration
    Build configuration: Debug or Release. Default: Release.
.PARAMETER VcpkgTriplet
    vcpkg triplet override. Auto-detected from platform if omitted.
.EXAMPLE
    pwsh scripts/build-native.ps1 -Configuration Release
#>
param(
    [ValidateSet("Debug", "Release")] [string]$Configuration = "Release",
    [string]$VcpkgTriplet = ""
)

$ErrorActionPreference = "Stop"

$RepoRoot    = Split-Path $PSScriptRoot -Parent                  # PagmoNet4j.ipopt/
$MonoRoot    = Split-Path $RepoRoot -Parent                       # pagmoNet/ root (monorepo)
$PagmoNet4jRoot = if ($env:PAGMONET4J_ROOT) { $env:PAGMONET4J_ROOT } `
                  elseif (Test-Path (Join-Path $RepoRoot "PagmoNet4j\scripts\build-native.ps1")) { Join-Path $RepoRoot "PagmoNet4j" } `
                  else { Join-Path $MonoRoot "PagmoNet4j" }

if (-not (Test-Path "$PagmoNet4jRoot\scripts\build-native.ps1")) {
    throw "PagmoNet4j not found at '$PagmoNet4jRoot'. In monorepo layout it lives at ../PagmoNet4j; set `$env:PAGMONET4J_ROOT to override."
}

# IPOPT overlay ports live at the monorepo root ports/ (merged from the old per-repo ports/).
# Fall back to a local ports/ for standalone checkouts.
$ipoptPort = if (Test-Path (Join-Path $RepoRoot "ports")) { Join-Path $RepoRoot "ports" } `
             else { Join-Path $MonoRoot "ports" }
$env:VCPKG_OVERLAY_PORTS = if ($env:VCPKG_OVERLAY_PORTS) {
    "$ipoptPort;$env:VCPKG_OVERLAY_PORTS"
} else {
    $ipoptPort
}

Write-Host "IPOPT overlay port: $ipoptPort"
Write-Host "Delegating to PagmoNet4j build script..."

$scriptArgs = @("-Configuration", $Configuration)
if ($VcpkgTriplet) { $scriptArgs += "-VcpkgTriplet", $VcpkgTriplet }

& pwsh (Join-Path $PagmoNet4jRoot "scripts" "build-native.ps1") @scriptArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
