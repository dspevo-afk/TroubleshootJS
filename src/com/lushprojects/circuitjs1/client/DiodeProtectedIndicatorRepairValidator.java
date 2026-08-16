package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        DiodeProtectedIndicatorFamilyState state =
            DiodeProtectedIndicatorFamilyState.require(instance);
        if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay ||
                state.getD1Slot().isEmpty() || !modifications.isComponentInstalled("D1") ||
                state.getD1Slot().getInstalledPart().isFaulted())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        double diodeCurrent = Math.abs(instance.getComponentBindings().getSingleElement("D1").getCurrent());
        double resistorCurrent = Math.abs(instance.getComponentBindings().getSingleElement("R1").getCurrent());
        double ledCurrent = Math.abs(instance.getComponentBindings().getSingleElement("LED1").getCurrent());
        if (diodeCurrent <= .001 || Math.abs(diodeCurrent - resistorCurrent) > .0001 ||
                Math.abs(diodeCurrent - ledCurrent) > .0001 ||
                !instance.getOperationalStates().isIlluminated("LED1"))
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        if (diodeCurrent >= .005 && diodeCurrent <= .015)
            return GeneratedRepairStatus.CORRECTLY_RESTORED;
        return GeneratedRepairStatus.DEGRADED_BUT_OPERATING;
    }

    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState, activeMeasurementOverlay) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
    }
}
