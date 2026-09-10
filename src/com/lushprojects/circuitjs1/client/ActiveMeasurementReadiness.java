package com.lushprojects.circuitjs1.client;

/**
 * Generic readiness for a meter that injects energy.  0.25 V is the single
 * residual-voltage threshold used by the workbench: below it, the simulated
 * stored energy is treated as safe for resistance, continuity, and diode
 * overlay measurements.
 */
enum ActiveMeasurementReadiness {
    READY(""),
    POWER_OFF("POWER OFF"),
    WAITING("SETTLING"),
    DISCHARGE("DISCHARGE"),
    UNKNOWN("UNKNOWN");

    static final double RESIDUAL_VOLTAGE_THRESHOLD_VOLTS = .25;
    private final String displayText;
    ActiveMeasurementReadiness(String displayText) { this.displayText = displayText; }
    String getDisplayText() { return displayText; }
    /** Order-independent conservative policy composition; a missing answer never grants readiness. */
    static ActiveMeasurementReadiness combine(ActiveMeasurementReadiness a, ActiveMeasurementReadiness b) {
        if (a == null) a = UNKNOWN;
        if (b == null) b = UNKNOWN;
        return priority(a) >= priority(b) ? a : b;
    }
    private static int priority(ActiveMeasurementReadiness r) {
        switch (r) {
        case POWER_OFF: return 4;
        case UNKNOWN: return 3;
        case DISCHARGE: return 2;
        case WAITING: return 1;
        default: return 0;
        }
    }
    boolean isReady() { return this == READY; }
}
