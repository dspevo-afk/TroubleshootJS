package com.lushprojects.circuitjs1.client;

/**
 * One-channel, high-impedance differential scope.  Its trace comes directly
 * from accepted solver-time observations; drawing it never advances CircuitJS.
 */
final class OscilloscopeInstrumentMode extends AbstractInstrumentModeStrategy {
    private static final double[] TIME_PER_DIVISION = { .001, .005, .02, .1 };
    private static final double[] VOLTS_PER_DIVISION = { .05, .2, 1, 5, 20 };
    private int timeScaleIndex;
    private int voltageScaleIndex = 2;
    private SignalMeasurementAnalysis.Trigger trigger = SignalMeasurementAnalysis.Trigger.RISING;
    private SolverTimeObservationService.Subscription samples;
    /* Selection is presentation state. It remains visible for a rejected
     * reference even though no solver subscription may be active. */
    private ProbeTarget selectedRed;
    private ProbeTarget selectedBlack;
    /* These are the exact endpoints owned by the active subscription. */
    private ProbeTarget observedRed;
    private ProbeTarget observedBlack;
    private long subscriptionGeneration = -1;
    private SignalMeasurementAnalysis.Result waveform;
    private SignalMeasurementAnalysis.Result frequency;
    private MeasurementReferencePolicy.Result reference;

