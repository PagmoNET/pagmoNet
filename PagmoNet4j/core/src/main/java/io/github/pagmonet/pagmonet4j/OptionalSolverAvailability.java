package io.github.pagmonet.pagmonet4j;

/**
 * Runtime detection of optional pagmo solvers that may or may not be compiled
 * into the native library.
 *
 * <p>Detection is based on a compile-time flag exposed via a lightweight JNI probe.
 * No solver objects are constructed, so this is safe even when the underlying
 * solver library is absent or would crash on instantiation.
 *
 * <p>Use these flags to guard solver construction in code that should work both
 * with and without the optional solvers available.
 */
public final class OptionalSolverAvailability {

    private OptionalSolverAvailability() {}

    private static final boolean NLOPT_AVAILABLE = pagmonet4jJNI.pagmonet4j_has_nlopt_support();
    private static final boolean IPOPT_AVAILABLE = pagmonet4jJNI.pagmonet4j_has_ipopt_support();

    /** Returns {@code true} if the native library was built with NLopt support. */
    public static boolean isNloptAvailable() {
        return NLOPT_AVAILABLE;
    }

    /** Returns {@code true} if the native library was built with IPOPT support. */
    public static boolean isIpoptAvailable() {
        return IPOPT_AVAILABLE;
    }
}
