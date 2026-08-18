package com.lushprojects.circuitjs1.client;

final class DiodeTestInstrumentMode extends AbstractInstrumentModeStrategy {
    private static final double MINIMUM_CURRENT = .00001;
    private static final double COMPLIANCE_THRESHOLD = 2.95;
    private boolean refreshPending;

    DiodeTestInstrumentMode() {
        super("DIODE", "DIODE", "--- V", 4,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.UNPOWERED_ONLY, true);
    }

    public void deactivate(InstrumentController controller) {
        controller.finishActiveMeasurementForStrategy();
    }

    public void refresh(InstrumentController controller) {
        getState().setPrimaryValue(Double.NaN);
        getState().setSecondaryValue(Double.NaN);
        refreshPending = true;
        getState().setRefreshPending(true);
        getState().setDisplayText(getInitialDisplay());
        controller.setInstrumentDisplayForStrategy(getInitialDisplay());
    }

    public void measure(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        if (red == null || black == null) {
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        if (!refreshPending)
            return;
        if (!controller.isMeasurementAllowedForStrategy(this, red, black)) {
            keepWaitingIfNecessary(controller, red, black);
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        refreshPending = false;
        getState().setRefreshPending(false);
        DiodeMeasurementResult result = controller.measureDiodeForStrategy(red, black);
        getState().incrementMeasurementCount();
        controller.validateTargetsForStrategy();
        red = controller.getRedProbeForStrategy();
        black = controller.getBlackProbeForStrategy();
        if (red == null || black == null ||
                !controller.isMeasurementAllowedForStrategy(this, red, black)) {
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        if (result == null || Double.isNaN(result.voltage) || Double.isInfinite(result.voltage) ||
                Double.isNaN(result.current) || Double.isInfinite(result.current) ||
                result.current < MINIMUM_CURRENT || result.voltage >= COMPLIANCE_THRESHOLD) {
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(result == null ? Double.NaN : result.current);
            return;
        }
        getState().setPrimaryValue(result.voltage);
        getState().setSecondaryValue(result.current);
    }

    public void display(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        double voltage = getState().getPrimaryValue();
        double current = getState().getSecondaryValue();
        String text;
        if (red == null || black == null)
            text = getInitialDisplay();
        else if (!controller.isMeasurementAllowedForStrategy(this, red, black))
            text = controller.getActiveMeasurementReadinessForStrategy(red, black).getDisplayText();
        else if (Double.isNaN(voltage) || Double.isNaN(current) ||
                Double.isInfinite(voltage) || Double.isInfinite(current))
            text = "OL";
        else
            text = CircuitElm.getVoltageText(voltage);
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        ActiveMeasurementReadiness readiness = controller.getActiveMeasurementReadinessForStrategy(
            controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
        if (refreshPending && (didAnalyze || readiness == ActiveMeasurementReadiness.WAITING ||
                readiness == ActiveMeasurementReadiness.DISCHARGE))
            controller.updateReadingForStrategy();
    }

    private void keepWaitingIfNecessary(InstrumentController controller, ProbeTarget red,
            ProbeTarget black) {
        ActiveMeasurementReadiness readiness =
            controller.getActiveMeasurementReadinessForStrategy(red, black);
        refreshPending = readiness == ActiveMeasurementReadiness.WAITING ||
            readiness == ActiveMeasurementReadiness.DISCHARGE;
        getState().setRefreshPending(refreshPending);
    }
}
