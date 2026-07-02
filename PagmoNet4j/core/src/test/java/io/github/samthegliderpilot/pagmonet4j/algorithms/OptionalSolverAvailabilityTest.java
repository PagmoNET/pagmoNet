package io.github.samthegliderpilot.pagmonet4j.algorithms;

import io.github.samthegliderpilot.pagmonet4j.OptionalSolverAvailability;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Smoke tests: optional solver detection never throws, always returns a definite boolean. */
class OptionalSolverAvailabilityTest {

    @Test
    void nloptAvailabilityIsDetectable() {
        assertDoesNotThrow(OptionalSolverAvailability::isNloptAvailable,
            "isNloptAvailable() must not throw");
    }

    @Test
    void ipoptAvailabilityIsDetectable() {
        assertDoesNotThrow(OptionalSolverAvailability::isIpoptAvailable,
            "isIpoptAvailable() must not throw");
    }

    @Test
    void multipleCallsReturnConsistentResult() {
        boolean first  = OptionalSolverAvailability.isNloptAvailable();
        boolean second = OptionalSolverAvailability.isNloptAvailable();
        assertEquals(first, second, "repeated calls must return the same value");
    }

    @Test
    void nloptIsAvailableInThisBuild() {
        // The base pagmonet4j is always built with NLopt — this verifies the JNI
        // probe correctly detects a solver that IS present.
        assertTrue(OptionalSolverAvailability.isNloptAvailable(),
            "NLopt must be available in every standard pagmonet4j build");
    }

    @Test
    void availabilityMatchesSolverBehavior() {
        // If the probe says NLopt is available, we must be able to construct one.
        // This is a red/green cross-check: probe result and actual construction agree.
        if (OptionalSolverAvailability.isNloptAvailable()) {
            assertDoesNotThrow(() -> {
                try (var algo = new io.github.samthegliderpilot.pagmonet4j.nlopt("cobyla")) {
                    assertNotNull(algo);
                }
            }, "isNloptAvailable()=true but nlopt construction threw");
        }
    }

    @Test
    void ipoptIsAbsentInBaseLibrary() {
        // This assertion only fires in CI base builds (without IPOPT).
        // Set PAGMONET4J_BASE_VERIFY=1 to activate it.
        assumeTrue("1".equals(System.getenv("PAGMONET4J_BASE_VERIFY")),
            "Set PAGMONET4J_BASE_VERIFY=1 to run base build verification");
        assertFalse(OptionalSolverAvailability.isIpoptAvailable(),
            "Base pagmonet4j must be built without IPOPT — use PagmoNet4j.ipopt for IPOPT support");
    }
}
