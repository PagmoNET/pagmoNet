package io.github.pagmonet.pagmonet4j.algorithms;

import io.github.pagmonet.pagmonet4j.*;
import io.github.pagmonet.pagmonet4j.testproblems.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the Java {@link grid_search} UDA (the Java twin of the C# grid_search demo). Mirrors
 * the C# Test_grid_search: exact optimum on the 2-D quadratic, an empty universal log surface,
 * a feasible point on a constrained problem, and the type-erased island path.
 */
class GridSearchTest {

    @Test
    void findsExactOptimumWhenGridHitsIt() {
        // 20 steps over [-10, 10] samples every integer, so (0, 3) -- the optimum of x^2+(y-3)^2 --
        // is on the grid and must be found exactly.
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             grid_search algo = new grid_search(20L);
             population pop = new population(wrapped, 32L, 7L)) {

            algo.evolve(pop); // grid_search mutates and returns the same population

            DoubleVector cx = pop.champion_x();
            DoubleVector cf = pop.champion_f();
            try {
                assertEquals(0.0, cx.get(0), 1e-12, "champion x0 should be the sampled optimum");
                assertEquals(3.0, cx.get(1), 1e-12, "champion x1 should be the sampled optimum");
                assertEquals(0.0, cf.get(0), 1e-12, "champion objective should be the sampled optimum");
            } finally {
                cf.delete();
                cx.delete();
            }
        }
    }

    @Test
    void exposesEmptyUniversalLogSurface() {
        // grid_search produces no structured log; getLogLines() must be non-null and empty.
        try (grid_search algo = new grid_search(4L)) {
            IAlgorithm iface = algo;
            var lines = iface.getLogLines();
            assertNotNull(lines);
            assertTrue(lines.isEmpty());
        }
    }

    @Test
    void selectsFeasiblePointOnConstrainedProblem() {
        // Constrained problem: minimise x0+x1 s.t. x0^2+x1^2 <= 1. grid_search must pick the best
        // *feasible* sampled point, so the champion satisfies the constraint and improves the objective.
        try (TwoDimensionalConstrainedProblem prob = new TwoDimensionalConstrainedProblem();
             problem wrapped = new problem(prob);
             grid_search algo = new grid_search(6L);
             population pop = new population(wrapped, 16L, 9L)) {

            algo.evolve(pop);

            DoubleVector cf = pop.champion_f();
            try {
                assertTrue(cf.get(1) <= 1e-9, "champion must satisfy the inequality constraint (feasible)");
                assertTrue(cf.get(0) < 0.0, "grid_search should find a negative-objective feasible point");
            } finally {
                cf.delete();
            }
        }
    }

    @Test
    void runsInTypeErasedIslandPath() {
        // Same managed UDA driven through the native island path (island.create wraps the
        // IAlgorithm in a pagmo::algorithm via the director adapter).
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             grid_search algo = new grid_search(20L);
             island isl = island.create(algo, prob, 8L, 2L)) {

            isl.evolve(1L);
            isl.wait_check();
            assertEquals(EvolveStatus.Idle, isl.status());

            try (population evolved = isl.get_population()) {
                DoubleVector cf = evolved.champion_f();
                try {
                    assertTrue(cf.get(0) < 1e-9,
                        "type-erased managed grid_search should reach the sampled optimum f*=0");
                } finally {
                    cf.delete();
                }
            }
        }
    }

    @Test
    void constructorRejectsInvalidSteps() {
        assertThrows(IllegalArgumentException.class, () -> new grid_search(new long[] {}));
        assertThrows(IllegalArgumentException.class, () -> new grid_search(0L));
        assertThrows(IllegalArgumentException.class, () -> new grid_search(new long[] {4L, 0L}));
    }
}
