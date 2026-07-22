# PagmoNet4j

Java and Kotlin bindings for [pagmo2](https://github.com/esa/pagmo2), part of the [PagmoNet](https://github.com/PagmoNET) family.

```kotlin
// build.gradle.kts
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/PagmoNET/pagmoNet")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR") ?: "").get()
            password = providers.gradleProperty("gpr.token").orElse(System.getenv("GITHUB_TOKEN") ?: "").get()
        }
    }
}
dependencies {
    implementation("io.github.pagmonet:pagmonet4j:1.0.0")
    // optional: Kotlin DSL extensions
    implementation("io.github.pagmonet:pagmonet4j-kotlin:1.0.0")
    // optional: IPOPT gradient-based solver native runtime (see below)
    implementation("io.github.pagmonet:pagmonet4j-ipopt:1.0.0")
}
```

> **GitHub Packages auth**: GitHub requires authentication even for public packages. Create a [personal access token](https://github.com/settings/tokens) with `read:packages` scope and store it as `gpr.token` in `~/.gradle/gradle.properties`, or set `GITHUB_TOKEN` in your environment.

> **IPOPT (`pagmonet4j-ipopt`)**: The base `pagmonet4j` already contains the `ipopt` algorithm — it loads `libipopt` at runtime via `dlopen`, so no IPOPT is linked into the (MPL-2.0) base. Add `pagmonet4j-ipopt` to bundle a `libipopt` (it carries the native for every platform), or bring your own via a system install or the `PAGMONET_IPOPT_LIBRARY` environment variable. Without it, `ipopt` simply reports unavailable — check `OptionalSolverAvailability.isIpoptAvailable()` before use.

## Quickstart

```java
import io.github.pagmonet.pagmonet4j.*;
import io.github.pagmonet.pagmonet4j.problems.ManagedProblemBase;

// 1. Define your problem — minimise x² + (y-3)²
class MyProblem extends ManagedProblemBase {
    @Override public DoubleVector fitness(DoubleVector x) {
        double f = x.get(0)*x.get(0) + Math.pow(x.get(1)-3, 2);
        return vec(f);
    }
    @Override public PairOfDoubleVectors get_bounds() {
        return bounds(new double[]{-10, -10}, new double[]{10, 10});
    }
}

// 2. Evolve with Differential Evolution
try (MyProblem prob = new MyProblem();
     problem p = new problem(prob);
     de algo = new de(100);
     algorithm a = new algorithm(algo);
     population pop = new population(p, 20, 42L)) {
    pop = a.evolve(pop);
    DoubleVector cx = pop.champion_x();
    System.out.printf("champion: (%.4f, %.4f)%n", cx.get(0), cx.get(1));
    cx.delete();
}
```

The champion should converge near `(0.0, 3.0)` — the global minimum.

## Choosing an algorithm

`de` is a solid default, but the right algorithm depends on whether your problem is
single- or multi-objective, constrained, or mixed-integer. See the
[Algorithm Selection Guide](docs/algorithm-selection.md) for a table mapping each wrapped
pagmo2 algorithm to its problem category.

## Running the examples

The examples resolve the **published** `pagmonet4j` packages from GitHub Packages — no native build
required. GitHub Packages needs a token even for *public* packages, so there's a one-time step:

1. Create a GitHub Personal Access Token (classic) with the **`read:packages`** scope.
2. Add it to `~/.gradle/gradle.properties` (create the file if needed):
   ```properties
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.token=YOUR_read_packages_PAT
   ```
   (or set the `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables instead).

Then run any scenario:

```bash
./gradlew :examples:run --args single      # single-island DE
./gradlew :examples:run --args archipelago # multi-island parallel evolution
./gradlew :examples:run --args policies    # migration policies
./gradlew :examples:run --args maneuver    # constrained orbital-transfer problem
./gradlew :examples:run --args cloning     # thread-safe problem cloning
./gradlew :examples:run --args ipopt       # IPOPT gradient-based solve
./gradlew :examples:run --args kotlin      # Kotlin DSL
./gradlew :examples:run --args all         # all of the above
```

## Building from source

Building `pagmonet4j` from source — the native JNI library plus running the tests — is a contributor
task; see **[CONTRIBUTING.md](CONTRIBUTING.md)**. Users of the published package never build anything:
the jar carries the native library for every platform.

## Threading

`ThreadSafety` controls how PagmoNet4j uses your problem across islands:

| Value | Meaning | What to do |
|-------|---------|------------|
| `None` (default) | Not thread-safe | Implement `IThreadCloneableProblem.clone()` to return an independent copy; PagmoNet4j creates one per island automatically |
| `Basic` | Safe for concurrent reads | No cloning needed |
| `Constant` | Fully immutable | No cloning needed |

For BFE (batch fitness evaluation), implement `has_batch_fitness()` + `batch_fitness()` and use `ManagedThreadBfe`.

## Known limitations (v1.0)

- **Native bundling** — the published JAR bundles native libraries for Windows x64, Linux x64, and macOS (arm64 + x86_64 universal). No separate installation required on any supported platform.
- **Object lifecycle** — use try-with-resources (`try (var p = new problem(...))`) whenever possible. If you don't call `close()`, cleanup is finalizer-based and non-deterministic.
- **`free_form` topology** — dynamic edge add/remove is not yet exposed in Java.
- **Gradient/hessian** — wrapped and tested for basic cases; sparse Hessian patterns have limited test coverage.
- **Typed log projections** — every algorithm exposes structured logs, but the level differs: `bee_colony`, `cmaes`, `compass_search`, `de`, `ihs`, `mbh`, `pso`, `sade`, `sea`, `sga`, and `simulated_annealing` provide a typed `getTypedLogLines()` (and a populated `getLogLines()`). The remaining algorithms (e.g. `nsga2`, `moead`, `gaco`, `xnes`, `ipopt`, `nlopt`) currently expose only the raw `get_log_entry(i)` / `get_log_entry_count()` primitives — their `getLogLines()` returns an empty list. (The C# binding provides typed projections for all of these; Java parity is planned.)
- **Kotlin wrappers** — BFE, hypervolume, and multi-objective utilities are available via the Java API; Kotlin convenience wrappers are not yet complete.

## License

PagmoNet4j is licensed under the **MPL-2.0**. See [LICENSE](LICENSE).

This package bundles pre-built native binaries from the following third-party projects, each under its own license (see [NOTICE](NOTICE)):

| Component | License | Linking |
|-----------|---------|---------|
| pagmo2 | [LGPL-3.0-or-later / GPL-3.0-or-later](https://www.gnu.org/licenses/lgpl-3.0) | Static |
| Boost.Serialization | [BSL-1.0](https://www.boost.org/users/license.html) | Static |
| NLopt | [LGPL-2.1-or-later](https://www.gnu.org/licenses/lgpl-2.1) | Static |
| Intel TBB | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Static |

pagmo2 and NLopt are statically linked under the LGPL; you may modify them and relink `pagmonet4j` against your modified versions. Source for all bundled components is available from their upstream repositories.

## Related

- [pagmo.NET](https://github.com/PagmoNET/pagmoNet) — C# / .NET bindings
- [pagmoNet](https://github.com/PagmoNET/pagmoNet) — shared SWIG + native bridge (monorepo root)
- [PagmoNet4j.ipopt](https://github.com/PagmoNET/pagmoNet) — IPOPT native runtime companion (bundles libipopt)
