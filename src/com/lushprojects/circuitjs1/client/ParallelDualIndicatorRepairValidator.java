package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        ReplaceableComponentSlot slot = ReplaceableResistorBoardCapability.require(instance)
            .getSlot();
        if (powerState != BoardPowerState.POWERED || activeMeasurementOverlay || slot.isEmpty() ||
                !modifications.isComponentInstalled("R1") || slot.getInstalledPart().isFaulted())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        ResistorElm r1 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R1");
        ResistorElm r2 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2");
        LEDElm led1 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED1");
        LEDElm led2 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED2");
        double branch1 = Math.abs(r1.getCurrent());
        double branch2 = Math.abs(r2.getCurrent());
        double led1Current = Math.abs(led1.getCurrent());
        double led2Current = Math.abs(led2.getCurrent());
        boolean operating = branch1 > .001 && branch2 > .001 && led1Current > .001 &&
            led2Current > .001 && instance.getOperationalStates().isIlluminated("LED1") &&
            instance.getOperationalStates().isIlluminated("LED2") &&
            Math.abs(branch1 - led1Current) <= .0001 &&
            Math.abs(branch2 - led2Current) <= .0001;
        if (!operating)
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        boolean restored = branch1 >= .002 && branch1 <= .020 && branch2 >= .002 && branch2 <= .020 &&
            led1Current >= .002 && led1Current <= .020 && led2Current >= .002 && led2Current <= .020 &&
            Math.abs(Math.abs(ParallelDualIndicatorGeneratedBoardValidator.source(instance).getCurrent()) -
                (branch1 + branch2)) <= .0001;
        return restored ? GeneratedRepairStatus.CORRECTLY_RESTORED :
            GeneratedRepairStatus.DEGRADED_BUT_OPERATING;
    }

    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState, activeMeasurementOverlay) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
    }
}
