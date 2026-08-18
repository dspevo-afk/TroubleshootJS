package com.lushprojects.circuitjs1.client;

/**
 * Optional generated-family contract for an interactive transient.  The
 * common simulator advances only real CircuitJS solver time by this bounded
 * amount; it never supplies a waveform, a reading, or a family-specific
 * topology decision.
 */
interface GeneratedLiveTemporalSimulation {
    /** Positive bounded simulation-time increment requested per live UI step. */
    double getLiveSolverAdvanceSeconds();
}
