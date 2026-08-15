package com.lushprojects.circuitjs1.client;

class LedIndicatorGeneratedBoardValidator implements GeneratedBoardValidator {
    private static final double MIN_LED_CURRENT = .005;
    private static final double MAX_LED_CURRENT = .015;

    public void verify(GeneratedBoardInstance instance) {
        CircuitElm resistorElement = instance.getComponentBindings().getSingleElement("R1");
        CircuitElm ledElement = instance.getComponentBindings().getSingleElement("LED1");
        if (!(resistorElement instanceof ResistorElm) || !(ledElement instanceof LEDElm))
            throw new IllegalStateException("LED indicator component bindings are incompatible with " +
                instance.getTopologyVariantId());
        double ledCurrent = ((LEDElm) ledElement).getCurrent();
        double resistorCurrent = ((ResistorElm) resistorElement).getCurrent();
        if (ledCurrent < MIN_LED_CURRENT || ledCurrent > MAX_LED_CURRENT)
            throw new IllegalStateException("LED current outside generated range: " + ledCurrent);
        if (Math.abs(ledCurrent - resistorCurrent) > .0001)
            throw new IllegalStateException("Resistor and LED currents do not match");
    }
}