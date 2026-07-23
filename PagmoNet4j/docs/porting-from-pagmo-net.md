# Porting from Pagmo.NET (C#) to PagmoNet4j (Java)

Both bindings wrap the same pagmo2 core through SWIG, so **most of the API is identical** — pagmo's
`snake_case` names (`fitness`, `get_bounds`, `get_nobj`, `evolve`, `champion_x`, `get_log_entry`, …)
are the same in C# and Java, and the ergonomic helpers were built to match. This guide lists the
handful of translate-time differences a C#-to-Java port has to account for. Everything not listed
here translates verbatim.

## At a glance

| Concern | Pagmo.NET (C#) | PagmoNet4j (Java) |
|---|---|---|
| Package / namespace | `using pagmo;` | `import io.github.pagmonet.pagmonet4j.*;` plus `…​.algorithms.*`, `…​.problems.*` |
| Object lifecycle | `IDisposable` / `Dispose()` / `using (…)` | `AutoCloseable` / `close()` / `try (… )` |
| Optional-solver check | `OptionalSolverAvailability.IsIpoptAvailable` (property) | `OptionalSolverAvailability.isIpoptAvailable()` (method) |
| Unsigned integers | `uint` / `ulong` | `long` (Java has no unsigned types) |
| Typed log line record | `de.DeLogLine { Generation, … }` | `de.DeLogLine { generation(), … }` |
| Log accessors | `GetTypedLogLines()`, `GetLogLines()` | `getTypedLogLines()`, `getLogLines()` |
| Algorithm/class names | `new de(…)`, `new nsga2(…)`, `new grid_search(…)` | same lowercase names: `new de(…)`, `new nsga2(…)`, `new grid_search(…)` |

## The differences in detail

### 1. Package and imports
`using pagmo;` becomes `import io.github.pagmonet.pagmonet4j.*;`. Two sub-packages hold the
hand-written surface: algorithms (`IAlgorithm`, `grid_search`, `OptionalSolverAvailability`, the log
interfaces) live in `io.github.pagmonet.pagmonet4j.algorithms`, and managed-problem support
(`ManagedProblemBase`, `IProblem`, `IThreadCloneableProblem`) in `io.github.pagmonet.pagmonet4j.problems`.

### 2. Lifecycle: `Dispose()` → `close()`
Native objects are disposable on both sides, but the mechanism differs:

```csharp
// C#
using var prob = new MyProblem();
using var algo = new de(100);
```
```java
// Java
try (MyProblem prob = new MyProblem();
     de algo = new de(100L)) { … }
```

When you subclass `ManagedProblemBase`, override **`close()`** in Java where you overrode
**`Dispose()`** in C#. A naïve translator that emits `dispose()` overrides nothing — rename it to
`close()`.

### 3. Booleans are properties in C#, methods in Java
This is the only public property on the surface, and the one spot a literal translator trips on:

```csharp
if (OptionalSolverAvailability.IsIpoptAvailable) { … }   // property
```
```java
if (OptionalSolverAvailability.isIpoptAvailable()) { … } // method
```

Same for `IsNloptAvailable` → `isNloptAvailable()`.

### 4. Unsigned → `long`
Java has no unsigned types, so every C# `uint`/`ulong` becomes `long`: `set_seed(uint)` →
`set_seed(long)`, `get_nobj()` returns `long`, `new de(100)` → `new de(100L)`. Overrides must target
the `long` signatures.

### 5. Typed log lines
Every wrapped algorithm exposes a typed log projection on both sides, with matching fields — only the
casing changes under normal C#→Java rules:

```csharp
IReadOnlyList<de.DeLogLine> log = algo.GetTypedLogLines();
double best = log[0].BestFitness;
string s   = log[0].ToDisplayString();
```
```java
java.util.List<de.DeLogLine> log = algo.getTypedLogLines();
double best = log.get(0).bestFitness();   // record component -> accessor method
String s    = log.get(0).toDisplayString();
```

The generic surface matches too: `GetLogLines()` → `getLogLines()`, and on each line
`RawFields` → `getRawFields()`, `AlgorithmName` → `getAlgorithmName()`, `ToDisplayString()` →
`toDisplayString()`. The `RawFields`/`getRawFields()` map uses the **same** snake_case keys
(`"generation"`, `"function_evaluations"`, …) in both languages.

### 6. IPOPT specifics
- `GetLastOptimizationResultCode()` → `getLastOptimizationResultCode()` (both also expose the SWIG
  primitive `get_last_opt_result_code()`).
- Option setters are identical: `set_integer_option(name, value)`, `set_numeric_option`,
  `set_string_option`. For an integer value above `int` range use `set_integer_option_u64`.
- IPOPT ships separately for licensing reasons; guard real solves with the availability check in §3.

### 7. Class names stay lowercase
pagmo's algorithm and problem classes keep their pagmo `snake_case` names in Java — `de`, `sade`,
`nsga2`, `xnes`, `grid_search` — rather than being PascalCased, precisely so translated C# code
resolves without renaming. When you write your **own** UDA in Java you may of course follow Java
naming conventions; `grid_search` (a hand-written `IAlgorithm`, the reference example for building a
custom algorithm) keeps the lowercase name only to mirror the C# demo.

## What has full parity

Typed logs for every algorithm present in both languages, `grid_search`, the IPOPT result-code
alias, and the option setters are all present on the Java side — a C# app that uses them ports
without hitting a missing member. (`snopt7` is C#-only for now: its solver is proprietary and not
built into the Java package; it is the one algorithm without a Java form.)
