<#
.SYNOPSIS
    Builds the pagmonet4j JNI native shared library.
.DESCRIPTION
    Uses CMake + vcpkg to build pagmonet4j.dll / libpagmonet4j.so / libpagmonet4j.dylib.
    The SWIG-generated pagmonet4j_wrap.cxx must already exist in
    pagmoWrapper/generated/ — run scripts/regen-swig.ps1 first.
.PARAMETER Configuration
    Build configuration: Debug or Release. Default: Release.
.PARAMETER VcpkgTriplet
    vcpkg triplet override. Auto-detected from platform if omitted.
.EXAMPLE
    pwsh java/scripts/build-native.ps1 -Configuration Release
#>
param(
    [ValidateSet("Debug", "Release")] [string]$Configuration = "Release",
    [string]$VcpkgTriplet = ""
)

$ErrorActionPreference = "Stop"

$JavaRoot     = Split-Path $PSScriptRoot -Parent
$MonorepoRoot = Split-Path $JavaRoot -Parent

# Overlay ports and triplets live in the pagmoNet submodule when the repo is
# cloned standalone; in the monorepo they live one level up. Prefer submodule.
$PortsDir    = if (Test-Path "$JavaRoot/pagmoNet/ports")    { "$JavaRoot/pagmoNet/ports" }    else { "$MonorepoRoot/ports" }
$TripletsDir = if (Test-Path "$JavaRoot/pagmoNet/triplets") { "$JavaRoot/pagmoNet/triplets" } else { "$MonorepoRoot/triplets" }


$vcpkgRoot = $env:VCPKG_ROOT
if (-not $vcpkgRoot) { throw "VCPKG_ROOT is not set." }

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { throw "JAVA_HOME is not set. JNI headers require a JDK installation." }

if ($IsWindows -or $env:OS -eq "Windows_NT") {
    if (-not $VcpkgTriplet) {
        $ipoptRequested = $env:PAGMONET4J_FEATURES -match "ipopt"
        # When IPOPT_PREFIX is set, conda-forge IPOPT is available as a pre-built
        # MSVC DLL — use the standard MSVC triplet. Otherwise fall back to the
        # legacy MinGW/MSYS2 path which requires x64-mingw-static.
        $useCondaIPOPT = $ipoptRequested -and $env:IPOPT_PREFIX
        $VcpkgTriplet = if ($ipoptRequested -and -not $useCondaIPOPT) { "x64-mingw-static" } else { "x64-windows-static-md" }
    }
    $buildDir = Join-Path $JavaRoot "pagmoWrapper\win-build"
} elseif ($IsMacOS) {
    $arch = (uname -m).Trim()
    if (-not $VcpkgTriplet) {
        $VcpkgTriplet = if ($arch -eq "arm64") { "arm64-osx-static-pic" } else { "x64-osx-static-pic" }
    }
    $buildDir = Join-Path $JavaRoot "pagmoWrapper/build"
} else {
    if (-not $VcpkgTriplet) { $VcpkgTriplet = "x64-linux-static-pic" }
    $buildDir = Join-Path $JavaRoot "pagmoWrapper/build"
}

$features = if ($env:PAGMONET4J_FEATURES) { $env:PAGMONET4J_FEATURES } else { "nlopt" }
$installSpec = "pagmo2[$features]:$VcpkgTriplet"

$vcpkgArgs = @(
    "install", $installSpec,
    "--overlay-triplets=$TripletsDir/",
    "--overlay-ports=$PortsDir/",
    "--recurse"
)
# Allow callers to inject additional overlay ports.
if ($env:VCPKG_OVERLAY_PORTS) {
    foreach ($extra in $env:VCPKG_OVERLAY_PORTS -split ";") {
        if ($extra) { $vcpkgArgs += "--overlay-ports=$extra/" }
    }
}

Write-Host "Installing $installSpec via vcpkg (triplet: $VcpkgTriplet)..."
& "$vcpkgRoot/vcpkg" @vcpkgArgs
if ($LASTEXITCODE -ne 0) { throw "vcpkg install failed ($LASTEXITCODE)." }

