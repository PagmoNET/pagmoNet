package io.github.samthegliderpilot.pagmonet4j;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads the pagmonet4j JNI native library.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Environment variable {@code PAGMO4J_NATIVE_DIR} — directory containing
 *       the platform native library.
 *   <li>System property {@code java.library.path} — standard JVM lookup.
 *   <li>Bundled resources in the JAR under {@code /natives/<platform>/} — the whole
 *       directory (wrapper plus any bundled dependency libraries) is extracted to a
 *       temp directory on first use, then the wrapper is loaded from there.
 * </ol>
 *
 * <p>The IPOPT-enabled distribution bundles a dynamic dependency closure
 * (IPOPT/MUMPS/OpenBLAS/gfortran) alongside the wrapper on macOS and Windows, so all
 * files in {@code /natives/<platform>/} must be extracted together: dyld resolves them
 * via {@code @loader_path} and the Windows loader searches the wrapper's own directory.
 * The base (NLopt-only) distribution is statically linked, so the directory contains
 * just the wrapper — the same code path handles both.
 *
 * <p>Call {@link #load()} once before using any pagmonet4j class. The {@link pagmonet4j}
 * module class calls this automatically in its static initializer.
 */
final class NativeLoader {

    private static final String LIB_NAME = "pagmonet4j";
    private static volatile boolean loaded = false;

    private NativeLoader() {}

    public static synchronized void load() {
        if (loaded) return;

        String nativeDir = System.getenv("PAGMO4J_NATIVE_DIR");
        if (nativeDir != null && !nativeDir.isBlank()) {
            String path = resolveLibPath(nativeDir);
            try {
                System.load(path);
            } catch (UnsatisfiedLinkError e) {
                java.io.File f = new java.io.File(path);
                System.err.println("[pagmonet4j] System.load failed for: " + path);
                System.err.println("[pagmonet4j] File exists=" + f.exists() + " size=" + (f.exists() ? f.length() : -1L));
                System.err.println("[pagmonet4j] Error: " + e.getMessage());
                throw e;
            }
            loaded = true;
            return;
        }

        try {
            System.loadLibrary(LIB_NAME);
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError ignored) {}

        extractAndLoad();
        loaded = true;
    }

    private static String resolveLibPath(String dir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String name;
        if (os.contains("win")) {
            name = LIB_NAME + ".dll";
        } else if (os.contains("mac")) {
            name = "lib" + LIB_NAME + ".dylib";
        } else {
            name = "lib" + LIB_NAME + ".so";
        }
        return Paths.get(dir, name).toAbsolutePath().toString();
    }

    private static void extractAndLoad() {
        String rid = platformRid();
        String wrapper = nativeFileName();
        String resourceDir = "natives/" + rid;
        try {
            // Use a process-scoped UUID directory so concurrent JVM instances don't collide.
            // Register a shutdown hook for deterministic cleanup even on System.exit() and
            // kill signals (unlike deleteOnExit, which only fires on normal JVM termination).
            String dirName = "pagmonet4j-native-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID();
            Path tmp = Files.createTempDirectory(dirName);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walk(tmp)
                         .sorted(java.util.Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(java.io.File::delete);
                } catch (IOException ignored) {}
            }));

            // Extract the entire natives/<rid>/ directory so the wrapper's bundled
            // dependency closure lands next to it (required on macOS/Windows; harmless
            // on the static Linux/base builds where the directory holds only the wrapper).
            int extracted = extractClosure(resourceDir, tmp);
            Path dest = tmp.resolve(wrapper);
            if (extracted == 0 || !Files.exists(dest)) {
                throw new UnsatisfiedLinkError(
                    "pagmonet4j native library not found. Set PAGMO4J_NATIVE_DIR or ensure the " +
                    "native library is on java.library.path. Resource tried: /" + resourceDir + "/" + wrapper);
            }
            // Windows resolves a DLL's imports via the standard search order, which does
            // NOT include the directory we just extracted into. macOS (@loader_path) and
            // Linux (static) need nothing extra, but on Windows the wrapper's bundled
            // IPOPT/MUMPS/OpenBLAS/gfortran DLLs must already be loaded by the time we load
            // the wrapper. Pre-load them so their module names resolve in-process.
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                preloadSiblingDlls(tmp, wrapper);
            }
            System.load(dest.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract pagmonet4j native library: " + e.getMessage());
        }
    }

    /**
     * Extract every file under {@code resourceDir} (e.g. {@code natives/osx-arm64}) into
     * {@code destDir}, returning the number of files extracted. Handles both the packaged
     * case (running from a JAR — enumerate the zip) and the exploded case (running from a
     * classes directory — copy the on-disk files), so dependency dylibs/DLLs travel with
     * the wrapper. Falls back to extracting just the wrapper if the location can't be
     * enumerated.
     */
    private static int extractClosure(String resourceDir, Path destDir) throws IOException {
        URL location = null;
        try {
            location = NativeLoader.class.getProtectionDomain().getCodeSource().getLocation();
        } catch (Exception ignored) {}

        if (location != null && "file".equals(location.getProtocol())) {
            File src;
            try {
                src = new File(location.toURI());
            } catch (Exception e) {
                src = new File(location.getPath());
            }
            if (src.isFile()) {
                // Packaged JAR: enumerate entries under resourceDir/.
                int count = 0;
                try (ZipFile zip = new ZipFile(src)) {
                    String prefix = resourceDir + "/";
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry e = entries.nextElement();
                        if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
                        String name = e.getName().substring(prefix.length());
                        if (name.isEmpty() || name.contains("/")) continue; // flat dir only
                        try (InputStream in = zip.getInputStream(e)) {
                            Files.copy(in, destDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                        }
                        count++;
                    }
                }
                if (count > 0) return count;
            } else if (src.isDirectory()) {
                // Exploded classpath: copy the on-disk closure.
                File dir = new File(src, resourceDir);
                File[] files = dir.listFiles(File::isFile);
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        Files.copy(f.toPath(), destDir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                    return files.length;
                }
            }
        }

        // Last resort: extract only the wrapper via the resource stream. This works when
        // the closure can't be enumerated (unusual classloaders); a wrapper with no
        // bundled deps — the static base/Linux case — loads fine this way.
        String single = "/" + resourceDir + "/" + nativeFileName();
        try (InputStream in = NativeLoader.class.getResourceAsStream(single)) {
            if (in == null) return 0;
            Files.copy(in, destDir.resolve(nativeFileName()), StandardCopyOption.REPLACE_EXISTING);
            return 1;
        }
    }

    /**
     * Pre-load every sibling {@code .dll} in {@code dir} (except the wrapper itself) so
     * the wrapper's imports resolve to already-loaded modules. Dependency order is
     * unknown, so this loads iteratively: each pass loads whatever currently links and
     * retries the rest, until a pass makes no progress. DLLs that never load are left
     * for the wrapper load to surface as a clear error (they are typically unused).
     */
    private static void preloadSiblingDlls(Path dir, String wrapperName) {
        File[] dlls = dir.toFile().listFiles(
            (d, n) -> n.toLowerCase().endsWith(".dll") && !n.equals(wrapperName));
        if (dlls == null || dlls.length == 0) return;

        java.util.List<File> pending = new java.util.ArrayList<>(java.util.Arrays.asList(dlls));
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            java.util.Iterator<File> it = pending.iterator();
            while (it.hasNext()) {
                File dll = it.next();
                try {
                    System.load(dll.getAbsolutePath());
                    it.remove();
                    progress = true;
                } catch (UnsatisfiedLinkError retryLater) {
                    // A dependency of this DLL is not loaded yet; try again next pass.
                }
            }
        }
    }

    private static String platformRid() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("win")) {
            if (!arch.contains("amd64") && !arch.contains("x86_64"))
                throw new UnsupportedOperationException(
                    "PagmoNet4j does not provide native binaries for Windows/" + arch +
                    ". Only win-x64 is supported. See the project README for build-from-source instructions.");
            return "win-x64";
        }
        if (os.contains("mac") || os.contains("darwin"))
            return (arch.contains("aarch64") || arch.contains("arm")) ? "osx-arm64" : "osx-x64";
        if (os.contains("linux")) {
            if (arch.contains("aarch64") || arch.contains("arm"))
                throw new UnsupportedOperationException(
                    "PagmoNet4j does not provide native binaries for Linux/" + arch +
                    ". Only linux-x64 is supported. See the project README for build-from-source instructions.");
            return "linux-x64";
        }
        throw new UnsupportedOperationException(
            "PagmoNet4j does not support this platform: os.name=" +
            System.getProperty("os.name") + " os.arch=" + System.getProperty("os.arch") +
            ". See the project README for build-from-source instructions.");
    }

    private static String nativeFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return LIB_NAME + ".dll";
        if (os.contains("mac")) return "lib" + LIB_NAME + ".dylib";
        return "lib" + LIB_NAME + ".so";
    }
}
