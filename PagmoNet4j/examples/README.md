# PagmoNet4j Examples

Runnable examples that teach both how to use PagmoNet4j APIs and why optimization structures
(islands, archipelagos, topology, policies) matter in practice.

## Prerequisites

- **JDK 21** — the examples build with a Java 21 toolchain (Gradle provisions it if missing). Run
  Gradle itself on a JDK it supports (17–24); JDK 26 is newer than the bundled Gradle 9.0 supports.
- **A GitHub `read:packages` token** — GitHub Packages requires authentication even for *public*
  packages. Create a [personal access token](https://github.com/settings/tokens) (classic) with the
  **`read:packages`** scope, then put it in your **global** `~/.gradle/gradle.properties` (create the
  file if needed) so it lives outside the project and never gets committed:

  ```properties
  gpr.user=YOUR_GITHUB_USERNAME
  gpr.token=YOUR_read_packages_PAT
  ```

  (Or set the `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables instead.)

## Run

The examples resolve the **published** `pagmonet4j` packages from GitHub Packages — no native build
required. Once the token above is in place, from the repo root:

```powershell
.\gradlew :examples:run --args="all"
```

Add `--verbose` (or `-v`) to print algorithm logs after each scenario:

```powershell
.\gradlew :examples:run --args="all --verbose"
```

## Scenarios

| Scenario | Description |
|----------|-------------|
| `single` | Baseline: one optimizer on one island. |
| `archipelago` | Topology intuition + multi-island vs single-island comparison. |
| `policies` | Default policy wiring vs managed `IRPolicy`/`ISPolicy` callbacks. |
| `maneuver` | 2-burn Hohmann-like transfer with equality constraints via `cstrs_self_adaptive`. |
| `cloning` | Non-thread-safe problem with `clone()` for parallel island search. |
| `ipopt` | IPOPT gradient-based solve, with graceful degradation if the IPOPT runtime is absent. |
| `kotlin` | Kotlin-idiomatic version using the `kotlin-ext` DSL extensions. |
| `all` | All scenarios in sequence (default). |

## What each scenario demonstrates

- **single**: `island.create(algo, problem, popSize, seed)` — the simplest path from problem to result.
- **archipelago**: `buildArchipelago {}` with `withTopology(ring())` — how topology affects exploration.
- **policies**: `IRPolicy` / `ISPolicy` callbacks — observe and customize migration behaviour.
- **maneuver**: `ManagedProblemBase` with `get_nec()` + `cstrs_self_adaptive` wrapping `de` — equality-constrained optimisation of a 2-burn LEO→MEO transfer. Demonstrates constraint normalisation and multi-seed retry for reliable convergence.
- **cloning**: `IThreadCloneableProblem.clone()` — safely run a non-thread-safe problem in parallel.
- **ipopt**: `ipopt` gradient-based local solve of `min x² + (y-3)²`. Checks
  `OptionalSolverAvailability.isIpoptAvailable()` first — with the `pagmonet4j-ipopt` runtime present it
  solves for real; without it, prints a friendly note and skips (the optional-solver pattern).
- **kotlin**: The same scenarios using the Kotlin extension DSL (`buildArchipelago`, `islandOf`,
  `withTopology`, `pushBackIslandWithPolicies`, `bestChampionF`, etc.).
