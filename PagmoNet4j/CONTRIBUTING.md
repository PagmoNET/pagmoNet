# Contributing to PagmoNet4j

## Prerequisites

- JDK 17+
- Gradle (wrapper included — no separate install needed)
- Native DLL from the `pagmoNet` submodule (wired automatically — see below)

## Building the native JNI library

Clone with submodules so the `pagmoNet` native layer is included:

```powershell
git clone --recurse-submodules https://github.com/samthegliderpilot/PagmoNet4j
# or, if you already cloned without:
git submodule update --init --recursive
```

Then build the native JNI library:

```powershell
# Windows — builds pagmoWrapper/win-build/pagmonet4j.dll
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh scripts/build-native.ps1 -Configuration Release
```

```bash
# Linux/macOS — builds pagmoWrapper/build/libpagmonet4j.so (or .dylib)
export VCPKG_ROOT=~/vcpkg
pwsh scripts/build-native.ps1 -Configuration Release
```

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
| `pagmoNet/` | Submodule — shared SWIG/native layer (CMake, SWIG interface, vcpkg ports) |

## License

MPL-2.0. See [LICENSE](LICENSE).
