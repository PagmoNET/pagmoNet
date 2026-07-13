#Requires -Version 7.0
<#
.SYNOPSIS
    Stage the IPOPT runtime closure (libipopt + every non-system dependency it pulls in)
    into a single, self-contained output directory so the companion payload package works
    on an end-user machine with no conda, no dev tools, and no LD/DYLD hints.

.DESCRIPTION
    This is the shared "make the IPOPT payload actually work on a clean machine" step used
    by the C# and Java IPOPT *companion* publish pipelines. It is intentionally a
    binding/packaging tool, not a general-purpose one -- it only knows the platforms we ship
    and the one dependency-source convention we use: a conda-forge prefix created with just
    `ipopt nomkl`, which IS exactly IPOPT's runtime closure (libipopt + MUMPS + OpenBLAS +
    the gfortran/quadmath/gcc runtime) and nothing else.

    It does NOT bundle the base wrapper (PagmoWrapper / libpagmonet4j). That belongs to the
    base package. The companion drops this libipopt closure into the SAME runtimes/<rid>/native
    (C#) / natives/<rid> (Java) directory as the base wrapper, where deferred_ipopt's dlopen
    finds it (searching its own module directory first).

    Windows copies ALL non-system DLLs from the env rather than walking the import table: there,
    conda-forge's libblas/liblapack are thin forwarders that dlopen the real OpenBLAS at runtime, so
    the BLAS implementation never appears in a static import table and an import-table walk would ship
    a payload that fails on a clean machine. Because the env was created with only `ipopt nomkl`,
    copying every non-system DLL is complete by construction.

    Linux and macOS instead walk the dependency closure by NAME (DT_NEEDED / otool -L), because on
    those platforms nomkl links OpenBLAS directly (liblapack / libblas are aliases of the OpenBLAS
    library, both direct dependencies) -- so the closure is complete AND far smaller than copy-all,
    which drags in unrelated env libraries. The walk is by dependency NAME, not by the build box's
    resolved path, so every alias a binary asks for (e.g. both libblas.so.3 and liblapack.so.3, which
    share one SONAME) is bundled -- a path-based walk silently dropped one and broke clean machines.

    Platform behaviour (each stages the same closure, differing only in how the copied libraries
    are made to resolve each other from the package directory):

      Windows  DLLs resolve from the directory of the loading module, so nothing needs
               rewriting or signing -- just copy every non-system DLL from -SearchDir.

      Linux    Copy every non-system .so*; set an RPATH of $ORIGIN on each (via patchelf) so a
               library finds its siblings in the same directory with no LD_LIBRARY_PATH.

      macOS    Copy every .dylib; rewrite each library's id and its references to bundled
               siblings to @loader_path/<file> so dyld resolves them from the package directory
               with no DYLD_LIBRARY_PATH; re-sign ad-hoc (rewriting invalidates signatures).

               Why @loader_path for every reference: dyld de-duplicates loaded images by install
               name, so a dependency referenced under two different names (an absolute conda path
               from one binary, @rpath from another) can load twice and crash. Normalising every
               reference to the same @loader_path/<file> string prevents that.

.PARAMETER OutputDir
    Directory to stage the self-contained closure into. Created if absent. All libraries are
    placed here flat; this is what gets packed into runtimes/<rid>/native (C#) or natives/<rid>
    (Java).

.PARAMETER SearchDir
    One or more directories to copy the closure from -- the conda-forge prefix's library dir
    (Library\bin on Windows, lib on Linux/macOS). Every non-system shared library found here is
    bundled.

.PARAMETER SkipCodeSign
    macOS only: skip the ad-hoc re-sign step (useful when the caller signs/notarizes separately).
    Has no effect on other platforms.
#>
param(
    [Parameter(Mandatory)][string]$OutputDir,
    [Parameter(Mandatory)][string[]]$SearchDir,
    [switch]$SkipCodeSign
)

$ErrorActionPreference = "Stop"

# Start from a clean output dir so a stale closure from a previous run can't leak in (e.g. an old
# copy-all payload left behind would bloat the package back up). CI runners are fresh; this matters
# for local re-runs and any caller that reuses a staging directory.
if (Test-Path $OutputDir) { Remove-Item -Recurse -Force $OutputDir }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$OutputDir = (Resolve-Path $OutputDir).Path

# Normalise search dirs to absolute, existing paths.
$searchDirs = @()
foreach ($d in $SearchDir) {
    if (Test-Path $d) { $searchDirs += (Resolve-Path $d).Path }
    else { Write-Warning "Search dir does not exist, ignoring: $d" }
}
if ($searchDirs.Count -eq 0) { throw "No usable -SearchDir given; nothing to bundle." }

# Copy one shared library into OutputDir, resolving symlinks to real content but keeping the
# entry's own basename so a soname-named symlink (libipopt.so.3 -> libipopt.so.3.14) lands as a
# real file under the name that DT_NEEDED / install names reference.
function Copy-Lib([System.IO.FileInfo]$item) {
    $target = $item.ResolveLinkTarget($true)
    $src = if ($target) { $target.FullName } else { $item.FullName }
    Copy-Item $src (Join-Path $OutputDir $item.Name) -Force
}

# ──────────────────────────────────────────────────────────────────────────────
if ($IsLinux) {
    if (-not (Get-Command patchelf -ErrorAction SilentlyContinue)) {
        throw "patchelf is required on Linux to set RPATH=`$ORIGIN on the bundled closure; install it (apt-get install -y patchelf)."
    }
    # Locate the IPOPT library the deferred loader dlopen's.
    $ipopt = $null
    foreach ($dir in $searchDirs) {
        foreach ($n in @('libipopt.so.3', 'libipopt.so.1', 'libipopt.so')) {
            $c = Join-Path $dir $n
            if (Test-Path $c) { $ipopt = Get-Item $c; break }
        }
        if ($ipopt) { break }
    }
    if (-not $ipopt) { throw "libipopt.so not found in any -SearchDir." }

    # Walk IPOPT's DT_NEEDED closure by NAME (like the macOS otool -L BFS below), rather than parsing
    # what `ldd` RESOLVED each name to. `ldd` reports the build machine's resolution, which is a trap
    # here: conda-forge nomkl makes libblas.so.3 AND liblapack.so.3 two aliases of one OpenBLAS blob
    # (both SONAME libopenblas.so.0). On the build box `ldd` can satisfy libblas.so.3 from a system
    # BLAS (then our in-SearchDir filter drops it) or collapse it into liblapack.so.3 by shared SONAME
    # -- either way only one name got bundled, and a clean box asking for the other name by DT_NEEDED
    # failed to load ("Could not load IPOPT"). Reading DT_NEEDED keeps every name a bundled ELF asks
    # for, so each is present as a real file even when several map to the same physical library.
    #
    # glibc core libraries are present on every Linux and are intentionally NOT bundled; everything
    # else a bundled ELF needs must travel with the payload.
    $systemLibs = @(
        'libc.so.6', 'libm.so.6', 'libdl.so.2', 'libpthread.so.0', 'librt.so.1', 'libresolv.so.2',
        'libutil.so.1', 'libnsl.so.1', 'libanl.so.1', 'ld-linux-x86-64.so.2', 'ld-linux.so.2'
    )
    function Find-InSearch([string]$name) {
        foreach ($dir in $searchDirs) {
            $c = Join-Path $dir $name
            if (Test-Path $c) { return (Get-Item $c) }
        }
        return $null
    }

    $bundled = @{}
    $queue = [System.Collections.Queue]::new()
    Copy-Lib $ipopt
    $bundled[$ipopt.Name] = $true
    $queue.Enqueue($ipopt.FullName)
    while ($queue.Count -gt 0) {
        foreach ($name in (& patchelf --print-needed $queue.Dequeue() 2>$null)) {
            if ($bundled.ContainsKey($name) -or ($systemLibs -contains $name)) { continue }
            $item = Find-InSearch $name
            if (-not $item) {
                Write-Warning "DT_NEEDED '$name' not found in any -SearchDir and is not a known glibc lib; leaving it to the target loader."
                continue
            }
            Copy-Lib $item
            $bundled[$name] = $true
            # Read NEEDED from the staged copy (Copy-Lib resolved the symlink to real content).
            $queue.Enqueue((Join-Path $OutputDir $name))
        }
    }

    # De-duplicate identical libraries shipped under multiple DT_NEEDED aliases. conda-forge nomkl
    # makes libblas.so.3 and liblapack.so.3 two NAMES for one OpenBLAS blob (SONAME libopenblas.so.0),
    # and libipopt/MUMPS/SPRAL reference it under both -- so the by-name walk above bundled it twice
    # (~40 MB wasted). A symlink would collapse them but does not survive nupkg/jar packing, so instead
    # keep ONE copy under its SONAME and repoint every bundled ELF's aliased NEEDED entries at it.
    $byHash = @{}
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        $h = (Get-FileHash -Path $f.FullName -Algorithm SHA256).Hash
        if (-not $byHash.ContainsKey($h)) { $byHash[$h] = [System.Collections.Generic.List[string]]::new() }
        $byHash[$h].Add($f.Name)
    }
    $aliasMap = @{}   # dropped alias name -> canonical (kept) name
    foreach ($names in $byHash.Values) {
        if ($names.Count -lt 2) { continue }
        # Canonical name = the library's own SONAME (best practice: file name == SONAME); if it reports
        # none, keep the first alias as canonical.
        $soname = "$(& patchelf --print-soname (Join-Path $OutputDir $names[0]) 2>$null)".Trim()
        $canonical = if ($soname) { $soname } else { $names[0] }
        if (-not (Test-Path (Join-Path $OutputDir $canonical))) {
            Move-Item (Join-Path $OutputDir $names[0]) (Join-Path $OutputDir $canonical) -Force
        }
        foreach ($n in $names) {
            if ($n -eq $canonical) { continue }
            $p = Join-Path $OutputDir $n
            if (Test-Path $p) { Remove-Item $p -Force }
            $aliasMap[$n] = $canonical
        }
    }
    if ($aliasMap.Count -gt 0) {
        foreach ($f in Get-ChildItem -Path $OutputDir -File) {
            foreach ($needed in (& patchelf --print-needed $f.FullName 2>$null)) {
                if ($aliasMap.ContainsKey($needed)) {
                    & patchelf --replace-needed $needed $aliasMap[$needed] $f.FullName 2>$null
                }
            }
        }
        Write-Host "Linux: de-duplicated $($aliasMap.Count) alias(es) -> $(($aliasMap.Values | Sort-Object -Unique) -join ', ')."
    }

    # Point every bundled ELF at its own directory so siblings resolve with no LD_LIBRARY_PATH.
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        & patchelf --set-rpath '$ORIGIN' $f.FullName 2>$null
    }

    # Self-containment gate: every DT_NEEDED of every bundled ELF must resolve to a bundled sibling or
    # a glibc core library -- never to a system copy that merely happens to exist on the build box.
    # This fails the BUILD (not the end user) if a dependency name slipped through, and it can't be
    # fooled by a contaminated runner the way a clean-room test can -- which is precisely how a missing
    # OpenBLAS alias (libblas.so.3, satisfied by the CI machine's own libblas) shipped once already.
    $present = @{}
    foreach ($f in Get-ChildItem -Path $OutputDir -File) { $present[$f.Name] = $true }
    $missing = [System.Collections.Generic.List[string]]::new()
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        foreach ($name in (& patchelf --print-needed $f.FullName 2>$null)) {
            if (-not $present.ContainsKey($name) -and -not ($systemLibs -contains $name)) {
                $missing.Add("$($f.Name) -> $name")
            }
        }
    }
    if ($missing.Count -gt 0) {
        throw ("IPOPT closure is not self-contained; these DT_NEEDED are neither bundled nor glibc core:`n  " `
            + ($missing -join "`n  ") `
            + "`nAdd the missing library to the conda env / -SearchDir, or, if it is genuinely provided by every target system, add it to `$systemLibs.")
    }

    Write-Host "Linux: staged $((Get-ChildItem $OutputDir -File).Count) libs (self-contained IPOPT DT_NEEDED closure) into $OutputDir (RPATH=`$ORIGIN)."
    return
}

