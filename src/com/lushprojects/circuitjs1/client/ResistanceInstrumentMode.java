package com.lushprojects.circuitjs1.client;

final class ResistanceInstrumentMode extends AbstractInstrumentModeStrategy {
    static final double MAX_RESISTANCE = 10000000;
    static final double CONTINUITY_THRESHOLD_OHMS = 50;
    private final boolean continuity;

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
        controller.finishActiveMeasurementForStrategy();
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
        if (!getState().isRefreshPending())
            return;
        if (!controller.isMeasurementAllowedForStrategy(this, red, black)) {
            keepWaitingIfNecessary(controller, red, black);
            getState().setPrimaryValue(Double.NaN);
            getState().setContinuityDetected(false);
            return;
        }
        getState().setRefreshPending(false);
        getState().setPrimaryValue(controller.measureResistanceForStrategy(red, black));
        getState().incrementMeasurementCount();
        controller.validateTargetsForStrategy();
        red = controller.getRedProbeForStrategy();
        black = controller.getBlackProbeForStrategy();
        boolean completed = !Double.isNaN(getState().getPrimaryValue()) && getState().getPrimaryValue() >= 0;
        if (red == null || black == null ||
                !controller.isMeasurementAllowedForStrategy(this, red, black) &&
                !(completed && !continuity && controller.canDisplayCompletedResistanceForStrategy(this, red, black))) {
            getState().setPrimaryValue(Double.NaN);
            getState().setContinuityDetected(false);
        }
    }

    public void display(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        String text;
        String status = "";
        double resistance = getState().getPrimaryValue();
        if (red == null || black == null) {
            text = getInitialDisplay();
            getState().setContinuityDetected(false);
        } else if (!controller.isMeasurementAllowedForStrategy(this, red, black) &&
                !(resistance >= 0 && !continuity &&
                    controller.canDisplayCompletedResistanceForStrategy(this, red, black))) {
            text = controller.getActiveMeasurementReadinessForStrategy(red, black).getDisplayText();
            getState().setContinuityDetected(false);
        } else if (Double.isNaN(resistance)) {
            text = getInitialDisplay();
            getState().setContinuityDetected(false);
        } else if (Double.isInfinite(resistance) ||
                resistance > MAX_RESISTANCE) {
            text = "OL";
            getState().setContinuityDetected(false);
        } else {
            text = CircuitElm.getUnitText(resistance, "Ohm");
            getState().setContinuityDetected(continuity &&
                resistance <= CONTINUITY_THRESHOLD_OHMS);
        }
        if (!continuity && resistance >= 0 &&
                controller.getActiveMeasurementReadinessForStrategy(red, black) == ActiveMeasurementReadiness.DISCHARGE &&
                controller.canDisplayCompletedResistanceForStrategy(this, red, black))
            status = "Reading held; discharge first";
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
        controller.setInstrumentStatusForStrategy(status);
        controller.setContinuityFeedbackForStrategy(getState().isContinuityDetected());
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        ActiveMeasurementReadiness readiness = controller.getActiveMeasurementReadinessForStrategy(
            controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
        if (getState().isRefreshPending() && (didAnalyze || readiness == ActiveMeasurementReadiness.WAITING ||
                readiness == ActiveMeasurementReadiness.DISCHARGE ||
                (readiness == ActiveMeasurementReadiness.READY &&
                 controller.getRedProbeForStrategy() != null &&
                 controller.getBlackProbeForStrategy() != null)))
            controller.updateReadingForStrategy();
        else if (!continuity && getState().getPrimaryValue() >= 0)
            display(controller);
    }

    private void keepWaitingIfNecessary(InstrumentController controller, ProbeTarget red,
            ProbeTarget black) {
        ActiveMeasurementReadiness readiness =
            controller.getActiveMeasurementReadinessForStrategy(red, black);
        getState().setRefreshPending(readiness == ActiveMeasurementReadiness.WAITING ||
            readiness == ActiveMeasurementReadiness.DISCHARGE);
    }
}
