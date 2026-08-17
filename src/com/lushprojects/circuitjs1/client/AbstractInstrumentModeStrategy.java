package com.lushprojects.circuitjs1.client;

/** Shared metadata/lifecycle defaults for provider implementations. */
abstract class AbstractInstrumentModeStrategy implements InstrumentModeProvider {
    private final InstrumentMode mode;
    private final String label;
    private final String initialDisplay;
    private final int developerCode;
    private final InstrumentProbeRequirements probeRequirements;
    private final InstrumentPowerPolicy powerPolicy;
    private final boolean playerVisible;
    private final InstrumentModeState state;

    AbstractInstrumentModeStrategy(String id, String label, String initialDisplay,
            int developerCode, InstrumentProbeRequirements probeRequirements,
            InstrumentPowerPolicy powerPolicy, boolean playerVisible) {
        mode = new InstrumentMode(id);
        this.label = label;
        this.initialDisplay = initialDisplay;
        this.developerCode = developerCode;
        this.probeRequirements = probeRequirements;
        this.powerPolicy = powerPolicy;
        this.playerVisible = playerVisible;
        state = new InstrumentModeState(initialDisplay);
    }

    public InstrumentMode getMode() { return mode; }
    public String getId() { return mode.getId(); }
    public String getLabel() { return label; }
    public String getInitialDisplay() { return initialDisplay; }
    public int getDeveloperCode() { return developerCode; }
    public InstrumentProbeRequirements getProbeRequirements() { return probeRequirements; }
    public InstrumentPowerPolicy getPowerPolicy() { return powerPolicy; }
    public boolean isPlayerVisible() { return playerVisible; }
    public InstrumentModeState getState() { return state; }

    public void activate(InstrumentController controller) { }
    public void deactivate(InstrumentController controller) { }
    public void onProbeChanged(InstrumentController controller) { }
    public void refresh(InstrumentController controller) { }
    public void measure(InstrumentController controller) { }
    public void display(InstrumentController controller) {
        controller.setInstrumentDisplayForStrategy(initialDisplay);
    }
    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) { }

    protected void resetState() {
        state.clearMeasurement();
        state.setDisplayText(initialDisplay);
    }
}
