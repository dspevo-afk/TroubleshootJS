package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorRepairValidator implements GeneratedRepairValidator {
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        ParallelDualIndicatorFamilyState state = ParallelDualIndicatorFamilyState.require(instance);
        ReplaceableComponentSlot slot = state.getR1Slot();
        if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay || slot.isEmpty() ||
                !modifications.isComponentInstalled("R1") || slot.getInstalledPart().isFaulted() ||
                state.getResistorInventory().get("R1_ORIGINAL").getLocation() ==
                    ResistorPartLocation.INSTALLED)
            return false;
        ResistorElm r1 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R1");
        ResistorElm r2 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2");
        LEDElm led1 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED1");
        LEDElm led2 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED2");
        double branch1 = r1.getCurrent();
        double branch2 = r2.getCurrent();
        return branch1 >= .002 && branch1 <= .020 && branch2 >= .002 && branch2 <= .020 &&
            led1.getCurrent() >= .002 && led1.getCurrent() <= .020 &&
            led2.getCurrent() >= .002 && led2.getCurrent() <= .020 &&
            Math.abs(branch1 - led1.getCurrent()) <= .0001 &&
            Math.abs(branch2 - led2.getCurrent()) <= .0001 &&
            instance.getOperationalStates().isIlluminated("LED1") &&
            instance.getOperationalStates().isIlluminated("LED2") &&
            Math.abs((ParallelDualIndicatorGeneratedBoardValidator.source(instance).getCurrent()) -
                (branch1 + branch2)) <= .0001;
    }
}
