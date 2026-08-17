package com.lushprojects.circuitjs1.client;

final class DcVoltageInstrumentMode extends AbstractInstrumentModeStrategy {
    private boolean refreshPending;

    DcVoltageInstrumentMode() {
        super("DC_VOLTAGE", "DC V", "--- V", 1,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true);
    }

    public void refresh(InstrumentController controller) {
        getState().setPrimaryValue(Double.NaN);
        refreshPending = true;
        getState().setRefreshPending(true);
        getState().setDisplayText(getInitialDisplay());
        controller.setInstrumentDisplayForStrategy(getInitialDisplay());
    }

    public void measure(InstrumentController controller) {
        if (controller.getRedProbeForStrategy() == null ||
                controller.getBlackProbeForStrategy() == null) {
            getState().setPrimaryValue(Double.NaN);
            return;
        }
        if (!refreshPending)
            return;
        refreshPending = false;
        getState().setRefreshPending(false);
        getState().setPrimaryValue(controller.measureDcVoltageForStrategy(
            controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy()));
        getState().incrementMeasurementCount();
        controller.validateTargetsForStrategy();
    }

    public void display(InstrumentController controller) {
        double voltage = getState().getPrimaryValue();
        String text = controller.getRedProbeForStrategy() == null ||
            controller.getBlackProbeForStrategy() == null || Double.isNaN(voltage) ?
            getInitialDisplay() : CircuitElm.getVoltageText(voltage);
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        if (didAnalyze && refreshPending)
            controller.updateReadingForStrategy();
    }
}
