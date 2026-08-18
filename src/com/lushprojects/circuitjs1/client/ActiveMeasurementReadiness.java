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
    DISCHARGE("DISCHARGE");

    static final double RESIDUAL_VOLTAGE_THRESHOLD_VOLTS = .25;
    private final String displayText;
    ActiveMeasurementReadiness(String displayText) { this.displayText = displayText; }
    String getDisplayText() { return displayText; }
    boolean isReady() { return this == READY; }
}
