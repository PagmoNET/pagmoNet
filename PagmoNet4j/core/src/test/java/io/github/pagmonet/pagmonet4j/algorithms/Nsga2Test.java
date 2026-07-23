package io.github.pagmonet.pagmonet4j.algorithms;
import io.github.pagmonet.pagmonet4j.*;
import io.github.pagmonet.pagmonet4j.testproblems.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Nsga2Test extends AlgorithmTestBase {
    @Override public IAlgorithm createAlgorithm()      { return new nsga2(8L); }
    @Override public boolean supportsUnconstrained()   { return true; }
    @Override public boolean supportsConstrained()     { return false; }
    @Override public boolean supportsSingleObjective() { return false; }
    @Override public boolean supportsMultiObjective()  { return true; }
    @Test void nameStartsWithNSGA() { try (nsga2 a = new nsga2(4L)) { assertTrue(a.get_name().contains("NSGA")); } }

    // Multi-objective typed-log projection: the log entry carries a fitness vector (not a
    // scalar best), matching the C# nsga2.Nsga2LogLine shape. Was emptyList() before the fix.
    @Test
    void typedMultiObjectiveLogsExposeFitnessVector() {
        try (TwoDimensionalMultiObjectiveProblem prob = new TwoDimensionalMultiObjectiveProblem();
             problem wrapped = new problem(prob);
             nsga2 algo = new nsga2(20L);
             population pop = new population(wrapped, 128L, 2L)) {

            algo.set_seed(2L);
            algo.set_verbosity(1L);
            try (population ignored = algo.evolve(pop)) {}

            var typed = algo.getTypedLogLines();
            assertFalse(typed.isEmpty(), "verbosity should produce at least one log line");

            var line = typed.get(0);
            assertEquals("nsga2", line.getAlgorithmName());
            assertFalse(line.fitnessVector().isEmpty(), "MO log line must carry the fitness vector");
            assertTrue(line.getRawFields().containsKey("fitness_vector"));
            assertTrue(line.toDisplayString().contains("objectives="));

            IAlgorithm iface = algo;
            assertEquals(typed.size(), iface.getLogLines().size(),
                "generic getLogLines() must project every typed line (was emptyList() before the fix)");
        }
    }
}
