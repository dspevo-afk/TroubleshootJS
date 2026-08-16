package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED)
            throw new IllegalStateException("Faulted diode challenge must validate powered and installed");
        if (instance.getFaultBinding() == null || !instance.getFaultBinding().isApplied())
            throw new IllegalStateException("Selected diode fault is not applied");
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        if (type == GeneratedFaultType.CONNECTOR_OPEN_PATH) {
            verifyConnectorOpen(instance);
            return;
        }
        if (modifications.getComponentState("D1") != ComponentPhysicalState.INSTALLED)
            throw new IllegalStateException("Faulted diode target was modified before validation");
        DiodeElm diode = (DiodeElm) instance.getComponentBindings().getSingleElement("D1");
        double diodeCurrent = Math.abs(diode.getCurrent());
        double resistorCurrent = Math.abs(instance.getComponentBindings()
            .getSingleElement("R1").getCurrent());
        double ledCurrent = Math.abs(instance.getComponentBindings().getSingleElement("LED1").getCurrent());
        if (type == GeneratedFaultType.DIODE_OPEN) {
            if (diodeCurrent >= .000001 || resistorCurrent >= .000001 || ledCurrent >= .000001 ||
                    instance.getOperationalStates().isIlluminated("LED1"))
                throw new IllegalStateException("Open D1 did not stop the indicator branch");
            return;
        }
        if (type == GeneratedFaultType.DIODE_SHORT) {
            double supply = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
                .getNominalVoltage();
            double resistance = ((ResistorElm) instance.getComponentBindings()
                .getSingleElement("R1")).getResistance();
            double expectedShortCurrent = (supply - 2.1) / resistance;
            double diodeDrop = voltage(instance, "D1.A") - voltage(instance, "D1.K");
            if (diodeCurrent >= .000001 || diodeDrop > .01 ||
                    ledCurrent < expectedShortCurrent * .85 ||
                    Math.abs(ledCurrent - resistorCurrent) > .0001 ||
                    !instance.getOperationalStates().isIlluminated("LED1"))
                throw new IllegalStateException("Shorted D1 did not produce the expected zero-diode-current overcurrent symptom");
            return;
        }
        throw new IllegalStateException("Unsupported diode fault type: " + type);
    }

    private void verifyConnectorOpen(GeneratedBoardInstance instance) {
        double vin = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        double ledCurrent = Math.abs(instance.getComponentBindings().getSingleElement("LED1").getCurrent());
        if (Math.abs(vin) > .001 || ledCurrent >= .000001)
            throw new IllegalStateException("Connector open path did not remove diode-board power");
    }

    private double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }
}
