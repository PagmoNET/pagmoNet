# Contributing to Pagmo.NET

This file covers the `pagmo.NET/` sub-project of the `pagmoNet` monorepo. The shared SWIG
interface and native C++ bridge live once at the monorepo **root** (`native/` + `swig/`); the
commands below are run from that root.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| .NET SDK | 10.x | |
| Visual Studio 2022 / Build Tools | 2022+ | C++ toolchain required |
| vcpkg | latest | Set `VCPKG_ROOT` env var |
| SWIG | 4.4.x | Only needed to regenerate wrappers |
| PowerShell | 7+ | `pwsh` must be on PATH |

## Cloning

```powershell
git clone https://github.com/samthegliderpilot/pagmoNet
```

No submodules — `native/`, `swig/`, and all four sub-projects are in the one repo.

## Building the native layer

From the monorepo root:

```powershell
# Debug (fast)
pwsh scripts/build-native.ps1

# Release with all optimizers
pwsh scripts/build-native.ps1 -Configuration Release
```

This builds the root `native/` CMake project; the DLL lands in `native/win-build/PagmoWrapper.dll`.
IPOPT is never linked in — the base's `ipopt` algorithm loads `libipopt` at runtime via `dlopen`
(supply it with the `Pagmo.NET.Ipopt` companion, or `PAGMONET_IPOPT_LIBRARY`).

## Running tests

```powershell
$env:PATH = "$(Resolve-Path native\win-build);$env:PATH"
dotnet test pagmo.NET/Tests/Tests.Pagmo.NET/Tests.Pagmo.NET.csproj -p:Platform=x64 --logger "console;verbosity=normal"
```

## Running examples

```powershell
dotnet run --project pagmo.NET/Examples/Examples.Pagmo.NET -- all
```

## Regenerating SWIG wrappers

Only needed after editing `.i` interface files in the root `swig/`:

```powershell
pwsh pagmo.NET/createSwigWrappersAndPlaceThem.ps1
```

SWIG is resolved via `SWIG_EXE`, `SWIG_HOME`, or `PATH`. Pre-generated wrappers are checked in —
most contributions do not require regeneration.

## Repo layout (monorepo)

```
pagmoNet/
  native/             Shared native C++ bridge (CMake) — builds PagmoWrapper / libpagmonet4j
  swig/               Shared SWIG interface (.i files) for C# and Java
  scripts/            Shared build-native.ps1, bundle-native-deps.ps1, ...
  ports/ triplets/    vcpkg overlay ports (coin-or-ipopt, ...) + custom triplets
  pagmo.NET/          C# base library (this sub-project): Pagmo.NET/, Tests/, Examples/, docs/
  pagmo.NET.ipopt/    C# IPOPT companion payload
  PagmoNet4j/         Java/Kotlin base library
  PagmoNet4j.ipopt/   Java IPOPT companion payload
```

## Pull requests

- Keep PRs focused — one concern per PR
- Run `dotnet test` before opening
- If you change the SWIG interface (root `swig/`), regenerate wrappers and include them in the PR —
  it affects both the C# and Java bindings
