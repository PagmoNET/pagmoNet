package io.github.pagmonet.pagmonet4j.ext

import io.github.pagmonet.pagmonet4j.*
import io.github.pagmonet.pagmonet4j.utils.MultiObjectiveUtils

// ── conversions ───────────────────────────────────────────────────────────────

/**
 * Converts a list of objective/fitness vectors (one [DoubleArray] per individual) into pagmo's
 * native [VectorOfVectorOfDoubles]. The caller owns the result and must [VectorOfVectorOfDoubles.delete]
 * it. The multi-objective and hypervolume helpers in this package use this internally and clean up
 * for you; it is exposed for callers reaching down to the raw API.
 */
fun List<DoubleArray>.toPointMatrix(): VectorOfVectorOfDoubles {
    val out = VectorOfVectorOfDoubles()
    for (row in this) {
        val dv = row.toDoubleVector()
        out.add(dv) // copies into the vector-of-vectors
        dv.delete()
    }
    return out
}

internal fun SizeTVector.toLongArrayAndDelete(): LongArray {
    val arr = LongArray(size)
    for (i in 0 until size) arr[i] = get(i)
    delete()
    return arr
}

// ── multi-objective utilities ─────────────────────────────────────────────────

/**
 * Returns `true` when [lhs] Pareto-dominates [rhs] — i.e. it is no worse in every objective and
 * strictly better in at least one (minimisation).
 */
fun paretoDominates(lhs: DoubleArray, rhs: DoubleArray): Boolean {
    val a = lhs.toDoubleVector()
    val b = rhs.toDoubleVector()
    try {
        return pagmonet4j.pareto_dominance(a, b)
    } finally {
        a.delete(); b.delete()
    }
}

/**
 * Crowding distance for each individual in a non-dominated [front] (higher = more isolated;
 * front endpoints are infinite). Order matches the input.
 */
fun crowdingDistance(front: List<DoubleArray>): DoubleArray {
    val m = front.toPointMatrix()
    try {
        val v = pagmonet4j.crowding_distance(m)
        return try { v.toDoubleArray() } finally { v.delete() }
    } finally {
        m.delete()
    }
}

/** Component-wise minimum (ideal point) across [fitness]. */
fun idealPoint(fitness: List<DoubleArray>): DoubleArray {
    val m = fitness.toPointMatrix()
    return try { MultiObjectiveUtils.idealValues(m) } finally { m.delete() }
}

/** Component-wise maximum of the non-dominated front (nadir point) across [fitness]. */
fun nadirPoint(fitness: List<DoubleArray>): DoubleArray {
    val m = fitness.toPointMatrix()
    return try { MultiObjectiveUtils.nadirValues(m) } finally { m.delete() }
}

/** Population indices sorted by non-dominated rank then crowding distance, best first. */
fun sortPopulationMo(fitness: List<DoubleArray>): LongArray {
    val m = fitness.toPointMatrix()
    return try { MultiObjectiveUtils.sortPopulationMoIndices(m) } finally { m.delete() }
}

/** Indices of the best [n] individuals under multi-objective (rank + crowding) sorting. */
fun selectBestNMo(fitness: List<DoubleArray>, n: Long): LongArray {
    val m = fitness.toPointMatrix()
    try {
        return pagmonet4j.select_best_N_mo(m, n).toLongArrayAndDelete()
    } finally {
        m.delete()
    }
}

/** Indices of the non-dominated (Pareto) front for a 2-objective [fitness] set. */
fun nonDominatedFront2D(fitness: List<DoubleArray>): LongArray {
    val m = fitness.toPointMatrix()
    return try { MultiObjectiveUtils.nonDominatedFront2DIndices(m) } finally { m.delete() }
}

/**
 * Scalarises [objectives] via [method] against [weights] and [referencePoint].
 * Supported methods: `"weighted"`, `"tchebycheff"`, `"bi"`.
 */
fun decomposeObjectives(
    objectives: DoubleArray,
    weights: DoubleArray,
    referencePoint: DoubleArray,
    method: String,
): DoubleArray {
    val o = objectives.toDoubleVector()
    val w = weights.toDoubleVector()
    val r = referencePoint.toDoubleVector()
    try {
        return MultiObjectiveUtils.decomposeObjectiveValues(o, w, r, method)
    } finally {
        o.delete(); w.delete(); r.delete()
    }
}
