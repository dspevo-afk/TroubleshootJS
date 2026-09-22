package com.lushprojects.circuitjs1.client;

/** Live proof that the selected serviceable E04 resistor fault affects the graph. */
final class SensorControlFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED || instance.getFaultBinding() == null ||
                !instance.getFaultBinding().isApplied() || modifications == null ||
                !modifications.isFullyRestored())
            throw new IllegalStateException("E04 fault validation requires powered, untouched board");
        GeneratedFault fault = instance.getFaultBinding().getFault();
        if (fault.getType() != GeneratedFaultType.RESISTOR_OPEN)
            throw new IllegalStateException("Unsupported E04 fault type: " + fault.getType());
        SensorControlFamilyState state = state(instance);
        CirSim sim = CircuitElm.sim;
        E04SensorControlModel.SensorCondition prior = state.getCommandedCondition();
        if (sim != null) sim.beginObservationalValidation();
        try {
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);
            double low = state.getModel().getSensorNodeVoltage();
            boolean lowHealthy = SensorControlGeneratedBoardValidator.isHealthyLow(instance);
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            double high = state.getModel().getSensorNodeVoltage();
            boolean highHealthy = SensorControlGeneratedBoardValidator.isHealthyHigh(instance);
            // An open RBIAS deliberately isolates the conditioned node, so
            // LOW and HIGH may now solve to the same finite value.  Treating
            // that loss of tracking as invalid would reject the exact public
            // symptom that the fault is meant to exercise.
            if (!finite(low) || !finite(high))
                throw new IllegalStateException("E04 fault left an invalid solved sensor node");
            if (SensorControlGeneratedBoardValidator.resistorCurrent(instance,
                    fault.getTargetComponentId()) > .000001)
                throw new IllegalStateException("E04 open resistor still carries current");
            if (lowHealthy && highHealthy)
                throw new IllegalStateException(
                    "Admitted E04 resistor fault has no live LOW/HIGH symptom");
        } finally {
            try {
                state.applyCondition(sim, prior);
            } finally {
                if (sim != null) sim.endObservationalValidation();
            }
        }
    }

    private SensorControlFamilyState state(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof SensorControlFamilyState))
            throw new IllegalStateException("E04 family state is missing");
        return (SensorControlFamilyState) instance.getFamilyState();
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
