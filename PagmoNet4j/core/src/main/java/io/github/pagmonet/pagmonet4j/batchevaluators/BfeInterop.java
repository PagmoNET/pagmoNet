package io.github.pagmonet.pagmonet4j.batchevaluators;

import io.github.pagmonet.pagmonet4j.*;

/**
 * Interop utilities for converting managed batch fitness evaluators to pagmo's
 * type-erased {@link bfe}.
 *
 * <p>The full custom-BFE director pattern (subclassing {@code BfeCallbackAdapter})
 * requires running the {@code regen-swig} CMake target to generate the
 * {@code BfeCallback} SWIG director base class. Once generated, users can:
 *
 * <pre>{@code
 * class MyGpuBfe extends BfeCallbackAdapter {
 *     \@Override
 *     protected DoubleVector callManaged(problem prob, DoubleVector dvs) {
 *         // custom GPU batch evaluation
 *     }
 * }
 *
 * try (MyGpuBfe cb = new MyGpuBfe();
 *      bfe b = BfeInterop.toBfe(cb)) {
 *     island isl = island.create(algo, prob, b, 64L, 42L);
 * }
 * }</pre>
 *
 * <p>Until the director class is generated, use {@link bfe}'s native implementations
 * ({@code default_bfe().to_bfe()}, {@code thread_bfe().to_bfe()}) or the
 * {@link BfeBridge} serial fallback.
 */
public final class BfeInterop {

    private BfeInterop() {}

    // NOTE: toBfe(BfeCallbackAdapter) will be added here once the BfeCallback
    // SWIG director class is generated via 'cmake --build . --target regen-swig'.
    // The native bridge functions pagmonet_bfe_from_callback_java / pagmonet_bfe_from_callback
    // are already present in the native library and ready to be called.
}
