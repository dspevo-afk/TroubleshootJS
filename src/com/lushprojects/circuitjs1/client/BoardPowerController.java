package com.lushprojects.circuitjs1.client;

class BoardPowerController {
    private BoardPowerState state = BoardPowerState.POWERED;
    private GeneratedExternalPowerBindings powerBindings;

    BoardPowerState getState() {
        return state;
    }

    GeneratedExternalPowerBindings getBindingsForDeveloperVerification() {
        return powerBindings;
    }

    void restoreForDeveloperVerification(GeneratedExternalPowerBindings bindings,
            BoardPowerState savedState, GeneratedExternalPowerBindings.SavedControls controls) {
        if (bindings == null || savedState == null || controls == null)
            throw new IllegalArgumentException("Missing board power snapshot");
        controls.restore(bindings);
        powerBindings = bindings;
        state = savedState;
    }

    boolean setState(BoardPowerState state) {
    if (this.state == state && isStateEnforced(state))
        return false;
    this.state = state;
    if (powerBindings == null)
        return false;
    powerBindings.setConnected(state == BoardPowerState.POWERED);
    return true;
    }

    void attach(GeneratedExternalPowerBindings powerBindings) {
    if (powerBindings == null || !powerBindings.hasControlsForAllInputs())
        throw new IllegalArgumentException("Generated board requires controllable external power inputs");
    this.powerBindings = powerBindings;
    state = BoardPowerState.POWERED;
    powerBindings.setConnected(true);
    }

    void detach() {
    powerBindings = null;
    state = BoardPowerState.POWERED;
    }

    void setSourceConnected(String inputId, boolean connected) {
        if (powerBindings == null) throw new IllegalStateException("No current board power owner");
        powerBindings.getBinding(inputId).setConnected(connected);
        state = powerBindings.areAllDisconnected() ? BoardPowerState.UNPOWERED : BoardPowerState.POWERED;
    }

    java.util.Map<String,PowerOperatingAssessment.SourceState> getSourceStates() {
        return powerBindings == null ?
            java.util.Collections.<String,PowerOperatingAssessment.SourceState>emptyMap() : powerBindings.getSourceStates();
    }

    boolean isElectricallyUnpowered() {
    return state == BoardPowerState.UNPOWERED && powerBindings != null &&
        powerBindings.hasControlsForAllInputs() && powerBindings.areAllDisconnected();
    }

    private boolean isStateEnforced(BoardPowerState state) {
    if (powerBindings == null || !powerBindings.hasControlsForAllInputs())
        return false;
    return state == BoardPowerState.POWERED ? powerBindings.areAllConnected() :
        powerBindings.areAllDisconnected();
    }
}
