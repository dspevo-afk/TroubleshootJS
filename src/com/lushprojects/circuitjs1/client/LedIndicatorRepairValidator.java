package com.lushprojects.circuitjs1.client;

class LedIndicatorRepairValidator implements GeneratedRepairValidator {
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        ReplaceableComponentSlot slot = LedIndicatorFamilyState.require(instance).getR1Slot();
        if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay || slot.isEmpty() ||
                !modifications.isComponentInstalled("R1") || slot.getInstalledPart().isFaulted() ||
                LedIndicatorFamilyState.require(instance).getResistorInventory().get("R1_ORIGINAL").getLocation() ==
                    ResistorPartLocation.INSTALLED)
            return false;
        CircuitElm resistorElement = instance.getComponentBindings().getSingleElement("R1");
        CircuitElm ledElement = instance.getComponentBindings().getSingleElement("LED1");
        if (!(resistorElement instanceof ResistorElm) || !(ledElement instanceof LEDElm))
            return false;
        double resistorCurrent = Math.abs(((ResistorElm) resistorElement).getCurrent());
        double ledCurrent = Math.abs(((LEDElm) ledElement).getCurrent());
        return ledCurrent >= .005 && ledCurrent <= .015 &&
            Math.abs(ledCurrent - resistorCurrent) <= .0001 &&
            instance.getOperationalStates().isIlluminated("LED1");
    }
}