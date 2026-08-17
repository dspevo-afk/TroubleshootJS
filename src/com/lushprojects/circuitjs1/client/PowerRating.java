package com.lushprojects.circuitjs1.client;

/** Typed resistor/component power rating. */
final class PowerRating extends PhysicalRating {
    private final double watts;

    PowerRating(double watts) {
        super("POWER");
        if (Double.isNaN(watts) || Double.isInfinite(watts) || watts <= 0)
            throw new IllegalArgumentException("Invalid power rating");
        this.watts = watts;
    }

    double getWatts() { return watts; }
}
