# Contributing to PagmoNet4j.ipopt

## Prerequisites

- JDK 17+
- Gradle (wrapper included)
- vcpkg with `VCPKG_ROOT` set
- PowerShell 7+ (`pwsh`)

## Cloning

```powershell
git clone --recurse-submodules https://github.com/samthegliderpilot/PagmoNet4j.ipopt
```

## Building the native layer

IPOPT is compiled into the JNI library. The `ports/coin-or-ipopt/` overlay in this repo must be on the vcpkg overlay path alongside the PagmoNet4j ports.

```powershell
# Windows
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh scripts/build-native.ps1 -Configuration Release
```

```bash
# Linux/macOS
export VCPKG_ROOT=~/vcpkg
pwsh scripts/build-native.ps1 -Configuration Release
```

## Running tests

```powershell
$env:PAGMO4J_NATIVE_DIR = "PagmoNet4j/pagmoWrapper/win-build"
./gradlew test
```

## Repo layout

| Path | Contents |
|---|---|
| `src/generated/java/` | SWIG-generated Java wrapper class |
| `swig/ipopt.i` | SWIG interface file |
| `ports/coin-or-ipopt/` | vcpkg overlay port for COIN-OR IPOPT |
| `PagmoNet4j/` | Submodule — full PagmoNet4j repo (contains `pagmoNet/` nested submodule) |

## License

EPL-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
