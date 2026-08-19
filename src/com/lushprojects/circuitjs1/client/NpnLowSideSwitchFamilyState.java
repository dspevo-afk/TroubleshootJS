package com.lushprojects.circuitjs1.client;

/** Family-owned control input state; the load response is always solver-derived. */
final class NpnLowSideSwitchFamilyState implements GeneratedBoardFamilyState {
    private final SwitchElm controlCommandSwitch;
    private final GeneratedBoardOperationCatalog operations =
        new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retestProfile;
    private boolean commandedOn = true;

    NpnLowSideSwitchFamilyState(SwitchElm controlCommandSwitch) {
        if (controlCommandSwitch == null)
            throw new IllegalArgumentException("Missing NPN control switch");
        this.controlCommandSwitch = controlCommandSwitch;
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,
            "Set control HIGH", new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                applyCommandState(sim, true);
                return null;
            }
        }));
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
            "Set control LOW", new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                applyCommandState(sim, false);
                return null;
            }
        }));
        retestProfile = new GeneratedCustomerRetestProfile(
            "NPN_LOW_SIDE_SWITCH_CUSTOMER_RETEST",
            "Set control HIGH and LOW and observe the controlled load response.",
            "Board powered during the functional check.",
            "External control HIGH, then LOW.", "J2.1 and the controlled load response.",
            "One HIGH/LOW repetition after CircuitJS settles each command.",
            "Load supply remains connected; no other input is exercised.",
            new GeneratedCustomerRetestProfile.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, instance))
                    return GeneratedCustomerRetestSupport.failure();
                BoardPowerState priorPower = sim.getBoardPowerController().getState();
                boolean priorCommand = commandedOn;
                boolean priorPhysicalState = sim.getBoardModificationController().isFullyRestored();
                try {
                    instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
                    if (!NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance))
                        return GeneratedCustomerRetestSupport.failure();
                    instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
                    if (!NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance))
                        return GeneratedCustomerRetestSupport.failure();
                    return GeneratedCustomerRetestSupport.success();
                } finally {
                    try {
                        instance.invokeOperation(priorCommand ?
                            GeneratedBoardOperationIds.CONTROL_INPUT_HIGH :
                            GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
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
            }
        });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Retest Customer", new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                return retestProfile.execute(sim, instance);
            }
        }));
    }

    boolean isCommandedOn() { return commandedOn; }

    private void applyCommandState(CirSim sim, boolean on) {
        boolean closed = controlCommandSwitch.position == 0;
        if (closed != on)
            controlCommandSwitch.toggle();
        commandedOn = on;
        if (sim != null) {
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.runCircuit(true);
            sim.runCircuit(true);
        }
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }

    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
}
