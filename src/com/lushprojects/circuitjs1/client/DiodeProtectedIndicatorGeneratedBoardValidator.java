package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorGeneratedBoardValidator implements GeneratedBoardValidator {
    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        CircuitElm diodeElement = instance.getComponentBindings().getSingleElement("D1");
        CircuitElm resistorElement = instance.getComponentBindings().getSingleElement("R1");
        CircuitElm ledElement = instance.getComponentBindings().getSingleElement("LED1");
        if (!(diodeElement instanceof DiodeElm) || !(resistorElement instanceof ResistorElm) ||
                !(ledElement instanceof LEDElm))
            throw new IllegalStateException("Diode family component bindings are incompatible");
        double diodeCurrent = Math.abs(diodeElement.getCurrent());
        double resistorCurrent = Math.abs(resistorElement.getCurrent());
        double ledCurrent = Math.abs(ledElement.getCurrent());
        if (powerState == BoardPowerState.UNPOWERED) {
            if (diodeCurrent > .000001 || resistorCurrent > .000001 || ledCurrent > .000001)
                throw new IllegalStateException("Unpowered diode indicator current is not zero");
            return;
        }
        double vin = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        double solvedVin = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        double diodeDrop = voltage(instance, "D1.A") - voltage(instance, "D1.K");
        if (Math.abs(vin - solvedVin) > .02)
            throw new IllegalStateException("Diode family supply is incorrect: " + solvedVin);
        if (diodeCurrent < .005 || diodeCurrent > .015 ||
                Math.abs(diodeCurrent - resistorCurrent) > .0001 ||
                Math.abs(diodeCurrent - ledCurrent) > .0001)
            throw new IllegalStateException("Diode indicator branch current is not sensible: " + diodeCurrent);
        if (diodeDrop < .45 || diodeDrop > .95)
            throw new IllegalStateException("D1 polarity or forward drop is incorrect: " + diodeDrop);
        if (!instance.getOperationalStates().isIlluminated("LED1"))
            throw new IllegalStateException("Healthy diode indicator LED is not operational");
    }

    private double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }
}
