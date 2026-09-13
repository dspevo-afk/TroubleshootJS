package com.lushprojects.circuitjs1.client;

/** Bounded DC bench model. No switching ripple, foldback or four-quadrant sink. */
final class LowVoltageSourceModel {
    static final double OUTPUT_OHMS = .05;
    // Explicit finite compliance/reverse-blocking leakage: at most 44 uA in envelope.
    static final double LEAKAGE_SIEMENS = 1e-6;
    static final double REVERSE_KNEE_VOLTS = -1e-6;
    static final double DEFAULT_LIMIT_AMPS = .25;
    private LowVoltageSourceModel() { }

    static void validate(double volts, double amps) {
        if (!finite(volts) || volts < 0 || volts > 24 || !finite(amps) ||
                amps < .001 || amps > .5)
            throw new IllegalArgumentException("Unsupported supply setting: 0-24 V, 1-500 mA required");
    }
    static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
    static double current(double drop, double limit) {
        double knee = limit * OUTPUT_OHMS;
        if (drop < REVERSE_KNEE_VOLTS) return REVERSE_KNEE_VOLTS / OUTPUT_OHMS +
            (drop - REVERSE_KNEE_VOLTS) * LEAKAGE_SIEMENS;
        if (drop > knee) return limit + (drop - knee) * LEAKAGE_SIEMENS;
        return drop / OUTPUT_OHMS;
    }
    static double conductance(double drop, double limit) {
        return drop < REVERSE_KNEE_VOLTS || drop > limit * OUTPUT_OHMS ?
            LEAKAGE_SIEMENS : 1 / OUTPUT_OHMS;
    }
}
