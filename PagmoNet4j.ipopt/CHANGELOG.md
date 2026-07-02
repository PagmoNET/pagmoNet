# Changelog

## v1.0.0

First stable release.

### Highlights

- **Self-sufficient superset.** `pagmonet4j-ipopt` bundles the full PagmoNet4j API plus IPOPT and declares the `pagmonet4j` capability, so it is a drop-in replacement — depend on this artifact **or** `pagmonet4j`, never both (Gradle enforces this via a capability conflict).
- **Self-contained JAR.** The published JAR bundles the IPOPT-enabled native library and its dependency closure under `natives/<rid>/` and extracts them at load time — no `java.library.path` or external IPOPT install required on Windows x64, Linux x64, or macOS (arm64 + x86_64).
- **Clean-room verification.** CI resolves the published artifact in a clean Gradle project and runs an IPOPT solve, gating publish on the artifact working for a first-time user.
- **Single-source version** via `gradle.properties`.

### Breaking / Behavior Notes

- The previous model that layered IPOPT on top of a separate `pagmonet4j` dependency is removed. Depend on `pagmonet4j-ipopt` alone.

## v1.0.0-beta.6

### Highlights

- Initial release as a standalone repository, split from `pagmoNet.ipopt`.
- Windows x64, Linux x64, and macOS (arm64 + x86_64) are all supported via CI.
- JUnit 5 test suite covering availability, instantiation, option setting, evolve improvement, and name verification.
- CI checks out PagmoNet4j, builds the native JNI library with IPOPT enabled, publishes `pagmonet4j` to `mavenLocal`, then runs the add-on tests.

### Known limitations

- The MA27 linear solver in this build uses IPOPT's HSL runtime-load model. The internal result code may not reflect full convergence status.
- MUMPS and SPRAL linear solvers are not included in this build.
