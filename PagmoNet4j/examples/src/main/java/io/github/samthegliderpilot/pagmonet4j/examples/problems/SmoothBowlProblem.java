package io.github.samthegliderpilot.pagmonet4j.examples.problems;

import io.github.samthegliderpilot.pagmonet4j.*;
import io.github.samthegliderpilot.pagmonet4j.problems.*;

/**
 * Smooth differentiable problem for the IPOPT example: minimise x^2 + (y-3)^2
 * (optimum (0,3), f*=0).
 *
 * <p>IPOPT is a gradient-based interior-point solver, so it requires
 * {@link #has_gradient()} = true; supplying the sparsity pattern lets it set up
 * the NLP efficiently. Java twin of the C# {@code SmoothBowlProblem}.
 */
public final class SmoothBowlProblem extends ManagedProblemBase {

    @Override
    public DoubleVector fitness(DoubleVector x) {
        return vec(x.get(0) * x.get(0) + Math.pow(x.get(1) - 3.0, 2));
    }

    @Override
    public PairOfDoubleVectors get_bounds() {
        return bounds(new double[]{-5, -5}, new double[]{5, 5});
    }

    @Override public boolean has_gradient() { return true; }

    @Override
    public DoubleVector gradient(DoubleVector x) {
        return vec(2.0 * x.get(0), 2.0 * (x.get(1) - 3.0));
    }

    @Override public boolean has_gradient_sparsity() { return true; }

    @Override
    public SparsityPattern gradient_sparsity() {
        return sparsity(new long[]{0, 0}, new long[]{0, 1});
    }

    @Override public String get_name() { return "SmoothBowlProblem"; }
    @Override public ThreadSafety get_thread_safety() { return ThreadSafety.Constant; }
}
