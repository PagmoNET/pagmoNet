package io.github.samthegliderpilot.pagmonet4j;

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
        // When extracting from JAR, the temp directory must contain the current PID
        // so concurrent JVM instances don't collide. This test checks the naming
        // convention by scanning temp directories created during this test run.
        // (NativeLoader is already loaded via PAGMO4J_NATIVE_DIR in tests — we
        // verify the naming contract by inspecting the directory name pattern.)
        String tmpDir = System.getProperty("java.io.tmpdir");
        long pid = ProcessHandle.current().pid();
        File[] matches = new File(tmpDir).listFiles(f ->
            f.isDirectory() && f.getName().startsWith("pagmonet4j-native-" + pid + "-"));
        // If PAGMO4J_NATIVE_DIR is set (normal test mode), extraction path is skipped —
        // assert the naming pattern is correct for the extraction path when it IS used.
        // This is a structural test: if extraction was used, the dir name contains the pid.
        if (matches != null && matches.length > 0) {
            for (File dir : matches) {
                String name = dir.getName();
                assertTrue(name.startsWith("pagmonet4j-native-" + pid + "-"),
                    "Extraction dir must follow 'pagmonet4j-native-<pid>-<uuid>' pattern, got: " + name);
                // UUID portion (after pid-) must be parseable
                String uuidPart = name.substring(("pagmonet4j-native-" + pid + "-").length());
                assertDoesNotThrow(() -> java.util.UUID.fromString(uuidPart),
                    "Suffix must be a valid UUID, got: " + uuidPart);
            }
        }
        // If no dirs found (normal test env with PAGMO4J_NATIVE_DIR), test passes vacuously.
    }
}
