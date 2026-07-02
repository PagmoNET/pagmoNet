package io.github.samthegliderpilot.pagmonet4j;

import io.github.samthegliderpilot.pagmonet4j.testproblems.TwoDimensionalSingleObjectiveProblem;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PopulationTest {

    // ── construction ──────────────────────────────────────────────────────────

    @Test
    void managedProblemWithSeedSetsCorrectSizeAndSeed() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             population pop = new population(prob, 10L, 42L)) {
            assertEquals(10L, pop.size());
            assertEquals(42L, pop.get_seed());
        }
    }

    @Test
    void nativeProblemConstructorWithSeed() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 8L, 1L)) {
            assertEquals(8L, pop.size());
            assertEquals(1L, pop.get_seed());
        }
    }

    @Test
    void nativeProblemConstructorWithoutSeed() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 5L)) {
            assertEquals(5L, pop.size());
        }
    }

    @Test
    void emptyPopulationHasSizeZero() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped)) {
            assertEquals(0L, pop.size());
        }
    }

    // ── champion ──────────────────────────────────────────────────────────────

    @Test
    void championXIsCorrectDimensionAndWithinBounds() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 20L, 7L)) {
            DoubleVector cx = pop.champion_x();
            try {
                assertEquals(2L, cx.size());
                assertTrue(cx.get(0) >= -10.0 && cx.get(0) <= 10.0);
                assertTrue(cx.get(1) >= -10.0 && cx.get(1) <= 10.0);
            } finally { cx.delete(); }
        }
    }

    @Test
    void championFIsScalarForSingleObjectiveProblem() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 20L, 7L)) {
            DoubleVector cf = pop.champion_f();
            try {
                assertEquals(1L, cf.size());
            } finally { cf.delete(); }
        }
    }

    // ── best / worst index ────────────────────────────────────────────────────

    @Test
    void bestIdxIsValidIndex() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 16L, 3L)) {
            long idx = pop.best_idx();
            assertTrue(idx >= 0L && idx < 16L);
        }
    }

    @Test
    void worstIdxIsValidIndex() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 16L, 3L)) {
            long idx = pop.worst_idx();
            assertTrue(idx >= 0L && idx < 16L);
        }
    }

    // ── set_xf ────────────────────────────────────────────────────────────────

    @Test
    void setXfAppearsInGetX() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 4L, 1L)) {
            DoubleVector x = new DoubleVector(); x.add(0.0); x.add(3.0);
            DoubleVector f = new DoubleVector(); f.add(0.0);
            pop.set_xf(0L, x, f);
            x.delete(); f.delete();

            VectorOfVectorOfDoubles allX = pop.get_x();
            try (var ignored = SWIGTestUtils.close(allX)) {
                assertEquals(0.0, allX.get(0).get(0), 1e-12);
                assertEquals(3.0, allX.get(0).get(1), 1e-12);
            }
        }
    }

    @Test
    void setXfAppearsInGetF() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 4L, 1L)) {
            DoubleVector x = new DoubleVector(); x.add(0.0); x.add(3.0);
            DoubleVector f = new DoubleVector(); f.add(0.0);
            pop.set_xf(0L, x, f);
            x.delete(); f.delete();

            VectorOfVectorOfDoubles allF = pop.get_f();
            try (var ignored = SWIGTestUtils.close(allF)) {
                assertEquals(0.0, allF.get(0).get(0), 1e-12);
            }
        }
    }

    // ── set_x re-evaluation ───────────────────────────────────────────────────

    @Test
    void setXTriggersReEvaluationAtKnownOptimum() {
        // f = x0^2 + (x1-3)^2 = 0 at (0, 3)
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 4L, 1L)) {
            DoubleVector x = new DoubleVector(); x.add(0.0); x.add(3.0);
            pop.set_x(0L, x);
            x.delete();

            VectorOfVectorOfDoubles allF = pop.get_f();
            try (var ignored = SWIGTestUtils.close(allF)) {
                assertEquals(0.0, allF.get(0).get(0), 1e-12,
                    "set_x() must re-evaluate; at the optimum (0,3) f=0");
            }
        }
    }

    // ── push_back ─────────────────────────────────────────────────────────────

    @Test
    void pushBackDecisionVectorOnlyIncreasesSize() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped)) {
            assertEquals(0L, pop.size());
            DoubleVector x = new DoubleVector(); x.add(1.0); x.add(1.0);
            pop.push_back(x);
            x.delete();
            assertEquals(1L, pop.size());
        }
    }

    @Test
    void pushBackDecisionAndFitnessVectorIncreasesSize() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped)) {
            DoubleVector x = new DoubleVector(); x.add(0.0); x.add(3.0);
            DoubleVector f = new DoubleVector(); f.add(0.0);
            pop.push_back(x, f);
            x.delete(); f.delete();
            assertEquals(1L, pop.size());
        }
    }

    // ── get_x / get_f ─────────────────────────────────────────────────────────

    @Test
    void getXReturnsOneVectorPerIndividual() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 6L, 1L)) {
            VectorOfVectorOfDoubles allX = pop.get_x();
            try (var ignored = SWIGTestUtils.close(allX)) {
                assertEquals(6L, allX.size());
                for (int i = 0; i < 6; i++) {
                    assertEquals(2L, allX.get(i).size(), "each decision vector must be 2-D");
                }
            }
        }
    }

    @Test
    void getFReturnsOneVectorPerIndividual() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 6L, 1L)) {
            VectorOfVectorOfDoubles allF = pop.get_f();
            try (var ignored = SWIGTestUtils.close(allF)) {
                assertEquals(6L, allF.size());
                for (int i = 0; i < 6; i++) {
                    assertEquals(1L, allF.get(i).size(), "each fitness vector must be 1-D");
                }
            }
        }
    }

    // ── IDs ───────────────────────────────────────────────────────────────────

    @Test
    void getIdReturnsUniqueIdPerIndividual() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 5L, 1L)) {
            ULongLongVector ids = pop.get_ID();
            try (var ignored = SWIGTestUtils.close(ids)) {
                assertEquals(5L, ids.size());
                Set<BigInteger> idSet = new HashSet<>();
                for (int i = 0; i < 5; i++) idSet.add(ids.get(i));
                assertEquals(5, idSet.size(), "each individual must have a unique ID");
            }
        }
    }

    // ── random_decision_vector ────────────────────────────────────────────────

    @Test
    void randomDecisionVectorIsCorrectDimensionAndWithinBounds() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 1L, 1L)) {
            DoubleVector rdv = pop.random_decision_vector();
            try {
                assertEquals(2L, rdv.size());
                assertTrue(rdv.get(0) >= -10.0 && rdv.get(0) <= 10.0);
                assertTrue(rdv.get(1) >= -10.0 && rdv.get(1) <= 10.0);
            } finally { rdv.delete(); }
        }
    }

    // ── get_problem ───────────────────────────────────────────────────────────

    @Test
    void getProblemReturnsCopyThatIsCloseable() {
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             problem wrapped = new problem(prob);
             population pop = new population(wrapped, 4L, 1L)) {
            try (problem p = pop.get_problem()) {
                assertNotNull(p);
            }
        }
    }
}