    OscilloscopeInstrumentMode() {
        super("SCOPE", "SCOPE", "SCOPE: PROBES", 6,
            new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                InstrumentProbePolarity.NEGATIVE),
            InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true);
    }

    public void activate(InstrumentController controller) {
        refresh(controller);
    }

    public void deactivate(InstrumentController controller) {
        stopSubscription(controller);
        clearSelection();
        clearAnalysis();
        getState().clearMeasurement();
    }

    public void onProbeChanged(InstrumentController controller) {
        stopSubscription(controller);
        selectedRed = controller.getRedProbeForStrategy();
        selectedBlack = controller.getBlackProbeForStrategy();
        clearAnalysis();
        reference = null;
    }

    public void refresh(InstrumentController controller) {
        ensureSubscription(controller);
        analyze();
        controller.configureScopeControlsForStrategy(timeLabel(), voltageLabel(), triggerLabel());
        /* Refresh calls from topology/power lifecycle owners do not always
         * reach InstrumentController.updateReading(). Clear stale text/trace
         * now; this remains a read-only projection of existing samples. */
        display(controller);
    }

    public void measure(InstrumentController controller) {
        ensureSubscription(controller);
        analyze();
    }

    public void display(InstrumentController controller) {
        String text = displayText();
        getState().setDisplayText(text);
        controller.setInstrumentDisplayForStrategy(text);
        SolverTimeSample[] values = samples == null ? new SolverTimeSample[0] : samples.snapshot();
        double capture = timePerDivision() * 10;
        double viewStart = Double.NaN;
        if (values.length > 0) {
            double latest = values[values.length - 1].getTime();
            viewStart = latest - capture;
            double triggerTime = latestTrigger(values, waveform == null ? Double.NaN :
                waveform.getDcMean());
            if (finite(triggerTime))
                viewStart = triggerTime - capture * .2;
        }
        controller.renderScopeTraceForStrategy(values, viewStart, capture,
            voltsPerDivision(), timeLabel() + " " + voltageLabel() + " " + triggerLabel(),
            scopeStatus(), policy().maximumGap, isWaveformDrawable());
    }

    public void onSimulationStepComplete(InstrumentController controller, boolean didAnalyze) {
        if (samples != null)
            controller.updateReadingForStrategy();
    }

    public void draw(InstrumentController controller, Graphics graphics) {
        /* The workbench meter owns a native canvas above its opaque display.
         * Strategy display() projects accepted samples there; never paint a
         * duplicate waveform on the board canvas beneath the meter shell. */
    }

    void cycleTimeScale(InstrumentController controller) {
        timeScaleIndex = (timeScaleIndex + 1) % TIME_PER_DIVISION.length;
        refresh(controller);
        controller.updateReadingForStrategy();
    }

    void cycleVoltageScale(InstrumentController controller) {
        voltageScaleIndex = (voltageScaleIndex + 1) % VOLTS_PER_DIVISION.length;
        refresh(controller);
        controller.updateReadingForStrategy();
    }

    void cycleTrigger(InstrumentController controller) {
        switch (trigger) {
        case RISING: trigger = SignalMeasurementAnalysis.Trigger.FALLING; break;
        case FALLING: trigger = SignalMeasurementAnalysis.Trigger.ANY; break;
        case ANY:
        default: trigger = SignalMeasurementAnalysis.Trigger.RISING; break;
        }
        refresh(controller);
        controller.updateReadingForStrategy();
    }

    SolverTimeObservationService.Subscription getSubscriptionForDeveloperVerification() {
        return samples;
    }

    private void ensureSubscription(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        if (red == null || black == null) {
            stopSubscription(controller);
            clearSelection();
            reference = null;
            return;
        }
        selectedRed = red;
        selectedBlack = black;
        reference = controller.assessDifferentialReferenceForStrategy(red, black);
        if (!admits(reference)) {
            /* Reference rejection occurs before any subscription. Keep the
             * physical selection so the player sees REF? rather than PROBES. */
            stopSubscription(controller);
            return;
        }
        if (samples != null && same(observedRed, red) && same(observedBlack, black) &&
                samples.getGeneration() == subscriptionGeneration)
            return;
        stopSubscription(controller);
        samples = controller.observeDifferentialVoltageForStrategy(red, black);
        if (samples != null) {
            observedRed = red;
            observedBlack = black;
            subscriptionGeneration = samples.getGeneration();
        }
    }

    private void stopSubscription(InstrumentController controller) {
        if (samples != null)
            controller.stopObservingDifferentialVoltageForStrategy(samples);
        samples = null;
        observedRed = null;
        observedBlack = null;
        subscriptionGeneration = -1;
    }

    private void clearSelection() {
        selectedRed = null;
        selectedBlack = null;
    }

    private void clearAnalysis() {
        waveform = null;
        frequency = null;
    }

    private void analyze() {
        if (samples == null) {
            clearAnalysis();
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        SolverTimeWindow window = samples.snapshotWindow();
        SignalMeasurementAnalysis.Policy policy = policy();
        waveform = SignalMeasurementAnalysis.measureDcMean(window, policy);
        frequency = SignalMeasurementAnalysis.measureFrequency(window, policy);
        getState().setPrimaryValue(frequency.isOk() ? frequency.getValue() : Double.NaN);
        getState().setSecondaryValue(waveform.isOk() ? waveform.getDcMean() :
            frequency.getDcMean());
    }

    private SignalMeasurementAnalysis.Policy policy() {
        double time = timePerDivision();
        return new SignalMeasurementAnalysis.Policy(16, time * 4, time * .5,
            1 / (time * 4), CirSim.VOLTAGE_MEASUREMENT_MAXIMUM_ABS, 1e-6, trigger);
    }

    private String displayText() {
        return displayTextForContract(selectedRed != null && selectedBlack != null,
            reference, waveform, frequency);
    }

    private String scopeStatus() {
        return scopeStatusForContract(waveform, frequency);
    }

    /** Shared presentation rule: selected invalid-reference probes are not
     * mistaken for missing probes just because no subscription was created. */
    static String displayTextForContract(boolean hasSelectedProbes,
            MeasurementReferencePolicy.Result reference,
            SignalMeasurementAnalysis.Result waveform,
            SignalMeasurementAnalysis.Result frequency) {
        if (!hasSelectedProbes)
            return "SCOPE: PROBES";
        if (!admits(reference))
            return "SCOPE: REF?";
        return "SCOPE: " + scopeStatusForContract(waveform, frequency);
    }

    static String scopeStatusForContract(SignalMeasurementAnalysis.Result waveform,
            SignalMeasurementAnalysis.Result frequency) {
        if (waveform == null)
            return "WINDOW";
        if (!waveform.isOk())
            return statusTextForContract(waveform);
        if (frequency == null)
            return "WINDOW";
        if (frequency.getStatus() == SignalMeasurementAnalysis.Status.INSUFFICIENT_WINDOW)
            return "FREQ?";
        return statusTextForContract(frequency);
    }

    private static String statusTextForContract(SignalMeasurementAnalysis.Result result) {
        if (result == null)
            return "WINDOW";
        switch (result.getStatus()) {
        case OK:
            return CircuitElm.getUnitText(result.getValue(), "Hz");
        case OVER_RANGE:
            return "OVER RANGE";
        case GAP:
            return "GAP";
        case BANDWIDTH_LIMITED:
            return "BANDWIDTH";
        case ALIASED:
            return "ALIASED";
        case NO_SIGNAL:
            return "NO SIGNAL";
        case NO_TRIGGER:
            return "NO TRIGGER";
        case NUMERICAL_LIMITED:
            return "MATH";
        case INSUFFICIENT_WINDOW:
        default:
            return "WINDOW";
        }
    }

    /**
     * Frequency extraction is an annotation, not a trace-validity proxy.
     * A flat DC trace and a one-shot with no second crossing are both useful
     * accepted waveforms. Unsafe sample acquisition, overrange, and explicitly
     * aliased/bandwidth-limited periodic content remain non-drawable.
     */
    private boolean isWaveformDrawable() {
        return permitsWaveformRendering(waveform, frequency);
    }

    /** Shared with the focused contract: rendering depends on a qualified
     * waveform, not on success at assigning it a periodic frequency. */
    static boolean permitsWaveformRendering(SignalMeasurementAnalysis.Result waveform,
            SignalMeasurementAnalysis.Result frequency) {
        if (waveform == null || !waveform.isOk() || frequency == null)
            return false;
        switch (frequency.getStatus()) {
        case OVER_RANGE:
        case GAP:
        case BANDWIDTH_LIMITED:
        case ALIASED:
        case NUMERICAL_LIMITED:
            return false;
        default:
            return true;
        }
    }

    private double latestTrigger(SolverTimeSample[] values, double mean) {
        if (values.length < 2 || !finite(mean))
            return Double.NaN;
        double result = Double.NaN;
        for (int i = 1; i < values.length; i++) {
            double a = values[i - 1].getValue() - mean;
            double b = values[i].getValue() - mean;
            if (!finite(a) || !finite(b) || a == b)
                continue;
            boolean rising = a <= 0 && b > 0;
            boolean falling = a >= 0 && b < 0;
            if ((trigger == SignalMeasurementAnalysis.Trigger.RISING && !rising) ||
                    (trigger == SignalMeasurementAnalysis.Trigger.FALLING && !falling) ||
                    (trigger == SignalMeasurementAnalysis.Trigger.ANY && !rising && !falling))
                continue;
            double fraction = -a / (b - a);
            double crossing = values[i - 1].getTime() + fraction *
                (values[i].getTime() - values[i - 1].getTime());
            if (finite(crossing)) result = crossing;
        }
        return result;
    }

    private String timeLabel() {
        return "T " + CircuitElm.getUnitText(timePerDivision(), "s") + "/div";
    }

    private String voltageLabel() {
        return "V " + CircuitElm.getUnitText(voltsPerDivision(), "V") + "/div";
    }

    private String triggerLabel() {
        return "TRIG " + (trigger == SignalMeasurementAnalysis.Trigger.RISING ? "↑" :
            trigger == SignalMeasurementAnalysis.Trigger.FALLING ? "↓" : "±");
    }

    private double timePerDivision() { return TIME_PER_DIVISION[timeScaleIndex]; }
    private double voltsPerDivision() { return VOLTS_PER_DIVISION[voltageScaleIndex]; }

    private static boolean same(ProbeTarget a, ProbeTarget b) {
        return a == b || (a != null && b != null && a.isSameTarget(b));
    }

    private static boolean admits(MeasurementReferencePolicy.Result result) {
        return result != null && (result.admitsReading() ||
            result.getDecision() == MeasurementReferencePolicy.Decision.NOT_APPLICABLE);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
