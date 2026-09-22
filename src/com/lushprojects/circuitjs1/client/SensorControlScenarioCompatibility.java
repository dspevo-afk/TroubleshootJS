package com.lushprojects.circuitjs1.client;

/**
 * Solver-backed complaint predicate for E04.  It observes the legal HIGH
 * sensor operation and the measured branch response; it never reads the
 * selected fault identity or a generated answer key.
 */
final class SensorControlScenarioCompatibility
        implements GeneratedScenarioCompatibility<GeneratedObservedBehavior>,
        GeneratedScenarioPresentation<GeneratedObservedBehavior> {
    public boolean matches(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            GeneratedObservedBehavior observedBehavior) {
        if (powerState != BoardPowerState.POWERED ||
                !(instance.getFamilyState() instanceof SensorControlFamilyState))
            return false;
        SensorControlFamilyState state =
            (SensorControlFamilyState) instance.getFamilyState();
        E04SensorControlModel.SensorCondition prior = state.getCommandedCondition();
        CirSim sim = CircuitElm.sim;
        if (sim != null) sim.beginObservationalValidation();
        try {
            // Complaint matching is deliberately based only on the public
            // live sweep.  Fault identity, target component and answer keys
            // are not observations available to a player and must not enter
            // scenario admission.
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);
            double lowSensor = state.getModel().getSensorNodeVoltage();
            double lowOutput = state.getModel().getOutputVoltage();
            boolean lowHealthy = SensorControlGeneratedBoardValidator.isHealthyLow(instance);
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_MID);
            double midSensor = state.getModel().getSensorNodeVoltage();
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            double highSensor = state.getModel().getSensorNodeVoltage();
            double highOutput = state.getModel().getOutputVoltage();
            boolean highHealthy = SensorControlGeneratedBoardValidator.isHealthyHigh(instance);
            boolean orderedSweep = finite(lowSensor) && finite(midSensor) && finite(highSensor) &&
                midSensor > lowSensor && highSensor > midSensor;
            boolean outputTracks = finite(lowOutput) && finite(highOutput) &&
                highOutput > lowOutput + .05;
            return !lowHealthy || !highHealthy || !orderedSweep || !outputTracks;
        } finally {
            try {
                instance.invokeOperation(operationFor(prior), sim);
            } finally {
                if (sim != null) sim.endObservationalValidation();
            }
        }
    }

    public void present(CirSim sim, GeneratedBoardInstance instance,
            GeneratedObservedBehavior observedBehavior) {
        instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH, sim);
    }

    private String operationFor(E04SensorControlModel.SensorCondition condition) {
        if (condition == E04SensorControlModel.SensorCondition.SENSOR_HIGH)
            return GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH;
        if (condition == E04SensorControlModel.SensorCondition.SENSOR_MID)
            return GeneratedBoardOperationIds.SENSOR_CONDITION_MID;
        return GeneratedBoardOperationIds.SENSOR_CONDITION_LOW;
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
