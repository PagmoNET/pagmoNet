# Release Notes

## v1.0.0

First stable release of the PagmoNet family — C# (`Pagmo.NET`) and Java/Kotlin (`PagmoNet4j`)
bindings for [pagmo2](https://esa.github.io/pagmo2/), built from one shared SWIG + native layer.

### Highlights

- **Deferred-load IPOPT (MPL base, EPL companion).** The base packages contain the `ipopt`
  algorithm but link **no** IPOPT — it loads `libipopt` at runtime via `dlopen`/`LoadLibrary`, so the
  base stays **MPL-2.0** and EPL-free (NLopt is statically included under LGPL). Add the
  `Pagmo.NET.Ipopt` / `pagmonet4j-ipopt` **EPL-2.0** companion to bundle a `libipopt` for every
  platform, install one system-wide, or point `PAGMONET_IPOPT_LIBRARY` at your own. Without one,
  `ipopt` degrades gracefully — check `OptionalSolverAvailability.IsIpoptAvailable` /
  `isIpoptAvailable()`.
- **Cross-language parity.** The C# and Java/Kotlin APIs are kept deliberately close (pagmo's
  snake_case names are identical across both). Both expose **typed structured logs** for every
  wrapped algorithm (`GetTypedLogLines()` / `getTypedLogLines()`), the managed problem/algorithm
  (UDP/UDA) architecture, thread-safe and cloneable problems, archipelago migration policies, and a
  `grid_search` demonstration UDA. A "Porting from Pagmo.NET" guide lists the handful of C#→Java
  translate-time differences.
- **Kotlin DSLs.** `pagmonet4j-kotlin` provides idiomatic Kotlin over islands, archipelagos,
  problems, and algorithms, plus multi-objective utilities, hypervolume, and batch-fitness evaluation.
- **Platforms.** Windows x64, Linux x64, and macOS (arm64 + x86_64 universal). Native runtimes are
  bundled in every package — no separate installation on any supported platform.
- **Reproducible release.** The release pipeline regenerates the SWIG bindings and rebuilds the
  native layer the same way the build/test pipeline does (ship == test); a single version tag
  publishes all five packages in lockstep at one single-sourced version.
- **Licensing.** Base packages are MPL-2.0; the IPOPT companions are EPL-2.0. Each package ships the
  verbatim texts of its statically-linked third-party components (pagmo2 / NLopt LGPL, GPL, Boost,
  Intel TBB) plus a `RELINKING.md` describing the LGPL relink path. See `LICENSING.md`.

### Breaking / Behavior Notes

- **Archipelago topology validation.** Setting a connected topology whose vertex count exceeds the
  archipelago's island count (e.g. `new ring(8)` on an archipelago that is not already 8 islands) now
  throws a clear error immediately, instead of failing later inside `evolve()` with a cryptic
  `cannot access the migrants of the island`. Set an empty topology on an empty archipelago and grow
  it with `push_back`, or size the topology to match the existing islands.
- No other breaking API changes from beta.6.

## v1.0.0-beta.6

### Highlights

**macOS support**

Windows x64, Linux x64, and macOS (arm64 + x86_64 universal binary) are all now supported.
CI builds on all three platforms. The NuGet package bundles the macOS universal binary at
`runtimes/osx/native/`. No additional installation required on macOS.

**Archipelago and island resource cleanup fixes**

- C#: `archipelago.Dispose()` now explicitly disposes per-island problem clones created
  for `IThreadCloneableProblem` users, instead of relying on GC finalization.
- Java: `archipelago.close()` now closes clone adapters on the same code path.
  `island.close()` removes its entry from the static `constructionRoots` map and closes
  the wrapped problem objects, eliminating a slow map-accumulation leak.

**Orbital maneuver example**

New `maneuver` scenario in both the C# and Java example runners: a 2-burn Hohmann-like
LEO→MEO transfer with SMA and eccentricity equality constraints solved via
`cstrs_self_adaptive` wrapping `de`. Demonstrates `ManagedProblemBase.get_nec()`,
constraint normalisation (SMA residual in km vs dimensionless eccentricity), and a
multi-seed retry loop for reliable stochastic convergence.

