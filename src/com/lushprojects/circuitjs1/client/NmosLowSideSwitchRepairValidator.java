package com.lushprojects.circuitjs1.client;

/** Generic repair boundary: a live NMOS must pass both command states. */
final class NmosLowSideSwitchRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        NmosLowSideSwitchFamilyState state = state(instance);
        boolean priorCommandedOn = state.isCommandedOn();
        CirSim sim = CircuitElm.sim;
        if (sim != null) sim.beginObservationalValidation();
        try {
            if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay ||
                    modifications == null || !modifications.isFullyRestored() ||
                    !allInstalled(instance, modifications))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            state.setCommandedOn(sim, true);
            if (!NmosLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            state.setCommandedOn(sim, false);
            if (!NmosLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance))
                return GeneratedRepairStatus.DEGRADED_BUT_OPERATING;
            return GeneratedRepairStatus.CORRECTLY_RESTORED;
        } finally {
            try {
                state.setCommandedOn(sim, priorCommandedOn);
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
        return modifications.isComponentInstalled("Q1") &&
            ReplaceableNmosBoardCapability.require(instance).getSlot().getInstalledPart() != null;
    }
    private NmosLowSideSwitchFamilyState state(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof NmosLowSideSwitchFamilyState))
            throw new IllegalStateException("NMOS family state is missing");
        return (NmosLowSideSwitchFamilyState) instance.getFamilyState();
    }
}
