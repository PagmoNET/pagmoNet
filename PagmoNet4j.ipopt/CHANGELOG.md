# Changelog

## v1.0.0

First stable release.

### Highlights

- **Pure native payload.** `pagmonet4j-ipopt` ships only `libipopt` and its dependency closure and takes a hard dependency on the base `pagmonet4j` artifact. The base carries the `ipopt` algorithm, which loads `libipopt` at runtime via `dlopen` (no IPOPT is ever linked into the base). Depend on this artifact **alongside** `pagmonet4j` — you get both.
- **Bring-your-own IPOPT.** Prefer a system install or the `PAGMONET_IPOPT_LIBRARY` override? Depend on the base `pagmonet4j` artifact on its own; the `ipopt` algorithm is there and will find your library.
- **Self-contained payload.** The published JAR bundles the IPOPT dependency closure (IPOPT, MUMPS, OpenBLAS, GCC runtime) under `natives/<rid>/`; the base `NativeLoader` extracts it at load time — no `java.library.path` or external IPOPT install required on Windows x64, Linux x64, or macOS (arm64 + x86_64).
- **Clean-room verification.** CI resolves the published-shaped base + companion in a clean Gradle project and runs an IPOPT solve, gating publish on the artifacts working for a first-time user.
- **Single-source version** via `gradle.properties`.

### Breaking / Behavior Notes

- The interim "self-sufficient superset" pre-release model (bundle the whole API, link IPOPT, depend on this artifact alone) is removed for licensing reasons: the base stays MPL-2.0 and never links EPL IPOPT. Depend on **both** `pagmonet4j` and `pagmonet4j-ipopt`.

## v1.0.0-beta.6

### Highlights

- Initial release as a standalone repository, split from `pagmoNet.ipopt`.
- Windows x64, Linux x64, and macOS (arm64 + x86_64) are all supported via CI.
- JUnit 5 test suite covering availability, instantiation, option setting, evolve improvement, and name verification.
- CI checks out PagmoNet4j, builds the native JNI library with IPOPT enabled, publishes `pagmonet4j` to `mavenLocal`, then runs the add-on tests.

### Known limitations

- The MA27 linear solver in this build uses IPOPT's HSL runtime-load model. The internal result code may not reflect full convergence status.
- MUMPS and SPRAL linear solvers are not included in this build.
