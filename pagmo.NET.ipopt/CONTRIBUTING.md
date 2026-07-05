# Contributing to Pagmo.NET.Ipopt

`Pagmo.NET.Ipopt` is a **pure native payload** in the `pagmoNet` monorepo: it has no managed,
SWIG, or pagmo code of its own. The `ipopt` algorithm and its bindings live in the base
`Pagmo.NET` package (under `pagmo.NET/`); this package only bundles `libipopt` and its dependency
closure, which the base loads at runtime via `dlopen`. So most changes here are to packaging
(the `.csproj`, the bundling script, the workflow), not code.

## Prerequisites

- .NET 10 SDK
- PowerShell 7+ (`pwsh`) — for `scripts/bundle-native-deps.ps1`
- To exercise a real IPOPT solve locally: a `libipopt` on the load path (e.g. a conda-forge
  `ipopt` env), or set `PAGMONET_IPOPT_LIBRARY` to one

## Cloning

```powershell
git clone https://github.com/samthegliderpilot/pagmoNet
```

No submodules — the shared `native/` + `swig/` layer and all four sub-projects live in the one repo.

## Building and testing

The companion itself ships no binary you build; to test it, build the shared base native once,
then run the companion tests with a `libipopt` available:

```powershell
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh scripts/build-native.ps1 -Configuration Release          # builds the base PagmoWrapper (no IPOPT linked)
$env:PATH = "C:\path\to\ipopt\bin;" + $env:PATH               # a libipopt for the dlopen probe
dotnet test pagmo.NET.ipopt/Tests/Tests.Pagmo.NET.Ipopt.csproj -p:Platform=x64
```

Packaging (bundle the `libipopt` closure, pack the payload `.nupkg`) is driven by
`.github/workflows/release-dotnet-ipopt.yml` via `scripts/bundle-native-deps.ps1`; see that
workflow for the exact per-platform steps.

## Repo layout (this sub-project)

| Path | Contents |
|---|---|
| `Pagmo.NET.Ipopt.csproj` | The payload package: depends on `Pagmo.NET`, packs the staged `runtimes/<rid>/native/*` closure |
| `Tests/` | NUnit tests exercised against the base + a runtime `libipopt` |
| `cleanroom/` | Throwaway consumer used by the release clean-room gate |
| `buildTransitive/` | MSBuild targets that place the payload next to the base wrapper in consumer output |

The shared bundling script lives at the monorepo root: `scripts/bundle-native-deps.ps1`.

## License

EPL-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
