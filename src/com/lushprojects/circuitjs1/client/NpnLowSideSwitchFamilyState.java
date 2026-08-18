package com.lushprojects.circuitjs1.client;

/** Family-owned control input state; the load response is always solver-derived. */
final class NpnLowSideSwitchFamilyState implements GeneratedBoardFamilyState {
    private final SwitchElm controlCommandSwitch;
    private boolean commandedOn = true;

    NpnLowSideSwitchFamilyState(SwitchElm controlCommandSwitch) {
        if (controlCommandSwitch == null)
            throw new IllegalArgumentException("Missing NPN control switch");
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
