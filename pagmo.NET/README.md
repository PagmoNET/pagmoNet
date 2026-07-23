# Pagmo.NET

![Pagmo.NET](logo_small.png)

**Pagmo.NET** is a C# wrapper for [pagmo2](https://esa.github.io/pagmo2/), a C++ library
providing high-quality metaheuristic and gradient-based optimization routines with multi-island
parallel evolution support.

The wrapper is built with [SWIG 4.4](https://www.swig.org/) and supports Windows x64, Linux x64, and macOS (arm64 + x86_64 universal binary).

> **Using Java or Kotlin?** The same pagmo2 core is also wrapped as **[PagmoNet4j](https://github.com/PagmoNET/pagmoNet)** — the two APIs are kept deliberately close.

```
dotnet add package Pagmo.NET --version 1.0.0
```

The NuGet package is self-contained — native runtime libraries for Windows x64, Linux x64, and
macOS (universal binary) are bundled in `runtimes/`. No additional installation required.

For the built-in `ipopt` gradient-based solver, also add the companion native-runtime package:

```
dotnet add package Pagmo.NET.Ipopt --version 1.0.0
```

The base `Pagmo.NET` already contains the `ipopt` algorithm — it loads `libipopt` at runtime via
`dlopen`, so no IPOPT is linked into the (MPL-2.0) base. `Pagmo.NET.Ipopt` bundles a `libipopt` for
every platform; alternatively install IPOPT system-wide or point `PAGMONET_IPOPT_LIBRARY` at one.
Without it, `ipopt` reports unavailable — check `OptionalSolverAvailability.IsIpoptAvailable` first.

Source archives and individual native bundles are available at
`https://github.com/PagmoNET/pagmoNet/releases`.

---

## Building from source

Building Pagmo.NET from source — the native wrapper, the vcpkg dependencies, running the tests, the
VS Code workflow — is a contributor task; see **[CONTRIBUTING.md](CONTRIBUTING.md)**. Users of the
NuGet package never build anything: the native runtimes are bundled in the package.

---

## Managed problem architecture (C# UDP support)

1. User implements `IProblem` / `ManagedProblemBase` in C#
2. A SWIG director adapter (`problem_callback`) forwards calls to managed code
3. Native bridge wraps callback into `managed_problem` (`std::shared_ptr` owned)
4. A real `pagmo::problem` is built from `managed_problem`
5. `population`, `archipelago`, and BFE operator helpers consume that `pagmo::problem`

Ownership lives on the native side via `shared_ptr`, avoiding raw-pointer lifetime bugs.

**Minimal UDP:**
```csharp
public override DoubleVector fitness(DoubleVector x) { ... }
public override PairOfDoubleVectors get_bounds() { ... }
```

**Threading — thread-safe problems:** declare `ThreadSafety.Basic` or `ThreadSafety.Constant`
and the instance is shared directly across threads.

**Threading — cloneable non-thread-safe problems:** implement `IThreadCloneableProblem` and
override `Clone()` on `ManagedProblemBase`. The system creates one exclusive clone per island
(`archipelago`) or per OS thread (`thread_bfe`). The clone reports `ThreadSafety.Basic`
transparently so pagmo's native `thread_island` check passes. Problems returning `null` from
`Clone()` (the default) are still rejected on threaded entrypoints.

---

## FAQ

**Where's SNOPT7?**
SNOPT7 is proprietary and cannot be bundled. Users with a license can build from source:
1. Obtain SNOPT7 headers and compiled shared library from Stanford University.
2. Build pagmo2 with `-DPAGMO_WITH_SNOPT7=ON`.
3. Add `#define PAGMO_WITH_SNOPT7` to `swig/pagmo/config.hpp`.
4. Copy `pagmo/algorithms/snopt7.hpp` into `swig/pagmo/algorithms/`.
5. Run `pwsh scripts/regen-swig.ps1` then `pwsh scripts/build-native.ps1`.
6. Build `Pagmo.NET.csproj` — MSBuild detects the generated `snopt7.cs` automatically.

At runtime, pagmo's `snopt7` loads the solver DLL dynamically:
```csharp
using var solver = new snopt7(screenOutput: false, snopt7LibPath: "path/to/snopt7.dll", minorVersion: 6u);
```
Set `SNOPT7_LIB` to the DLL path to enable the live execution test.

**Is this affiliated with ESA or the pagmo2 team?**
No — Pagmo.NET is an independent .NET binding.

---

## Runnable examples

The example project references the **published** `Pagmo.NET.Ipopt` package, so it needs no build
toolchain — `dotnet run` restores the managed library and the native runtime straight from NuGet:

```bash
dotnet run --project Examples/Examples.Pagmo.NET/Examples.Pagmo.NET.csproj -- all
```

Scenarios: `single`, `archipelago`, `policies`, `maneuver`, `cloning`, `ipopt`. Add `--verbose`
to print algorithm logs.

Concept-first walkthroughs in `docs/`:
- `docs/getting-started.md`
- `docs/archipelago-topology-policies.md`
- `docs/algorithm-selection.md` — which algorithm class to instantiate for your problem type

---

## API naming

- Managed extension helpers use C#-style PascalCase.
- Existing snake_case entrypoints are retained for pagmo parity.
- See `.ai/API_NAMING_POLICY.md` for the full policy.

---

## License

Pagmo.NET is licensed under the **MPL-2.0**. See [LICENSE](LICENSE).

This package bundles pre-built native binaries from the following third-party projects, each under its own license (see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) and [NOTICE](NOTICE)):

| Component | License | Linking |
|-----------|---------|---------|
| pagmo2 | [LGPL-3.0-or-later / GPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0) | Static |
| Boost.Serialization | [BSL-1.0](https://www.boost.org/users/license.html) | Static |
| NLopt | [LGPL-2.1-or-later](https://www.gnu.org/licenses/lgpl-2.1) | Static |
| Intel TBB | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Static |
| Eigen3 | [MPL-2.0](https://www.mozilla.org/en-US/MPL/2.0/) | Header-only (no binary) |

pagmo2 and NLopt are statically linked under the LGPL; you may modify them and relink `PagmoWrapper` against your modified versions. Source for all bundled components is available from their upstream repositories.
