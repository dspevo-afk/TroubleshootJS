package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED ||
                modifications.getComponentState("D1") != ComponentPhysicalState.INSTALLED)
            throw new IllegalStateException("Faulted diode challenge must validate powered and installed");
        if (instance.getFaultBinding() == null || !instance.getFaultBinding().isApplied())
            throw new IllegalStateException("D1 open fault is not applied");
        double diodeCurrent = Math.abs(instance.getComponentBindings().getSingleElement("D1").getCurrent());
        double ledCurrent = Math.abs(instance.getComponentBindings().getSingleElement("LED1").getCurrent());
        if (diodeCurrent >= .000001 || ledCurrent >= .000001 ||
                instance.getOperationalStates().isIlluminated("LED1"))
            throw new IllegalStateException("Open D1 did not stop the indicator branch");
    }
}