**Constraint normalisation fix in `ManeuverOptimizationProblem`**

SMA residual is now divided by 1000 before being returned as a fitness component, making
it dimensionless and comparable in scale to the eccentricity residual. Without this,
`cstrs_self_adaptive` ignored eccentricity because the SMA violation dominated by orders
of magnitude.

### Breaking / Behavior Notes

No breaking changes from beta.2.

---

## v1.0.0-beta.2

### Highlights

**Per-thread problem cloning (`IThreadCloneableProblem`)**

Managed problems that declare `ThreadSafety.None` can now participate in threaded
execution paths (`archipelago.push_back_island`, `thread_bfe`) by implementing
`IThreadCloneableProblem` and overriding `Clone()` on `ManagedProblemBase`:

```csharp
public override ThreadSafety get_thread_safety() => ThreadSafety.None;

public override IProblem Clone() => new MyProblem(); // fresh independent copy
```

Each island receives its own exclusive clone; `thread_bfe` creates one clone per OS
thread via `managed_thread_bfe`. Problems that return `null` from `Clone()` (the default)
continue to be rejected on threaded paths as before — the feature is fully opt-in.

**IPOPT included in Windows build**

The Windows NuGet package now includes IPOPT statically linked (via a vcpkg overlay port
that fixes LAPACK detection under the `x64-windows-static-md` triplet, with Intel MKL as
the BLAS/LAPACK backend to avoid the OpenBLAS `DllMain` conflict).
`OptionalSolverAvailability.IsIpoptAvailable` returns `true` out of the box on both
Windows and Linux.

**NuGet package improvements**

- Consumer-focused readme on NuGet.org (installation, quickstart, feature table).
- Package logo.
- `--verbose` flag added to the examples runner — prints algorithm logs after each scenario.

### Breaking / Behavior Notes

No breaking changes from beta.1.

`ManagedProblemBase` now implements `IThreadCloneableProblem` with `virtual Clone() => null`.
Subclasses that previously had no `Clone()` method are unaffected.

---

## v1.0.0-beta.1

> **Historical — superseded by v1.0.0.** This entry describes the pre-release "superset"
> model in which IPOPT was statically linked into the base and
> `OptionalSolverAvailability.IsIpoptAvailable` returned `true` out of the box. As of
> v1.0.0 the base links **no** IPOPT and loads `libipopt` at runtime via `dlopen`; IPOPT is
> unavailable unless the `Pagmo.NET.Ipopt` companion (or a system/`PAGMONET_IPOPT_LIBRARY`
> library) is present. The IPOPT rows in the environment matrix below reflect the old model.

### Overview

First public beta of Pagmo.NET - a C# wrapper for [pagmo2](https://esa.github.io/pagmo2/).
Targets Windows x64 and Linux x64. The managed library targets .NET 8 (consumers on .NET 8, 9, or 10 can reference it).

### Highlights

**Managed C# problem and algorithm extensibility**
- Implement `IProblem` / `ManagedProblemBase` to define custom optimization problems in pure C#.
  Full lifecycle (fitness, bounds, gradients, hessians, batch evaluation, seed, thread safety)
  is supported, with exception-safe callback boundaries.
- Implement `IAlgorithm` to define custom optimization algorithms in pure C#. Managed algorithms
  participate in `island` and `archipelago` type-erased execution paths (evolve, wait, migrate).

**Broad algorithm and problem coverage**
All major pagmo algorithms are wrapped with typed extension helpers and log projections:
`bee_colony`, `cmaes`, `compass_search`, `cstrs_self_adaptive`, `de`, `de1220`, `gaco`,
`gwo`, `ihs`, `maco`, `mbh`, `moead`, `moead_gen`, `nsga2`, `nspso`, `pso`, `pso_gen`,
`sade`, `sea`, `sga`, `simulated_annealing`, `xnes`.

Optional/feature-gated solvers: `ipopt`, `nlopt` (build-dependent; availability is
asserted by tests).  `snopt7` is supported for users who build from source with their
own SNOPT7 license; see README for build instructions.

