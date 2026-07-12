package io.github.samthegliderpilot.pagmonet4j.examples;

import io.github.samthegliderpilot.pagmonet4j.*;
import io.github.samthegliderpilot.pagmonet4j.algorithms.*;
import io.github.samthegliderpilot.pagmonet4j.migration.*;
import io.github.samthegliderpilot.pagmonet4j.examples.problems.*;
import java.util.Arrays;
import java.util.List;

/** Java-idiomatic scenario implementations. */
final class Scenarios {

    private Scenarios() {}

    // Scenario 1 ── single island baseline ───────────────────────────────────

    static void runSingleIslandBaseline(boolean verbose) {
        System.out.println("Scenario: single island baseline");
        double best = runSingleIsland(/*seed=*/42L, /*evolveCalls=*/20L, verbose);
        System.out.printf("  Best fitness after evolve: %.6f%n", best);
        System.out.println("  Why it matters: this is the simplest production path and a useful baseline.");
    }

    static double runSingleIsland(long seed, long evolveCalls, boolean verbose) {
        try (RastriginProblem prob = new RastriginProblem();
             de algo = new de(80L, 0.8, 0.9, 2L, 1e-6, 1e-6, seed)) {
            if (verbose) algo.set_verbosity(1L);
            try (island isl = island.create(algo, prob, Main.DEFAULT_POP_SIZE, seed)) {
                isl.evolve(evolveCalls);
                isl.waitCheck();
                // Log from the concrete algo object; the type-erased island.get_algorithm()
                // wrapper does not expose typed log lines.
                if (verbose) Main.printAlgorithmLog("de", algo.getLogLines());
                return Main.getIslandChampionFitness(isl);
            }
        }
    }

    // Scenario 2 ── archipelago topology ────────────────────────────────────

    static void runArchipelagoScenario(boolean verbose) {
        System.out.println("Scenario: archipelago and topology intuition");

        describeTopologyConnectivity();

        double singleBest = runSingleIsland(77L, (long) 15 * Main.DEFAULT_ISLAND_COUNT, verbose);
        double archiBest  = runArchipelago(false, verbose);

        System.out.printf("  Single island (same total evolve rounds) vs archipelago:%n");
        System.out.printf("    single-island best: %.6f%n", singleBest);
        System.out.printf("    archipelago best:   %.6f%n", archiBest);
        System.out.println("  Why it matters: parallel search trajectories help avoid local minima.");
    }

    private static void describeTopologyConnectivity() {
        // ring(n) connects each island to its two neighbours; default archipelago has no topology.
        try (ring ringTopo = new ring()) {
            // Get connections for vertex 0 once two islands are registered
            ringTopo.push_back(); ringTopo.push_back(); ringTopo.push_back();
            TopologyConnections conn = ringTopo.get_connections(0L);
            SizeTVector neighbours = conn.getFirst();
            System.out.println("  Topology intuition (ring, vertex 0):");
            System.out.println("    ring neighbours:      " + neighbours.size());
            System.out.println("    default (unconnected): 0  — islands evolve independently");
            neighbours.delete();
            conn.delete();
        }
    }

    // Scenario 3 ── policy comparison ────────────────────────────────────────

    static void runPolicyComparison(boolean verbose) {
        System.out.println("Scenario: migration policy impact");
        System.out.println("  Default: no custom policies (pagmo uses built-in fair_replace / select_best).");
        System.out.println("  Managed: IRPolicy / ISPolicy callbacks let you observe and customise migration.");

        double defaultBest = runArchipelago(false, verbose);
        double managedBest = runArchipelagoWithManagedPolicies(verbose);

        System.out.printf("  Default policies:   best=%.6f%n", defaultBest);
        System.out.printf("  Managed pass-through: best=%.6f%n", managedBest);
        System.out.println("  Why it matters: IRPolicy/ISPolicy let you log, filter, or customise migration.");
    }

