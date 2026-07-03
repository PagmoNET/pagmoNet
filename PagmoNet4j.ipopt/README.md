# PagmoNet4j.ipopt

[PagmoNet4j](https://github.com/samthegliderpilot/PagmoNet4j) with the IPOPT (Interior Point OPTimizer) nonlinear solver bundled — a self-sufficient **superset** of PagmoNet4j. Depend on this artifact **or** `pagmonet4j`, never both (Gradle enforces this via a capability conflict).

IPOPT is a gradient-based interior-point solver for large-scale nonlinear constrained optimization. It requires the problem to supply gradients (`has_gradient()` returns `true`).

## Requirements

- JDK 17+
- No dependency on the base `pagmonet4j` artifact — `pagmonet4j-ipopt` provides the full PagmoNet4j API plus IPOPT and declares the `pagmonet4j` capability, so depend on this **or** `pagmonet4j`, never both.
- No separate IPOPT installation required — the solver is statically linked into the native library

## Installation

Add the GitHub Packages repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/samthegliderpilot/PagmoNet4j.ipopt")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR") ?: "").get()
            password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN") ?: "").get()
        }
    }
}
dependencies {
    implementation("io.github.samthegliderpilot:pagmonet4j-ipopt:1.0.0")
}
```

> **GitHub Packages auth**: Create a [personal access token](https://github.com/settings/tokens) with `read:packages` scope and store it as `gpr.key` in `~/.gradle/gradle.properties`.

## Usage

```java
import io.github.samthegliderpilot.pagmonet4j.*;
import io.github.samthegliderpilot.pagmonet4j.problems.ManagedProblemBase;

class MyProblem extends ManagedProblemBase {
    @Override public DoubleVector fitness(DoubleVector x) { /* ... */ }
    @Override public PairOfDoubleVectors get_bounds()     { /* ... */ }
    @Override public boolean has_gradient()               { return true; }
    @Override public DoubleVector gradient(DoubleVector x){ /* ... */ }
}

try (MyProblem prob = new MyProblem();
     ipopt algo = new ipopt();
     population pop = new population(prob, 1L, 42L);
     population evolved = algo.evolve(pop)) {

    System.out.printf("champion f = %.6f%n", evolved.champion_f().get(0));
}
```

### Useful IPOPT options

| Option | Method | Description |
|---|---|---|
| `tol` | `set_numeric_option` | Convergence tolerance (default `1e-8`) |
| `max_iter` | `set_integer_option_u64` | Maximum iterations |
| `linear_solver` | `set_string_option` | `mumps` (default when available), `ma27`, `ma57`, `ma86`, `ma97` |
| `hessian_approximation` | `set_string_option` | `exact` or `limited-memory` (L-BFGS) |

### Known limitations

- SPRAL linear solver is not included in this build.

## License

This package is licensed under the **EPL-2.0**, matching IPOPT. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Related

- [PagmoNet4j](https://github.com/samthegliderpilot/PagmoNet4j) — base Java/Kotlin bindings
- [pagmoNet](https://github.com/samthegliderpilot/pagmoNet) — shared SWIG + native bridge
- [pagmo.NET.ipopt](https://github.com/samthegliderpilot/pagmo.NET.ipopt) — C# equivalent
