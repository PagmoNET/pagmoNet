# Relinking the LGPL components (LGPL-3.0 §4 / LGPL-2.1 §6)

The native JNI library shipped in this jar — `pagmonet4j.dll` (Windows),
`libpagmonet4j.so` (Linux), `libpagmonet4j.dylib` (macOS), under `natives/<rid>/` — 
**statically links** two LGPL libraries:

- **pagmo2** (LGPL-3.0-or-later)
- **NLopt** (LGPL-2.1-or-later)

The LGPL requires that you be able to modify those libraries and relink the wrapper
against your modified versions. This document is the "how" that the `NOTICE` and
`THIRD_PARTY_LICENSES.md` refer to. The full license texts are in
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

## The relinkable form is the public source

The Corresponding Source for the JNI library is public and builds reproducibly. Everything
needed to relink is in the repository:

- **Repository:** https://github.com/PagmoNET/pagmoNet
- **Wrapper source:** the shared `native/` and `swig/` layers plus the JNI build under
  `PagmoNet4j/pagmoWrapper/`.
- **Dependency pinning:** the vcpkg overlay ports under `ports/` (which fix `pagmo2` at the
  version noted in `THIRD_PARTY_LICENSES.md`) and the triplets under `triplets/`.
- **Build script:** `PagmoNet4j/scripts/build-native.ps1` (regenerate SWIG first with
  `PagmoNet4j/scripts/regen-swig.ps1`).

## Relinking against a modified pagmo2 or NLopt

1. Clone the repository at the tag matching this jar's version.
2. Point vcpkg at your modified library — either edit the relevant overlay port under
   `ports/` (e.g. change the `pagmo2` source ref to your fork) or install your modified
   `pagmo2` / `nlopt` into your vcpkg tree.
3. Regenerate and rebuild the JNI native:
   ```powershell
   pwsh PagmoNet4j/scripts/regen-swig.ps1
   pwsh PagmoNet4j/scripts/build-native.ps1 -Configuration Release
   ```
   This statically links your modified pagmo2 / NLopt into a fresh `pagmonet4j` native.
4. Replace the `natives/<rid>/` library inside the jar (or place your rebuilt native on
   `java.library.path` / `PAGMO4J_NATIVE_DIR`, which `NativeLoader` honours ahead of the
   bundled copy).

The Java classes bind to the native through JNI by library name, so a rebuilt native of the
same name is picked up with no changes to the Java layer.

## Notes

- The JNI library exposes the LGPL libraries through the generated C/C++ interface, so
  relinking does not require recompiling the Java classes.
- IPOPT is **not** part of this jar and is not covered here; it is loaded at runtime from the
  separate EPL-2.0 `pagmonet4j-ipopt` companion (or a system/`PAGMONET_IPOPT_LIBRARY`
  library) and is never statically linked.
