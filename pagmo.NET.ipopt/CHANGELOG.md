# Changelog

## v1.0.0

First stable release.

### Highlights

- **Self-sufficient superset.** `Pagmo.NET.Ipopt` no longer depends on the base `Pagmo.NET` package. It compiles the base sources directly and ships its own IPOPT-enabled native library, so it is a drop-in superset — reference this package **or** `Pagmo.NET`, never both (a build-time guard enforces this).
- **Bundled native dependencies on every platform.** The Windows x64, Linux x64, and macOS (arm64 + x86_64) packages carry the full IPOPT dependency closure in `runtimes/`, so the package works on a clean machine with no conda, MSYS2, or environment setup.
- **Clean-room verification.** CI installs the published-shaped package on a machine with no dev tools and runs an IPOPT solve, gating publish on the artifact actually working for a first-time user.
- **Single-source version** via `Directory.Build.props`.

### Breaking / Behavior Notes

- The previous "pure C# add-on that pulls in `Pagmo.NET` as a dependency" model is removed. Consumers on that model should switch to referencing `Pagmo.NET.Ipopt` alone.

## v1.0.0-beta.6

### Highlights

- Initial release as a standalone repository, split from `pagmoNet.ipopt`.
- Windows x64, Linux x64, and macOS (arm64 + x86_64 universal binary) are all supported via CI.
- NUnit test suite covering availability, instantiation, option setting, evolve improvement, and log extraction.
- `InternalsVisibleTo` grant in `Pagmo.NET` enables the add-on to access SWIG-internal plumbing from a separate assembly.

### Known limitations

- `GetLastOptimizationResultCode()` may return `-12` (Invalid_Option / HSL loader artefact) even when the solver successfully improves the objective. This is not a functional regression.
- MUMPS and SPRAL linear solvers are not included in this build; use `ma27`, `ma57`, `ma86`, `ma97`, or `pardiso`.
