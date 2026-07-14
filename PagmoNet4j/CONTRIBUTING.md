# Contributing to PagmoNet4j

This file covers the `PagmoNet4j/` sub-project of the `pagmoNet` monorepo. The shared SWIG
interface and native C++ bridge live once at the monorepo **root** (`native/` + `swig/`); the JNI
wrapper for Java is built under `PagmoNet4j/pagmoWrapper/`. The commands below are run from the
`PagmoNet4j/` directory unless noted.

## Prerequisites

- JDK 17+
- Gradle (wrapper included — no separate install needed)
- vcpkg with `VCPKG_ROOT` set, and PowerShell 7+ (`pwsh`) — to build the native JNI library

## Cloning

```powershell
git clone https://github.com/PagmoNET/pagmoNet
```

No submodules — the shared `native/` + `swig/` layer and all four sub-projects are in the one repo.

## Building the native JNI library

```powershell
# Windows — builds PagmoNet4j/pagmoWrapper/win-build/pagmonet4j.dll
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh scripts/build-native.ps1 -Configuration Release
```

```bash
# Linux/macOS — builds PagmoNet4j/pagmoWrapper/build/libpagmonet4j.so (or .dylib)
export VCPKG_ROOT=~/vcpkg
pwsh scripts/build-native.ps1 -Configuration Release
```

IPOPT is never linked in — the base's `ipopt` algorithm loads `libipopt` at runtime via `dlopen`
(supply it with the `pagmonet4j-ipopt` companion, or `PAGMONET_IPOPT_LIBRARY`).

## Running the tests

```powershell
# Windows
$env:PAGMO4J_NATIVE_DIR = "pagmoWrapper/win-build"
./gradlew :core:test :kotlin-ext:test
```

```bash
# Linux/macOS
export PAGMO4J_NATIVE_DIR="pagmoWrapper/build"
./gradlew :core:test :kotlin-ext:test
```

## Repo layout

| Path | Contents |
|---|---|
| `core/` | Java core module (generated + hand-written extensions) |
| `kotlin-ext/` | Kotlin extension API |
| `examples/` | Runnable examples |
| `pagmoWrapper/` | JNI wrapper CMake project (built above) |
| `../native/`, `../swig/` | Shared native C++ bridge + SWIG interface at the monorepo root |

## License

MPL-2.0. See [LICENSE](LICENSE).
