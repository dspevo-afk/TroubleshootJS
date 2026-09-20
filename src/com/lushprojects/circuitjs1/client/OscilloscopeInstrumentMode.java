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
    private ProbeTarget boundRed;
    private ProbeTarget boundBlack;
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
        stop(controller);
        getState().clearMeasurement();
    }

    public void onProbeChanged(InstrumentController controller) {
        stop(controller);
        frequency = null;
        reference = null;
    }

    public void refresh(InstrumentController controller) {
        ensureSubscription(controller);
        analyze();
        controller.configureScopeControlsForStrategy(timeLabel(), voltageLabel(), triggerLabel());
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
            double triggerTime = latestTrigger(values, frequency == null ? 0 : frequency.getDcMean());
            if (finite(triggerTime))
                viewStart = triggerTime - capture * .2;
        }
        controller.renderScopeTraceForStrategy(values, viewStart, capture,
            voltsPerDivision(), timeLabel() + " " + voltageLabel() + " " + triggerLabel(),
            scopeStatus(), frequency != null && frequency.isOk());
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

    private void ensureSubscription(InstrumentController controller) {
        ProbeTarget red = controller.getRedProbeForStrategy();
        ProbeTarget black = controller.getBlackProbeForStrategy();
        if (red == null || black == null) {
            stop(controller);
            reference = null;
            return;
        }
        reference = controller.assessDifferentialReferenceForStrategy(red, black);
        if (!admits(reference)) {
            stop(controller);
            return;
        }
        if (samples != null && same(boundRed, red) && same(boundBlack, black))
            return;
        stop(controller);
        samples = controller.observeDifferentialVoltageForStrategy(red, black);
        if (samples != null) {
            boundRed = red;
            boundBlack = black;
        }
    }

    private void stop(InstrumentController controller) {
        if (samples != null)
            controller.stopObservingDifferentialVoltageForStrategy(samples);
        samples = null;
        boundRed = null;
        boundBlack = null;
    }

    private void analyze() {
        if (samples == null) {
            frequency = null;
            getState().setPrimaryValue(Double.NaN);
            getState().setSecondaryValue(Double.NaN);
            return;
        }
        frequency = SignalMeasurementAnalysis.measureFrequency(samples.snapshotWindow(), policy());
        getState().setPrimaryValue(frequency.isOk() ? frequency.getValue() : Double.NaN);
        getState().setSecondaryValue(frequency.getDcMean());
    }

    private SignalMeasurementAnalysis.Policy policy() {
        double time = timePerDivision();
        return new SignalMeasurementAnalysis.Policy(16, time * 4, time * .5,
            1 / (time * 4), CirSim.VOLTAGE_MEASUREMENT_MAXIMUM_ABS, 1e-6, trigger);
    }

    private String displayText() {
        if (boundRed == null || boundBlack == null)
            return getInitialDisplay();
        if (!admits(reference))
            return "SCOPE: REF?";
        return "SCOPE: " + scopeStatus();
    }

    private String scopeStatus() {
        if (frequency == null)
            return "WINDOW";
        switch (frequency.getStatus()) {
        case OK:
            return CircuitElm.getUnitText(frequency.getValue(), "Hz");
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
