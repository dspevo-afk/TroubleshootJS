package com.lushprojects.circuitjs1.client;

/** Differential, AC-coupled true-RMS meter backed by a finite 10 Mohm load. */
final class AcVoltageInstrumentMode extends AbstractInstrumentModeStrategy {
    /* Reacquisition is governed by accepted CircuitJS time, not paint or wall
     * cadence.  One deferred transaction captures at most this same bounded
     * solver-time window before the next eligibility point. */
    private boolean refreshPending;
    private double nextCaptureAt = Double.NaN;
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
        nextCaptureAt = Double.NaN;
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
            nextCaptureAt = Double.NaN;
            getState().setRefreshPending(false);
            getState().setPrimaryValue(Double.NaN);
            return;
        }
        if (!refreshPending)
            return;
        refreshPending = false;
        getState().setRefreshPending(false);
        try {
            latest = controller.measureAcVoltageForStrategy(red, black);
            getState().setPrimaryValue(latest.isNumeric() ? latest.getValue() : Double.NaN);
            getState().incrementMeasurementCount();
            nextCaptureAt = VoltageMeasurementReacquisitionPolicy.nextDueAt(latest,
                controller.getSimulationTimeForStrategy());
            controller.validateTargetsForStrategy();
        } catch (RuntimeException failure) {
            retireFailedAcquisition(controller);
            throw failure;
        } catch (Error failure) {
            retireFailedAcquisition(controller);
            throw failure;
        }
    }

    public void display(InstrumentController controller) {
        String text = format(controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        if (refreshPending) {
            controller.requestDeferredMeasurementUpdateForStrategy(this);
            return;
        }
        if (VoltageMeasurementReacquisitionPolicy.isDue(nextCaptureAt,
                controller.getSimulationTimeForStrategy())) {
            refreshPending = true;
            getState().setRefreshPending(true);
            controller.requestDeferredMeasurementUpdateForStrategy(this);
        }
    }

    /** A failed solver transaction must never leave a previously accepted RMS
     * value visible as though it described the successor electrical state. */
    private void retireFailedAcquisition(InstrumentController controller) {
        latest = null;
        refreshPending = false;
        nextCaptureAt = Double.NaN;
        getState().setRefreshPending(false);
        getState().setPrimaryValue(Double.NaN);
        getState().setDisplayText(getInitialDisplay());
        controller.setInstrumentDisplayForStrategy(getInitialDisplay());
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
