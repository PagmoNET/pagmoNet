# Contributing to PagmoNet4j.ipopt

`pagmonet4j-ipopt` is a **pure native payload** in the `pagmoNet` monorepo: it has no Java,
SWIG, or pagmo code of its own. The `ipopt` algorithm and its bindings live in the base
`pagmonet4j` artifact (under `PagmoNet4j/`); this artifact only bundles `libipopt` and its
dependency closure under `natives/<rid>/`, which the base's `NativeLoader` extracts and the base
loads at runtime via `dlopen`. So most changes here are to packaging (the `build.gradle.kts`, the
bundling script, the workflow), not code.

## Prerequisites

- JDK 17+
- Gradle (wrapper included)
- PowerShell 7+ (`pwsh`) — for `scripts/bundle-native-deps.ps1`
- To exercise a real IPOPT solve locally: a `libipopt` on the load path (e.g. a conda-forge
  `ipopt` env), or set `PAGMONET_IPOPT_LIBRARY` to one

## Cloning

```powershell
git clone https://github.com/samthegliderpilot/pagmoNet
```

No submodules — the shared `native/` + `swig/` layer and all four sub-projects live in the one repo.

## Building and testing

The companion itself ships no binary you build; to test it, build the shared base JNI wrapper once,
then run the base tests with a `libipopt` available (a real solve is exercised by
`IpoptSolveWhenAvailableTest`, which is skipped when no `libipopt` is loadable):

```powershell
$env:VCPKG_ROOT = "C:\vcpkg"
pwsh PagmoNet4j/scripts/build-native.ps1 -Configuration Release   # base JNI wrapper (no IPOPT linked)
$env:PATH = "C:\path\to\ipopt\bin;" + $env:PATH                   # a libipopt for the dlopen probe
cd PagmoNet4j; ./gradlew :core:test
```

Packaging (bundle the `libipopt` closure into `natives/<rid>/`, assemble the payload JAR) is driven
by `.github/workflows/release-java-ipopt.yml` via `scripts/bundle-native-deps.ps1`; see that
workflow for the exact per-platform steps.

## Repo layout (this sub-project)

| Path | Contents |
|---|---|
| `build.gradle.kts` | The payload JAR: `api`-depends on `pagmonet4j`, bundles the staged `natives/<rid>/*` closure |
| `cleanroom/` | Throwaway consumer used by the release clean-room gate |

The shared bundling script lives at the monorepo root: `scripts/bundle-native-deps.ps1`.

## License

EPL-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
