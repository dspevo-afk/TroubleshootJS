package com.lushprojects.circuitjs1.client;

/** Fault proof uses live NMOS voltage/current state and restores command state. */
final class NmosLowSideSwitchFaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (powerState != BoardPowerState.POWERED || instance.getFaultBinding() == null ||
                !instance.getFaultBinding().isApplied() || modifications == null ||
                !modifications.isFullyRestored())
            throw new IllegalStateException("NMOS fault validation requires powered, untouched board");
        NmosLowSideSwitchFamilyState state = state(instance);
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        CirSim sim = CircuitElm.sim;
        boolean priorCommandedOn = state.isCommandedOn();
        if (sim != null) sim.beginObservationalValidation();
        try {
            if (type == GeneratedFaultType.NMOS_DS_SHORT) {
                instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
                if (NmosLowSideSwitchGeneratedBoardValidator.gateSourceVoltage(instance) > .1 ||
                        NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) < .005 ||
                        NmosLowSideSwitchGeneratedBoardValidator.drainSourceVoltage(instance) > 1)
                    throw new IllegalStateException("NMOS D-S short did not create stuck-active behavior");
                return;
            }
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
            if (NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) > .000001)
                throw new IllegalStateException("NMOS open fault still drives the load");
            if (type == GeneratedFaultType.NMOS_DS_OPEN) {
                if (NmosLowSideSwitchGeneratedBoardValidator.gateSourceVoltage(instance) < 3)
                    throw new IllegalStateException("NMOS D-S open lost independent gate drive");
            } else if (type == GeneratedFaultType.NMOS_GATE_OPEN) {
                if (NmosLowSideSwitchGeneratedBoardValidator.boardGateVoltage(instance) < 3 ||
                        NmosLowSideSwitchGeneratedBoardValidator.internalGateVoltage(instance) > .1)
                    throw new IllegalStateException("NMOS gate-open fault did not isolate the gate");
            } else {
                throw new IllegalStateException("Unsupported NMOS fault type: " + type);
            }
        } finally {
            try {
                instance.invokeOperation(priorCommandedOn ?
                    GeneratedBoardOperationIds.CONTROL_INPUT_HIGH :
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            } finally {
                if (sim != null) sim.endObservationalValidation();
            }
        }
    }
    private NmosLowSideSwitchFamilyState state(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof NmosLowSideSwitchFamilyState))
            throw new IllegalStateException("NMOS family state is missing");
        return (NmosLowSideSwitchFamilyState) instance.getFamilyState();
    }
}
