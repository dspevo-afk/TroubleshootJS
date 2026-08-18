package com.lushprojects.circuitjs1.client;

/** Fault validation intentionally consumes only the observed temporal symptom. */
final class RcDelayFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState) {
        GeneratedTemporalBehavior temporal = instance.getTemporalBehavior();
        if (temporal == null || (temporal.getObservedBehavior() !=
                GeneratedObservedBehavior.RC_DELAY_TOO_FAST &&
                temporal.getObservedBehavior() != GeneratedObservedBehavior.RC_DELAY_STUCK_LOW))
            throw new IllegalStateException("RC fault lacks an observed temporal symptom");
    }
}
