package com.lushprojects.circuitjs1.client;

/** Functional customer-facing retest for an E04 repaired board. */
final class SensorControlRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        SensorControlFamilyState state = state(instance);
        E04SensorControlModel.SensorCondition prior = state.getCommandedCondition();
        CirSim sim = CircuitElm.sim;
        if (sim != null) sim.beginObservationalValidation();
        try {
            if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay ||
                    modifications == null || !modifications.isFullyRestored() ||
                    !allInstalled(instance, modifications))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);
            if (!SensorControlGeneratedBoardValidator.isHealthyLow(instance))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            if (!SensorControlGeneratedBoardValidator.isHealthyHigh(instance))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            return GeneratedRepairStatus.CORRECTLY_RESTORED;
        } finally {
            try {
                state.applyCondition(sim, prior);
            } finally {
                if (sim != null) sim.endObservationalValidation();
            }
        }
    }

    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState, activeMeasurementOverlay) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    private boolean allInstalled(GeneratedBoardInstance instance,
            BoardModificationController modifications) {
        return modifications.isComponentInstalled("RBIAS") &&
            modifications.isComponentInstalled("RREF") &&
            modifications.isComponentInstalled("RFB") &&
            ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), "RBIAS") != null &&
            ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), "RREF") != null &&
            ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), "RFB") != null;
    }

    private SensorControlFamilyState state(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof SensorControlFamilyState))
            throw new IllegalStateException("E04 family state is missing");
        return (SensorControlFamilyState) instance.getFamilyState();
    }
}
