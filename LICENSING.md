# Licensing

This repository is a monorepo containing four published packages plus a shared
native/binding layer. Different parts carry different licenses. This document is
the single source of truth for how it all fits together; each package also ships
its own `LICENSE` and (where third-party code is bundled) `NOTICE` file.

## Summary

| Component | License | Notes |
|---|---|---|
| **Pagmo.NET** (C# base) | MPL-2.0 | statically links LGPL/permissive third-party libraries |
| **PagmoNet4j** (Java base) | MPL-2.0 | statically links LGPL/permissive third-party libraries |
| **Pagmo.NET.Ipopt** (C# companion) | EPL-2.0 | ships the IPOPT native library + its dependency closure |
| **PagmoNet4j.ipopt** (Java companion) | EPL-2.0 | ships the IPOPT native library + its dependency closure |
| Shared `native/`, `swig/`, root build scripts | MPL-2.0 | original work; the umbrella covered by the root `LICENSE` |

The root `LICENSE` is MPL-2.0 and covers the shared original code. Per-package
`LICENSE` files govern each published package.

## The two families

### Base packages — MPL-2.0

`Pagmo.NET` and `PagmoNet4j` are our original wrapper code (SWIG interface files,
hand-written C#/Java/Kotlin extensions, the native bridge, and CMake) licensed
under the **Mozilla Public License 2.0**. MPL-2.0 is a file-level copyleft:
modifications to MPL-covered files must stay MPL and be shared, but the files may
be combined with code under other licenses.

The base packages do **not** contain or link IPOPT. The IPOPT algorithm is
present in the API but is inert unless the matching companion package (or a
system-provided IPOPT) is available at runtime.

### IPOPT companion packages — EPL-2.0

`Pagmo.NET.Ipopt` and `PagmoNet4j.ipopt` exist to deliver the **IPOPT** native
library, which is licensed under the **Eclipse Public License 2.0**. To keep the
license story clean, these packages carry the EPL-2.0 license and contain no
pagmo-derived or MPL code — they are a native payload plus the minimal packaging
needed to place it where the base package can load it. They depend on the
corresponding base package.

These packages also redistribute IPOPT's own dependency closure (e.g. MUMPS,
OpenBLAS/LAPACK, and the GCC runtime libraries), each under its own license. The
authoritative, per-platform list lives in each companion's `NOTICE` file.

## Why the base (MPL) and companion (EPL) coexist cleanly

MPL-2.0 and EPL-2.0 are both weak/file-level copyleft licenses that are generally
considered incompatible for *combining into a single linked work*. We avoid that
problem entirely: **the base never links IPOPT at build time.** IPOPT is loaded at
runtime (via `dlopen`/`LoadLibrary`) through its C interface. No distributed binary
combines MPL/pagmo code with EPL/IPOPT code — the two only meet in the end user's
process, on the end user's machine, which is private use rather than distribution
of a combined work. This runtime-loading boundary is what makes the MPL-base /
EPL-companion split valid.

## Third-party components bundled in the base

The base native library statically links the following. Full attribution is in
each base package's `NOTICE` file.

| Component | License |
|---|---|
| pagmo2 2.19.1 | GPL-3.0-or-later **OR** LGPL-3.0-or-later (used here under **LGPL-3.0-or-later**) |
| NLopt | LGPL-2.1-or-later (portions MIT) |
| Boost (any, graph, safe-numerics, serialization) | Boost Software License 1.0 |
| Eigen 3 | MPL-2.0 (some modules BSD-3-Clause) |
| Intel oneTBB | Apache-2.0 |

## MPL-2.0 and secondary licenses (no "Exhibit B")

Our MPL-2.0 files are distributed **without** the optional Exhibit B
("Incompatible With Secondary Licenses") notice. Under MPL-2.0 §3.3, this means
the MPL code may be combined into a "Larger Work" governed by a Secondary License
(GNU GPL 2.0+, LGPL 2.1+, or AGPL 3.0+) and distributed under that license. This
matters because pagmo2 is offered as GPL **or** LGPL: omitting Exhibit B keeps our
wrapper code combinable with pagmo under **either** choice.

(Our right to link the MPL base against LGPL pagmo comes from LGPL itself, which
permits the combination; Exhibit B governs whether downstream GPL/LGPL projects may
absorb our files.)

## LGPL obligations for the base

Because the base statically links **pagmo2** and **NLopt** under the LGPL, each
base distribution:

- includes copies of the applicable LGPL license texts, and
- must let recipients modify those libraries and relink the wrapper against their
  modified versions (LGPL-3.0 §4 / LGPL-2.1 §6).

Each base package ships the verbatim LGPL/GPL license texts in its
`THIRD_PARTY_LICENSES.md`, and its `RELINKING.md` describes how to relink the wrapper
against modified versions of pagmo2 / NLopt (the Corresponding Source is the public,
reproducible build in this repository).

## Files derived from pagmo inside the MPL base

Some source files in the base are **derived from** pagmo's LGPL source (for
example, the IPOPT problem-marshalling code adapted from pagmo's `ipopt.cpp`).
Because MPL-2.0 is file-scoped, these files retain their original
**LGPL-3.0-or-later** headers even though the surrounding package is MPL-2.0. Only
our own original files are MPL-2.0; do not apply MPL headers to pagmo-derived files.

## For redistributors and end users

- Using a base package alone: MPL-2.0 for our code, plus the LGPL/permissive terms
  of the bundled libraries (see the base `NOTICE`).
- Adding an IPOPT companion: additionally subject to EPL-2.0 (IPOPT) and the
  licenses of IPOPT's dependency closure (see the companion `NOTICE`).
- Redistributing binaries: carry the `LICENSE` and `NOTICE` files, honor the LGPL
  relink obligation for the base, and retain EPL-2.0 notices for the companion.
