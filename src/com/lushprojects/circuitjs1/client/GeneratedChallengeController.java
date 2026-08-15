package com.lushprojects.circuitjs1.client;

class GeneratedChallengeController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final GeneratedFaultController faults;
    private GeneratedChallengeState state = GeneratedChallengeState.PREPARING_HEALTHY;

    GeneratedChallengeController(CirSim sim, GeneratedBoardInstance instance) {
        if (instance.getFaultBinding() == null)
            throw new IllegalArgumentException("Generated challenge requires a fault binding");
        this.sim = sim;
        this.instance = instance;
        faults = new GeneratedFaultController(sim, instance, instance.getFaultBinding());
    }

    void begin() { sim.requestGeneratedBoardVerification(); }

    void afterGeneratedVerification() {
        if (state == GeneratedChallengeState.PREPARING_HEALTHY) {
            state = GeneratedChallengeState.PREPARING_FAULTED;
            faults.apply();
            return;
        }
        if (state == GeneratedChallengeState.PREPARING_FAULTED) {
            LedIndicatorFaultValidator.verifyOpenResistor(instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            state = GeneratedChallengeState.READY;
            sim.refreshBoardModificationControls();
            sim.repaint();
        }
    }

    boolean isHealthyValidationExpected() {
        return state == GeneratedChallengeState.PREPARING_HEALTHY;
    }

    boolean isReady() { return state == GeneratedChallengeState.READY; }
    GeneratedChallengeState getState() { return state; }
    GeneratedFaultController getFaultController() { return faults; }
    String getComplaintText() { return "Indicator does not light."; }
}
