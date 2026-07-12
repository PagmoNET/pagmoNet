package io.github.pagmonet.pagmonet4j.examples.problems;

import java.util.ArrayList;
import java.util.List;

/** Two-body orbital mechanics helpers: Kepler propagation and Gauss variation-of-parameters. */
public final class OrbitalMechanics {

    public static final double EARTH_GM = 398600.4418;  // km³/s²

    private static final int    KEPLER_MAX_ITER = 50;
    private static final double KEPLER_TOL      = 1e-12;

    private OrbitalMechanics() {}

    /** Returns the true anomaly after coasting for {@code duration} seconds. */
    public static double advanceTrueAnomaly(double semiMajorAxis, double eccentricity,
                                            double trueAnomaly, double duration, double mu) {
        double halfNu = trueAnomaly / 2.0;
        double E0 = 2.0 * Math.atan2(
            Math.sqrt(1.0 - eccentricity) * Math.sin(halfNu),
            Math.sqrt(1.0 + eccentricity) * Math.cos(halfNu));

        double M0 = E0 - eccentricity * Math.sin(E0);
        double n  = Math.sqrt(mu / (semiMajorAxis * semiMajorAxis * semiMajorAxis));
        double M1 = M0 + n * duration;

        double E1 = M1;
        for (int iter = 0; iter < KEPLER_MAX_ITER; iter++) {
            double dE = (M1 - E1 + eccentricity * Math.sin(E1))
                      / (1.0 - eccentricity * Math.cos(E1));
            E1 += dE;
            if (Math.abs(dE) < KEPLER_TOL) break;
        }

        return 2.0 * Math.atan2(
            Math.sqrt(1.0 + eccentricity) * Math.sin(E1 / 2.0),
            Math.sqrt(1.0 - eccentricity) * Math.cos(E1 / 2.0));
    }

    /** Applies an impulsive RIC burn via Gauss variation-of-parameters. */
    public static KeplerianElements applyBurn(KeplerianElements el, CoastAndBurn burn, double mu) {
        double a     = el.semiMajorAxis;
        double e     = el.eccentricity;
        double i     = el.inclination;
        double nu    = el.trueAnomaly;
        double omega = el.argumentOfPeriapsis;
        double u     = omega + nu;

        double p  = a * (1.0 - e * e);
        double h  = Math.sqrt(mu * p);
        double r  = p / (1.0 + e * Math.cos(nu));

        double dvR = burn.dvRadial;
        double dvS = burn.dvInTrack;
        double dvW = burn.dvCrossTrack;

        double da = (2.0 * a * a / h) * (e * Math.sin(nu) * dvR + (p / r) * dvS);
        double de = (1.0 / h) * (p * Math.sin(nu) * dvR + ((p + r) * Math.cos(nu) + r * e) * dvS);
        double di = (r * Math.cos(u) / h) * dvW;
        double dRaan  = (r * Math.sin(u)) / (h * Math.sin(i)) * dvW;
        double dOmega = (1.0 / (h * e)) * (-p * Math.cos(nu) * dvR + (p + r) * Math.sin(nu) * dvS)
                      - (r * Math.sin(u) * Math.cos(i)) / (h * Math.sin(i)) * dvW;

        double aNew = a + da;
        double eNew = e + de;

        double pNew  = aNew * (1.0 - eNew * eNew);
        double hNew  = Math.sqrt(mu * pNew);
        double vrBefore = (mu / h) * e * Math.sin(nu);
        double vrAfter  = vrBefore + dvR;

        double cosNuNew = (pNew / r - 1.0) / eNew;
        double sinNuNew = vrAfter * hNew / (mu * eNew);
        double nuNew    = Math.atan2(sinNuNew, cosNuNew);

        return new KeplerianElements(aNew, eNew, i + di,
                                     el.raan + dRaan,
                                     el.argumentOfPeriapsis + dOmega,
                                     nuNew);
    }

    /** Coasts then burns; returns (elements, time) after the maneuver. */
    public static PropagationResult propagate(KeplerianElements initial, double t0,
                                              CoastAndBurn burn, double mu) {
        double nuAfterCoast = advanceTrueAnomaly(
            initial.semiMajorAxis, initial.eccentricity, initial.trueAnomaly,
            burn.coastDuration, mu);
        KeplerianElements afterCoast = initial.withTrueAnomaly(nuAfterCoast);
        KeplerianElements afterBurn  = applyBurn(afterCoast, burn, mu);
        return new PropagationResult(afterBurn, t0 + burn.coastDuration);
    }

    /** Applies each maneuver in sequence; returns state after every burn. */
    public static List<PropagationResult> propagate(KeplerianElements initial, double t0,
                                                    List<CoastAndBurn> maneuvers, double mu) {
        List<PropagationResult> results = new ArrayList<>(maneuvers.size());
        KeplerianElements current = initial;
        double currentTime = t0;
        for (CoastAndBurn m : maneuvers) {
            PropagationResult r = propagate(current, currentTime, m, mu);
            results.add(r);
            current     = r.elements;
            currentTime = r.time;
        }
        return results;
    }

    public static final class PropagationResult {
        public final KeplerianElements elements;
        public final double time;
        public PropagationResult(KeplerianElements elements, double time) {
            this.elements = elements;
            this.time     = time;
        }
    }
}
