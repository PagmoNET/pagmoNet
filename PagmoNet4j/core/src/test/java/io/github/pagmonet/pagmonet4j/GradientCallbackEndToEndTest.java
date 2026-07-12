package io.github.pagmonet.pagmonet4j;

import io.github.pagmonet.pagmonet4j.testproblems.TwoDimensionalSingleObjectiveProblem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a managed problem's gradient() and has_gradient() are correctly
 * dispatched through the JNI director boundary when the problem is wrapped in a
 * native {@link problem}.
 */
class GradientCallbackEndToEndTest {

    @Test
    void hasGradientIsTrueForManagedProblemThatDeclaresSo() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob)) {
            assertTrue(wrapped.has_gradient(),
                "has_gradient() must propagate from managed problem through JNI director");
        }
    }

    @Test
    void gradientValuesFlowThroughJniBoundary() {
        // f = x0^2 + (x1-3)^2  =>  grad = [2*x0, 2*x1 - 6]
        // at (1, 1): grad = [2, -4]
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob)) {
            DoubleVector x = new DoubleVector();
            x.add(1.0); x.add(1.0);
            DoubleVector grad = wrapped.gradient(x);
            try {
                assertEquals(2L, grad.size(), "gradient must have one entry per decision variable");
                assertEquals(2.0,  grad.get(0), 1e-12, "∂f/∂x0 at (1,1) must be 2");
                assertEquals(-4.0, grad.get(1), 1e-12, "∂f/∂x1 at (1,1) must be -4");
            } finally {
                x.delete();
                grad.delete();
            }
        }
    }

    @Test
    void gradientAtOptimumIsZero() {
        // at the optimum (0, 3): grad = [0, 0]
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob)) {
            DoubleVector x = new DoubleVector();
            x.add(0.0); x.add(3.0);
            DoubleVector grad = wrapped.gradient(x);
            try {
                assertEquals(0.0, grad.get(0), 1e-12, "∂f/∂x0 at optimum must be 0");
                assertEquals(0.0, grad.get(1), 1e-12, "∂f/∂x1 at optimum must be 0");
            } finally {
                x.delete();
                grad.delete();
            }
        }
    }

    @Test
    void hasGradientIsFalseWhenManagedProblemDoesNotProvideGradient() {
        // A plain ManagedProblemBase subclass with no gradient override defaults to false.
        try (problem wrapped = new problem(new io.github.pagmonet.pagmonet4j.problems.ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) {
                DoubleVector r = new DoubleVector(); r.add(x.get(0) * x.get(0)); return r;
            }
            @Override public PairOfDoubleVectors get_bounds() {
                DoubleVector lo = new DoubleVector(); lo.add(-1.0);
                DoubleVector hi = new DoubleVector(); hi.add(1.0);
                return new PairOfDoubleVectors(lo, hi);
            }
        })) {
            assertFalse(wrapped.has_gradient(),
                "has_gradient() must be false when managed problem does not override it");
        }
    }
}
