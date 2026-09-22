package com.lushprojects.circuitjs1.client;

/**
 * Live player controls for the E04 sensor/control leaf.  The E04 model owns
 * the source, decision and feedback elements; this class only exposes the
 * bounded semantic commands and settles the existing solver graph after each
 * command.
 */
final class SensorControlFamilyState implements GeneratedBoardFamilyState {
    private final E04SensorControlModel model;
    private final GeneratedBoardOperationCatalog operations =
        new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retestProfile;
    private E04SensorControlModel.SensorCondition commandedCondition =
        E04SensorControlModel.SensorCondition.SENSOR_LOW;

    SensorControlFamilyState(E04SensorControlModel model) {
        if (model == null)
            throw new IllegalArgumentException("Missing E04 sensor/control model");
        this.model = model;
        operations.add(new GeneratedBoardOperation(
            GeneratedBoardOperationIds.SENSOR_CONDITION_LOW, "Set sensor LOW",
            new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);
                return null;
            }
        }));
        operations.add(new GeneratedBoardOperation(
            GeneratedBoardOperationIds.SENSOR_CONDITION_MID, "Set sensor MID",
            new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_MID);
                return null;
            }
        }));
        operations.add(new GeneratedBoardOperation(
            GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH, "Set sensor HIGH",
            new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
                return null;
            }
        }));
        retestProfile = new GeneratedCustomerRetestProfile(
            "SENSOR_CONTROL_CUSTOMER_RETEST",
            "Set the sensor LOW and HIGH and confirm the control output follows.",
            "Board powered during the functional check.",
            "Sensor LOW, then HIGH.",
            "J3.1 output header and the conditioned sensor response.",
            "One LOW/HIGH repetition after CircuitJS settles each condition.",
            "No physical lead is left lifted and no board source is changed.",
            new GeneratedCustomerRetestProfile.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, instance))
                    return GeneratedCustomerRetestSupport.failure();
                BoardPowerState priorPower = sim.getBoardPowerController().getState();
                E04SensorControlModel.SensorCondition priorCondition = commandedCondition;
                boolean priorPhysicalState = sim.getBoardModificationController()
                    .isFullyRestored();
                try {
                    applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);
                    if (!SensorControlGeneratedBoardValidator.isHealthyLow(instance))
                        return GeneratedCustomerRetestSupport.failure();
                    applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
                    if (!SensorControlGeneratedBoardValidator.isHealthyHigh(instance))
                        return GeneratedCustomerRetestSupport.failure();
                    return GeneratedCustomerRetestSupport.success();
                } finally {
                    try {
                        applyCondition(sim, priorCondition);
                    } finally {
                        try {
                            GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
                        } finally {
                            if (priorPhysicalState != sim.getBoardModificationController()
                                    .isFullyRestored())
                                throw new IllegalStateException(
                                    "Customer retest changed physical board state");
                        }
                    }
                }
            }
        });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Retest Customer", new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                return retestProfile.execute(sim, instance);
            }
        }));
    }

    E04SensorControlModel getModel() { return model; }

    E04SensorControlModel.SensorCondition getCommandedCondition() {
        return commandedCondition;
    }

    void applyCondition(CirSim sim, E04SensorControlModel.SensorCondition condition) {
        if (condition == null || condition == E04SensorControlModel.SensorCondition.SENSOR_UNSUPPORTED)
            throw new IllegalArgumentException("Unsupported E04 player sensor condition");
        model.setSensorCondition(condition);
        commandedCondition = condition;
        if (sim != null) {
            if (sim.cv == null) {
                // Native contract harnesses do not construct the GWT canvas;
                // preserve the same analysis invalidation without scheduling
                // a browser repaint through Scheduler.
                sim.solverExecutor.invalidate();
                sim.analyzeFlag = true;
            } else {
                sim.needAnalyze();
            }
            sim.analyzeCircuit();
            // Use the solver executor directly so the semantic control remains
            // synchronous in both the native contract harness and the browser;
            // this still advances the live CircuitJS graph, without depending
            // on the UI speed scrollbar being initialized.
            sim.solverExecutor.advanceSteps(8);
        }
    }

    public void requireOwnedBy(GeneratedBoardInstance instance) {
        if (instance == null)
            throw new IllegalArgumentException("Missing E04 generated board");
        for (CircuitElm element : model.getSimulationElements())
            if (!instance.getSimulationElements().contains(element))
                throw new IllegalArgumentException(
                    "Fresh E04 family state captures a foreign solver element");
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }

    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
}