    /** Shows managed policy wiring — a pass-through that counts calls. */
    static double runArchipelagoWithManagedPolicies(boolean verbose) {
        int[] replaceCalls = {0};
        int[] selectCalls  = {0};

        IRPolicy rPolicy = new RPolicyCallbackAdapter() {
            @Override
            public IndividualsGroup replaceManaged(IndividualsGroup incoming, long nF, long nEc,
                    long nIc, long nObj, long popSize, DoubleVector tol,
                    IndividualsGroup current) {
                replaceCalls[0]++;
                return incoming;  // accept all incoming migrants
            }
            @Override public String get_name() { return "CountingRPolicy"; }
        };

        ISPolicy sPolicy = new SPolicyCallbackAdapter() {
            @Override
            public IndividualsGroup selectManaged(IndividualsGroup population, long nF, long nEc,
                    long nIc, long nObj, long popSize, DoubleVector tol) {
                selectCalls[0]++;
                return population;  // send full population as emigrants
            }
            @Override public String get_name() { return "CountingSPolicy"; }
        };

        try (RastriginProblem prob = new RastriginProblem();
             archipelago archi = new archipelago()) {
            try (ring topo = new ring()) { archi.set_topology_ring(topo); }

            for (int i = 0; i < Main.DEFAULT_ISLAND_COUNT; i++) {
                try (de algo = new de(60L, 0.8, 0.9, 2L, 1e-6, 1e-6, 101L + i)) {
                    archi.pushBackIsland(algo, prob, rPolicy, sPolicy,
                        Main.DEFAULT_POP_SIZE, 201L + i);
                }
            }

            archi.evolve(5L);
            archi.waitCheck();

            System.out.printf("    replace() called: %d  select() called: %d%n",
                replaceCalls[0], selectCalls[0]);

            return Main.getArchipelagoBestFitness(archi);
        }
    }

    static double runArchipelago(boolean usePolicies, boolean verbose) {
        try (RastriginProblem prob = new RastriginProblem();
             archipelago archi = new archipelago()) {

            for (int i = 0; i < Main.DEFAULT_ISLAND_COUNT; i++) {
                try (de algo = new de(60L, 0.8, 0.9, 2L, 1e-6, 1e-6, 101L + i)) {
                    if (verbose) algo.set_verbosity(1L);
                    archi.pushBackIsland(algo, prob, Main.DEFAULT_POP_SIZE, 201L + i);
                }
            }

            archi.evolve(15L);
            archi.waitCheck();

            return Main.getArchipelagoBestFitness(archi);
        }
    }

    // Scenario 4 ── orbital maneuver optimisation ────────────────────────────

    static void runOrbitalManeuverOptimization(boolean verbose) {
        System.out.println("Scenario: orbital maneuver optimisation (2-burn Hohmann-like transfer)");

        final double earthRadiusKm = 6371.0;
        KeplerianElements initial = new KeplerianElements(
            earthRadiusKm + 400.0,  // 6771 km SMA
            0.001,                  // near-circular
            0.5,                    // ~28.6 deg inclination
            0.0, 0.0, 0.0);

        double targetSma = earthRadiusKm + 1000.0;
        double targetEcc = 0.001;

        double period = 2.0 * Math.PI * Math.sqrt(
            Math.pow(initial.semiMajorAxis, 3) / OrbitalMechanics.EARTH_GM);

        System.out.printf("  Initial SMA: %.1f km  Target SMA: %.1f km%n",
            initial.semiMajorAxis, targetSma);
        System.out.println("  Constraints: 2 equality (Δa, Δe)");

        try (ManeuverOptimizationProblem problem = new ManeuverOptimizationProblem(
                initial, 0.0, 2, targetSma, targetEcc, period, 3.0,
                OrbitalMechanics.EARTH_GM)) {

            double bestDv = Double.POSITIVE_INFINITY;
            double[] xBest = null;

            for (int attempt = 0; attempt < 3 && xBest == null; attempt++) {
                long algSeed = 42L + attempt * 17L;
                long popSeed = 1L + attempt;

                try (de innerAlgo = new de(20L);
                     algorithm erasedInner = innerAlgo.to_algorithm();
                     cstrs_self_adaptive algo = new cstrs_self_adaptive(500L, erasedInner, algSeed)) {

                    if (verbose) algo.set_verbosity(1L);

                    try (population pop = new population(problem, 64L, popSeed);
                         population evolved = algo.evolve(pop)) {

                        if (verbose)
                            Main.printAlgorithmLog("cstrs_self_adaptive (attempt " + (attempt + 1) + ")",
                                algo.getLogLines());

                        VectorOfVectorOfDoubles allF = evolved.get_f();
                        VectorOfVectorOfDoubles allX = evolved.get_x();
                        for (int idx = 0; idx < (int) evolved.size(); idx++) {
                            DoubleVector fVec = allF.get(idx);
                            boolean feasible = true;
                            for (int c = 1; c < fVec.size(); c++) {
                                if (Math.abs(fVec.get(c)) > 1e-2) { feasible = false; break; }
                            }
                            if (feasible && fVec.get(0) < bestDv) {
                                bestDv = fVec.get(0);
                                DoubleVector xVec = allX.get(idx);
                                xBest = new double[(int) xVec.size()];
                                for (int k = 0; k < xBest.length; k++) xBest[k] = xVec.get(k);
                            }
                        }
                    }
                }
            }

            if (xBest == null) {
                System.out.println("  No feasible solution found after 3 attempts.");
                return;
            }

            System.out.printf("  Best feasible total Δv: %.1f m/s%n", bestDv * 1000.0);
            for (int b = 0; b < 2; b++) {
                double coast = xBest[b * 4];
                double dv = Math.sqrt(xBest[b*4+1]*xBest[b*4+1]
                                    + xBest[b*4+2]*xBest[b*4+2]
                                    + xBest[b*4+3]*xBest[b*4+3]);
                System.out.printf("  Burn %d: coast %.0f s, |Δv| %.1f m/s%n",
                    b + 1, coast, dv * 1000.0);
            }

            List<CoastAndBurn> burns = Arrays.asList(
                new CoastAndBurn(xBest[0], xBest[1], xBest[2], xBest[3]),
                new CoastAndBurn(xBest[4], xBest[5], xBest[6], xBest[7]));
            List<OrbitalMechanics.PropagationResult> history =
                OrbitalMechanics.propagate(initial, 0.0, burns, OrbitalMechanics.EARTH_GM);
            KeplerianElements finalEl = history.get(history.size() - 1).elements;
            System.out.printf("  Final SMA:  %.1f km  (target %.1f km, Δ %+.1f km)%n",
                finalEl.semiMajorAxis, targetSma, finalEl.semiMajorAxis - targetSma);
            System.out.printf("  Final ecc:  %.4f  (target %.4f)%n",
                finalEl.eccentricity, targetEcc);
        }
    }

