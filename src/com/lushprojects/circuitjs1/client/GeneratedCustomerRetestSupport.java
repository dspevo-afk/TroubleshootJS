package com.lushprojects.circuitjs1.client;

/** Small shared helpers for cleanup-safe customer retest executors. */
final class GeneratedCustomerRetestSupport {
    static final String PASS_MESSAGE =
        "Customer retest passed. The reported behavior is resolved.";
    static final String FAIL_MESSAGE =
        "Customer retest did not pass. Continue troubleshooting.";

    private GeneratedCustomerRetestSupport() { }

    static GeneratedCustomerRetestResult failure() {
        return new GeneratedCustomerRetestResult(false, FAIL_MESSAGE);
    }

    static GeneratedCustomerRetestResult powerRequiredFailure() {
        return new GeneratedCustomerRetestResult(false,
            "Power the board ON before running the customer retest.");
    }

    static GeneratedCustomerRetestResult success() {
        return new GeneratedCustomerRetestResult(true, PASS_MESSAGE);
    }

    static boolean isReadyForPoweredObservation(CirSim sim,
            GeneratedBoardInstance instance) {
        return sim != null && instance != null &&
            sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
            !sim.activeMeasurementOverlay && sim.getBoardModificationController() != null &&
            sim.getBoardModificationController().isFullyRestored();
    }

    static void restorePower(CirSim sim, BoardPowerState priorPower) {
        if (sim != null && priorPower != null &&
                sim.getBoardPowerController().getState() != priorPower)
            sim.setBoardPowerStateForGeneratedTemporalProfile(priorPower);
    }
}
