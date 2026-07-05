# Contributing to pagmoNet

## Prerequisites

- CMake 3.20+
- SWIG 4.x (`swig` on Linux/macOS, [swigwin](https://www.swig.org/download.html) on Windows)
- Visual Studio 2022 Build Tools (Windows) or GCC/Clang (Linux/macOS)
- [vcpkg](https://github.com/microsoft/vcpkg) with `VCPKG_ROOT` set

## Building the native DLL

```powershell
# Windows (PowerShell)
$env:VCPKG_ROOT = "C:\vcpkg"
.\scripts\build-native.ps1 -Configuration Release
# Output: native/win-build/PagmoWrapper.dll
```

```bash
# Linux / macOS
export VCPKG_ROOT=~/vcpkg
pwsh scripts/build-native.ps1 -Configuration Release
# Output: native/build/libPagmoWrapper.so (or .dylib)
```

## Repo layout

| Path | Contents |
|---|---|
| `native/` | C++ bridge source — `CMakeLists.txt`, `.cpp`, `.h` |
| `swig/` | SWIG `.i` interface files for C# and Java |
| `ports/pagmo2/` | vcpkg overlay port for pagmo2 |
| `triplets/` | vcpkg triplet overrides (static-md, static-pic) |
| `scripts/` | Build helper scripts |
| `.ai/` | Project documentation and AI context |

## Regenerating SWIG wrappers

After changing `.i` files, re-run SWIG to regenerate the language-specific wrappers:

```powershell
pwsh pagmo.NET/createSwigWrappersAndPlaceThem.ps1
```

## License

MPL-2.0. See [LICENSE](LICENSE).