    // Scenario 5 ── cloneable non-thread-safe problem ────────────────────────

    static void runCloneableProblemsScenario(boolean verbose) {
        System.out.println("Scenario: cloneable non-thread-safe problem in archipelago");
        System.out.println("  CloneableRastriginProblem declares ThreadSafety.None but implements clone().");
        System.out.println("  Each island receives its own exclusive copy — no concurrency required.");

        CloneableRastriginProblem.cloneCount.set(0);
        CloneableRastriginProblem.totalEvaluations.set(0);

        try (CloneableRastriginProblem prob = new CloneableRastriginProblem();
             archipelago archi = new archipelago()) {

            for (int i = 0; i < Main.DEFAULT_ISLAND_COUNT; i++) {
                try (de algo = new de(60L, 0.8, 0.9, 2L, 1e-6, 1e-6, 301L + i)) {
                    archi.pushBackIsland(algo, prob, Main.DEFAULT_POP_SIZE, 401L + i);
                }
            }

            System.out.println("  Clones created for " + Main.DEFAULT_ISLAND_COUNT +
                " islands: " + CloneableRastriginProblem.cloneCount.get());

            archi.evolve(15L);
            archi.waitCheck();

            System.out.printf("  Best fitness: %.6f%n", Main.getArchipelagoBestFitness(archi));
            System.out.println("  Total fitness evaluations across all clones: " +
                CloneableRastriginProblem.totalEvaluations.get());
        }
    }

    // Scenario 6 ── IPOPT optional solver (graceful when absent) ──────────────

    // Optional-solver pattern: use IPOPT if its native runtime is present, else skip gracefully.
    // IPOPT (a gradient-based interior-point solver) is the `ipopt` algorithm in the base package;
    // the pagmonet4j-ipopt companion package -- or a system libipopt, or the PAGMONET_IPOPT_LIBRARY
    // override -- supplies the native runtime it loads at startup. A base-only build cleanly skips.
    static void runIpoptScenario(boolean verbose) {
        System.out.println("Scenario: IPOPT gradient-based local solve (optional solver)");

        if (!OptionalSolverAvailability.isIpoptAvailable()) {
            System.out.println("  IPOPT is not available in this build.");
            System.out.println("  Add the pagmonet4j-ipopt companion package (or set PAGMONET_IPOPT_LIBRARY) to enable it.");
            System.out.println("  Skipping -- optional native solvers should degrade gracefully, not crash.");
            return;
        }

        try (SmoothBowlProblem prob = new SmoothBowlProblem();
             ipopt algo = new ipopt()) {
            algo.set_integer_option("print_level", 0);   // quiet; raise for IPOPT's own iteration log

            try (population pop = new population(prob, 1L, 42L);
                 population evolved = algo.evolve(pop)) {

                if (verbose) Main.printAlgorithmLog("ipopt", algo.getLogLines());

                DoubleVector x = evolved.champion_x();
                DoubleVector f = evolved.champion_f();
                System.out.printf("  result code = %d  (0 = Solve_Succeeded)%n",
                    algo.get_last_opt_result_code());
                System.out.printf("  minimum f   = %.3e  at (%.4f, %.4f)  (expected ~0 at (0, 3))%n",
                    f.get(0), x.get(0), x.get(1));
                x.delete();
                f.delete();
                System.out.println("  Why it matters: IPOPT exploits the analytic gradient + sparsity for fast local convergence on smooth problems.");
            }
        }
    }
}
