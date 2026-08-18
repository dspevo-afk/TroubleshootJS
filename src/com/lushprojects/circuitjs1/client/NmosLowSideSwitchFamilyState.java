package com.lushprojects.circuitjs1.client;

/** Family-owned control command; load response remains solver-derived. */
final class NmosLowSideSwitchFamilyState implements GeneratedBoardFamilyState {
    private final SwitchElm controlCommandSwitch;
    private boolean commandedOn = true;

    NmosLowSideSwitchFamilyState(SwitchElm controlCommandSwitch) {
        if (controlCommandSwitch == null)
            throw new IllegalArgumentException("Missing NMOS control switch");
        this.controlCommandSwitch = controlCommandSwitch;
    }

    boolean isCommandedOn() { return commandedOn; }

    void setCommandedOn(CirSim sim, boolean on) {
        boolean closed = controlCommandSwitch.position == 0;
        if (closed != on)
            controlCommandSwitch.toggle();
        commandedOn = on;
        if (sim != null) {
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.runCircuit(true);
            sim.runCircuit(true);
        }
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }
}
