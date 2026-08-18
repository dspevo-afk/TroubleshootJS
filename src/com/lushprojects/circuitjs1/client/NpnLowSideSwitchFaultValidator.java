package com.lushprojects.circuitjs1.client;

/** Fault proof consumes solved currents/voltages, not component identity. */
final class NpnLowSideSwitchFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED || instance.getFaultBinding() == null ||
                !instance.getFaultBinding().isApplied() || modifications == null ||
                !modifications.isFullyRestored())
            throw new IllegalStateException("NPN fault validation requires powered, untouched board");
        NpnLowSideSwitchFamilyState state = state(instance);
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        if (type == GeneratedFaultType.TRANSISTOR_CE_SHORT) {
            state.setCommandedOn(CircuitElm.sim, false);
            if (NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance) ||
                    NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) < .005 ||
                    NpnLowSideSwitchGeneratedBoardValidator.collectorVoltage(instance) > 1.0)
                throw new IllegalStateException("NPN C-E short did not create stuck-active low-control behavior");
            state.setCommandedOn(CircuitElm.sim, true);
            return;
        }
        state.setCommandedOn(CircuitElm.sim, true);
        double load = NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
        double base = NpnLowSideSwitchGeneratedBoardValidator.baseCurrent(instance);
        if (load > .000001)
            throw new IllegalStateException("NPN open fault still drives the load");
        if (type == GeneratedFaultType.TRANSISTOR_CE_OPEN && base < .00002)
            throw new IllegalStateException("NPN C-E open fault lost independent base drive");
        if (type == GeneratedFaultType.BASE_RESISTOR_OPEN && base > .000001)
            throw new IllegalStateException("Base-path open fault still drives base");
        if (type == GeneratedFaultType.LOAD_PATH_OPEN && base < .00002)
            throw new IllegalStateException("Load-path open masquerades as a base fault");
        if (type != GeneratedFaultType.TRANSISTOR_CE_OPEN &&
                type != GeneratedFaultType.BASE_RESISTOR_OPEN &&
                type != GeneratedFaultType.LOAD_PATH_OPEN)
            throw new IllegalStateException("Unsupported NPN fault type: " + type);
    }

    private NpnLowSideSwitchFamilyState state(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof NpnLowSideSwitchFamilyState))
            throw new IllegalStateException("NPN family state is missing");
        return (NpnLowSideSwitchFamilyState) instance.getFamilyState();
    }
}
