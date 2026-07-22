# Relinking the LGPL components (LGPL-3.0 §4 / LGPL-2.1 §6)

The native runtime library shipped in this package — `PagmoWrapper.dll` (Windows),
`libPagmoWrapper.so` (Linux), `libPagmoWrapper.dylib` (macOS) — **statically links**
two LGPL libraries:

- **pagmo2** (LGPL-3.0-or-later)
- **NLopt** (LGPL-2.1-or-later)

The LGPL requires that you be able to modify those libraries and relink the wrapper
against your modified versions. This document is the "how" that the `NOTICE` and
`THIRD_PARTY_LICENSES.md` refer to. The full license texts are in
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

## The relinkable form is the public source

The Corresponding Source for `PagmoWrapper` is public and builds reproducibly. Everything
needed to relink is in the repository:

- **Repository:** https://github.com/PagmoNET/pagmoNet
- **Wrapper source:** the shared `native/` and `swig/` layers (MPL-2.0 / pagmo-derived
  files as marked).
- **Dependency pinning:** the vcpkg overlay ports under `ports/` (which fix `pagmo2` at
  the version noted in `THIRD_PARTY_LICENSES.md`) and the triplets under `triplets/`.
- **Build script:** `scripts/build-native.ps1`.

## Relinking against a modified pagmo2 or NLopt

1. Clone the repository at the tag matching this package's version.
2. Point vcpkg at your modified library — either edit the relevant overlay port under
   `ports/` (e.g. change the `pagmo2` source ref to your fork) or install your modified
   `pagmo2` / `nlopt` into your vcpkg tree.
3. Rebuild the native wrapper:
   ```powershell
   pwsh scripts/build-native.ps1 -Configuration Release
   ```
   This statically links your modified pagmo2 / NLopt into a fresh `PagmoWrapper`.
4. Replace the `runtimes/<rid>/native/PagmoWrapper.*` file in your copy of the package (or
   in your application's output) with the one you just built.

The managed `Pagmo.NET` assembly loads `PagmoWrapper` by name through the standard native
library resolution path, so a rebuilt wrapper of the same name is picked up with no changes
to the managed layer.

## Notes

- `PagmoWrapper` exposes the LGPL libraries through a C-compatible interface, so relinking
  does not require rebuilding the managed assembly.
- IPOPT is **not** part of this package and is not covered here; it is loaded at runtime
  from the separate EPL-2.0 `Pagmo.NET.Ipopt` companion (or a system/`PAGMONET_IPOPT_LIBRARY`
  library) and is never statically linked.
