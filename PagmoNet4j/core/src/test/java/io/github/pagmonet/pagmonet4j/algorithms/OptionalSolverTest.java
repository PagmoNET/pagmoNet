package io.github.pagmonet.pagmonet4j.algorithms;

import io.github.pagmonet.pagmonet4j.*;
import io.github.pagmonet.pagmonet4j.problems.*;
import io.github.pagmonet.pagmonet4j.testproblems.*;
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

    // NOTE: the base library links no IPOPT, but the `ipopt` algorithm IS present (it loads
    // libipopt at runtime via dlopen). The actual solve is exercised by
    // IpoptSolveWhenAvailableTest, which runs only when a libipopt is loadable, and on a clean
    // machine via the cleanroom gate.

    @Test
    void optionalSolverAvailabilityDoesNotThrow() {
        assertDoesNotThrow(() -> {
            boolean nl = OptionalSolverAvailability.isNloptAvailable();
            boolean ip = OptionalSolverAvailability.isIpoptAvailable();
        });
    }
}
