param(
    [string]$Configuration = "Debug",
    [string]$Platform = "x64"
)

$ErrorActionPreference = "Stop"

$mutexName = "Global\pagmoNet_swig_native_build"
$mutex = New-Object System.Threading.Mutex($false, $mutexName)
$hasLock = $false

try {
    $hasLock = $mutex.WaitOne([TimeSpan]::FromMinutes(30))
    if (-not $hasLock) {
        throw "Timed out waiting for build/SWIG lock '$mutexName'."
    }

    $repoRoot      = Resolve-Path (Join-Path $PSScriptRoot "..")
    $nativeDir     = Join-Path $repoRoot "native"
    $tripletOverlay = (Resolve-Path (Join-Path $repoRoot "triplets")).Path
    $portsOverlay   = (Resolve-Path (Join-Path $repoRoot "ports")).Path

    if ($IsLinux) {
        if (-not $env:VCPKG_ROOT) {
            throw "VCPKG_ROOT must be set for Linux builds."
        }
        $vcpkgExe       = Join-Path $env:VCPKG_ROOT "vcpkg"
        $vcpkgToolchain = Join-Path $env:VCPKG_ROOT "scripts/buildsystems/vcpkg.cmake"
        $triplet        = "x64-linux-static-pic"

        Write-Host "==> vcpkg install pagmo2[nlopt,ipopt]:$triplet"
        & $vcpkgExe install "pagmo2[nlopt,ipopt]:$triplet" `
            "--overlay-triplets=$tripletOverlay" `
            "--overlay-ports=$portsOverlay" `
            "--recurse"
        if ($LASTEXITCODE -ne 0) { throw "vcpkg install failed ($LASTEXITCODE)." }

        $buildDir   = Join-Path $nativeDir "build"
        $cmakeCache = Join-Path $buildDir "CMakeCache.txt"
        if (Test-Path $cmakeCache) { Remove-Item -Force $cmakeCache }

        & cmake `
            "-B$buildDir" "-S$nativeDir" `
            "-DCMAKE_BUILD_TYPE=$Configuration" `
            "-DCMAKE_TOOLCHAIN_FILE=$vcpkgToolchain" `
            "-DVCPKG_TARGET_TRIPLET=$triplet" `
            "-DVCPKG_OVERLAY_TRIPLETS=$tripletOverlay" `
            "-DPAGMONET_WITH_NLOPT=ON" "-DPAGMONET_WITH_IPOPT=ON"
        if ($LASTEXITCODE -ne 0) { throw "cmake configure failed ($LASTEXITCODE)." }
        & cmake --build $buildDir --config $Configuration
        if ($LASTEXITCODE -ne 0) { throw "cmake build failed ($LASTEXITCODE)." }

    } elseif ($IsMacOS) {
        if (-not $env:VCPKG_ROOT) {
            throw "VCPKG_ROOT must be set for macOS builds."
        }
        $vcpkgExe       = Join-Path $env:VCPKG_ROOT "vcpkg"
        $vcpkgToolchain = Join-Path $env:VCPKG_ROOT "scripts/buildsystems/vcpkg.cmake"
        $arch    = (& uname -m).Trim()
        $triplet = if ($arch -eq "arm64") { "arm64-osx-static-pic" } else { "x64-osx-static-pic" }

        Write-Host "==> vcpkg install pagmo2[nlopt]:$triplet"
        & $vcpkgExe install "pagmo2[nlopt]:$triplet" `
            "--overlay-triplets=$tripletOverlay" `
            "--overlay-ports=$portsOverlay" `
            "--recurse"
        if ($LASTEXITCODE -ne 0) { throw "vcpkg install failed ($LASTEXITCODE)." }

        $buildDir   = Join-Path $nativeDir "build"
        $cmakeCache = Join-Path $buildDir "CMakeCache.txt"
        if (Test-Path $cmakeCache) { Remove-Item -Force $cmakeCache }

        & cmake `
            "-B$buildDir" "-S$nativeDir" `
            "-DCMAKE_BUILD_TYPE=$Configuration" `
            "-DCMAKE_TOOLCHAIN_FILE=$vcpkgToolchain" `
            "-DVCPKG_TARGET_TRIPLET=$triplet" `
            "-DVCPKG_OVERLAY_TRIPLETS=$tripletOverlay" `
            "-DPAGMONET_WITH_NLOPT=ON"
        if ($LASTEXITCODE -ne 0) { throw "cmake configure failed ($LASTEXITCODE)." }
        & cmake --build $buildDir --config $Configuration
        if ($LASTEXITCODE -ne 0) { throw "cmake build failed ($LASTEXITCODE)." }

    } elseif ($env:VCPKG_ROOT) {
        $vcpkgExe       = Join-Path $env:VCPKG_ROOT "vcpkg.exe"
        $vcpkgToolchain = Join-Path $env:VCPKG_ROOT "scripts/buildsystems/vcpkg.cmake"
        $triplet        = "x64-windows-static-md"

        Write-Host "==> vcpkg install pagmo2[nlopt,ipopt]:$triplet"
        & $vcpkgExe install "pagmo2[nlopt,ipopt]:$triplet" `
            "--overlay-triplets=$tripletOverlay" `
            "--overlay-ports=$portsOverlay" `
            "--recurse"
        if ($LASTEXITCODE -ne 0) { throw "vcpkg install failed ($LASTEXITCODE)." }

        $buildDir   = Join-Path $nativeDir "win-build"
        $cmakeCache = Join-Path $buildDir "CMakeCache.txt"
        if (Test-Path $cmakeCache) { Remove-Item -Force $cmakeCache }

        $vsWhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
        if (-not (Test-Path $vsWhere)) { throw "vswhere.exe not found. Install Visual Studio Build Tools." }
        $vsInstallPath = & $vsWhere -latest -property installationPath
        $vcvars = Join-Path $vsInstallPath "VC\Auxiliary\Build\vcvars64.bat"
        if (-not (Test-Path $vcvars)) { throw "vcvars64.bat not found at '$vcvars'." }

        Write-Host "==> Importing VC environment from $vsInstallPath"
        $vcEnvLines = cmd /c "`"$vcvars`" > nul 2>&1 && set"
        foreach ($line in $vcEnvLines) {
            if ($line -match '^([^=]+)=(.*)$') {
                [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
            }
        }

        & cmake `
            "-B$buildDir" "-S$nativeDir" `
            "-G" "Ninja" `
            "-DCMAKE_BUILD_TYPE=$Configuration" `
            "-DCMAKE_TOOLCHAIN_FILE=$vcpkgToolchain" `
            "-DVCPKG_TARGET_TRIPLET=$triplet" `
            "-DVCPKG_OVERLAY_TRIPLETS=$tripletOverlay" `
            "-DPAGMONET_WITH_NLOPT=ON" "-DPAGMONET_WITH_IPOPT=ON"
        if ($LASTEXITCODE -ne 0) { throw "cmake configure failed ($LASTEXITCODE)." }
        & cmake --build $buildDir --config $Configuration
        if ($LASTEXITCODE -ne 0) { throw "cmake build failed ($LASTEXITCODE)." }

    } else {
        Write-Warning "VCPKG_ROOT is not set - falling back to MSBuild (no nlopt/ipopt)."
        $vsWhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
        if (-not (Test-Path $vsWhere)) {
            throw "vswhere.exe was not found. Install Visual Studio Build Tools 2022."
        }
        $msbuildExe = & $vsWhere -latest -requires Microsoft.Component.MSBuild -find "MSBuild\**\Bin\MSBuild.exe" | Select-Object -First 1
        if (-not $msbuildExe) { throw "MSBuild.exe was not found." }
        & $msbuildExe (Join-Path $nativeDir "pagmoWrapper.vcxproj") /m /p:Configuration=$Configuration /p:Platform=$Platform
        if ($LASTEXITCODE -ne 0) { throw "MSBuild failed ($LASTEXITCODE)." }
    }
}
finally {
    if ($hasLock) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
