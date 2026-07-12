package io.github.pagmonet.pagmonet4j.algorithms;

import io.github.pagmonet.pagmonet4j.*;
import io.github.pagmonet.pagmonet4j.NativeInterop;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes any {@link IAlgorithm} to a type-erased {@link algorithm} for
 * pagmo's island/archipelago machinery.
 *
 * <p>Concrete pagmo algorithm classes expose a {@code to_algorithm()} method that
 * performs a zero-copy native conversion. This is discovered via reflection so the
 * registry is self-maintaining — new SWIG-generated algorithm classes automatically
 * use the fast native path without any manual registration.
 *
 * <p>Custom managed {@link IAlgorithm} implementations that don't have
 * {@code to_algorithm()} are wrapped via the director callback bridge.
 */
public final class AlgorithmInterop {

    private AlgorithmInterop() {}

    // Cache Method lookups per class to amortize reflection overhead.
    // null sentinel means "no to_algorithm() — use director bridge".
    private static final ConcurrentHashMap<Class<?>, Method> TO_ALGORITHM_CACHE =
        new ConcurrentHashMap<>();
    private static final Method NO_METHOD;

    static {
        try {
            // Sentinel: a Method object that can never be invoked, used as a
            // not-found marker in the ConcurrentHashMap (which disallows null values).
            NO_METHOD = Object.class.getMethod("getClass");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Normalizes any {@link IAlgorithm} to a type-erased {@link algorithm}.
     *
     * <p>If the concrete class exposes {@code public algorithm to_algorithm()}, it is called
     * for zero-copy native wrapping. Otherwise the director callback bridge is used.
     */
    public static algorithm normalizeToTypeErased(IAlgorithm source) {
        if (source == null) throw new NullPointerException("source");
        if (source instanceof algorithm) return (algorithm) source;

        Method toAlgorithm = TO_ALGORITHM_CACHE.computeIfAbsent(
            source.getClass(), AlgorithmInterop::findToAlgorithmMethod);

        if (toAlgorithm == NO_METHOD) {
            // Custom managed algorithm — use director bridge
            return new algorithm(source);
        }

        try {
            return (algorithm) toAlgorithm.invoke(source);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException("to_algorithm() failed on " + source.getClass().getName(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("to_algorithm() not accessible on " + source.getClass().getName(), e);
        }
    }

    private static Method findToAlgorithmMethod(Class<?> cls) {
        try {
            Method m = cls.getMethod("to_algorithm");
            if (algorithm.class.isAssignableFrom(m.getReturnType())) {
                return m;
            }
        } catch (NoSuchMethodException ignored) {}
        return NO_METHOD;
    }
}
