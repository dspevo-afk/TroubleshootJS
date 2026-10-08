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
    UNKNOWN("UNKNOWN"),
    ISOLATE_COMPONENT("ISOLATE COMPONENT"),
    UNSUPPORTED("UNSUPPORTED");

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
        // Present the actual energy action before the instrument's support/isolation policy.
        case POWER_OFF: return 6;
        case UNKNOWN: return 5;
        case DISCHARGE: return 4;
        case WAITING: return 3;
        case UNSUPPORTED: return 2;
        case ISOLATE_COMPONENT: return 1;
        default: return 0;
        }
    }
    boolean isReady() { return this == READY; }
}
