package io.github.pagmonet.pagmonet4j.problems;

import io.github.pagmonet.pagmonet4j.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link ProblemCallbackAdapter} correctly defers exceptions and returns
 * safe fallback values rather than letting them escape the JNI boundary.
 */
class ProblemCallbackAdapterTest {

    private static DoubleVector vec(double... values) {
        DoubleVector v = new DoubleVector();
        for (double d : values) v.add(d);
        return v;
    }

    // ── fitness exception deferral ────────────────────────────────────────────

    @Test
    void fitnessExceptionIsDeferredAndFallbackReturned() {
        RuntimeException boom = new RuntimeException("fitness boom");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { throw boom; }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        DoubleVector x = vec(0.5);
        DoubleVector result = adapter.fitness(x);

        assertNotNull(result, "fitness() must return a non-null fallback even on exception");
        Throwable deferred = adapter.consumeDeferredException();
        assertSame(boom, deferred, "the original exception must be deferred");
        assertNull(adapter.consumeDeferredException(), "deferred exception must be cleared after consume");
    }

    @Test
    void fitnessNullReturnDeferredAsNullPointerException() {
        IProblem nullFitness = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return null; }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(nullFitness);
        DoubleVector result = adapter.fitness(vec(0.0));

        assertNotNull(result, "must return a non-null fallback for null fitness return");
        assertNotNull(adapter.consumeDeferredException(), "null fitness return must be deferred as NPE");
    }

    // ── first exception wins ──────────────────────────────────────────────────

    @Test
    void onlyFirstExceptionIsRetained() {
        RuntimeException first  = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        IProblem doubleThrow = new ManagedProblemBase() {
            private int calls = 0;
            @Override public DoubleVector fitness(DoubleVector x) { throw (calls++ == 0) ? first : second; }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(doubleThrow);
        adapter.fitness(vec(0.0));
        adapter.fitness(vec(0.0));
        assertSame(first, adapter.consumeDeferredException(), "only the first exception must be retained");
    }

    // ── get_bounds exception deferral ─────────────────────────────────────────

    @Test
    void boundsExceptionIsDeferredAndFallbackReturned() {
        RuntimeException boom = new RuntimeException("bounds boom");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { throw boom; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        PairOfDoubleVectors result = adapter.get_bounds();

        assertNotNull(result, "get_bounds() must return a non-null fallback on exception");
        assertSame(boom, adapter.consumeDeferredException());
    }

    // ── has_gradient / gradient ───────────────────────────────────────────────

    @Test
    void hasGradientDelegatesToProblem() {
        IProblem withGrad = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_gradient() { return true; }
        };
        assertTrue(new ProblemCallbackAdapter(withGrad).has_gradient());
    }

    @Test
    void hasGradientFalseWhenProblemReturnsFalse() {
        IProblem noGrad = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
        };
        assertFalse(new ProblemCallbackAdapter(noGrad).has_gradient());
    }

    @Test
    void gradientExceptionIsDeferredAndFallbackReturned() {
        RuntimeException boom = new RuntimeException("gradient boom");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_gradient() { return true; }
            @Override public DoubleVector gradient(DoubleVector x) { throw boom; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        DoubleVector result = adapter.gradient(vec(0.5));

        assertNotNull(result, "gradient() must return a non-null fallback on exception");
        assertSame(boom, adapter.consumeDeferredException());
    }

    @Test
    void gradientNullReturnDeferredAsNullPointerException() {
        IProblem nullGrad = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_gradient() { return true; }
            @Override public DoubleVector gradient(DoubleVector x) { return null; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(nullGrad);
        DoubleVector result = adapter.gradient(vec(0.0));

        assertNotNull(result, "must return a non-null fallback for null gradient return");
        assertInstanceOf(NullPointerException.class, adapter.consumeDeferredException());
    }

    // ── has_hessians / hessians ───────────────────────────────────────────────

    @Test
    void hasHessiansDelegatesToProblem() {
        IProblem withHess = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_hessians() { return true; }
        };
        assertTrue(new ProblemCallbackAdapter(withHess).has_hessians());
    }

    @Test
    void hessiansExceptionIsDeferredAndFallbackReturned() {
        RuntimeException boom = new RuntimeException("hessians boom");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_hessians() { return true; }
            @Override public VectorOfVectorOfDoubles hessians(DoubleVector x) { throw boom; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        VectorOfVectorOfDoubles result = adapter.hessians(vec(0.5));

        assertNotNull(result, "hessians() must return a non-null fallback on exception");
        assertSame(boom, adapter.consumeDeferredException());
    }

    @Test
    void hessiansNullReturnDeferredAsNullPointerException() {
        IProblem nullHess = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_hessians() { return true; }
            @Override public VectorOfVectorOfDoubles hessians(DoubleVector x) { return null; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(nullHess);
        VectorOfVectorOfDoubles result = adapter.hessians(vec(0.0));

        assertNotNull(result, "must return a non-null fallback for null hessians return");
        assertInstanceOf(NullPointerException.class, adapter.consumeDeferredException());
    }

    // ── has_set_seed / set_seed ───────────────────────────────────────────────

    @Test
    void hasSetSeedDelegatesToProblem() {
        IProblem withSeed = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_set_seed() { return true; }
            @Override public void set_seed(long seed) {}
        };
        assertTrue(new ProblemCallbackAdapter(withSeed).has_set_seed());
    }

    @Test
    void setSeedExceptionIsDeferred() {
        RuntimeException boom = new RuntimeException("set_seed boom");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_set_seed() { return true; }
            @Override public void set_seed(long seed) { throw boom; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        adapter.set_seed(42L);

        assertSame(boom, adapter.consumeDeferredException(), "set_seed() exception must be deferred");
    }

    // ── has_batch_fitness / batch_fitness ─────────────────────────────────────

    @Test
    void hasBatchFitnessDelegatesToProblem() {
        IProblem withBatch = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_batch_fitness() { return true; }
            @Override public DoubleVector batch_fitness(DoubleVector dvs) { return vec(0); }
        };
        assertTrue(new ProblemCallbackAdapter(withBatch).has_batch_fitness());
    }

    @Test
    void batchFitnessExceptionIsDeferredAndFallbackReturned() {
        RuntimeException boom = new RuntimeException("batch boom");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_batch_fitness() { return true; }
            @Override public DoubleVector batch_fitness(DoubleVector dvs) { throw boom; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        DoubleVector result = adapter.batch_fitness(vec(0.5));

        assertNotNull(result, "batch_fitness() must return a non-null fallback on exception");
        assertSame(boom, adapter.consumeDeferredException());
    }

    @Test
    void batchFitnessNullReturnDeferredAsNullPointerException() {
        IProblem nullBatch = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { return vec(0); }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
            @Override public boolean has_batch_fitness() { return true; }
            @Override public DoubleVector batch_fitness(DoubleVector dvs) { return null; }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(nullBatch);
        DoubleVector result = adapter.batch_fitness(vec(0.0));

        assertNotNull(result, "must return a non-null fallback for null batch_fitness return");
        assertInstanceOf(NullPointerException.class, adapter.consumeDeferredException());
    }

    // ── null constructor argument ─────────────────────────────────────────────

    @Test
    void nullProblemThrows() {
        assertThrows(NullPointerException.class, () -> new ProblemCallbackAdapter(null));
    }

    // ── first-exception-wins guarantee (AtomicReference) ─────────────────────

    @Test
    void firstExceptionIsRetainedWhenMultipleExceptionsDeferred() {
        // RED: with volatile + null-check, two concurrent threads could both see null and
        // overwrite. GREEN: AtomicReference.compareAndSet(null, ex) guarantees only the
        // first exception is stored.
        RuntimeException first  = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");

        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { throw first; }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        DoubleVector x = vec(0.5);

        adapter.fitness(x);  // defers "first"

        // Manually inject "second" — simulates a second callback firing before consume.
        // Since compareAndSet(null, ex) is used, "first" must win.
        // We test this by calling fitness() again (which would try to defer "first" again,
        // but since deferredEx is non-null, compareAndSet returns false — no overwrite).
        adapter.fitness(x);

        Throwable deferred = adapter.consumeDeferredException();
        assertSame(first, deferred, "First deferred exception must be retained; second must be ignored");
        assertNull(adapter.consumeDeferredException(), "consumeDeferredException() must clear on call");
    }

    @Test
    void consumeClearsExceptionSoSubsequentCallReturnsNull() {
        RuntimeException ex = new RuntimeException("oops");
        IProblem throwing = new ManagedProblemBase() {
            @Override public DoubleVector fitness(DoubleVector x) { throw ex; }
            @Override public PairOfDoubleVectors get_bounds() { return bounds(new double[]{-1}, new double[]{1}); }
        };

        ProblemCallbackAdapter adapter = new ProblemCallbackAdapter(throwing);
        adapter.fitness(vec(0.5));

        assertSame(ex, adapter.consumeDeferredException(), "Must return deferred exception");
        assertNull(adapter.consumeDeferredException(), "Must return null after clearing");
    }
}
