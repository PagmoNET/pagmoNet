# Work Log

Active journal for cross-session / cross-device continuity. Newest session on top.

---

## 2026-07-12 — Java groupId rename: io.github.samthegliderpilot → io.github.pagmonet

User created the `pagmonet` GitHub org (for Maven Central `io.github.*` namespace verification) and
had the Java groupId/package renamed off his personal handle. **C# side unchanged** (its namespace is
`pagmo` / `Pagmo.NET`; author/copyright legitimately stay `samthegliderpilot`).

**Surgical rename — three prefixed identifier forms only**, so repo URLs / GH Packages publish URLs /
SCM / developer id+email (all bare `github.com/samthegliderpilot`, `id.set("samthegliderpilot")`, etc.)
were **preserved by construction**:
- `io.github.samthegliderpilot` → `io.github.pagmonet` (dotted: packages, groupId, gradle deps, mainClass)
- `io/github/samthegliderpilot` → `io/github/pagmonet` (fwd-slash paths: swig -outdir, mkdir)
- `io_github_samthegliderpilot` → `io_github_pagmonet` (JNI mangling; regenerated anyway)
- Plus a **backslash** path form `io\github\samthegliderpilot\pagmonet4j` in `regen-swig.ps1` that the
  fwd-slash sed missed — fixed separately (watch for `\`-delimited paths in any future rename).

Touched ~290 files (mostly checked-in SWIG-generated bindings) + moved 7 package dirs (core main/test/
generated, examples java+kotlin, kotlin-ext main/test). All swig `-package`/`-outdir` across the 4 java
workflows + `build-linux-artifacts.sh` + `regen-swig.ps1` now agree; gradle `group` updated in both
`gradle.properties`. Skipped `hs_err_pid*.log` crash-dump junk.

**Validated end-to-end** (WSL full build, exit 0): Java native regenerated with new JNI names, base
loaded with **no UnsatisfiedLinkError**, both clean-room solves passed (negative control + positive
IPOPT solve); fresh jar has all classes under `io/github/pagmonet/pagmonet4j/`, zero `samthegliderpilot`.

**Y-drive re-stage:** `Java-Linux/` refreshed — renamed jars + examples repackaged to the new package.
**Still stale:** `Java-Windows/` jars+examples are pre-rename (old namespace, internally consistent) —
need a Windows Java rebuild to match; CI release will produce renamed Windows/mac artifacts. **Open
decision:** repo/publish URLs still point at `github.com/samthegliderpilot` — user's call whether to
move the repos into the `pagmonet` org (would update POM `<url>`/`<scm>` + GH Packages targets).

---

## 2026-07-12 — Linux companion missing OpenBLAS alias (libblas.so.3); bundler self-containment gate

Clean-box testing (user's Linux machine, genuinely no system BLAS) caught another real bug CI missed:
the `ipopt` example reported IPOPT "not available" on Linux for **both** .NET and Java, even though the
companion nupkg/jar were built with IPOPT. Base wrapper loaded fine — only `dlopen(libipopt.so.3)`
failed.

**Root cause:** `libipopt.so.3` has `DT_NEEDED` for **both** `liblapack.so.3` and `libblas.so.3`. On
conda-forge `nomkl`, both are aliases of one OpenBLAS blob (SONAME `libopenblas.so.0`; confirmed it
exports `cblas_dgemm`/`dgemm_`). The old Linux path in `bundle-native-deps.ps1` walked **`ldd`'s
resolved paths** and keyed by basename — so on the build box `libblas.so.3` either resolved to a
*system* BLAS (dropped by the in-SearchDir filter) or collapsed into `liblapack.so.3` by shared SONAME.
Only `liblapack.so.3` got bundled; a clean box asking for `libblas.so.3` by name → `dlopen` fails →
"not available." **CI never caught it**: the build-ipopt Linux job tests against apt
`coinor-libipopt-dev` + system BLAS, and even GitHub runners have a system `libblas.so.3`, so the
missing alias was silently borrowed. Verified in WSL: BEFORE, `libblas.so.3 => /usr/lib/.../libblas.so.3`
(system); adding a local copy → `libblas.so.3 => $ORIGIN/libblas.so.3`, not-found count 0.

**Fix (`scripts/bundle-native-deps.ps1`, shared by C#+Java release workflows AND local
`build-linux-artifacts.sh`):**
1. Linux now walks the closure by **DT_NEEDED name** via `patchelf --print-needed` (BFS, mirroring the
   macOS `otool -L` walk) instead of `ldd` resolved paths — so every alias a binary asks for is
   bundled as a real file, even when several map to one physical library. glibc core libs are an
   explicit skip-list; anything else not found in a SearchDir warns.
2. Added a **self-containment gate**: after staging, every DT_NEEDED of every bundled ELF must resolve
   to a bundled sibling or glibc core, else the build **throws**. Can't be fooled by a contaminated
   runner the way a clean-room can — this is the durable prevention.

Fixed the stale top-of-file doc that wrongly claimed BLAS "never appears in any static import table"
(true only for MKL/Windows dlopen-forwarders; on nomkl Linux OpenBLAS is a direct DT_NEEDED).

**Not yet done:** (a) rebuild Linux companions with the fixed bundler (user can re-run
`build-linux-artifacts.sh`, or CI release); note NuGet global-packages cache holds the old
`1.0.0-local` — clear it or bump version. (b) CI coverage gap: the Linux clean-room should install
ONLY the bundled release companion (no apt libipopt, ideally a BLAS-free image) and assert a solve +
self-containment. The bundler gate largely closes this regardless. (c) Size follow-up: OpenBLAS is now
duplicated (libblas.so.3 + liblapack.so.3, ~41 MB each); a `patchelf --replace-needed ... libopenblas.so.0`
pass could ship it once — deferred, correctness first.

**Immediate user unblock:** on the Linux box, in the dir holding `libipopt.so.3`,
`cp liblapack.so.3 libblas.so.3` (rpath `$ORIGIN` is already inherited) makes IPOPT work — confirms
the diagnosis on the real clean machine.

---

## 2026-07-09 — Deferred-loader bug (app-local libipopt) + Linux clean-box packages

Clean-box (WSL) testing caught a real bug the CI didn't: the offline C# clean-room reported "IPOPT
reports unavailable" on Linux even though `libipopt.so` was co-located with the wrapper and loaded
fine standalone.

**Root cause:** `dynamic_library.cpp` only did a bare-name `dlopen("libipopt.so")` / `LoadLibrary`,
relying on the OS search path. Windows' search includes the app dir (so it worked); CI worked only
because `coinor-libipopt-dev` put libipopt on the *system* path. But **Linux `dlopen` does NOT
search the caller's own directory**, so an app-local libipopt (dropped by the companion, offline) is
invisible. The plan's "search the wrapper's own dir" was never implemented.

**Fix (native, shared by C# + Java):** `dynamic_library.cpp` now resolves candidates relative to the
wrapper's own directory FIRST (`dladdr` on Unix — needs `_GNU_SOURCE`; `GetModuleHandleEx` on
Windows), then falls back to the OS search path. Validated on Linux: `pagmonet_ipopt_available()` and
`pagmonet4j_has_ipopt_support()` both return true with libipopt co-located only (not on any path),
and the delivered Linux C# nupkg runs a full solve (`PASS f=4.2E-24`). **This also fixes the
Linux/macOS release clean-rooms**, which would otherwise have failed the same way. Needs push.

**WSL Linux build (`scripts/build-linux-artifacts.sh`) — fixes made:** build on ext4 not `/mnt/c`
(cmake `configure_file` EPERM on drvfs); pack via `-p:PackageOutputPath` not `-o` (SDK
mis-translates `-o` to a bare MSBuild arg); strip CRLF from `gradlew` (Windows checkout → `env:
sh\r`); dedicated `ipopt-ob-env` conda env so `nomkl`/OpenBLAS can't be shadowed by a stale MKL env;
clear NuGet/.m2/gradle caches before the same-version verifications.

**Confirmed OK:** `dotnet run` DOES propagate a non-zero app exit code (tested 2→2, incl.
`-r linux-x64 --self-contained false`) — the clean-room gate is reliable.

**Closure scoping (DONE 2026-07-11):** replaced copy-all on Linux/macOS with a dependency-graph walk
from libipopt (Linux `ldd` is already transitive; macOS `otool -L` is direct-only so BFS). Key fact:
on conda-forge nomkl, `liblapack.so.3`/`.dylib` is a symlink to the OpenBLAS implementation (241
BLAS/LAPACK symbols) — directly linked, NOT runtime-dlopen'd (that was Windows/MKL only), so the walk
captures BLAS. Result: **105 libs / 216 MB -> 24 libs / ~43 MB** (both C# nupkg + Java jar), validated
on Linux — full solve + negative control pass for both. macOS uses the same otool-BFS (validated by
the macOS release clean-room in the dry-run). Windows stays copy-all (its conda `Library\bin` is
already minimal, 42 MB). Also fixed a stale-staging bug: `bundle-native-deps.ps1` now wipes its
OutputDir first, and `build-linux-artifacts.sh` scrubs carried-in staging dirs (a stale
staged-natives/win-x64 was contaminating the Linux jar).

**Remaining optional trim:** ~33 MB is ICU pulled via SPRAL (`libipopt -> libspral -> hwloc -> xml2
-> icu`), a linear solver we don't use. Dropping it (nospral conda ipopt, or `patchelf
--remove-needed libspral`) would reach ~30 MB but needs all-platform validation via the clean-room;
deferred.

---

## 2026-07-06/07 — Java Windows jar crash: root cause + static-CRT fix

**Symptom:** locally-built Windows `pagmonet4j` jar crashes the JVM with `EXCEPTION_ACCESS_VIOLATION`
in `msvcp140.dll+0x13080` at the first C++ call (`new_ipopt`). C# nupkgs are fine; CI Java is green.

**Root cause (conclusive, from the hs_err loaded-modules list):** the JVM loaded `msvcp140.dll` from
`C:\Program Files\Amazon Corretto\jdk17\bin\` — **v14.29** (2021) — *before* the system's **v14.50**.
The JNI is a dynamic-CRT (`/MD`) native built with a recent MSVC toolset (needs ~14.5x), so it binds
to Corretto's stale 14.29 and the export at `+0x13080` resolves wrong -> crash. CI is green because
**Temurin** doesn't bundle `msvcp140.dll` (uses the system redist, which matches). C# is fine because
.NET always resolves the *system* CRT. **This would bite real Corretto users**, even with CI-built jars.

**Fix (implemented):** build the Windows JNI with a **static CRT** so `pagmonet4j.dll` has NO
`msvcp140.dll` dependency at all -> immune to whatever CRT any JVM bundles.
- `PagmoNet4j/scripts/build-native.ps1`: Windows default triplet `x64-windows-static-md` ->
  **`x64-windows-static`**, and add `-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded$<$<CONFIG:Debug>:Debug>`
  (wrapper compiles `/MT` to match the static-CRT deps; CMP0091 is NEW at cmake 3.22).
- Java workflow vcpkg cache keys now also hash `PagmoNet4j/scripts/build-native.ps1` (they only hashed
  the C# `scripts/build-native.ps1` before — a latent bug — so the triplet change now busts + caches).
- C# stays `x64-windows-static-md` (unaffected; .NET resolves the system CRT).
- **Immediate workaround for testing without the rebuild:** run the jars under Temurin, not Corretto.

**RESOLVED + VERIFIED (static-CRT swing landed).** The first attempt at plain `x64-windows-static`
failed (CI: pagmo2 Debug build; local: `__imp_` link + a stale-cache red herring). Root causes and
fixes:
- Full static needs the **v143** toolset pinned (the unpinned default v145 produced dynamic-UCRT
  `__imp_` refs that won't link `/MT`) and **release-only** (pagmo2's `/MTd` Debug config fails on
  runners). Added overlay `triplets/x64-windows-static.cmake` (static CRT + `v143` + `VCPKG_BUILD_TYPE
  release`), mirroring the working `x64-windows-static-md`.
- Local link also hit a **stale cmake cache**: `win-build/CMakeCache.txt` kept `Pagmo_DIR` pointing at
  the old static-md tree after switching triplets → linked the `/MD` pagmo → RuntimeLibrary mismatch.
  `build-native.ps1` now **wipes `$buildDir` before configure** so a triplet switch can't stick.

Result: `dumpbin /dependents pagmonet4j.dll` → **only `KERNEL32.dll`** (no msvcp140/vcruntime140), and
a full clean-room IPOPT solve runs **`CLEAN-ROOM PASS  f=0.0` under Corretto 17** (the JDK that
crashed). The verified jars replaced the old ones in `local-packages/java/`.

**CI status:** the earlier CI failure (pagmo2 x64-windows-static Debug) should be fixed by the new
release-only/v143 overlay, but that's unconfirmed until pushed. The Java workflows inherit the triplet
via `build-native.ps1` and the cache keys already hash it (they'll bust + rebuild pagmo static once).
Also note the C# side stays `x64-windows-static-md` (unchanged) -- only the JNI went fully static.

---

## CI result 2026-07-06 — Linux `build-dotnet-ipopt` still red; robust code fix

After the v4 cache bump, Windows + macOS `build-dotnet-ipopt` went GREEN, but **Linux still failed**
with the same `pagmo::ipopt` redefinition (installed pagmo had `PAGMO_WITH_IPOPT` — the log shows
`Requested IPOPT components: header`). The `ports/pagmo2` port is correct (ipopt is NOT a default
feature; the portfile even sets `CMAKE_DISABLE_FIND_PACKAGE_IPOPT=ON` when ipopt isn't requested, to
stop pagmo auto-detecting a *system* IPOPT on Linux runners). So Linux's pagmo shouldn't have ipopt
— the v4 bump apparently didn't fully bust its cache / a system IPOPT slipped the guard.

**Stopped playing cache whack-a-mole and fixed it deterministically in our code.** Two of our headers
pulled in pagmo's real `<pagmo/algorithms/ipopt.hpp>` under `#if defined(PAGMO_WITH_IPOPT)`
(`native/algorithm_log_projections_more.h` + `swig/pagmo/pagmo.hpp`). On the normal `pagmo[nlopt]`
build those are compiled out (why Win/Mac pass), but when pagmo has ipopt they declare `class
pagmo::ipopt`, colliding with the SWIG alias `using ipopt = deferred_ipopt` **and** risking linking
EPL libipopt into the MPL-clean base. Both were **dead under deferred-load**: the ipopt log is
projected from `deferred_ipopt::get_log()` inline in `ipopt.i`'s `%extend`, and `Ipopt_GetLogEntries`
(the only consumer of the include) was unreferenced. **Removed both includes + `Ipopt_GetLogEntries`;
kept the `IpoptLogEntry` struct** (still used by the `%extend`). Now nothing includes pagmo's real
ipopt.hpp, so the collision is impossible regardless of the installed pagmo's features, and the base
can't accidentally link IPOPT. Compile-checked locally (native rebuilt clean). Kept the v4 cache
bump as belt-and-suspenders. Needs a push to confirm Linux.

---

## CI result 2026-07-05 — `build-dotnet-ipopt` red (stale vcpkg cache), fixed

First push after the rework: `pagmo.NET`, `PagmoNet4j`, `PagmoNet4j.ipopt` GREEN;
`pagmo.NET.ipopt` (the `build-dotnet-ipopt.yml` BUILD workflow) RED on all 3 OS with the
`pagmo::ipopt` redefinition (`GeneratedWrappers.cxx:3801 C2371` / conflicting `using ipopt =
::pagmoNet::deferred_ipopt` vs pagmo's real `class pagmo::ipopt`).

**Root cause (not a code bug): a stale vcpkg binary cache.** `build-native.ps1` without `-WithIpopt`
installs `pagmo2[nlopt]` (no ipopt), so a *fresh* pagmo has no `class pagmo::ipopt`. But
`build-dotnet-ipopt.yml`'s cache key was `...-ipopt-v2-` (Windows) / versionless (Linux) and did
NOT hash `build-native.ps1`, so it kept restoring a **superset-era `pagmo2[ipopt]` tree**. That
pagmo's installed `config.hpp` `#define`s `PAGMO_WITH_IPOPT` (defeating our command-line
`-UPAGMO_WITH_IPOPT`), which un-guards `class pagmo::ipopt` → collides with the SWIG alias. The tell
in the log: `Requested IPOPT components: header` / `Creating the 'pagmo::IPOPT::header' imported
target` come from *pagmo's own* CMake config, which only fires when pagmo was built with ipopt. The
three green jobs used caches that had already busted (`build-java-ipopt` is `-v3-` and hashes
`build-native.ps1`; the base jobs use the `-dotnet-`/`-java-` keys, all `pagmo2[nlopt]`).

**Fix:** bumped all three `build-dotnet-ipopt.yml` vcpkg cache keys to `-v4-` and aligned the hash
to `ports/**, triplets/**, native/CMakeLists.txt, scripts/build-native.ps1` (matching the green
jobs) so the stale tree can't restore and future feature changes auto-bust. Needs a re-push to confirm.

**Residual fragility (post-1.0 hardening candidate):** the SWIG `using ipopt = deferred_ipopt`
alias will always collide if the *installed* pagmo has the ipopt feature. Deterministic cache
hygiene avoids it (no workflow builds `pagmo2[ipopt]` anymore), but a truly robust fix would stop
depending on pagmo never having ipopt — e.g. present the C# type as `pagmo.ipopt` via SWIG
`%rename` without a C++ `using pagmo::ipopt`. Risky (touches the green base SWIG surface); deferred.

---

## Session 2026-07-05 — Release `*-ipopt` workflow rework (deferred-load IPOPT)

### Where we are in the big picture
The UDA-ipopt pivot (runtime `dlopen` of libipopt instead of compile-time linking) is
**landed and green for the BUILD workflows** (base + `*-ipopt`, all 3 OS). The base packages are
MPL-clean (no EPL linked); the `ipopt` algorithm ships in the base and loads libipopt at runtime.
This session reworked the **RELEASE** workflows off the old "superset" model.

### The model these release workflows now implement
The companion packages (`Pagmo.NET.Ipopt`, `PagmoNet4j.ipopt`) are **pure EPL-2.0 native
payloads**: they ship ONLY libipopt + its dependency closure (MUMPS / OpenBLAS / gfortran
runtime), no pagmo, no managed/Java code, no wrapper. The base package carries the `ipopt`
algorithm; the companion just supplies the library it dlopen's, dropped into the SAME
`runtimes/<rid>/native` (C#) / `natives/<rid>` (Java) dir as the base wrapper.

Consequence: **the companion release builds NO native wrapper** — no vcpkg, no build-native, no
SWIG-with-ipopt. Each platform job only assembles conda-forge's libipopt closure. Much simpler
than the superset it replaces.

### KEY DECISION — in-run reusable base build + fully-offline clean-room
The companion's clean-room gate must prove *base + companion together*, so it needs a real base
package. Sam did not want to publish just to test. **Chosen approach (over base-first-ordering):**
build the base *in the same run* and stage it into a local feed alongside the companion, so the
clean-room is fully offline and a dry-run publishes nothing.

To avoid duplicating the base build YAML, the base native-build + pack is factored into a
**reusable `workflow_call` workflow**, called by BOTH the base release and the companion release:
- `_build-base-dotnet.yml` → uploads `base-package` (base .nupkg, all RIDs injected).
- `_build-base-java.yml` → uploads `base-maven-repo` (base localrepo with the base jar + natives).

`release-dotnet.yml` / `release-java.yml` were refactored to CALL these (single source of truth;
they then run the base-only clean-room + publish). `release-*-ipopt.yml` also call them via a
`base:` job, then the clean-room downloads BOTH the base artifact and the companion into ONE local
feed and resolves offline (`<clear/>` + local feed only; no NuGet.org / GitHub Packages).

Tradeoff (accepted): the companion release now rebuilds the base natives during its run (vcpkg ×3
OS), so it's slower — but it's self-contained and needs no publish to test. Superseded the earlier
base-first idea (which required publishing base first + a live feed in the gate).

### Files changed this session (reusable-base + offline-gate refactor)
- `.github/workflows/_build-base-dotnet.yml`, `_build-base-java.yml` — **NEW** reusable
  (`workflow_call`) base native-build + pack; upload `base-package` / `base-maven-repo`.
- `.github/workflows/release-dotnet.yml`, `release-java.yml` — **refactored** to `meta → base
  (uses reusable) → cleanroom → publish`. Behaviour-preserving (base clean-room + publish unchanged
  in intent), just sourcing the build from the reusable. **These base releases were green before;
  the refactor is the main regression risk — watch them in CI.**
- `.github/workflows/release-dotnet-ipopt.yml`, `release-java-ipopt.yml` — added a `base:` job
  (reusable) and made the clean-room OFFLINE (downloads base + companion into one local feed;
  `<clear/>` + local feed only). Renamed the companion payload artifacts `native-<rid>` →
  **`ipopt-payload-<rid>`** so the pack's `pattern:` no longer also matches the reusable base
  build's `native-windows/linux/macos-universal` artifacts (that collision would have packed base
  wrapper libs into the companion payload — caught before it shipped).
- `PagmoNet4j.ipopt/cleanroom/build.gradle.kts` — reverted to offline (base + companion both from
  `../localrepo`); dropped the GitHub Packages resolution repo.
- `pagmo.NET.ipopt/cleanroom/CleanRoom.csproj` — comment: offline, one local feed with base+companion.

### Earlier this session (payload model + bundle script)
- `scripts/bundle-native-deps.ps1` — **rewritten** as a uniform closure-stager. Dropped the
  `-WrapperPath` param (companion ships no wrapper); now `-OutputDir` + `-SearchDir` only. Copies
  ALL non-system shared libs from the conda env (import-walk can't see conda's runtime-loaded BLAS —
  see the header comment). Per-platform fixups: Windows none; **Linux now patchelf `--set-rpath
  $ORIGIN`** (was a no-op before — the old static assumption is dead); **macOS now copy-ALL** dylibs
  then `@loader_path` rewrite + ad-hoc codesign (was an insufficient otool-walk).
- `pagmo.NET.ipopt/Pagmo.NET.Ipopt.csproj` — **BUG FIX**: pack glob used
  `PackagePath="%(RecursiveDir)"` which NuGet treats as a *directory* and re-appends the source
  path under it → doubled `runtimes/<rid>/native/runtimes/<rid>/native/*.dll`. Changed to
  `PackagePath="%(RecursiveDir)%(Filename)%(Extension)"` (full file path → verbatim). Verified single-level.
- `.github/workflows/release-dotnet-ipopt.yml` — **rewritten** to the payload model (conda `ipopt
  nomkl` → bundle → pack → clean-room(local+nuget.org) → publish). No vcpkg/build-native.
- `.github/workflows/release-java-ipopt.yml` — **rewritten** likewise (conda `ipopt nomkl` → bundle
  → gradle payload jar → clean-room → GitHub Packages). No vcpkg/build-native/superset SWIG.
- `PagmoNet4j.ipopt/cleanroom/build.gradle.kts` — added the base's GitHub Packages repo (with
  `GITHUB_ACTOR`/`GITHUB_TOKEN` creds) so the clean-room resolves the base `pagmonet4j`.
