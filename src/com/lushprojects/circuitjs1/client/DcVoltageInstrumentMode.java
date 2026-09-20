package com.lushprojects.circuitjs1.client;

final class DcVoltageInstrumentMode extends AbstractInstrumentModeStrategy {
    private boolean refreshPending;
    private VoltageMeasurementResult latest;

    DcVoltageInstrumentMode() {
        super("DC_VOLTAGE", "DC V", "--- V", 1,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true);
    }

    public void refresh(InstrumentController controller) {
        latest = null;
        getState().setPrimaryValue(Double.NaN);
        refreshPending = true;
        getState().setRefreshPending(true);
        getState().setDisplayText(getInitialDisplay());
        controller.setInstrumentDisplayForStrategy(getInitialDisplay());
    }

    public void measure(InstrumentController controller) {
        if (controller.getRedProbeForStrategy() == null ||
                controller.getBlackProbeForStrategy() == null) {
            latest = null;
            refreshPending = false;
            getState().setRefreshPending(false);
            getState().setPrimaryValue(Double.NaN);
            return;
        }
        if (!refreshPending)
            return;
        refreshPending = false;
        getState().setRefreshPending(false);
        latest = controller.measureDcVoltageResultForStrategy(
            controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
        getState().setPrimaryValue(latest.isNumeric() ? latest.getValue() : Double.NaN);
        getState().incrementMeasurementCount();
        controller.validateTargetsForStrategy();
    }

    public void display(InstrumentController controller) {
        String text = format(controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        /* A power/topology lifecycle refresh may complete an accepted solver
         * step without toggling the public analysis flag.  Queue exactly one
         * post-step passive DMM capture either way; never call it recursively
         * from this callback. */
        if (refreshPending)
            controller.requestDeferredMeasurementUpdateForStrategy(this);
    }

    private String format(ProbeTarget red, ProbeTarget black) {
        if (red == null || black == null || latest == null)
            return getInitialDisplay();
        switch (latest.getStatus()) {
        case OK:
        case NO_SIGNAL:
            return CircuitElm.getVoltageText(latest.getValue());
        case OVER_RANGE:
            return "OL V";
        case REFERENCE_REJECTED:
        case REFERENCE_UNPROVEN:
            return "REF? V";
        default:
            return getInitialDisplay();
        }
    }
}
