package io.github.samthegliderpilot.pagmonet4j.algorithms;

import io.github.samthegliderpilot.pagmonet4j.*;
import io.github.samthegliderpilot.pagmonet4j.problems.*;
import io.github.samthegliderpilot.pagmonet4j.testproblems.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Guards optional-solver tests behind availability checks. */
class OptionalSolverTest {

    @Test
    void nloptCanEvolveIfAvailable() {
        assumeTrue(OptionalSolverAvailability.isNloptAvailable(), "NLopt not available in this build.");
        try (TwoDimensionalSingleObjectiveProblem prob = new TwoDimensionalSingleObjectiveProblem();
             nlopt algo = new nlopt("cobyla");
             population pop = new population(prob, 1L, 2L)) {
            try (population evolved = algo.evolve(pop)) {
                assertNotNull(evolved);
            }
        }
    }

    // NOTE: no ipopt evolve test here — the base library is IPOPT-free by design, so the
    // ipopt class is not generated. The IPOPT solve is exercised in the PagmoNet4j.ipopt
    // superset (and on a clean machine via the cleanroom gate).

    @Test
    void optionalSolverAvailabilityDoesNotThrow() {
        assertDoesNotThrow(() -> {
            boolean nl = OptionalSolverAvailability.isNloptAvailable();
            boolean ip = OptionalSolverAvailability.isIpoptAvailable();
        });
    }
}
