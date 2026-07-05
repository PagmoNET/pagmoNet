# Work Log

Active journal for cross-session / cross-device continuity. Newest session on top.

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
