package com.lushprojects.circuitjs1.client;

/**
 * Typed provider contract for one instrument mode.
 *
 * Implementations own their identity, UI metadata, probe semantics,
 * measurement state, and lifecycle.  The controller only supplies common
 * probe/UI context and dispatches these operations.
 */
interface InstrumentModeStrategy {
    InstrumentMode getMode();
    String getId();
    String getLabel();
    String getInitialDisplay();
    int getDeveloperCode();
    InstrumentProbeRequirements getProbeRequirements();
    InstrumentPowerPolicy getPowerPolicy();
    boolean isPlayerVisible();
    InstrumentModeState getState();

    void activate(InstrumentController controller);
    void deactivate(InstrumentController controller);
    void onProbeChanged(InstrumentController controller);
    void refresh(InstrumentController controller);
    void measure(InstrumentController controller);
    void display(InstrumentController controller);
    void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze);
}
