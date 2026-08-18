package com.lushprojects.circuitjs1.client;

final class ResistanceInstrumentMode extends AbstractInstrumentModeStrategy {
    static final double MAX_RESISTANCE = 10000000;
    static final double CONTINUITY_THRESHOLD_OHMS = 50;
    private final boolean continuity;
    private boolean refreshPending;

    ResistanceInstrumentMode(String id, String label, int developerCode, boolean continuity) {
        super(id, label, "--- Ohm", developerCode,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.UNPOWERED_ONLY, true);
        this.continuity = continuity;
    }

    boolean isContinuityProvider() {
        return continuity;
    }

    public void activate(InstrumentController controller) {
        if (continuity)
            controller.prepareContinuityFeedbackForStrategy();
    }

    public void deactivate(InstrumentController controller) {
        if (continuity) {
            getState().setContinuityDetected(false);
            controller.setContinuityFeedbackForStrategy(false);
        }
    }

    public void onProbeChanged(InstrumentController controller) {
        if (continuity)
            controller.prepareContinuityFeedbackForStrategy();
    }

    public void refresh(InstrumentController controller) {
        getState().setPrimaryValue(Double.NaN);
        getState().setContinuityDetected(false);
        refreshPending = true;
        getState().setRefreshPending(true);
        getState().setDisplayText(getInitialDisplay());
        controller.setInstrumentDisplayForStrategy(getInitialDisplay());
        controller.setContinuityFeedbackForStrategy(false);
    }

    public void measure(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        if (red == null || black == null) {
            getState().setPrimaryValue(Double.NaN);
            getState().setContinuityDetected(false);
            return;
        }
        if (!refreshPending)
            return;
        if (!controller.isMeasurementAllowedForStrategy(this, red, black)) {
            keepWaitingIfNecessary(controller, red, black);
            getState().setPrimaryValue(Double.NaN);
            getState().setContinuityDetected(false);
            return;
        }
        refreshPending = false;
        getState().setRefreshPending(false);
        getState().setPrimaryValue(controller.measureResistanceForStrategy(red, black));
        getState().incrementMeasurementCount();
        controller.validateTargetsForStrategy();
        red = controller.getRedProbeForStrategy();
        black = controller.getBlackProbeForStrategy();
        if (red == null || black == null ||
                !controller.isMeasurementAllowedForStrategy(this, red, black)) {
            getState().setPrimaryValue(Double.NaN);
            getState().setContinuityDetected(false);
        }
    }

    public void display(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        String text;
        double resistance = getState().getPrimaryValue();
        if (red == null || black == null) {
            text = getInitialDisplay();
            getState().setContinuityDetected(false);
        } else if (!controller.isMeasurementAllowedForStrategy(this, red, black)) {
            text = controller.getActiveMeasurementReadinessForStrategy(red, black).getDisplayText();
            getState().setContinuityDetected(false);
        } else if (Double.isNaN(resistance) || Double.isInfinite(resistance) ||
                resistance > MAX_RESISTANCE) {
            text = "OL";
            getState().setContinuityDetected(false);
        } else {
            text = CircuitElm.getUnitText(resistance, "Ohm");
            getState().setContinuityDetected(continuity &&
                resistance <= CONTINUITY_THRESHOLD_OHMS);
        }
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
        controller.setContinuityFeedbackForStrategy(getState().isContinuityDetected());
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
