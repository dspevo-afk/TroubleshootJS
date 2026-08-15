package com.lushprojects.circuitjs1.client;

class LedIndicatorFaultValidator implements GeneratedFaultValidator {
    private static final double MAX_FAULTED_LED_CURRENT = .000001;

    public void verify(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED)
            throw new IllegalStateException("Faulted LED challenge must validate while powered");
        if (modifications.getComponentState("R1") != ComponentPhysicalState.INSTALLED)
            throw new IllegalStateException("Faulted LED challenge was modified before validation");
        if (instance.getFaultBinding() == null || !instance.getFaultBinding().isApplied())
            throw new IllegalStateException("Faulted LED challenge did not apply its fault");
        CircuitElm element = instance.getComponentBindings().getSingleElement("LED1");
        if (!(element instanceof LEDElm) || Math.abs(((LEDElm) element).getCurrent()) >=
                MAX_FAULTED_LED_CURRENT)
            throw new IllegalStateException("Open resistor fault did not extinguish LED");
        if (instance.getOperationalStates().isIlluminated("LED1"))
            throw new IllegalStateException("Faulted LED is still visually illuminated");
    }
}
