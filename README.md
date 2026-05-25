# pagmoNet (shared layer)

SWIG interface files, shared C++ native bridge, and vcpkg ports for the [PagmoNet](https://github.com/PagmoNet) family of pagmo2 wrappers.

## Contents

| Directory | Purpose |
|---|---|
| `swig/` | SWIG `.i` interface files for C# and Java bindings |
| `native/` | Shared C++ bridge code (`managed_bridge.cpp`, headers, CMake) |
| `ports/pagmo2/` | Custom vcpkg port for pagmo2 |
| `.ai/` | AI context documents for the PagmoNet project |

## Used by

- [PagmoNet/pagmo.NET](https://github.com/PagmoNet/pagmo.NET) — C# / .NET bindings
- [PagmoNet/PagmoNet4j](https://github.com/PagmoNet/PagmoNet4j) — Java / Kotlin bindings
- [PagmoNet/ipopt](https://github.com/PagmoNet/ipopt) — IPOPT add-on

## License

LGPL-2.1-or-later. See [LICENSE](LICENSE).