- `pagmo.NET.ipopt/cleanroom/CleanRoom.csproj` — comment updated (feed now includes nuget.org for base).

### Validated LOCALLY (Windows only)
- Bundle script Windows path against the OpenBLAS conda env (`C:\ipopt-ob-env`): **13 DLLs, 42 MB**,
  no VC-runtime leak. (< NuGet's 250 MB limit; MKL would be ~570 MB — hence `nomkl`.)
- `dotnet pack Pagmo.NET.Ipopt` with `PagmoNativePackDir` pointed at the exact post-download tree
  shape (`.../runtimes/win-x64/native/*`): nupkg has correct single-level `runtimes/win-x64/native/*.dll`,
  a `Pagmo.NET 1.0.0` dependency, EPL-2.0 license, and no managed lib.
- (Earlier session) full clean-room solve on Windows with only the 42 MB payload on PATH, conda
  fully excluded — exit 0, f 46.8 → 0.0, rc 0.

### NOT yet validated — needs CI (Sam pushes; I can't run Actions or build Java locally)
1. **Linux bundle branch** — new patchelf `$ORIGIN` logic; needs `apt-get install -y patchelf`
   (added to the workflow). Never run.
2. **macOS bundle branch** — new copy-all + `@loader_path` + codesign; the "verify nothing resolves
   outside the package dir" step should catch escapes. Never run.
3. **`upload-artifact@v4` rid-path preservation** — relying on single-directory `path: staged/`
   preserving the `<rid>` subtree (LCA-collapse only bites with wildcards/multi-path). If the rid
   prefix vanishes, add a sibling marker file directly under `staged/` to raise the LCA.
4. **Whole Java release** — I can't build the JNI locally (PAGMO_STATIC_BUILD vs dynamic pagmo
   `island_factory`). The payload-jar assembly, the `staged-natives/<rid>` → `natives/<rid>` bundling
   (rootProject = `PagmoNet4j.ipopt`; download target is `PagmoNet4j.ipopt/staged-natives`), and the
   clean-room base resolution from GitHub Packages are all unexercised.
