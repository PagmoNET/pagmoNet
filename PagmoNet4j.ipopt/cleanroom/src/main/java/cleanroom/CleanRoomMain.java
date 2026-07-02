package cleanroom;

import io.github.samthegliderpilot.pagmonet4j.*;
import io.github.samthegliderpilot.pagmonet4j.problems.*;

/**
 * Clean-room consumer of the published pagmonet4j-ipopt fat jar.
 *
 * <p>Runs on a machine with NO dev tools, NO conda, and NO {@code PAGMO4J_NATIVE_DIR} /
 * {@code java.library.path} hints — so a successful IPOPT solve proves the artifact
 * bundles its own IPOPT-enabled native plus the full dynamic dependency closure and
 * loads it unaided. Mirrors {@code IpoptSolverTest.mumpsLinearSolverConverges}.
 *
 * <p>Exit codes (consumed by the CI gate via JavaExec): 0 = solved; 1 = ran but did not
 * converge; 2 = IPOPT not present in the artifact.
 */
public final class CleanRoomMain {

    // Minimise x² + (y-3)² — optimum at (0,3), f*=0, analytic gradient supplied.
    private static final class QuadraticProblem extends ManagedProblemBase {
        @Override
        public DoubleVector fitness(DoubleVector x) {
            return vec(x.get(0) * x.get(0) + Math.pow(x.get(1) - 3.0, 2));
        }
        @Override
        public PairOfDoubleVectors get_bounds() {
            return bounds(new double[]{-10, -10}, new double[]{10, 10});
        }
        @Override public boolean has_gradient() { return true; }
        @Override
        public DoubleVector gradient(DoubleVector x) {
            return vec(2.0 * x.get(0), 2.0 * (x.get(1) - 3.0));
        }
        @Override public boolean has_gradient_sparsity() { return true; }
        @Override
        public SparsityPattern gradient_sparsity() {
            return sparsity(new long[]{0, 0}, new long[]{0, 1});
        }
    }

    public static void main(String[] args) {
        if (!OptionalSolverAvailability.isIpoptAvailable()) {
            System.err.println("CLEAN-ROOM FAIL: IPOPT is not available in the published artifact.");
            System.exit(2);
        }

        try (QuadraticProblem prob = new QuadraticProblem();
             ipopt algo = new ipopt()) {
            // MUMPS is the linear solver bundled in this build; it must be selectable
            // and must converge with only the published jar on the classpath.
            algo.set_string_option("linear_solver", "mumps");
            try (population pop = new population(prob, 1L, 42L);
                 population evolved = algo.evolve(pop)) {
                double fBest = evolved.champion_f().get(0);
                System.out.printf("clean-room IPOPT solve: f=%.9f (algorithm=%s)%n",
                    fBest, algo.get_name());
                if (fBest < 1e-6) {
                    System.out.println("CLEAN-ROOM PASS");
                    System.exit(0);
                }
                System.err.printf("CLEAN-ROOM FAIL: did not converge near f*=0; got f=%.6f%n", fBest);
                System.exit(1);
            }
        }
    }

    private CleanRoomMain() {}
}
