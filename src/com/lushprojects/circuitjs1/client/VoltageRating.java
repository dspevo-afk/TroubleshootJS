package com.lushprojects.circuitjs1.client;

/** Immutable maximum working-voltage rating for a physical part specification. */
final class VoltageRating extends PhysicalRating {
    private final double volts;

    VoltageRating(double volts) {
        super("VOLTAGE");
        if (Double.isNaN(volts) || Double.isInfinite(volts) || volts <= 0)
            throw new IllegalArgumentException("Invalid voltage rating");
        this.volts = volts;
    }

    double getVolts() { return volts; }
}
