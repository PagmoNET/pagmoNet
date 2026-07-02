package io.github.samthegliderpilot.pagmonet4j.examples.problems;

/**
 * Classical Keplerian orbital elements.
 * Angles in radians; distance/time units are caller-defined but must be consistent.
 */
public final class KeplerianElements {
    public final double semiMajorAxis;
    public final double eccentricity;
    public final double inclination;
    public final double raan;
    public final double argumentOfPeriapsis;
    public final double trueAnomaly;

    public KeplerianElements(double semiMajorAxis, double eccentricity, double inclination,
                              double raan, double argumentOfPeriapsis, double trueAnomaly) {
        this.semiMajorAxis        = semiMajorAxis;
        this.eccentricity         = eccentricity;
        this.inclination          = inclination;
        this.raan                 = raan;
        this.argumentOfPeriapsis  = argumentOfPeriapsis;
        this.trueAnomaly          = trueAnomaly;
    }

    public KeplerianElements withTrueAnomaly(double nu) {
        return new KeplerianElements(semiMajorAxis, eccentricity, inclination,
                                     raan, argumentOfPeriapsis, nu);
    }
}
