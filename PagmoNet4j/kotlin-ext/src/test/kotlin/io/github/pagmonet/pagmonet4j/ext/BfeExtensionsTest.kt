package io.github.pagmonet.pagmonet4j.ext

import io.github.pagmonet.pagmonet4j.*
import io.github.pagmonet.pagmonet4j.problems.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BfeExtensionsTest {

    private class SphereProblem : ManagedProblemBase() {
        override fun fitness(x: DoubleVector): DoubleVector = vec(x.toDoubleArray().sumOf { it * it })
        override fun get_bounds() = boundsOf(doubleArrayOf(-5.0, -5.0), doubleArrayOf(5.0, 5.0))
        override fun get_thread_safety() = ThreadSafety.Constant
    }

    @Test
    fun factoriesReturnUsableBfes() {
        val d = defaultBfe(); assertNotNull(d); d.delete()
        val t = threadBfe(); assertNotNull(t); t.delete()
        val m = memberBfe(); assertNotNull(m); m.delete()
    }

    @Test
    fun batchFitnessReshapesOneVectorPerPoint() {
        SphereProblem().use { prob ->
            val results = prob.batchFitnessOf(
                listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 2.0), doubleArrayOf(0.0, 3.0)),
            )
            assertEquals(3, results.size)
            assertEquals(1, results[0].size) // single-objective sphere
            assertEquals(2.0, results[0][0], 1e-12) // 1 + 1
            assertEquals(8.0, results[1][0], 1e-12) // 4 + 4
            assertEquals(9.0, results[2][0], 1e-12) // 0 + 9
        }
    }

    @Test
    fun emptyBatchReturnsEmpty() {
        SphereProblem().use { prob ->
            assertTrue(prob.batchFitnessOf(emptyList()).isEmpty())
        }
    }
}
