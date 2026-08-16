package com.lushprojects.circuitjs1.client;

class LedIndicatorFaultValidator implements GeneratedFaultValidator {
    private static final double MAX_FAULTED_LED_CURRENT = .000001;
    private static final double MIN_INCORRECT_VALUE_CURRENT = .000001;
    private static final double MAX_INCORRECT_VALUE_CURRENT = .001;

    public void verify(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED)
            throw new IllegalStateException("Faulted LED challenge must validate while powered");
        if (instance.getFaultBinding() == null || !instance.getFaultBinding().isApplied())
            throw new IllegalStateException("Faulted LED challenge did not apply its fault");
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        if (type == GeneratedFaultType.CONNECTOR_OPEN_PATH) {
            verifyConnectorOpen(instance);
            return;
        }
        if (modifications.getComponentState("R1") != ComponentPhysicalState.INSTALLED)
            throw new IllegalStateException("Faulted LED challenge was modified before validation");
        ResistorElm resistor = resistor(instance);
        double resistorCurrent = Math.abs(resistor.getCurrent());
        CircuitElm element = instance.getComponentBindings().getSingleElement("LED1");
        if (!(element instanceof LEDElm))
            throw new IllegalStateException("Faulted LED binding is not a LEDElm");
        double ledCurrent = Math.abs(((LEDElm) element).getCurrent());
        if (type == GeneratedFaultType.RESISTOR_OPEN) {
            if (ledCurrent >= MAX_FAULTED_LED_CURRENT || resistorCurrent >= MAX_FAULTED_LED_CURRENT)
                throw new IllegalStateException("Open resistor fault did not extinguish LED");
        } else if (type == GeneratedFaultType.RESISTOR_INCORRECT_VALUE) {
            double effectiveValue = instance.getFaultBinding().getFault().getEffectiveValue();
            if (Math.abs(resistor.getResistance() - effectiveValue) > effectiveValue * .001 ||
                    ledCurrent <= MIN_INCORRECT_VALUE_CURRENT ||
                    ledCurrent >= MAX_INCORRECT_VALUE_CURRENT ||
                    Math.abs(ledCurrent - resistorCurrent) > .00001)
                throw new IllegalStateException("Incorrect resistor value did not produce the expected low solved current");
        } else {
            throw new IllegalStateException("Unsupported LED fault type: " + type);
        }
        if (type == GeneratedFaultType.RESISTOR_OPEN &&
                instance.getOperationalStates().isIlluminated("LED1"))
            throw new IllegalStateException("Faulted LED is still visually illuminated");
        if (type == GeneratedFaultType.RESISTOR_INCORRECT_VALUE &&
                instance.getOperationalStates().isIlluminated("LED1"))
            throw new IllegalStateException("Incorrect resistor value did not make LED behavior abnormal");
    }

    private void verifyConnectorOpen(GeneratedBoardInstance instance) {
        double vin = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        double ledCurrent = Math.abs(((LEDElm) instance.getComponentBindings()
            .getSingleElement("LED1")).getCurrent());
        if (Math.abs(vin) > .001 || ledCurrent >= MAX_FAULTED_LED_CURRENT)
            throw new IllegalStateException("Connector open path did not remove board power");
    }

    private ResistorElm resistor(GeneratedBoardInstance instance) {
        CircuitElm element = instance.getComponentBindings().getSingleElement("R1");
        if (!(element instanceof ResistorElm))
            throw new IllegalStateException("Faulted LED resistor binding is not a ResistorElm");
        return (ResistorElm) element;
    }

    private double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }
}
