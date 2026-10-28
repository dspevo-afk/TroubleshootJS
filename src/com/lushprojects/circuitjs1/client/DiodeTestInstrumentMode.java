package com.lushprojects.circuitjs1.client;

final class DiodeTestInstrumentMode extends AbstractInstrumentModeStrategy {
    private static final double MINIMUM_CURRENT = .00001;
    private static final double COMPLIANCE_THRESHOLD = 2.95;

    DiodeTestInstrumentMode() {
        super("DIODE", "DIODE", "--- V", 4,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.UNPOWERED_ONLY, true);
    }

    public void deactivate(InstrumentController controller) {
        getState().setRefreshPending(false);
        getState().setPrimaryValue(Double.NaN);
        getState().setSecondaryValue(Double.NaN);
        getState().setDisplayText(getInitialDisplay());
        controller.finishActiveMeasurementForStrategy();
    }

    public void refresh(InstrumentController controller) {
        getState().setPrimaryValue(Double.NaN);
        getState().setSecondaryValue(Double.NaN);
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
        if (!getState().isRefreshPending())
            return;
        if (!controller.isMeasurementAllowedForStrategy(this, red, black)) {
            keepWaitingIfNecessary(controller, red, black);
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        getState().setRefreshPending(false);
        DiodeMeasurementResult result = controller.measureDiodeForStrategy(this, red, black);
        getState().incrementMeasurementCount();
        controller.validateTargetsForStrategy();
        red = controller.getRedProbeForStrategy();
        black = controller.getBlackProbeForStrategy();
        boolean completed = isCompletedFiniteResult(result);
        if (red == null || black == null ||
                !controller.isMeasurementAllowedForStrategy(this, red, black) &&
                !(completed && controller.canDisplayCompletedMeasurementForStrategy(this, red, black))) {
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        if (!completed) {
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        // OL is a completed electrical observation, but its voltage is not a
        // valid forward-drop value for downstream consumers.
        getState().setPrimaryValue(primaryValueForResult(result));
        getState().setSecondaryValue(result.current);
    }

    public void display(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        double voltage = getState().getPrimaryValue();
        double current = getState().getSecondaryValue();
        String text;
        if (red == null || black == null) {
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            text = getInitialDisplay();
        } else if (!controller.isMeasurementAllowedForStrategy(this, red, black) &&
                !(hasCompletedFiniteReading() &&
                    controller.canDisplayCompletedMeasurementForStrategy(this, red, black))) {
            text = controller.getActiveMeasurementReadinessForStrategy(red, black).getDisplayText();
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
        } else if (hasCompletedFiniteReading() && !isUsableResult(voltage, current))
            text = "OL";
        else if (Double.isNaN(voltage) || Double.isNaN(current) ||
                Double.isInfinite(voltage) || Double.isInfinite(current))
            text = "OL";
        else
            text = CircuitElm.getVoltageText(voltage);
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
        ActiveMeasurementReadiness readiness = controller.getActiveMeasurementReadinessForStrategy(red, black);
        controller.setInstrumentStatusForStrategy(readiness == ActiveMeasurementReadiness.DISCHARGE &&
                hasCompletedFiniteReading() &&
                controller.canDisplayCompletedMeasurementForStrategy(this, red, black) ?
            "Reading held; discharge first" : "");
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        ActiveMeasurementReadiness readiness = controller.getActiveMeasurementReadinessForStrategy(
            controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
        if (getState().isRefreshPending() &&
                (didAnalyze || readiness == ActiveMeasurementReadiness.WAITING ||
                readiness == ActiveMeasurementReadiness.DISCHARGE ||
                (readiness == ActiveMeasurementReadiness.READY &&
                    controller.getRedProbeForStrategy() != null &&
                    controller.getBlackProbeForStrategy() != null)))
            controller.updateReadingForStrategy();
        else if (hasCompletedFiniteReading())
            display(controller);
    }

    private boolean hasCompletedFiniteReading() {
        double current = getState().getSecondaryValue();
        return !Double.isNaN(current) && !Double.isInfinite(current);
    }

    private void keepWaitingIfNecessary(InstrumentController controller, ProbeTarget red,
            ProbeTarget black) {
        ActiveMeasurementReadiness readiness =
            controller.getActiveMeasurementReadinessForStrategy(red, black);
        boolean refreshPending = readiness == ActiveMeasurementReadiness.WAITING ||
            readiness == ActiveMeasurementReadiness.DISCHARGE;
        getState().setRefreshPending(refreshPending);
    }

    static boolean isCompletedFiniteResult(DiodeMeasurementResult result) {
        return result != null && !Double.isNaN(result.voltage) && !Double.isInfinite(result.voltage) &&
            !Double.isNaN(result.current) && !Double.isInfinite(result.current);
    }

    static boolean isUsableResult(DiodeMeasurementResult result) {
        return isCompletedFiniteResult(result) && isUsableResult(result.voltage, result.current);
    }

    static double primaryValueForResult(DiodeMeasurementResult result) {
        return isUsableResult(result) ? result.voltage : Double.NaN;
    }

    private static boolean isUsableResult(double voltage, double current) {
        return !Double.isNaN(voltage) && !Double.isInfinite(voltage) &&
            !Double.isNaN(current) && !Double.isInfinite(current) &&
            current >= MINIMUM_CURRENT && voltage < COMPLIANCE_THRESHOLD;
    }
}
