# Contributing to Pagmo.NET.Ipopt

## Prerequisites

- .NET 10 SDK
- The base `Pagmo.NET` sources via the `pagmo.NET` submodule (see Cloning below) — this package compiles them directly rather than referencing a published Pagmo.NET
- vcpkg with `VCPKG_ROOT` set
- PowerShell 7+ (`pwsh`)

## Cloning

```powershell
git clone --recurse-submodules https://github.com/samthegliderpilot/pagmo.NET.ipopt
```

## Building the native layer

IPOPT is compiled into the base `PagmoWrapper.dll` via the `pagmo.NET` submodule (which contains `pagmoNet` nested inside it). The `ports/coin-or-ipopt/` overlay in this repo must be on the vcpkg overlay path.

```powershell
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh pagmo.NET/pagmoNet/scripts/build-native.ps1 -Configuration Release
Copy-Item pagmo.NET/pagmoNet/native/win-build/PagmoWrapper.dll native/ -Force
```

## Building and testing

```powershell
dotnet build Pagmo.NET.Ipopt.csproj
dotnet test Pagmo.NET.Ipopt.csproj -p:Platform=x64
```

## Repo layout

| Path | Contents |
|---|---|
| `generated/` | SWIG-generated C# wrapper classes |
| `extensions/` | Hand-written C# extensions |
| `swig/ipopt.i` | SWIG interface file |
| `ports/coin-or-ipopt/` | vcpkg overlay port for COIN-OR IPOPT |
| `pagmo.NET/` | Submodule — full Pagmo.NET repo (contains `pagmoNet/` nested submodule) |

## License

EPL-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
