package com.lushprojects.circuitjs1.client;

/** RC family keeps selected-fault ownership with the physical C1 instance. */
final class RcDelayFamilyState implements GeneratedBoardFamilyState {
    private final GeneratedBoardOperationCatalog operations =
        new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retestProfile;
    private final RcDelayTemporalBehavior temporal;

    RcDelayFamilyState(final RcDelayTemporalBehavior temporal) {
        if (temporal == null)
            throw new IllegalArgumentException("Missing RC temporal behavior");
        this.temporal = temporal;
        retestProfile = new GeneratedCustomerRetestProfile(
            "RC_DELAY_CUSTOMER_RETEST", "Power the board ON, then power-cycle and observe the delayed output.",
            "Board Power OFF, then ON; stored energy must be safely discharged first.",
            "No external input transition.", "Delayed RC output behavior.",
            "One real power cycle with the existing temporal profile.",
            "Input power control remains the only exercised board function.",
            new GeneratedCustomerRetestProfile.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                if (sim == null || instance == null || sim.activeMeasurementOverlay ||
                        sim.getBoardModificationController() == null ||
                        !sim.getBoardModificationController().isFullyRestored())
                    return GeneratedCustomerRetestSupport.failure();
                if (sim.getBoardPowerController().getState() != BoardPowerState.POWERED)
                    return GeneratedCustomerRetestSupport.powerRequiredFailure();
                BoardPowerState priorPower = sim.getBoardPowerController().getState();
                boolean priorPhysicalState = sim.getBoardModificationController().isFullyRestored();
                try {
                    temporal.performCustomerRetest(sim);
                    return temporal.passedCustomerRetest() ?
                        GeneratedCustomerRetestSupport.success() :
                        GeneratedCustomerRetestSupport.failure();
                } finally {
                    try {
                        GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
                    } finally {
                        if (priorPhysicalState != sim.getBoardModificationController()
                                .isFullyRestored())
                            throw new IllegalStateException("Customer retest changed physical board state");
                    }
                }
            }
        });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Power-cycle and Retest Customer", new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                return retestProfile.execute(sim, instance);
            }
        }));
    }

    public void requireOwnedBy(GeneratedBoardInstance instance) {
        if (temporal != instance.getTemporalBehavior())
            throw new IllegalArgumentException("Fresh family state captures a foreign temporal owner");
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }

    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
}
