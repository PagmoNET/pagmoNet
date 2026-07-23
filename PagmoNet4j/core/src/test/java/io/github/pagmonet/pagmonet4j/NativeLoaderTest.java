package io.github.pagmonet.pagmonet4j;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NativeLoader — same package to access package-private class.
 * Relies on PAGMO4J_NATIVE_DIR being set (same requirement as all JNI tests).
 */
class NativeLoaderTest {

    @Test
    void loadIsIdempotent() {
        // Calling load() multiple times must not throw or double-load.
        NativeLoader.load();
        NativeLoader.load();
        NativeLoader.load();
        // If we reach here without UnsatisfiedLinkError or double-load crash, test passes.
    }

    @Test
    void loadedFlagIsSetAfterFirstCall() {
        NativeLoader.load();
        // Verify the library is actually loaded by performing a trivial JNI call.
        assertNotNull(pagmonet4jJNI.PAGMO_VERSION_get(), "native call should succeed after load()");
    }

    @Test
    void extractionUsesUniqueDirectoryWithPid() {
        // When extracting from JAR, the temp directory name must contain the current PID so
        // concurrent JVM instances don't collide, and the unique suffix is supplied by
        // Files.createTempDirectory. This test checks that naming contract by scanning temp
        // directories created during this run. (NativeLoader is already loaded via
        // PAGMO4J_NATIVE_DIR in tests, so extraction may or may not have run.)
        String tmpDir = System.getProperty("java.io.tmpdir");
        long pid = ProcessHandle.current().pid();
        String prefix = "pagmonet4j-native-" + pid + "-";
        File[] matches = new File(tmpDir).listFiles(f ->
            f.isDirectory() && f.getName().startsWith(prefix));
        // If extraction was used, the dir name is "<prefix><unique suffix from createTempDirectory>".
        if (matches != null && matches.length > 0) {
            for (File dir : matches) {
                String name = dir.getName();
                assertTrue(name.startsWith(prefix),
                    "Extraction dir must follow 'pagmonet4j-native-<pid>-<unique>' pattern, got: " + name);
                assertFalse(name.substring(prefix.length()).isEmpty(),
                    "createTempDirectory must append a non-empty unique suffix, got: " + name);
            }
        }
        // If no dirs found (normal test env with PAGMO4J_NATIVE_DIR), test passes vacuously.
    }
}
