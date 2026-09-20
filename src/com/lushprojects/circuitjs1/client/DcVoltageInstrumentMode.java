package com.lushprojects.circuitjs1.client;

final class DcVoltageInstrumentMode extends AbstractInstrumentModeStrategy {
    private boolean refreshPending;
    private double nextCaptureAt = Double.NaN;
    private VoltageMeasurementResult latest;

    DcVoltageInstrumentMode() {
        super("DC_VOLTAGE", "DC V", "--- V", 1,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true);
    }

    public void refresh(InstrumentController controller) {
        latest = null;
        nextCaptureAt = Double.NaN;
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
            latest = controller.measureDcVoltageResultForStrategy(
                controller.getRedProbeForStrategy(), controller.getBlackProbeForStrategy());
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
        /* A power/topology lifecycle refresh may complete an accepted solver
         * step without toggling the public analysis flag.  Retained probes
         * also reacquire only after bounded accepted solver time, so a real
         * changing DC state cannot freeze after the initial finite-load read.
         * The callback only queues one outer-boundary transaction. */
        if (refreshPending)
            controller.requestDeferredMeasurementUpdateForStrategy(this);
        else if (VoltageMeasurementReacquisitionPolicy.isDue(nextCaptureAt,
                controller.getSimulationTimeForStrategy())) {
            refreshPending = true;
            getState().setRefreshPending(true);
            controller.requestDeferredMeasurementUpdateForStrategy(this);
        }
    }

    /** A failed finite-load transaction must not leave a predecessor voltage
     * visible as though it represented the current accepted solver graph. */
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
