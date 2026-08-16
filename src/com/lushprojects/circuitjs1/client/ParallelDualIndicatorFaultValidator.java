package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED)
            throw new IllegalStateException("Parallel indicator challenge must validate while powered");
        if (instance.getFaultBinding() == null || !instance.getFaultBinding().isApplied())
            throw new IllegalStateException("Parallel R1 fault is not installed and applied");
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        if (type == GeneratedFaultType.CONNECTOR_OPEN_PATH) {
            verifyConnectorOpen(instance);
            return;
        }
        if (!modifications.isComponentInstalled("R1"))
            throw new IllegalStateException("Parallel R1 fault target was modified before validation");
        ResistorElm r1 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R1");
        LEDElm led1 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED1");
        LEDElm led2 = ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED2");
        ResistorElm r2 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2");
        double branch1 = Math.abs(r1.getCurrent());
        double branch2 = Math.abs(r2.getCurrent());
        double led1Current = Math.abs(led1.getCurrent());
        double led2Current = Math.abs(led2.getCurrent());
        if (branch2 < .002 || led2Current < .002 ||
                !instance.getOperationalStates().isIlluminated("LED2"))
            throw new IllegalStateException("Parallel fault disturbed healthy branch 2");
        if (type == GeneratedFaultType.RESISTOR_OPEN) {
            if (branch1 >= .000001 || led1Current >= .000001)
                throw new IllegalStateException("R1 open fault did not isolate branch 1");
        } else if (type == GeneratedFaultType.RESISTOR_INCORRECT_VALUE) {
            double effectiveValue = instance.getFaultBinding().getFault().getEffectiveValue();
            if (Math.abs(r1.getResistance() - effectiveValue) > effectiveValue * .001 ||
                    branch1 <= .000001 || branch1 >= .002 || led1Current <= .000001 ||
                    led1Current >= .002 || Math.abs(branch1 - led1Current) > .00001 ||
                    instance.getOperationalStates().isIlluminated("LED1"))
                throw new IllegalStateException("Incorrect R1 value did not produce a low-current branch-1 symptom");
        } else {
            throw new IllegalStateException("Unsupported parallel fault type: " + type);
        }
        double expected = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        double actual = ParallelDualIndicatorGeneratedBoardValidator.voltage(instance, "J1.1") -
            ParallelDualIndicatorGeneratedBoardValidator.voltage(instance, "J1.2");
        if (Math.abs(actual - expected) > .1)
            throw new IllegalStateException("Faulted parallel VIN differs from nameplate: " + actual);
    }

    private void verifyConnectorOpen(GeneratedBoardInstance instance) {
        double vin = ParallelDualIndicatorGeneratedBoardValidator.voltage(instance, "J1.1") -
            ParallelDualIndicatorGeneratedBoardValidator.voltage(instance, "J1.2");
        if (Math.abs(vin) > .001 ||
                Math.abs(ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R1").getCurrent()) >= .000001 ||
                Math.abs(ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2").getCurrent()) >= .000001)
            throw new IllegalStateException("Connector open path did not remove parallel board power");
    }
}
