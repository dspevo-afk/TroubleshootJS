package com.lushprojects.circuitjs1.client;

class GeneratedFaultController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final GeneratedFaultBinding binding;

    GeneratedFaultController(CirSim sim, GeneratedBoardInstance instance,
            GeneratedFaultBinding binding) {
        this.sim = sim;
        this.instance = instance;
        this.binding = binding;
    }

    boolean apply() { return setApplied(true); }
    boolean clearForDeveloperVerification() { return setApplied(false); }
    boolean isApplied() { return binding.isApplied(); }
    GeneratedFault getFault() { return binding.getFault(); }

    private boolean setApplied(boolean applied) {
        if (sim.getGeneratedBoardInstance() != instance)
            throw new IllegalStateException("Fault control is detached from generated board");
        if (sim.activeMeasurementOverlay)
            throw new IllegalStateException("Fault control cannot run during active measurement");
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        if (!applied && challenge != null && challenge.isReady() &&
                !challenge.isDeveloperVerificationScopeActive())
            throw new IllegalStateException("Fault clearing requires developer verification scope");
        if (binding.isApplied() == applied)
            return false;
        binding.setApplied(applied);
        sim.needAnalyze();
        sim.instrumentController.refreshActiveMeasurement();
        sim.requestGeneratedBoardVerification();
        return true;
    }
}
