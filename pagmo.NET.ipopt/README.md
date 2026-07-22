# Pagmo.NET.Ipopt

The IPOPT (Interior Point OPTimizer) native runtime for [Pagmo.NET](https://github.com/PagmoNET/pagmoNet). This is a **pure native payload**: it bundles the `libipopt` shared library and its dependency closure (MUMPS, OpenBLAS, the GCC runtime) and nothing else. The base `Pagmo.NET` package already contains the `ipopt` algorithm, which loads this library at runtime via `dlopen`; this package simply supplies it so the algorithm works out of the box.

Add it **alongside** `Pagmo.NET` (this package depends on the base — you get both). If you would rather bring your own IPOPT (a system install, or the `PAGMONET_IPOPT_LIBRARY` override), use the base `Pagmo.NET` package on its own.

IPOPT is a gradient-based interior-point solver for large-scale nonlinear constrained optimization. It requires the problem to supply gradients (`has_gradient() = true`).

## Requirements

- .NET 8.0+
- The base **`Pagmo.NET`** package (a dependency of this one — restored automatically)
- No separate IPOPT installation required — the native binaries are bundled here

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

This package is licensed under the **EPL-2.0**, matching IPOPT. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

It bundles pre-built native binaries from the following third-party projects, each under its own license:

| Component | License | Linking |
|-----------|---------|---------|
| IPOPT | [EPL-2.0](https://opensource.org/licenses/EPL-2.0) | Dynamic (bundled), loaded at runtime via `dlopen` — never linked into the base |
| MUMPS | [CeCILL-C](https://cecill.info/licences/Licence_CeCILL-C_V1-en.html) | Dynamic (bundled), part of the IPOPT dependency closure |
| OpenBLAS | [BSD-3-Clause](https://opensource.org/licenses/BSD-3-Clause) | Dynamic (bundled), part of the IPOPT dependency closure |
| pagmo2 | [LGPL-3.0-or-later / GPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0) | Static (via base Pagmo.NET) |
| Boost.Serialization | [BSL-1.0](https://www.boost.org/users/license.html) | Static (via base Pagmo.NET) |
| NLopt | [LGPL-2.1-or-later](https://www.gnu.org/licenses/lgpl-2.1) | Static (via base Pagmo.NET) |
| Intel TBB | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Static (via base Pagmo.NET) |

pagmo2 and NLopt (from the base package) are statically linked under the LGPL; you may modify them and relink against your modified versions. Source for all bundled components is available from their upstream repositories. EPL-2.0 (IPOPT) permits use in proprietary applications without source-disclosure obligations.

## Related

- [pagmo.NET](https://github.com/PagmoNET/pagmoNet) — base C# bindings
- [pagmoNet](https://github.com/PagmoNET/pagmoNet) — shared SWIG + native bridge
- [PagmoNet4j.ipopt](https://github.com/PagmoNET/pagmoNet) — Java/Kotlin equivalent
