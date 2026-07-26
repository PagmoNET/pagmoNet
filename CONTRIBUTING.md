# Contributing to pagmoNet

This is the single contribution guide for the whole **pagmoNet** monorepo. All four published
packages are built from one repository and one shared native/SWIG layer, so their setup, builds,
and tests are documented here together to keep them in lockstep:

| Package | Directory | License | What it is |
|---|---|---|---|
| `Pagmo.NET` | `pagmo.NET/` | MPL-2.0 | C# / .NET base bindings |
| `Pagmo.NET.Ipopt` | `pagmo.NET.ipopt/` | EPL-2.0 | C# IPOPT native runtime companion |
| `PagmoNet4j` | `PagmoNet4j/` | MPL-2.0 | Java / Kotlin base bindings |
| `PagmoNet4j.ipopt` | `PagmoNet4j.ipopt/` | EPL-2.0 | Java IPOPT native runtime companion |

The shared C++ bridge (`native/`) and SWIG interface (`swig/`) live once at the repo root and feed
both languages — a change there affects C# **and** Java, so regenerate and test both.

## Repo layout

```
pagmoNet/
  native/             Shared native C++ bridge (CMake) — builds PagmoWrapper / libpagmonet4j
  swig/               Shared SWIG interface (.i files) for C# and Java
  scripts/            Shared build-native.ps1, bundle-native-deps.ps1, ...
  ports/ triplets/    vcpkg overlay ports (coin-or-ipopt, pagmo2, ...) + custom triplets
  pagmo.NET/          C# base: Pagmo.NET/, Tests/, Examples/, docs/, scripts/, pagmoWrapper/
  pagmo.NET.ipopt/    C# IPOPT companion payload (packaging only — no managed/pagmo code)
  PagmoNet4j/         Java/Kotlin base: core/, kotlin-ext/, examples/, pagmoWrapper/, scripts/
  PagmoNet4j.ipopt/   Java IPOPT companion payload (packaging only)
  .ai/                Project + AI context documents
```

## Prerequisites

| Tool | Version | Needed for |
|------|---------|-----------|
| vcpkg | latest, `VCPKG_ROOT` set | building the native layer |
| CMake | 3.20+ | native builds (bundled with recent VS) |
| SWIG | 4.4.x | regenerating wrappers (`swig` on PATH, or `SWIG_EXE`/`SWIG_HOME`) |
| PowerShell | 7+ (`pwsh`) | the build/bundle scripts |
| Visual Studio 2022 / Build Tools | 2022+ | C++ toolchain (Windows); GCC/Clang on Linux/macOS |
| .NET SDK | 10.x | building/testing the C# packages |
| JDK | 17+ (21 for the `kotlin-ext` module toolchain) | building/testing the Java packages |
| Gradle | wrapper included (`./gradlew`) | Java builds — no separate install |

Clone (no submodules — everything is in the one repo):

```powershell
git clone https://github.com/PagmoNET/pagmoNet
```

## Building the native layer

IPOPT is **never linked** into either base wrapper — the `ipopt` algorithm loads `libipopt` at
runtime via `dlopen`/`LoadLibrary` (supply it with a companion package, a system install, or
`PAGMONET_IPOPT_LIBRARY`). NLopt is a static compile-time link.

**C# (`PagmoWrapper`)** — from the repo root:

```powershell
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh pagmo.NET/scripts/build-native.ps1 -Configuration Release
# Windows → native/win-build/PagmoWrapper.dll ; Linux/macOS → native/build/libPagmoWrapper.{so,dylib}
```

**Java JNI (`pagmonet4j`)**:

```powershell
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh PagmoNet4j/scripts/build-native.ps1 -Configuration Release
# Windows → PagmoNet4j/pagmoWrapper/win-build/pagmonet4j.dll ; Linux/macOS → .../pagmoWrapper/build/libpagmonet4j.{so,dylib}
```

## Regenerating SWIG wrappers

Only needed after editing the `.i` files under `swig/`. Pre-generated wrappers are produced by CI;
a `.i` change affects **both** languages, so regenerate both and include the effect in your PR.

