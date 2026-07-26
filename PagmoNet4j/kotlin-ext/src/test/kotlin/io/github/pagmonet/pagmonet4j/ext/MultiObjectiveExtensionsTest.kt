package io.github.pagmonet.pagmonet4j.ext

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MultiObjectiveExtensionsTest {

    // A 3-point Pareto front (all mutually non-dominated) plus one dominated point in some tests.
    private val front = listOf(
        doubleArrayOf(1.0, 3.0),
        doubleArrayOf(2.0, 2.0),
        doubleArrayOf(3.0, 1.0),
    )

    @Test
    fun paretoDominanceIsDirectionalAndStrict() {
        assertTrue(paretoDominates(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 3.0)))
        assertFalse(paretoDominates(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 1.0)))
        assertFalse(paretoDominates(doubleArrayOf(2.0, 2.0), doubleArrayOf(2.0, 2.0))) // equal → not strict
    }

    @Test
    fun idealAndNadirBracketTheFront() {
        assertArrayEquals(doubleArrayOf(1.0, 1.0), idealPoint(front), 1e-12)
        assertArrayEquals(doubleArrayOf(3.0, 3.0), nadirPoint(front), 1e-12)
    }

    @Test
    fun crowdingDistanceHasOneValuePerIndividualWithInfiniteEndpoints() {
        val cd = crowdingDistance(front)
        assertEquals(3, cd.size)
        assertTrue(cd.count { it.isInfinite() } >= 2, "front extremes have infinite crowding distance")
    }

    @Test
    fun nonDominatedFrontExcludesDominatedPoints() {
        val withDominated = front + listOf(doubleArrayOf(3.0, 3.0)) // dominated by (2,2) and (1,3)
        val idx = nonDominatedFront2D(withDominated).toSet()
        assertEquals(setOf(0L, 1L, 2L), idx)
        assertFalse(3L in idx)
    }

    @Test
    fun selectBestNAndSortReturnValidIndices() {
        val best2 = selectBestNMo(front, 2)
        assertEquals(2, best2.size)
        assertTrue(best2.all { it in 0L..2L })

        val sorted = sortPopulationMo(front)
        assertEquals(setOf(0L, 1L, 2L), sorted.toSet())
    }

    @Test
    fun decomposeWeightedIsAWeightedSum() {
        // "weighted" decomposition is the plain weighted sum of the objectives.
        val d = decomposeObjectives(
            objectives = doubleArrayOf(2.0, 4.0),
            weights = doubleArrayOf(0.5, 0.5),
            referencePoint = doubleArrayOf(0.0, 0.0),
            method = "weighted",
        )
        assertEquals(1, d.size)
        assertEquals(3.0, d[0], 1e-12) // 0.5*2 + 0.5*4
    }
}
