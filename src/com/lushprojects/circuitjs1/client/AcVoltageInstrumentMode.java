package com.lushprojects.circuitjs1.client;

/** Differential, AC-coupled true-RMS meter backed by a finite 10 Mohm load. */
final class AcVoltageInstrumentMode extends AbstractInstrumentModeStrategy {
    private boolean refreshPending;
    private VoltageMeasurementResult latest;

    AcVoltageInstrumentMode() {
        super("AC_VOLTAGE", "AC V", "--- V RMS", 5,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true);
    }

    public void refresh(InstrumentController controller) {
        latest = null;
        refreshPending = true;
        getState().setPrimaryValue(Double.NaN);
        getState().setRefreshPending(true);
        getState().setDisplayText(getInitialDisplay());
        controller.setInstrumentDisplayForStrategy(getInitialDisplay());
    }

    public void measure(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        if (red == null || black == null) {
            /* Probe changes explicitly refresh this strategy. Do not keep a
             * missing-probe request pending across every solver frame. */
            refreshPending = false;
            getState().setRefreshPending(false);
            getState().setPrimaryValue(Double.NaN);
            return;
        }
        if (!refreshPending)
            return;
        refreshPending = false;
        getState().setRefreshPending(false);
        latest = controller.measureAcVoltageForStrategy(red, black);
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
        if (refreshPending)
            controller.updateReadingForStrategy();
    }

    private String format(ProbeTarget red, ProbeTarget black) {
        if (red == null || black == null || latest == null)
            return getInitialDisplay();
        switch (latest.getStatus()) {
        case OK:
            return CircuitElm.getVoltageText(latest.getValue()) + " RMS";
        case NO_SIGNAL:
            return "0 V RMS";
        case OVER_RANGE:
            return "OL RMS";
        case REFERENCE_REJECTED:
        case REFERENCE_UNPROVEN:
            return "REF? RMS";
        case GAP:
            return "GAP RMS";
        case BANDWIDTH_LIMITED:
            return "BW RMS";
        case ALIASED:
            return "ALIAS RMS";
        case INSUFFICIENT_WINDOW:
            return "WINDOW RMS";
        case NUMERICAL_LIMITED:
            return "MATH RMS";
        case UNAVAILABLE:
        default:
            return getInitialDisplay();
        }
    }
}