# ──────────────────────────────────────────────────────────────────────────────
if ($IsMacOS) {
    function Get-OtoolDeps([string]$path) {
        $lines = & otool -L $path
        $deps = @()
        # Line 0 is "<path>:"; line 1 is the binary's own id; the rest are deps.
        for ($i = 2; $i -lt $lines.Count; $i++) {
            $m = [regex]::Match($lines[$i], '^\s*(\S+)\s*\(')
            if ($m.Success) { $deps += $m.Groups[1].Value }
        }
        return $deps
    }
    # Resolve an install-name reference (@rpath/@loader_path/absolute) to a file inside a search dir,
    # by basename. Returns $null for system dylibs (/usr/lib, /System), which are never in a SearchDir.
    function Resolve-InSearch([string]$ref) {
        $base = [System.IO.Path]::GetFileName($ref)
        foreach ($dir in $searchDirs) {
            $c = Join-Path $dir $base
            if (Test-Path $c) { return (Get-Item $c) }
        }
        return $null
    }

    # Locate the IPOPT dylib the deferred loader dlopen's.
    $ipopt = $null
    foreach ($dir in $searchDirs) {
        foreach ($n in @('libipopt.3.dylib', 'libipopt.1.dylib', 'libipopt.dylib')) {
            $c = Join-Path $dir $n
            if (Test-Path $c) { $ipopt = Get-Item $c; break }
        }
        if ($ipopt) { break }
    }
    if (-not $ipopt) { throw "libipopt.dylib not found in any -SearchDir." }

    # BFS the dependency closure from libipopt -- otool -L is direct-only (unlike Linux ldd, which is
    # already transitive), so recurse. Copy every dep resolving into a SearchDir; this is IPOPT's real
    # runtime closure, far smaller than copying the whole conda env. conda-forge nomkl symlinks
    # liblapack.dylib to the OpenBLAS implementation, so BLAS is captured here (not runtime-dlopen'd).
    $bundled = @{}
    $queue = [System.Collections.Queue]::new()
    Copy-Lib $ipopt
    $bundled[$ipopt.Name] = $true
    $queue.Enqueue($ipopt.FullName)
    while ($queue.Count -gt 0) {
        foreach ($ref in (Get-OtoolDeps $queue.Dequeue())) {
            $item = Resolve-InSearch $ref
            if (-not $item -or $bundled.ContainsKey($item.Name)) { continue }
            Copy-Lib $item
            $bundled[$item.Name] = $true
            $queue.Enqueue($item.FullName)
        }
    }

    # De-duplicate identical dylibs shipped under multiple names -- conda-forge nomkl makes libblas and
    # liblapack aliases of one OpenBLAS dylib, and the otool walk above bundles each name (~40 MB waste).
    # A symlink would collapse them but does not survive nupkg/jar packing, so keep ONE copy under its
    # install-id basename (the macOS analog of SONAME) and repoint references. Content-hash based, so it
    # is a no-op when there is no duplication.
    $byHash = @{}
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        $h = (Get-FileHash -Path $f.FullName -Algorithm SHA256).Hash
        if (-not $byHash.ContainsKey($h)) { $byHash[$h] = [System.Collections.Generic.List[string]]::new() }
        $byHash[$h].Add($f.Name)
    }
    $aliasMap = @{}   # dropped alias basename -> canonical (kept) basename
    foreach ($names in $byHash.Values) {
        if ($names.Count -lt 2) { continue }
        $idOut = & otool -D (Join-Path $OutputDir $names[0]) 2>$null
        $idLine = if ($idOut.Count -ge 2) { "$($idOut[-1])".Trim() } else { "" }
        $canonical = if ($idLine) { [System.IO.Path]::GetFileName($idLine) } else { $names[0] }
        if (-not (Test-Path (Join-Path $OutputDir $canonical))) {
            Move-Item (Join-Path $OutputDir $names[0]) (Join-Path $OutputDir $canonical) -Force
            $bundled.Remove($names[0]); $bundled[$canonical] = $true
        }
        foreach ($n in $names) {
            if ($n -eq $canonical) { continue }
            $p = Join-Path $OutputDir $n
            if (Test-Path $p) { Remove-Item $p -Force }
            $bundled.Remove($n)
            $aliasMap[$n] = $canonical
        }
    }
    if ($aliasMap.Count -gt 0) {
        Write-Host "macOS: de-duplicated $($aliasMap.Count) alias(es) -> $(($aliasMap.Values | Sort-Object -Unique) -join ', ')."
    }

    # Rewrite install names so everything resolves via @loader_path (its own id, and every reference to
    # a bundled sibling -- aliased names map to the canonical kept above), then re-sign ad-hoc (rewriting
    # invalidates signatures).
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        $path = $f.FullName
        & install_name_tool -id "@loader_path/$($f.Name)" $path 2>$null
        foreach ($ref in (Get-OtoolDeps $path)) {
            $refBase = [System.IO.Path]::GetFileName($ref)
            $target = if ($aliasMap.ContainsKey($refBase)) { $aliasMap[$refBase] } else { $refBase }
            if ($bundled.ContainsKey($target) -and $ref -ne "@loader_path/$target") {
                & install_name_tool -change $ref "@loader_path/$target" $path
            }
        }
    }

    # Self-containment check (macOS): after rewriting, every non-system dependency should resolve to a
    # bundled @loader_path sibling. Anything still on @rpath, an absolute path, or a missing @loader_path
    # target means the closure is incomplete. Kept as a WARNING (not a throw like the Linux gate) until
    # this path is validated on real macOS hardware, so a false positive can't block the macOS release.
    foreach ($f in Get-ChildItem -Path $OutputDir -File) {
        foreach ($ref in (Get-OtoolDeps $f.FullName)) {
            if ($ref -like '/usr/lib/*' -or $ref -like '/System/*') { continue }   # OS-provided
            if ($ref -like '@loader_path/*') {
                if (-not (Test-Path (Join-Path $OutputDir ([System.IO.Path]::GetFileName($ref))))) {
                    Write-Warning "macOS closure: $($f.Name) -> $ref (bundled file missing)"
                }
                continue
            }
            Write-Warning "macOS closure: $($f.Name) -> $ref (not resolved to a bundled sibling)"
        }
    }
    if (-not $SkipCodeSign) {
        foreach ($f in Get-ChildItem -Path $OutputDir -File) {
            & codesign --force --sign - $f.FullName
        }
    }
    Write-Host "macOS: staged $($bundled.Count) dylibs (IPOPT dependency closure) into $OutputDir (@loader_path, ad-hoc signed)."
    return
}

