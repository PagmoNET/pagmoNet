package io.github.pagmonet.pagmonet4j.examples.problems;

/**
 * A free-coast interval followed by an impulsive maneuver in the
 * Radial / In-track / Cross-track (RIC) frame.
 */
public final class CoastAndBurn {
    public final double coastDuration;
    public final double dvRadial;
    public final double dvInTrack;
    public final double dvCrossTrack;

    public CoastAndBurn(double coastDuration, double dvRadial,
                        double dvInTrack, double dvCrossTrack) {
        this.coastDuration = coastDuration;
        this.dvRadial      = dvRadial;
        this.dvInTrack     = dvInTrack;
        this.dvCrossTrack  = dvCrossTrack;
    }

    public double totalDeltaV() {
        return Math.sqrt(dvRadial * dvRadial + dvInTrack * dvInTrack + dvCrossTrack * dvCrossTrack);
    }
}
