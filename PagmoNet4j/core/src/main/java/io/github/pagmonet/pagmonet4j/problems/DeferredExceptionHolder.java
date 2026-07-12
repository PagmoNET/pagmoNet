package io.github.pagmonet.pagmonet4j.problems;

/**
 * Implemented by director adapters that defer managed exceptions across JNI boundaries.
 *
 * <p>Allows island/archipelago cleanup code to detect and log exceptions that were
 * deferred during evolve but never consumed via {@code wait_check()}.
 */
public interface DeferredExceptionHolder {
    /**
     * Returns and clears the first deferred exception, or {@code null} if none.
     */
    Throwable consumeDeferredException();
}
