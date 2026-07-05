# pagmoNet (shared layer)

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

MPL-2.0. See [LICENSE](LICENSE).
