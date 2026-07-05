# Changelog

## v1.0.0

First stable release.

### Highlights

- **Pure native payload.** `Pagmo.NET.Ipopt` ships only `libipopt` and its dependency closure and takes a hard dependency on the base `Pagmo.NET` package. The base carries the `ipopt` algorithm, which loads `libipopt` at runtime via `dlopen` (no IPOPT is ever linked into the base). Add this package **alongside** `Pagmo.NET` — you get both.
- **Bring-your-own IPOPT.** Prefer a system install or the `PAGMONET_IPOPT_LIBRARY` override? Use the base `Pagmo.NET` package on its own; the `ipopt` algorithm is there and will find your library.
- **Bundled native dependencies on every platform.** The Windows x64, Linux x64, and macOS (arm64 + x86_64) payloads carry the full IPOPT dependency closure (IPOPT, MUMPS, OpenBLAS, GCC runtime) under `runtimes/<rid>/native/`, so it works on a clean machine with no conda or environment setup.
- **Clean-room verification.** CI installs the published-shaped base + companion on a machine with no dev tools and runs an IPOPT solve, gating publish on the artifacts actually working for a first-time user.
- **Single-source version** via `Directory.Build.props`.

### Breaking / Behavior Notes

- The interim "self-sufficient superset" pre-release model (compile the base sources directly, link IPOPT, reference this package alone) is removed for licensing reasons: the base stays MPL-2.0 and never links EPL IPOPT. Depend on **both** `Pagmo.NET` and `Pagmo.NET.Ipopt`.

## v1.0.0-beta.6

### Highlights

- Initial release as a standalone repository, split from `pagmoNet.ipopt`.
- Windows x64, Linux x64, and macOS (arm64 + x86_64 universal binary) are all supported via CI.
- NUnit test suite covering availability, instantiation, option setting, evolve improvement, and log extraction.
- `InternalsVisibleTo` grant in `Pagmo.NET` enables the add-on to access SWIG-internal plumbing from a separate assembly.

### Known limitations

- `GetLastOptimizationResultCode()` may return `-12` (Invalid_Option / HSL loader artefact) even when the solver successfully improves the objective. This is not a functional regression.
- MUMPS and SPRAL linear solvers are not included in this build; use `ma27`, `ma57`, `ma86`, `ma97`, or `pardiso`.