```powershell
# C#  (emits Pagmo.NET/pygmoWrappers/*.cs + native/GeneratedWrappers.cxx)
pwsh pagmo.NET/createSwigWrappersAndPlaceThem.ps1     # add -WithSnopt7 to compile in SNOPT7 (see the C# README FAQ)

# Java (emits core/src/generated/java/... + pagmoWrapper/generated/pagmonet4j_wrap.cxx)
swig -c++ -java -package io.github.pagmonet.pagmonet4j `
  -outdir PagmoNet4j/core/src/generated/java/io/github/pagmonet/pagmonet4j `
  -o PagmoNet4j/pagmoWrapper/generated/pagmonet4j_wrap.cxx `
  -Inative -Iswig swig/Pagmo4jSwigInterface.i
```

A native rebuild is only required when the generated C++ wrapper changes; a javacode/cscode-only
change (e.g. a new typed-log projection) needs a regenerate + recompile of the managed side.

## Running tests

**C#** — put the native DLL on the load path, then run:

```powershell
$env:PATH = "$(Resolve-Path native\win-build);$env:PATH"
dotnet test pagmo.NET/Tests/Tests.Pagmo.NET/Tests.Pagmo.NET.csproj -p:Platform=x64 --logger "console;verbosity=normal"
```

**Java + Kotlin** — point at the freshly built JNI dir:

```powershell
$env:PAGMO4J_NATIVE_DIR = "pagmoWrapper/win-build"   # Linux/macOS: "pagmoWrapper/build"
cd PagmoNet4j; ./gradlew :core:test :kotlin-ext:test
```

## Running examples

```powershell
# C#
dotnet run --project pagmo.NET/Examples/Examples.Pagmo.NET -- all

# Java / Kotlin
cd PagmoNet4j; ./gradlew :examples:run --args="all"
```

## The IPOPT companions

`Pagmo.NET.Ipopt` and `PagmoNet4j.ipopt` are **pure native payloads** — they contain no managed,
Java, SWIG, or pagmo code of their own, only `libipopt` and its dependency closure (MUMPS, OpenBLAS,
the GCC runtime). The `ipopt` algorithm and its bindings live in the base packages. So most changes
here are to packaging (the `.csproj` / `build.gradle.kts`, the bundling script, the release
workflow), not code.

The closure is assembled by the shared `scripts/bundle-native-deps.ps1` in the
`release-dotnet-ipopt.yml` / `release-java-ipopt.yml` workflows (from a conda-forge `ipopt nomkl`
env) and laid out under `runtimes/<rid>/native/` (C#) or `natives/<rid>/` (Java). To exercise a real
IPOPT solve locally, build the base wrapper as above and make a `libipopt` loadable — put a
conda-forge `ipopt` env's library dir on `PATH`, or set `PAGMONET_IPOPT_LIBRARY` — then run the base
tests (`IpoptSolveWhenAvailableTest` / the IPOPT solver tests skip themselves when no `libipopt` is
found). The `cleanroom/` projects are throwaway consumers used by the release clean-room gate.

## VS Code workflow (C#)

The repo ships tasks and launch configs in `.vscode/` (`Pagmo.NET: regenerate SWIG wrappers`,
`build native (Debug x64)`, `build tests`, `test`, `Run Tests` / `Debug Examples` launch configs).
Requirements: VS Build Tools 2022 / cmake + build-essential + swig, .NET 10 SDK, PowerShell Core,
vcpkg, and the `ms-dotnettools.csharp`, `ms-dotnettools.csdevkit`, `ms-vscode.cpptools`,
`ms-vscode.powershell` extensions. Build the native library before using the tasks. C++ include
paths for `pagmoWrapper.vcxproj` resolve via the `PagmoVcpkgInstalledDir` MSBuild property, the
`VCPKG_INSTALLED_DIR` env var, or the repo-relative `$(SolutionDir)..\vcpkg\installed` fallback.

## Pull requests

- Keep PRs focused — one concern per PR.
- Run the tests for every language you touched before opening.
- **Shared layer = both languages.** If you change `swig/` or `native/`, regenerate the wrappers,
  rebuild, and run the C# **and** Java/Kotlin tests — the four packages are meant to stay in lockstep.
- Releases are cut by pushing a `v*` tag, which builds and publishes all five packages together at
  one version.

## Licensing

Base packages are **MPL-2.0**, the IPOPT companions are **EPL-2.0**, and some native files derived
from pagmo stay LGPL-3.0. See [`LICENSING.md`](LICENSING.md) for the authoritative per-component
breakdown and each package's `THIRD_PARTY_LICENSES.md` / `NOTICE` for bundled third-party texts.