$toolchainFile = (Join-Path $vcpkgRoot "scripts/buildsystems/vcpkg.cmake") -replace '\\', '/'
$javaHomeFwd   = $javaHome -replace '\\', '/'
$buildDirFwd   = $buildDir -replace '\\', '/'
$sourceDirFwd  = (Join-Path $JavaRoot "pagmoWrapper") -replace '\\', '/'
$overlayFwd    = "$TripletsDir/".Replace('\', '/')

New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

# On Windows: use the same VS/MSVC version that compiled pagmo.lib via vcpkg.
# pagmo.lib uses VS 2022 (MSVC 14.4x/14.5x) STL symbols. Without matching the
# generator, VS 2019 may be picked which produces unresolved external errors.
$cmakeArgs = @(
    "-B", $buildDirFwd,
    "-S", $sourceDirFwd,
    "-DCMAKE_BUILD_TYPE=$Configuration",
    "-DCMAKE_TOOLCHAIN_FILE=$toolchainFile",
    "-DVCPKG_TARGET_TRIPLET=$VcpkgTriplet",
    "-DVCPKG_OVERLAY_TRIPLETS=$overlayFwd",
    "-DPAGMO4J_JNI=ON",
    "-DJAVA_HOME=$javaHomeFwd"
)
if ($IsWindows -or $env:OS -eq "Windows_NT") {
    if ($VcpkgTriplet -eq "x64-mingw-static") {
        # MinGW build: gcc/g++ from MSYS2 MinGW64 must be on PATH.
        # -static-libgcc/-static-libstdc++ embed the GCC/libstdc++ runtime;
        # libgfortran (from MUMPS) still loads dynamically from MSYS2 at runtime.
        $cmakeArgs += "-G", "Ninja"
        $cmakeArgs += "-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++"
    } else {
        # MSVC build: import vcvars64.bat.
        $vsInstallerDir = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer"
        $vswhere = Join-Path $vsInstallerDir "vswhere.exe"
        $vcVars = $null

        if (Test-Path $vswhere) {
            $vsPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null
            if ($vsPath) {
                $candidate = Join-Path $vsPath.Trim() "VC\Auxiliary\Build\vcvars64.bat"
                if (Test-Path $candidate) { $vcVars = $candidate }
            }
        }
        if (-not $vcVars) {
            $vcVarsSearch = @(
                "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat",
                "C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat",
                "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat",
                "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
            )
            $vcVars = $vcVarsSearch | Where-Object { Test-Path $_ } | Select-Object -First 1
        }

        if ($vcVars) {
            Write-Host "Importing VS environment from: $vcVars"
            if (Test-Path $vsInstallerDir) { $env:PATH = "$vsInstallerDir;$env:PATH" }
            $envOutput = cmd /c "`"$vcVars`" && set" 2>&1
            $envOutput | ForEach-Object {
                if ($_ -match '^([^=]+)=(.*)$') {
                    [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
                }
            }
            [System.Environment]::SetEnvironmentVariable('VCPKG_ROOT', $vcpkgRoot, 'Process')
            [System.Environment]::SetEnvironmentVariable('CMAKE_TOOLCHAIN_FILE', '', 'Process')
            $cmakeArgs += "-G", "Ninja"
        } else {
            Write-Host "Warning: VS not found via vswhere or known paths; CMake will select its own generator."
        }
    }
}

if ($IsMacOS) {
    $osx_arch = if ($VcpkgTriplet -match "arm64") { "arm64" } else { "x86_64" }
    $cmakeArgs += "-DCMAKE_OSX_ARCHITECTURES=$osx_arch"
}

Write-Host "CMake configure..."
cmake @cmakeArgs
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed." }

Write-Host "CMake build ($Configuration)..."
cmake --build $buildDir --config $Configuration
if ($LASTEXITCODE -ne 0) { throw "CMake build failed." }

Write-Host "Native build complete. Output: $buildDir"
