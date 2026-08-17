package com.lushprojects.circuitjs1.client;

class LedIndicatorRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        ReplaceableComponentSlot slot = ReplaceableResistorBoardCapability.require(instance)
            .getSlot();
        LedComponentSlot ledSlot = ReplaceableLedBoardCapability.require(instance).getSlot();
        if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay || slot.isEmpty() ||
                ledSlot.isEmpty() || !modifications.isComponentInstalled("LED1") ||
                !modifications.isComponentInstalled("R1") || slot.getInstalledPart().isFaulted())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        CircuitElm resistorElement = instance.getComponentBindings().getSingleElement("R1");
        CircuitElm ledElement = instance.getComponentBindings().getSingleElement("LED1");
        if (!(resistorElement instanceof ResistorElm) || !(ledElement instanceof LEDElm))
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        double resistorCurrent = Math.abs(((ResistorElm) resistorElement).getCurrent());
        double ledCurrent = Math.abs(((LEDElm) ledElement).getCurrent());
        if (ledCurrent <= .001 || Math.abs(ledCurrent - resistorCurrent) > .0001 ||
                !instance.getOperationalStates().isIlluminated("LED1"))
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        if (ledCurrent >= .005 && ledCurrent <= .015)
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
