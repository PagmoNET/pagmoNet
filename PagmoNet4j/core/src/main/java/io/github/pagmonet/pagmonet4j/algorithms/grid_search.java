package io.github.pagmonet.pagmonet4j.algorithms;

import io.github.pagmonet.pagmonet4j.DoubleVector;
import io.github.pagmonet.pagmonet4j.population;
import io.github.pagmonet.pagmonet4j.problem;

/**
 * Simple exhaustive grid-search optimizer, and the reference example of a <b>user-defined
 * algorithm (UDA) written in pure Java</b>: it implements {@link IAlgorithm} directly rather
 * than wrapping a native pagmo algorithm. Samples each decision-variable range on a uniform
 * grid and selects the best feasible point.
 *
 * <p>Java twin of the C# {@code pagmo.grid_search} demonstration algorithm. Like the C# one it is
 * primarily a teaching example for building your own UDA; it also serves coarse-initialization and
 * educational scenarios. Single-objective problems only.
 *
 * <p>Use it directly ({@code new grid_search(20).evolve(pop)}) or through the type-erased island
 * path ({@code island.create(new grid_search(4), problem, popSize)}), exactly like any native UDA.
 */
public final class grid_search implements IAlgorithm {

    private final long[] stepsPerDimension;
    private long seed;
    private long verbosity;

    /** Creates a grid search with one uniform step count applied to every dimension. */
    public grid_search(long uniformStepsPerDimension) {
        this(new long[] { uniformStepsPerDimension });
    }

    /** Creates a grid search with per-dimension step counts. */
    public grid_search(long[] stepsPerDimension) {
        if (stepsPerDimension == null || stepsPerDimension.length == 0) {
            throw new IllegalArgumentException("At least one step value must be provided.");
        }
        for (long step : stepsPerDimension) {
            if (step < 1) {
                throw new IllegalArgumentException("All step values must be >= 1.");
            }
        }
        this.stepsPerDimension = stepsPerDimension.clone();
    }

    /** Evaluates a uniform grid and updates the population with the best feasible sampled point. */
    @Override
    public population evolve(population pop) {
        if (pop == null) {
            throw new NullPointerException("pop");
        }
        if (pop.size() == 0) {
            throw new IllegalStateException("grid_search requires a non-empty population.");
        }

        problem prob = pop.get_problem();
        try {
            if (prob.get_nobj() != 1) {
                throw new UnsupportedOperationException(
                    "grid_search currently supports only single-objective problems.");
            }

            int nx = (int) prob.get_nx();
            if (nx <= 0) {
                throw new IllegalStateException("Problem must have at least one decision variable.");
            }

            long[] steps = resolveStepsPerDimension(nx);
            DoubleVector lb = prob.get_lb();
            DoubleVector ub = prob.get_ub();
            try {
                long[] gridIndex = new long[nx];
                double[] candidateX = new double[nx];
                double[] bestX = null;
                double[] bestF = null;
                double bestObjective = Double.POSITIVE_INFINITY;

                while (true) {
                    for (int dim = 0; dim < nx; dim++) {
                        double lower = lb.get(dim);
                        double upper = ub.get(dim);
                        candidateX[dim] = lower + (upper - lower) * (gridIndex[dim] / (double) steps[dim]);
                    }

                    DoubleVector x = new DoubleVector(candidateX);
                    DoubleVector f = prob.fitness(x);
                    try {
                        if (prob.feasibility_f(f)) {
                            double objective = f.get(0);
                            if (objective < bestObjective) {
                                bestObjective = objective;
                                bestX = candidateX.clone();
                                bestF = new double[f.size()];
                                for (int i = 0; i < f.size(); i++) {
                                    bestF[i] = f.get(i);
                                }
                            }
                        }
                    } finally {
                        f.delete();
                        x.delete();
                    }

                    // Odometer over grid indices: each dimension takes steps[dim] + 1 samples
                    // (inclusive of both bounds).
                    boolean advanced = false;
                    for (int dim = nx - 1; dim >= 0; dim--) {
                        if (gridIndex[dim] < steps[dim]) {
                            gridIndex[dim]++;
                            for (int reset = dim + 1; reset < nx; reset++) {
                                gridIndex[reset] = 0;
                            }
                            advanced = true;
                            break;
                        }
                    }
                    if (!advanced) {
                        break;
                    }
                }

                if (bestX == null || bestF == null) {
                    throw new IllegalStateException(
                        "grid_search did not find any feasible point on the sampled grid.");
                }

                DoubleVector bestXVector = new DoubleVector(bestX);
                DoubleVector bestFVector = new DoubleVector(bestF);
                try {
                    for (long idx = 0; idx < pop.size(); idx++) {
                        pop.set_xf(idx, bestXVector, bestFVector);
                    }
                } finally {
                    bestFVector.delete();
                    bestXVector.delete();
                }

                return pop;
            } finally {
                ub.delete();
                lb.delete();
            }
        } finally {
            prob.delete();
        }
    }

    @Override public void set_seed(long seed) { this.seed = seed; }
    @Override public long get_seed() { return seed; }
    @Override public long get_verbosity() { return verbosity; }
    @Override public void set_verbosity(long level) { this.verbosity = level; }

    @Override public String get_name() { return "Java Grid Search"; }

    @Override public String get_extra_info() {
        StringBuilder sb = new StringBuilder("Grid steps per dimension: [");
        for (int i = 0; i < stepsPerDimension.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(stepsPerDimension[i]);
        }
        return sb.append(']').toString();
    }

    private long[] resolveStepsPerDimension(int nx) {
        if (stepsPerDimension.length == 1) {
            long[] uniform = new long[nx];
            java.util.Arrays.fill(uniform, stepsPerDimension[0]);
            return uniform;
        }
        if (stepsPerDimension.length != nx) {
            throw new IllegalStateException(
                "grid_search step configuration length (" + stepsPerDimension.length
                    + ") does not match problem dimension (" + nx + ").");
        }
        return stepsPerDimension.clone();
    }
}
