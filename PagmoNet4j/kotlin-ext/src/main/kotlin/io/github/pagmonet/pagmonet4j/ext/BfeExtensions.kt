package io.github.pagmonet.pagmonet4j.ext

import io.github.pagmonet.pagmonet4j.*
import io.github.pagmonet.pagmonet4j.problems.IProblem

// Idiomatic Kotlin for batch fitness evaluation (BFE). The factory functions build a type-erased
// [bfe] from each of pagmo's built-in evaluators (e.g. to pass to
// [pushBackIslandWithBfe]); [batchFitnessOf] evaluates a batch of points and reshapes the flat
// result into one fitness vector per point.

/** A type-erased [bfe] backed by pagmo's default batch fitness evaluator. */
fun defaultBfe(): bfe {
    val d = default_bfe()
    return try { d.to_bfe() } finally { d.delete() }
}

/** A type-erased [bfe] backed by pagmo's thread (one-per-thread) batch fitness evaluator. */
fun threadBfe(): bfe {
    val t = thread_bfe()
    return try { t.to_bfe() } finally { t.delete() }
}

/** A type-erased [bfe] backed by pagmo's member batch fitness evaluator (uses the problem's own `batch_fitness`). */
fun memberBfe(): bfe {
    val m = member_bfe()
    return try { m.to_bfe() } finally { m.delete() }
}

/**
 * Evaluates a batch of decision vectors and returns one fitness vector per input point.
 *
 * @param points   one decision vector per individual (all the same length)
 * @param parallel when true, requires a thread-safe or cloneable problem
 * @return one fitness vector (`nobj + nec + nic` values) per input point, in input order
 */
fun IProblem.batchFitnessOf(points: List<DoubleArray>, parallel: Boolean = true): List<DoubleArray> {
    if (points.isEmpty()) return emptyList()

    val flat = DoubleVector()
    for (p in points) for (d in p) flat.add(d)
    try {
        val result = batchFitness(flat, parallel)
        try {
            val nf = result.size / points.size
            return List(points.size) { i -> DoubleArray(nf) { j -> result.get(i * nf + j) } }
        } finally {
            result.delete()
        }
    } finally {
        flat.delete()
    }
}
