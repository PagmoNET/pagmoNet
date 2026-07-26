# pagmoNet

SWIG interface files, shared C++ native bridge, and vcpkg ports for the [PagmoNet](https://github.com/PagmoNet) family of pagmo2 wrappers.

## Contents

| Directory | Purpose |
|---|---|
| `swig/` | SWIG `.i` interface files for C# and Java bindings |
| `native/` | Shared C++ bridge code (`managed_bridge.cpp`, headers, CMake) |
| `ports/pagmo2/` | Custom vcpkg port for pagmo2 |
| `.ai/` | AI context documents for the PagmoNet project |

## Sub-projects

This is a monorepo. The shared `native/` + `swig/` layer feeds four sub-projects:

- [`pagmo.NET/`](pagmo.NET/) — C# / .NET base bindings (MPL-2.0)
- [`pagmo.NET.ipopt/`](pagmo.NET.ipopt/) — C# IPOPT native runtime companion (EPL-2.0)
- [`PagmoNet4j/`](PagmoNet4j/) — Java / Kotlin base bindings (MPL-2.0)
- [`PagmoNet4j.ipopt/`](PagmoNet4j.ipopt/) — Java IPOPT native runtime companion (EPL-2.0)

## License

The shared original code in this repository (`native/`, `swig/`, build scripts) is licensed
**MPL-2.0** — see [LICENSE](LICENSE). The published packages carry their own licenses: **MPL-2.0**
for the base bindings (`pagmo.NET`, `PagmoNet4j`) and **EPL-2.0** for the IPOPT companions
(`pagmo.NET.ipopt`, `PagmoNet4j.ipopt`) — each has its own `LICENSE` file. See
[LICENSING.md](LICENSING.md) for the authoritative per-component breakdown, including the
statically-linked third-party libraries and their obligations.
