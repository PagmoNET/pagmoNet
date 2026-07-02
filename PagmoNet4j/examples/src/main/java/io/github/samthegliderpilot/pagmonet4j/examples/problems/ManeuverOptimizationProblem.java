package io.github.samthegliderpilot.pagmonet4j.examples.problems;

import io.github.samthegliderpilot.pagmonet4j.*;
import io.github.samthegliderpilot.pagmonet4j.problems.ManagedProblemBase;

import java.util.Arrays;
import java.util.List;

/**
 * A pagmonet4j UDP that minimises total delta-v for a 2-burn coast-and-burn sequence
 * subject to equality constraints on the desired final orbital state.
 *
 * <p>Decision vector layout (4 variables per burn):
 * {@code [coastDuration_0, dvR_0, dvI_0, dvC_0, coastDuration_1, dvR_1, dvI_1, dvC_1]}
 *
 * <p>Fitness vector layout:
 * {@code [totalDeltaV, (sma_residual/1000), eccentricity_residual]}
 * where residuals are zero at the optimum. SMA residual is divided by 1000 to
 * make it dimensionless and comparable in scale to the eccentricity residual.
 */
public final class ManeuverOptimizationProblem extends ManagedProblemBase {

    private static final int VARIABLES_PER_BURN = 4;

    private final KeplerianElements initial;
    private final double t0;
    private final int numBurns;
    private final Double targetSma;
    private final Double targetEcc;
    private final double mu;
    private final double[] lower;
    private final double[] upper;

    public ManeuverOptimizationProblem(KeplerianElements initial, double t0, int numBurns,
                                       Double targetSma, Double targetEcc,
                                       double maxCoastDuration, double maxDeltaV,
                                       double mu) {
        this.initial    = initial;
        this.t0         = t0;
        this.numBurns   = numBurns;
        this.targetSma  = targetSma;
        this.targetEcc  = targetEcc;
        this.mu         = mu;

        int n = numBurns * VARIABLES_PER_BURN;
        this.lower = new double[n];
        this.upper = new double[n];
        for (int b = 0; b < numBurns; b++) {
            lower[b * VARIABLES_PER_BURN]     = 0.0;          upper[b * VARIABLES_PER_BURN]     = maxCoastDuration;
            lower[b * VARIABLES_PER_BURN + 1] = -maxDeltaV;   upper[b * VARIABLES_PER_BURN + 1] = maxDeltaV;
            lower[b * VARIABLES_PER_BURN + 2] = -maxDeltaV;   upper[b * VARIABLES_PER_BURN + 2] = maxDeltaV;
            lower[b * VARIABLES_PER_BURN + 3] = -maxDeltaV;   upper[b * VARIABLES_PER_BURN + 3] = maxDeltaV;
        }
    }

    @Override
    public DoubleVector fitness(DoubleVector x) {
        List<CoastAndBurn> burns = decodeBurns(x);
        List<OrbitalMechanics.PropagationResult> history =
            OrbitalMechanics.propagate(initial, t0, burns, mu);
        KeplerianElements finalEl = history.get(history.size() - 1).elements;

        double totalDv = 0.0;
        for (CoastAndBurn b : burns) totalDv += b.totalDeltaV();

        int nConstraints = (targetSma != null ? 1 : 0) + (targetEcc != null ? 1 : 0);
        double[] f = new double[1 + nConstraints];
        f[0] = totalDv;
        int fi = 1;
        if (targetSma != null) f[fi++] = (finalEl.semiMajorAxis - targetSma) / 1000.0;
        if (targetEcc != null) f[fi]   = finalEl.eccentricity - targetEcc;

        return vec(f);
    }

    @Override
    public PairOfDoubleVectors get_bounds() { return bounds(lower, upper); }

    @Override public String get_name() { return "ManeuverOptimizationProblem"; }
    @Override public ThreadSafety get_thread_safety() { return ThreadSafety.Basic; }

    @Override
    public long get_nec() {
        return (targetSma != null ? 1L : 0L) + (targetEcc != null ? 1L : 0L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<CoastAndBurn> decodeBurns(DoubleVector x) {
        CoastAndBurn[] burns = new CoastAndBurn[numBurns];
        for (int b = 0; b < numBurns; b++) {
            burns[b] = new CoastAndBurn(
                x.get(b * VARIABLES_PER_BURN),
                x.get(b * VARIABLES_PER_BURN + 1),
                x.get(b * VARIABLES_PER_BURN + 2),
                x.get(b * VARIABLES_PER_BURN + 3));
        }
        return Arrays.asList(burns);
    }
}