All built-in benchmark problems are wrapped: `ackley`, `cec2006/2009/2013/2014`, `decompose`,
`dtlz`, `golomb_ruler`, `griewank`, `hock_schittkowski_71`, `inventory`, `lennard_jones`,
`luksan_vlcek1`, `minlp_rastrigin`, `null_problem`, `rastrigin`, `rosenbrock`, `schwefel`,
`translate`, `unconstrain`, `wfg`, `zdt`.

**Island and archipelago orchestration**
- Full `island` and `archipelago` managed API with topology wrappers (`ring`, `fully_connected`,
  `unconnected`, `free_form`), migration type/handling controls, and island snapshot access.
- Managed replacement and selection policies: `RPolicyCallback` / `SPolicyCallback` base classes
  for custom policies, plus built-in `fair_replace` / `select_best`.

**Batch fitness evaluators**
- `default_bfe`, `thread_bfe`, `member_bfe` with safe managed-problem dispatch.

**Multi-objective utilities**
- `non_dominated_front_2d`, `crowding_distance`, `sort_population_mo`, `select_best_N_mo`,
  `ideal`, `nadir`, `decompose_objectives`, `hypervolume` and related APIs.

**Runnable examples and docs**
- `Examples/Examples.Pagmo.NET`: three teaching scenarios (single-island, archipelago topology,
  migration policies).
- `docs/`: concept-first walkthroughs linked to runnable example code.

### Breaking / Behavior Notes

This is a first beta. No public stable API surface has been previously released.

**C# naming conventions**: bridge and enum types use PascalCase as idiomatic C#.
Pagmo-compatible snake_case is preserved for method names that mirror the pagmo C++ API
(e.g. `get_bounds()`, `fitness()`, `evolve()`).

Notable type renames from internal pre-release names:
- Enum types: `ThreadSafety`, `EvolveStatus`, `MigrationType`, `MigrantHandling`,
  `SgaCrossover`, `SgaMutation`, `SgaSelection`
- Bridge types: `ProblemCallback`, `ManagedProblem`, `AlgorithmCallback`, `ManagedAlgorithm`,
  `RPolicyCallback`, `ManagedRPolicy`, `SPolicyCallback`, `ManagedSPolicy`, `NullProblemCallback`

### Known Limitations

- **Thread-clone strategy** - Resolved in beta.2 via `IThreadCloneableProblem`.

### Supported Environment Matrix

| Environment | Status |
|---|---|
| Windows x64, .NET 8+ | Supported |
| Windows x64, IPOPT enabled | Included — statically linked via vcpkg (`coin-or-ipopt:x64-windows-static-md` with overlay port fixing LAPACK detection). `OptionalSolverAvailability.IsIpoptAvailable` returns `true` in the released build. |
| Windows x64, NLopt enabled | Included — statically linked via vcpkg (`nlopt:x64-windows-static-md`). `OptionalSolverAvailability.IsNloptAvailable` returns `true` in the released build. |
| Linux x64, .NET 8+ | Supported — `libPagmoWrapper.so` is fully self-contained (pagmo2, Boost, TBB, NLopt, IPOPT all statically linked via vcpkg `x64-linux-static-pic` triplet). Runtime requires only `libstdc++` and `libgcc`. |
| Linux x64, IPOPT enabled | Included — statically linked. `OptionalSolverAvailability.IsIpoptAvailable` returns `true` in the released build. |
| Linux x64, NLopt enabled | Included — statically linked. `OptionalSolverAvailability.IsNloptAvailable` returns `true` in the released build. |
| .NET Framework | Not supported |
| x86 / ARM | Not supported in v1 |
| macOS arm64 + x86_64 universal, .NET 8+ | Supported — `libPagmoWrapper.dylib` universal binary (pagmo2, Boost, TBB, NLopt statically linked via vcpkg `arm64-osx-static-pic` / `x64-osx-static-pic` triplets). Runtime requires only system `libstdc++` / `libc++`. |

Repo note:
- The shipped library/package target is `.NET 8` (consumers on .NET 8, 9, or 10 can reference it).
- Building and running the full test suite on Linux requires the .NET 10 SDK (the .NET 8 SDK has a VsTest discovery limitation on Linux that causes only ~198 of 593 tests to be enumerated).
- Tests, examples, and documentation tooling target `net10.0` internally.



