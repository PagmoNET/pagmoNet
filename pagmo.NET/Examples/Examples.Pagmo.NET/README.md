# Examples.Pagmo.NET

Runnable, non-test examples that teach both:
- how to use Pagmo.NET APIs, and
- why optimization structures like islands, archipelagos, topology, and policies matter.

Concept-first walkthrough pages that reference these examples live in:
- `docs/getting-started.md`
- `docs/archipelago-topology-policies.md`

## Run

From repo root:

```powershell
dotnet run --project Examples/Examples.Pagmo.NET/Examples.Pagmo.NET.csproj -- all
```

Scenario options:

```powershell
dotnet run --project Examples/Examples.Pagmo.NET/Examples.Pagmo.NET.csproj -- single
dotnet run --project Examples/Examples.Pagmo.NET/Examples.Pagmo.NET.csproj -- archipelago
dotnet run --project Examples/Examples.Pagmo.NET/Examples.Pagmo.NET.csproj -- policies
```

## What each scenario demonstrates

- `single`: baseline optimization on one island.
- `archipelago`: teaches topology connectivity intuition (`ring` vs `unconnected`) and compares single-island vs archipelago multi-start search results.
- `policies`: compares default policy wiring against explicit `fair_replace` + `select_best` policy wiring through archipelago APIs.

Topology tip: set an **empty** topology (e.g. `new ring()`) on an empty archipelago and then
`push_back` islands — the topology grows one vertex per island, so vertex and island counts stay in
sync. A *pre-sized* topology whose vertex count exceeds the island count (e.g. `new ring(8)` on an
archipelago that isn't already 8 islands) is rejected up front with a clear error, rather than
failing later inside `evolve`.

