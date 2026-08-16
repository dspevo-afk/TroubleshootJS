package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED)
            throw new IllegalStateException("Parallel indicator challenge must validate while powered");
        if (!modifications.isComponentInstalled("R1") || instance.getFaultBinding() == null ||
                !instance.getFaultBinding().isApplied())
            throw new IllegalStateException("Parallel R1 fault is not installed and applied");
        ResistorElm r1 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R1");
        LEDElm led1 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED1");
        LEDElm led2 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED2");
        ResistorElm r2 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2");
        if (Math.abs(r1.getCurrent()) >= .000001 || Math.abs(led1.getCurrent()) >= .000001 ||
                r2.getCurrent() < .002 || led2.getCurrent() < .002 ||
                !instance.getOperationalStates().isIlluminated("LED2") ||
                instance.getOperationalStates().isIlluminated("LED1"))
            throw new IllegalStateException("R1 open fault did not isolate only branch 1");
        double expected = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        double actual = ParallelDualIndicatorGeneratedBoardValidator.voltage(instance, "J1.1") -
            ParallelDualIndicatorGeneratedBoardValidator.voltage(instance, "J1.2");
        if (Math.abs(actual - expected) > .1)
            throw new IllegalStateException("Faulted parallel VIN differs from nameplate: " + actual);
    }
}
