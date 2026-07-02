# Pagmo.NET.Ipopt

[Pagmo.NET](https://github.com/samthegliderpilot/pagmo.NET) with the IPOPT (Interior Point OPTimizer) nonlinear solver bundled — a self-sufficient **superset** of Pagmo.NET. Reference this package **or** Pagmo.NET, never both (a build-time guard enforces this).

IPOPT is a gradient-based interior-point solver for large-scale nonlinear constrained optimization. It requires the problem to supply gradients (`has_gradient() = true`).

## Requirements

- .NET 10.0+
- No dependency on the base `Pagmo.NET` package — this package bundles the full Pagmo.NET API plus IPOPT. Reference this **or** `Pagmo.NET`, never both.
- No separate IPOPT installation required — native binaries are bundled in the package

## Installation

Once published to NuGet.org:
```
dotnet add package Pagmo.NET.Ipopt --version 1.0.0
```

## Usage

```csharp
using pagmo;

// IPOPT requires gradients — implement has_gradient() and gradient() on your problem.
using var algo = new ipopt();
algo.set_integer_option("print_level", 0);   // suppress console output
algo.set_numeric_option("tol", 1e-8);        // convergence tolerance

using var island = Island.Create(algo, myGradientProblem, popSize: 1, seed: 42);
island.Evolve(1);
island.WaitCheck();
```

### Useful IPOPT options

| Option | Type | Description |
|---|---|---|
| `tol` | numeric | Convergence tolerance (default `1e-8`) |
| `max_iter` | integer | Maximum iterations (default `3000`) |
| `print_level` | integer | Console verbosity 0–12 (default `5`) |
| `linear_solver` | string | `mumps` (default when available), `ma27`, `ma57`, `ma86`, `ma97`, `pardiso` |
| `hessian_approximation` | string | `exact` or `limited-memory` (L-BFGS) |

### Log extraction

```csharp
using var evolved = algo.evolve(pop);
foreach (var line in algo.GetTypedLogLines())
    Console.WriteLine($"iter obj={line.Objective:F6} feasible={line.Feasible}");

int code = algo.GetLastOptimizationResultCode();
// 0 = Solve_Succeeded, 1 = Solved_To_Acceptable_Level
```

### Known limitations

- SPRAL linear solver is not included in this build.

## License

Wrapper code: LGPL-2.1-or-later. See [LICENSE](LICENSE).

This package bundles pre-built native binaries from the following third-party projects:

| Component | License | Linking |
|-----------|---------|---------|
| IPOPT | [EPL-2.0](https://opensource.org/licenses/EPL-2.0) | Static on Linux; dynamic (bundled DLL/dylib) on Windows/macOS |
| MUMPS | [LGPL-2.1-or-later](https://www.gnu.org/licenses/lgpl-2.1) | Dynamic (bundled), part of conda-forge IPOPT on Windows/macOS |
| OpenBLAS | [BSD-3-Clause](https://opensource.org/licenses/BSD-3-Clause) | Dynamic (bundled), part of conda-forge IPOPT on Windows/macOS |
| pagmo2 | [LGPL-2.1-or-later](https://www.gnu.org/licenses/lgpl-2.1) | Static (via base Pagmo.NET) |
| Boost.Serialization | [BSL-1.0](https://www.boost.org/users/license.html) | Static (via base Pagmo.NET) |
| NLopt | [LGPL-2.1-or-later](https://www.gnu.org/licenses/lgpl-2.1) | Static (via base Pagmo.NET) |
| Intel TBB | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Static (via base Pagmo.NET) |

LGPL components are statically embedded in the distributed binary on Linux. The source code for all components is available via their upstream repositories; rebuild from source for relinking under LGPL terms. EPL-2.0 (IPOPT) permits use in proprietary applications without source disclosure obligations.

## Related

- [pagmo.NET](https://github.com/samthegliderpilot/pagmo.NET) — base C# bindings
- [pagmoNet](https://github.com/samthegliderpilot/pagmoNet) — shared SWIG + native bridge
- [PagmoNet4j.ipopt](https://github.com/samthegliderpilot/PagmoNet4j.ipopt) — Java/Kotlin equivalent
