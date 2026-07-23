package io.github.pagmonet.pagmonet4j.algorithms;

import io.github.pagmonet.pagmonet4j.*;
import io.github.pagmonet.pagmonet4j.problems.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises a real IPOPT solve through the base pagmonet4j {@code ipopt} algorithm, which
 * loads libipopt at runtime via dlopen. Runs only when a libipopt is loadable (the
 * pagmonet4j-ipopt companion, a system install, or the {@code PAGMONET_IPOPT_LIBRARY}
 * override); otherwise it is assumed out, so the libipopt-free base build stays green.
 *
 * <p>Java twin of the C# test
 * {@code Test_optional_solver_availability.IpoptWhenPresentSupportsConstructAndEvolve}.
 */
class IpoptSolveWhenAvailableTest {

    // Minimise x^2 + (y-3)^2 -- optimum (0,3), f*=0, with an analytic gradient. Same problem
    // as the C# QuadraticProblem and the clean-room consumer.
    private static final class QuadraticProblem extends ManagedProblemBase {
        @Override public DoubleVector fitness(DoubleVector x) {
            return vec(x.get(0) * x.get(0) + Math.pow(x.get(1) - 3.0, 2));
        }
        @Override public PairOfDoubleVectors get_bounds() {
            return bounds(new double[]{-10, -10}, new double[]{10, 10});
        }
        @Override public boolean has_gradient() { return true; }
        @Override public DoubleVector gradient(DoubleVector x) {
            return vec(2.0 * x.get(0), 2.0 * (x.get(1) - 3.0));
        }
        @Override public boolean has_gradient_sparsity() { return true; }
        @Override public SparsityPattern gradient_sparsity() {
            return sparsity(new long[]{0, 0}, new long[]{0, 1});
        }
    }

    @Test
    void ipoptSolvesWhenLibipoptIsAvailable() {
        assumeTrue(OptionalSolverAvailability.isIpoptAvailable(),
            "libipopt is not loadable here; add the pagmonet4j-ipopt companion, install IPOPT, "
                + "or set PAGMONET_IPOPT_LIBRARY to exercise a real ipopt solve.");

        try (QuadraticProblem prob = new QuadraticProblem();
             ipopt algo = new ipopt()) {
            assertNotNull(algo.get_name(), "ipopt must report a name");
            algo.set_integer_option("print_level", 0);

            try (population pop = new population(prob, 1L, 42L);
                 population evolved = algo.evolve(pop)) {
                int rc = algo.get_last_opt_result_code();
                double fBest = evolved.champion_f().get(0);
                assertTrue(rc == 0 || rc == 1,
                    "ipopt must report Solve_Succeeded (0) or Solved_To_Acceptable_Level (1); got " + rc);
                assertTrue(fBest < 1e-6,
                    "ipopt must converge near f*=0; got f=" + fBest);
            }
        }
    }

    // The PascalCase alias (C# parity) must forward to the SWIG get_last_opt_result_code(), and the
    // typed log projection (was emptyList() in Java before the fix) must match the C# IpoptLogLine shape.
    @Test
    void ipoptTypedLogAndResultCodeAliasWhenAvailable() {
        assumeTrue(OptionalSolverAvailability.isIpoptAvailable(),
            "libipopt is not loadable here; add the pagmonet4j-ipopt companion to exercise this.");

        try (QuadraticProblem prob = new QuadraticProblem();
             ipopt algo = new ipopt()) {
            algo.set_integer_option("print_level", 0);
            algo.set_verbosity(1L);

            try (population pop = new population(prob, 1L, 42L);
                 population evolved = algo.evolve(pop)) {

                assertEquals(algo.get_last_opt_result_code(), algo.getLastOptimizationResultCode(),
                    "getLastOptimizationResultCode() must forward to get_last_opt_result_code()");

                var typed = algo.getTypedLogLines();
                assertEquals(typed.size(), algo.getLogLines().size(),
                    "generic getLogLines() must project every typed line (was emptyList() before the fix)");
                if (!typed.isEmpty()) {
                    var line = typed.get(0);
                    assertEquals("ipopt", line.getAlgorithmName());
                    assertTrue(line.getRawFields().containsKey("objective_evaluations"));
                    assertTrue(line.toDisplayString().contains("obj_eval="));
                }
            }
        }
    }
}