# ──────────────────────────────────────────────────────────────────────────────
# Windows: DLLs resolve each other from the same directory at load time -- no rewriting/signing.
# The Windows system DLLs, API sets, the VC++ redistributable, and the UCRT are provided by the OS
# or the VC++ redistributable and must never be bundled -- even when the conda-forge prefix ships
# its own copies of them (it does: MSVCP140, VCRUNTIME140*, api-ms-win-crt-*, ...). Skip them
# regardless of -SearchDir, the same assumption every native NuGet makes.
function Test-IsWindowsSystemDll([string]$name) {
    return ($name -match '(?i)^(api-ms-win-|ext-ms-win-)') `
        -or ($name -match '(?i)^(msvcp\d+|vcruntime\d+|msvcr\d+|ucrtbase|concrt\d+|vcomp\d+|vccorlib\d+|vcamp\d+|mfc\d+)(_\w+)?\.dll$') `
        -or ($name -match '(?i)^(kernel32|kernelbase|user32|advapi32|ole32|oleaut32|ws2_32|shell32|gdi32|gdi32full|ntdll|combase|bcrypt|bcryptprimitives|crypt32|sechost|rpcrt4|shlwapi|setupapi|version|winmm|msvcrt|powrprof|dbghelp)\.dll$')
}

$count = 0
foreach ($dir in $searchDirs) {
    Get-ChildItem -Path $dir -Filter *.dll -File -ErrorAction SilentlyContinue | ForEach-Object {
        if (Test-IsWindowsSystemDll $_.Name) { return }
        Copy-Item $_.FullName (Join-Path $OutputDir $_.Name) -Force
        $count++
    }
}
Write-Host "Windows: staged $count non-system DLLs from the IPOPT env into $OutputDir"
