package com.lushprojects.circuitjs1.client;

/** Generic finish proof: the board must switch correctly in both command states. */
final class NpnLowSideSwitchRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay || modifications == null ||
                !modifications.isFullyRestored() || !allInstalled(instance, modifications))
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        NpnLowSideSwitchFamilyState state = state(instance);
        state.setCommandedOn(CircuitElm.sim, true);
        if (!NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance))
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        state.setCommandedOn(CircuitElm.sim, false);
        if (!NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance))
            return GeneratedRepairStatus.DEGRADED_BUT_OPERATING;
        return GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState, activeMeasurementOverlay) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    private boolean allInstalled(GeneratedBoardInstance instance,
            BoardModificationController modifications) {
        return modifications.isComponentInstalled("Q1") && modifications.isComponentInstalled("RB") &&
            modifications.isComponentInstalled("RLOAD") &&
            ReplaceableNpnBoardCapability.require(instance).getSlot().getInstalledPart() != null &&
            ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), "RB") != null &&
            ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), "RLOAD") != null;
    }

    private NpnLowSideSwitchFamilyState state(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof NpnLowSideSwitchFamilyState))
            throw new IllegalStateException("NPN family state is missing");
        return (NpnLowSideSwitchFamilyState) instance.getFamilyState();
    }

}
