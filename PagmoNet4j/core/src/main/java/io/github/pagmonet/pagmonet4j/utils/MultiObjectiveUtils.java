package io.github.pagmonet.pagmonet4j.utils;

import io.github.pagmonet.pagmonet4j.*;

/**
 * Java-friendly projection helpers for pagmo multi-objective utility functions.
 *
 * <p>Mirrors {@code multi_objective.projections.cs} from the Pagmo.NET binding.
 * All methods return plain Java arrays or throw {@link NullPointerException} on
 * null input.
 */
public final class MultiObjectiveUtils {

    private MultiObjectiveUtils() {}

    /**
     * Returns indices of the non-dominated (Pareto) front for a 2D set of fitness vectors.
     *
     * @param fitnessValues two-column fitness matrix (each row is one individual)
     * @return array of 0-based population indices in the non-dominated front
     * @throws NullPointerException if {@code fitnessValues} is null
     */
    public static long[] nonDominatedFront2DIndices(VectorOfVectorOfDoubles fitnessValues) {
        if (fitnessValues == null) throw new NullPointerException("fitnessValues");
        SizeTVector raw = pagmonet4j.non_dominated_front_2d(fitnessValues);
        return toSizeTArray(raw);
    }

    /**
     * Returns population indices sorted by multi-objective rank and crowding-distance criteria.
     *
     * @param fitnessValues fitness matrix; each row is one individual's objectives
     * @return sorted array of population indices, best first
     * @throws NullPointerException if {@code fitnessValues} is null
     */
    public static long[] sortPopulationMoIndices(VectorOfVectorOfDoubles fitnessValues) {
        if (fitnessValues == null) throw new NullPointerException("fitnessValues");
        SizeTVector raw = pagmonet4j.sort_population_mo(fitnessValues);
        return toSizeTArray(raw);
    }

    /**
     * Returns the ideal point (component-wise minimum) across the provided fitness vectors.
     *
     * @param fitnessValues fitness matrix; each row is one individual's objectives
     * @return ideal point as a {@code double[]} of length equal to the number of objectives
     * @throws NullPointerException if {@code fitnessValues} is null
     */
    public static double[] idealValues(VectorOfVectorOfDoubles fitnessValues) {
        if (fitnessValues == null) throw new NullPointerException("fitnessValues");
        DoubleVector v = pagmonet4j.ideal(fitnessValues);
        return toDoubleArray(v);
    }

    /**
     * Returns the nadir point (component-wise maximum of the non-dominated front) across
     * the provided fitness vectors.
     *
     * @param fitnessValues fitness matrix; each row is one individual's objectives
     * @return nadir point as a {@code double[]} of length equal to the number of objectives
     * @throws NullPointerException if {@code fitnessValues} is null
     */
    public static double[] nadirValues(VectorOfVectorOfDoubles fitnessValues) {
        if (fitnessValues == null) throw new NullPointerException("fitnessValues");
        DoubleVector v = pagmonet4j.nadir(fitnessValues);
        return toDoubleArray(v);
    }

    /**
     * Decomposes multi-objective values using the selected scalarization method.
     *
     * <p>Supported methods: {@code "weighted"}, {@code "tchebycheff"}, {@code "bi"}.
     *
     * @param objectiveValues  objective vector to decompose
     * @param weights          weight vector (same length as objectiveValues)
     * @param referencePoint   reference/utopian point (same length as objectiveValues)
     * @param method           scalarization method name
     * @return decomposed scalar value(s) as {@code double[]}
     * @throws NullPointerException if any argument is null
     */
    public static double[] decomposeObjectiveValues(
            DoubleVector objectiveValues,
            DoubleVector weights,
            DoubleVector referencePoint,
            String method) {
        if (objectiveValues == null) throw new NullPointerException("objectiveValues");
        if (weights         == null) throw new NullPointerException("weights");
        if (referencePoint  == null) throw new NullPointerException("referencePoint");
        if (method          == null) throw new NullPointerException("method");
        DoubleVector v = pagmonet4j.decompose_objectives(objectiveValues, weights, referencePoint, method);
        return toDoubleArray(v);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static double[] toDoubleArray(DoubleVector v) {
        double[] arr = new double[(int) v.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = v.get(i);
        v.delete();
        return arr;
    }

    private static long[] toSizeTArray(SizeTVector v) {
        return NativeInterop.consumeOwnedSizeTVector(v);
    }
}