5. **Companion clean-rooms end-to-end** on Linux/macOS for both ecosystems.

### Remaining TODO (this workstream)
- [ ] Push and iterate the two reworked release workflows through CI until green (esp. Linux/macOS
      bundle + Java end-to-end).
- [ ] Reshape/confirm the clean-room gate covers base-only (BYO error) AND base+companion. Base-only
      lives in the base releases; base+companion is the companion clean-room reworked here.
- [ ] Final Consistency Pass (see `.ai/1.0_RELEASE_CHECKLIST.md`): purge remaining "superset"
      language in READMEs/CHANGELOGs/`.ai/AGENT.md`; refresh companion NOTICE against the real 42 MB
      closure (13 libs enumerated above); base LGPL relink materials.
- [ ] Version single-source (G) — largely done; re-verify base+companion move together on one bump.

### Constraints / reminders
- Do NOT git commit or push — Sam does that and reports CI results.
- `OLD_IGNORE/` is dead; `pagmoNet/` is THE repo.
- Local tools: pwsh 7 at `C:\Users\enfor\.dotnet\tools\pwsh.exe`; conda envs `C:\ipopt-env` (MKL) and
  `C:\ipopt-ob-env` (OpenBLAS, the shippable one); vcpkg pagmo at `C:\src\vcpkg\installed\x64-windows`.
