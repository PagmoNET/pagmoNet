package io.github.pagmonet.pagmonet4j.ext

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HypervolumeExtensionsTest {

    @Test
    fun computeMatchesKnown2DValue() {
        // Two points {(1,3),(2,2)} against reference (4,4): dominated area = 5. (Mirrors HypervolumeTest.java.)
        hypervolumeOf(listOf(doubleArrayOf(1.0, 3.0), doubleArrayOf(2.0, 2.0))).use { hv ->
            assertEquals(5.0, hv.compute(doubleArrayOf(4.0, 4.0)), 1e-12)
        }
    }

    @Test
    fun contributionsMatchExclusiveAndContributors() {
        val points = listOf(doubleArrayOf(1.0, 3.0), doubleArrayOf(2.0, 2.0), doubleArrayOf(3.0, 1.0))
        hypervolumeOf(points).use { hv ->
            val ref = doubleArrayOf(4.0, 4.0)
            val contribs = hv.contributions(ref)
            assertEquals(3, contribs.size)
            for (i in 0 until 3) {
                assertEquals(hv.exclusive(i.toLong(), ref), contribs[i], 1e-12)
                assertTrue(contribs[i] > 0.0)
            }
            assertTrue(hv.leastContributor(ref) in 0L..2L)
            assertTrue(hv.greatestContributor(ref) in 0L..2L)
        }
    }

    @Test
    fun referencePointIsWorseThanEveryStoredPoint() {
        hypervolumeOf(listOf(doubleArrayOf(1.0, 3.0), doubleArrayOf(2.0, 2.0), doubleArrayOf(3.0, 1.0))).use { hv ->
            val ref = hv.referencePoint(offset = 1.0)
            assertEquals(2, ref.size)
            assertTrue(ref[0] >= 3.0 && ref[1] >= 3.0, "refpoint dominates the worst objective values")
        }
    }
}
