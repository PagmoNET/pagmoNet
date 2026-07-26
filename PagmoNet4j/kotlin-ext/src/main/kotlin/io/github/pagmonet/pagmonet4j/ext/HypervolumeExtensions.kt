package io.github.pagmonet.pagmonet4j.ext

import io.github.pagmonet.pagmonet4j.*

// Idiomatic Kotlin over pagmo's [hypervolume]: build from Kotlin points and query with DoubleArray
// reference points. The extensions overload the native members (which take DoubleVector), so both
// forms are available. A [hypervolume] is AutoCloseable — wrap it in `use { }` or close it.

/** Builds a [hypervolume] from a list of objective-space [points]. Close it when done. */
fun hypervolumeOf(points: List<DoubleArray>): hypervolume {
    val m = points.toPointMatrix()
    return try { hypervolume(m) } finally { m.delete() }
}

/** Builds a [hypervolume] from this population's fitness vectors. Close it when done. */
fun population.hypervolume(): hypervolume = hypervolume(this)

/** Total hypervolume dominated by the point set, relative to [referencePoint]. */
fun hypervolume.compute(referencePoint: DoubleArray): Double {
    val r = referencePoint.toDoubleVector()
    try { return compute(r) } finally { r.delete() }
}

/** Exclusive hypervolume contributed by the point at [index], relative to [referencePoint]. */
fun hypervolume.exclusive(index: Long, referencePoint: DoubleArray): Double {
    val r = referencePoint.toDoubleVector()
    try { return exclusive(index, r) } finally { r.delete() }
}

/** Per-point exclusive contributions (one value per stored point), relative to [referencePoint]. */
fun hypervolume.contributions(referencePoint: DoubleArray): DoubleArray {
    val r = referencePoint.toDoubleVector()
    try {
        val v = contributions(r)
        return try { v.toDoubleArray() } finally { v.delete() }
    } finally {
        r.delete()
    }
}

/** Index of the point contributing the least hypervolume, relative to [referencePoint]. */
fun hypervolume.leastContributor(referencePoint: DoubleArray): Long {
    val r = referencePoint.toDoubleVector()
    try { return least_contributor(r).toLong() } finally { r.delete() }
}

/** Index of the point contributing the most hypervolume, relative to [referencePoint]. */
fun hypervolume.greatestContributor(referencePoint: DoubleArray): Long {
    val r = referencePoint.toDoubleVector()
    try { return greatest_contributor(r).toLong() } finally { r.delete() }
}

/** A reference point that is worse than every stored point by [offset] in each objective. */
fun hypervolume.referencePoint(offset: Double = 0.0): DoubleArray {
    val v = refpoint(offset)
    return try { v.toDoubleArray() } finally { v.delete() }
}
