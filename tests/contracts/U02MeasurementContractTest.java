package com.lushprojects.circuitjs1.client;

/** Current pure U02 measurement contracts. */
public final class U02MeasurementContractTest {
    private static int assertions;

    public static void main(String[] args) {
        samplesAreFiniteAndChronological();
        rmsRemovesOffsetAndDcKeepsPolarity();
        boundedRetentionIsChronological();
        windowQualityAndOverRangeAreExplicit();
        acBandwidthSeparatesSampleAdequacyFromSignalContent();
        acTemporalCoverageAndIrregularStepsAreQualified();
        finiteWindowNearCutoffSinesRemainInBand();
        irregularNearCutoffSinesRespectCrossingUncertainty();
        irregularNearCutoffQualificationIsTimeOriginInvariant();
        voltageReacquisitionIsBoundedAndReferenceAware();
        frequencyUsesIrregularSolverTime();
        noSignalAndNoTriggerAreExplicit();
        System.out.println("PASS: U02 measurement contracts assertions=" + assertions);
    }

    private static void samplesAreFiniteAndChronological() {
        reject(new Runnable() { public void run() {
            new SolverTimeSample(Double.NaN, 1);
        }}, "nonfinite sample time rejected");
        reject(new Runnable() { public void run() {
            new SolverTimeSample(0, Double.POSITIVE_INFINITY);
        }}, "nonfinite sample value rejected");
        final SolverTimeWindow window = new SolverTimeWindow(4);
        window.append(1, 2);
        reject(new Runnable() { public void run() {
            window.append(1, 3);
        }}, "repeated solver time rejected");
        reject(new Runnable() { public void run() {
            window.append(.5, 3);
        }}, "backward solver time rejected");
    }

    private static void rmsRemovesOffsetAndDcKeepsPolarity() {
        SolverTimeWindow window = new SolverTimeWindow(4096);
        double offset = 1.25;
        double amplitude = 2.5;
        double frequency = 5;
        for (int i = 0; i <= 2000; i++) {
            double time = i / 1000.0;
            window.append(time, offset + amplitude * Math.sin(2 * Math.PI * frequency * time));
        }
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            20, 1, .02, 25, 5);
        SignalMeasurementAnalysis.Result ac =
            SignalMeasurementAnalysis.measureAcRms(window, policy);
        check(ac.getStatus() == SignalMeasurementAnalysis.Status.OK,
            "offset sine AC RMS is usable");
        check(close(ac.getValue(), amplitude / Math.sqrt(2), .002),
            "AC RMS removes DC offset");
        check(close(ac.getDcMean(), offset, .002),
            "AC RMS reports signed DC mean");

        SignalMeasurementAnalysis.Result dc =
            SignalMeasurementAnalysis.measureDcMean(window, policy);
        check(dc.getStatus() == SignalMeasurementAnalysis.Status.OK,
            "DC mean is usable");
        check(close(dc.getValue(), offset, .002),
            "DC mean is signed and offset-aware");

        SolverTimeWindow negative = new SolverTimeWindow(8);
        negative.append(0, -3.5);
        negative.append(1, -3.5);
        SignalMeasurementAnalysis.Result negativeDc =
            SignalMeasurementAnalysis.measureDcMean(negative);
        check(negativeDc.getStatus() == SignalMeasurementAnalysis.Status.OK &&
            close(negativeDc.getValue(), -3.5, 1e-12),
            "negative DC polarity is preserved");
    }

    private static void boundedRetentionIsChronological() {
        SolverTimeWindow window = new SolverTimeWindow(3);
        for (int i = 0; i < 5; i++) window.append(i, 10 + i);
        check(window.getCapacity() == 3 && window.size() == 3,
            "ring retention is bounded");
        check(window.get(0).getTime() == 2 && window.get(0).getValue() == 12,
            "oldest retained sample is chronological");
        check(window.get(1).getTime() == 3 && window.get(2).getTime() == 4,
            "retained samples remain ordered");
        SolverTimeSample[] copy = window.snapshot();
        copy[0] = new SolverTimeSample(99, 99);
        check(window.get(0).getTime() == 2,
            "snapshot array is defensive");
    }

    private static void windowQualityAndOverRangeAreExplicit() {
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            4, .5, .1, 20, 5);
        SolverTimeWindow shortWindow = new SolverTimeWindow(8);
        shortWindow.append(0, 1);
        shortWindow.append(.1, 1);
        SignalMeasurementAnalysis.Result insufficient =
            SignalMeasurementAnalysis.measureDcMean(shortWindow, policy);
        check(insufficient.getStatus() == SignalMeasurementAnalysis.Status.INSUFFICIENT_WINDOW,
            "sample and duration insufficiency is explicit");
        check(insufficient.getWindowAssessment().getSampleStatus() ==
                SolverTimeWindow.SampleStatus.INSUFFICIENT &&
            insufficient.getWindowAssessment().getDurationStatus() ==
                SolverTimeWindow.DurationStatus.INSUFFICIENT,
            "sample and duration statuses are separately retained");

        SolverTimeWindow gapWindow = new SolverTimeWindow(8);
        gapWindow.append(0, 1);
        gapWindow.append(.2, 1);
        gapWindow.append(.3, 1);
        gapWindow.append(.4, 1);
        gapWindow.append(.5, 1);
        gapWindow.append(.6, 1);
        SignalMeasurementAnalysis.Result gap =
            SignalMeasurementAnalysis.measureDcMean(gapWindow, policy);
        check(gap.getStatus() == SignalMeasurementAnalysis.Status.GAP,
            "maximum solver-time gap is explicit");
        check(gap.getWindowAssessment().getMaxGapStatus() ==
                SolverTimeWindow.MaxGapStatus.EXCEEDED,
            "maximum-gap status is explicit");

        SolverTimeWindow bandwidthWindow = new SolverTimeWindow(8);
        bandwidthWindow.append(0, 1);
        bandwidthWindow.append(.01, 1);
        bandwidthWindow.append(.02, 1);
        SignalMeasurementAnalysis.Policy bandwidthPolicy =
            new SignalMeasurementAnalysis.Policy(2, .01, Double.NaN, 100, 5);
        SignalMeasurementAnalysis.Result bandwidth =
            SignalMeasurementAnalysis.measureDcMean(bandwidthWindow, bandwidthPolicy);
        check(bandwidth.getStatus() == SignalMeasurementAnalysis.Status.BANDWIDTH_LIMITED,
            "declared bandwidth limit is explicit");
        check(bandwidth.getWindowAssessment().getBandwidthStatus() ==
                SolverTimeWindow.BandwidthStatus.LIMITED,
            "bandwidth status is explicit");

        SolverTimeWindow over = new SolverTimeWindow(8);
        over.append(0, 0);
        over.append(.1, 6);
        SignalMeasurementAnalysis.Result overRange =
            SignalMeasurementAnalysis.measureDcMean(over, policy);
        check(overRange.getStatus() == SignalMeasurementAnalysis.Status.OVER_RANGE,
            "declared voltage overrange is explicit");
        check(Double.isNaN(overRange.getValue()),
            "overrange does not expose a numeric reading");
    }

    private static void frequencyUsesIrregularSolverTime() {
        SolverTimeWindow window = new SolverTimeWindow(512);
        double actualFrequency = 7;
        double time = 0;
        double[] increments = {.006, .011, .008, .014, .007, .010, .009};
        int i = 0;
        while (time < 1.8) {
            window.append(time, .7 + 1.8 *
                Math.sin(2 * Math.PI * actualFrequency * time));
            time += increments[i++ % increments.length];
        }
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            20, 1, .02, 20, 4, 1e-10, SignalMeasurementAnalysis.Trigger.RISING);
        SignalMeasurementAnalysis.Result result =
            SignalMeasurementAnalysis.measureFrequency(window, policy);
        check(result.getStatus() == SignalMeasurementAnalysis.Status.OK,
            "irregular solver-time frequency is usable");
        check(close(result.getFrequencyHz(), actualFrequency, .04),
            "frequency uses interpolated timestamped crossings");

        SolverTimeSample[] samples = window.snapshot();
        double assumedStep = (samples[samples.length - 1].getTime() -
            samples[0].getTime()) / (samples.length - 1);
        double firstRisingIndex = -1;
        double secondRisingIndex = -1;
        double mean = result.getDcMean();
        for (int j = 1; j < samples.length; j++) {
            double before = samples[j - 1].getValue() - mean;
            double after = samples[j].getValue() - mean;
            if (before < 0 && after >= 0) {
                if (firstRisingIndex < 0) firstRisingIndex = j - 1;
                else { secondRisingIndex = j - 1; break; }
            }
        }
        double fixedStepFrequency = 1 / ((secondRisingIndex - firstRisingIndex) * assumedStep);
        check(Math.abs(fixedStepFrequency - actualFrequency) > .04,
            "fixed-timestep assumption disagrees with irregular timestamps");
    }

    private static void acBandwidthSeparatesSampleAdequacyFromSignalContent() {
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            16, .05, .0025, 200, 20, 1e-9, SignalMeasurementAnalysis.Trigger.ANY);
        SolverTimeWindow aboveBand = new SolverTimeWindow(512);
        for (int i = 0; i <= 200; i++) {
            double time = i * .0005;
            aboveBand.append(time, 3 * Math.sin(2 * Math.PI * 500 * time));
        }
        SignalMeasurementAnalysis.Result result = SignalMeasurementAnalysis.measureAcRms(aboveBand,
            policy);
        check(result.getWindowAssessment().getBandwidthStatus() ==
                SolverTimeWindow.BandwidthStatus.OK,
            "fine accepted solver samples are adequate for the declared 200 Hz acquisition");
        check(result.getSignalBandwidthStatus() ==
                SignalMeasurementAnalysis.SignalBandwidthStatus.EXCEEDS_DECLARED_BAND,
            "observed 500 Hz crossings are separately qualified as out of band");
        check(result.getStatus() == SignalMeasurementAnalysis.Status.BANDWIDTH_LIMITED &&
                Double.isNaN(result.getValue()),
            "out-of-band AC RMS cannot become a valid numeric reading");
    }

    private static void acTemporalCoverageAndIrregularStepsAreQualified() {
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            16, .05, .0025, 200, 20, 1e-9, SignalMeasurementAnalysis.Trigger.ANY);
        SolverTimeWindow tooShort = new SolverTimeWindow(128);
        for (int i = 0; i <= 30; i++) {
            double time = i * .001;
            tooShort.append(time, 2 * Math.sin(2 * Math.PI * 60 * time));
        }
        SignalMeasurementAnalysis.Result shortResult = SignalMeasurementAnalysis.measureAcRms(tooShort,
            policy);
        check(shortResult.getStatus() == SignalMeasurementAnalysis.Status.INSUFFICIENT_WINDOW &&
                Double.isNaN(shortResult.getValue()),
            "a capture shorter than the declared AC window is explicitly nonnumeric");

        double[] frequencies = { 50, 60 };
        for (int f = 0; f < frequencies.length; f++) {
            double frequency = frequencies[f];
            double amplitude = 2.8;
            double offset = .9;
            SolverTimeWindow accepted = new SolverTimeWindow(1024);
            double[] increments = { .00025, .00045, .00030, .00055, .00020, .00040 };
            double time = 0;
            int step = 0;
            while (time <= .2000001) {
                accepted.append(time, offset + amplitude * Math.sin(2 * Math.PI * frequency * time));
                time += increments[step++ % increments.length];
            }
            SignalMeasurementAnalysis.Result result = SignalMeasurementAnalysis.measureAcRms(accepted,
                policy);
            check(result.getStatus() == SignalMeasurementAnalysis.Status.OK &&
                    result.getSignalBandwidthStatus() ==
                        SignalMeasurementAnalysis.SignalBandwidthStatus.WITHIN_DECLARED_BAND,
                "supported " + frequency + " Hz sine remains a qualified AC RMS signal");
            check(close(result.getValue(), amplitude / Math.sqrt(2), .008),
                "irregular accepted timestamps retain accurate " + frequency + " Hz RMS");
            check(close(result.getDcMean(), offset, .008),
                "AC coupling retains explicit DC offset reporting at " + frequency + " Hz");
        }
    }

    /**
     * The actual 50 ms / 8,192-sample DMM capture retains only about
     * 40.955 ms at 5 us accepted steps.  Exercise phase offsets at the
     * declared boundary so finite-window mean bias cannot turn a clean 199 Hz
     * sine into a false 200 Hz bandwidth rejection.
     */
    private static void finiteWindowNearCutoffSinesRemainInBand() {
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            16, .04, .0025, 200, 20, 1e-9, SignalMeasurementAnalysis.Trigger.ANY);
        for (int phaseIndex = 0; phaseIndex < 32; phaseIndex++) {
            SolverTimeWindow retained = new SolverTimeWindow(8192);
            double phase = 2 * Math.PI * phaseIndex / 32.0;
            for (int step = 0; step <= 10000; step++) {
                double time = step * .000005;
                retained.append(time, 3 * Math.sin(2 * Math.PI * 199 * time + phase));
            }
            SignalMeasurementAnalysis.Result result = SignalMeasurementAnalysis.measureAcRms(retained,
                policy);
            check(result.getWindowAssessment().getBandwidthStatus() ==
                    SolverTimeWindow.BandwidthStatus.OK &&
                    result.getStatus() == SignalMeasurementAnalysis.Status.OK &&
                    result.getSignalBandwidthStatus() ==
                        SignalMeasurementAnalysis.SignalBandwidthStatus.WITHIN_DECLARED_BAND,
                "production-sized retained 199 Hz sine stays inside the 200 Hz policy at phase " +
                phaseIndex);
        }

        SolverTimeWindow aboveCutoff = new SolverTimeWindow(8192);
        for (int step = 0; step <= 10000; step++) {
            double time = step * .000005;
            aboveCutoff.append(time, 3 * Math.sin(2 * Math.PI * 201 * time + .37));
        }
        SignalMeasurementAnalysis.Result rejected = SignalMeasurementAnalysis.measureAcRms(aboveCutoff,
            policy);
        check(rejected.getStatus() == SignalMeasurementAnalysis.Status.BANDWIDTH_LIMITED &&
                rejected.getSignalBandwidthStatus() ==
                    SignalMeasurementAnalysis.SignalBandwidthStatus.EXCEEDS_DECLARED_BAND,
            "production-sized retained 201 Hz sine is explicitly outside the 200 Hz policy");
    }

    /**
     * A linearly interpolated crossing is an estimate, not an exact solver
     * timestamp.  These are deliberately uneven accepted cadences that remain
     * sample-adequate for the declared 200 Hz instrument.  They prove that
     * crossing uncertainty is taken from the surrounding sample intervals,
     * rather than hiding a false cutoff rejection behind a fixed epsilon.
     */
    private static void irregularNearCutoffSinesRespectCrossingUncertainty() {
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            16, .04, .0025, 200, 20, 1e-9, SignalMeasurementAnalysis.Trigger.ANY);
        double[] coarseIrregular = { .000500, .000250, .001000, .000500, .001000, .000250 };
        verifyIrregularNearCutoff(policy, coarseIrregular, false,
            "500/250/1000/500/1000/250 us");
        verifyIrregularNearCutoff(policy, null, true,
            "5 us with one 2.5 us accepted step every 101 samples");
    }

    /**
     * Crossing uncertainty is derived from a local sample bracket, not from
     * the arbitrary absolute solver-time origin.  The same phase-varied
     * cadence must therefore retain the same 199/200/201/500 Hz judgement
     * after its accepted timestamps are offset by a large finite epoch.
     */
    private static void irregularNearCutoffQualificationIsTimeOriginInvariant() {
        SignalMeasurementAnalysis.Policy policy = new SignalMeasurementAnalysis.Policy(
            16, .04, .0025, 200, 20, 1e-9, SignalMeasurementAnalysis.Trigger.ANY);
        double[] coarseIrregular = { .000500, .000250, .001000, .000500, .001000, .000250 };
        verifyIrregularNearCutoff(policy, coarseIrregular, false,
            "500/250/1000/500/1000/250 us at t=1e9", 1e9);
        verifyIrregularNearCutoff(policy, null, true,
            "5 us with one 2.5 us accepted step every 101 samples at t=1e9", 1e9);
    }

    private static void verifyIrregularNearCutoff(SignalMeasurementAnalysis.Policy policy,
            double[] increments, boolean productionJitter, String cadence) {
        verifyIrregularNearCutoff(policy, increments, productionJitter, cadence, 0);
    }

    private static void verifyIrregularNearCutoff(SignalMeasurementAnalysis.Policy policy,
            double[] increments, boolean productionJitter, String cadence, double timeOrigin) {
        double[] supported = { 199, 200 };
        double[] outOfBand = { 201, 500 };
        for (int phaseIndex = 0; phaseIndex < 32; phaseIndex++) {
            double phase = 2 * Math.PI * phaseIndex / 32.0;
            for (int frequencyIndex = 0; frequencyIndex < supported.length; frequencyIndex++) {
                double frequency = supported[frequencyIndex];
                SignalMeasurementAnalysis.Result result = SignalMeasurementAnalysis.measureAcRms(
                    irregularNearCutoffWindow(frequency, phase, increments, productionJitter,
                        timeOrigin), policy);
                check(result.getWindowAssessment().getBandwidthStatus() ==
                        SolverTimeWindow.BandwidthStatus.OK &&
                        result.getStatus() == SignalMeasurementAnalysis.Status.OK &&
                        result.getSignalBandwidthStatus() ==
                            SignalMeasurementAnalysis.SignalBandwidthStatus.WITHIN_DECLARED_BAND,
                    "irregular " + cadence + " capture accepts " + frequency +
                    " Hz at phase " + phaseIndex + " status=" + result.getStatus() +
                    " signal=" + result.getSignalBandwidthStatus());
            }
            for (int frequencyIndex = 0; frequencyIndex < outOfBand.length; frequencyIndex++) {
                double frequency = outOfBand[frequencyIndex];
                SignalMeasurementAnalysis.Result result = SignalMeasurementAnalysis.measureAcRms(
                    irregularNearCutoffWindow(frequency, phase, increments, productionJitter,
                        timeOrigin), policy);
                check(result.getWindowAssessment().getBandwidthStatus() ==
                        SolverTimeWindow.BandwidthStatus.OK &&
                        result.getStatus() == SignalMeasurementAnalysis.Status.BANDWIDTH_LIMITED &&
                        result.getSignalBandwidthStatus() ==
                            SignalMeasurementAnalysis.SignalBandwidthStatus.EXCEEDS_DECLARED_BAND &&
                        Double.isNaN(result.getValue()),
                    "irregular " + cadence + " capture rejects " + frequency +
                    " Hz at phase " + phaseIndex + " status=" + result.getStatus() +
                    " signal=" + result.getSignalBandwidthStatus());
            }
        }
    }

    private static SolverTimeWindow irregularNearCutoffWindow(double frequency, double phase,
            double[] increments, boolean productionJitter) {
        return irregularNearCutoffWindow(frequency, phase, increments, productionJitter, 0);
    }

    private static SolverTimeWindow irregularNearCutoffWindow(double frequency, double phase,
            double[] increments, boolean productionJitter, double timeOrigin) {
        SolverTimeWindow retained = new SolverTimeWindow(8192);
        double elapsed = 0;
        int step = 0;
        while (elapsed <= .0500000001) {
            retained.append(timeOrigin + elapsed,
                3 * Math.sin(2 * Math.PI * frequency * elapsed + phase));
            if (productionJitter)
                elapsed += step++ % 101 == 100 ? .0000025 : .000005;
            else
                elapsed += increments[step++ % increments.length];
        }
        return retained;
    }

    private static void voltageReacquisitionIsBoundedAndReferenceAware() {
        VoltageMeasurementResult numeric = VoltageMeasurementResult.numeric(3.2,
            MeasurementReferencePolicy.notApplicable(), 1000);
        double due = VoltageMeasurementReacquisitionPolicy.nextDueAt(numeric, 4.0);
        check(Math.abs(due - (4.0 + CirSim.AC_VOLTAGE_CAPTURE_SECONDS)) < 1e-12 &&
                !VoltageMeasurementReacquisitionPolicy.isDue(due,
                    4.0 + CirSim.AC_VOLTAGE_CAPTURE_SECONDS - 1e-9) &&
                VoltageMeasurementReacquisitionPolicy.isDue(due,
                    4.0 + CirSim.AC_VOLTAGE_CAPTURE_SECONDS),
            "retained finite-load voltage capture waits for bounded accepted solver time");
        VoltageMeasurementResult rejected = VoltageMeasurementResult.reference(
            new MeasurementReferencePolicy.Result(MeasurementReferencePolicy.Decision.REJECTED,
                "ISOLATION_BOUNDARY"));
        check(Double.isNaN(VoltageMeasurementReacquisitionPolicy.nextDueAt(rejected, 4.0)) &&
                Double.isNaN(VoltageMeasurementReacquisitionPolicy.nextDueAt(
                    VoltageMeasurementResult.unavailable(null), 4.0)),
            "reference and unavailable voltage outcomes retire solver-time reacquisition");
    }

    private static void noSignalAndNoTriggerAreExplicit() {
        SolverTimeWindow flat = new SolverTimeWindow(8);
        flat.append(0, 2.2);
        flat.append(.1, 2.2);
        flat.append(.2, 2.2);
        SignalMeasurementAnalysis.Result noSignal =
            SignalMeasurementAnalysis.measureFrequency(flat);
        check(noSignal.getStatus() == SignalMeasurementAnalysis.Status.NO_SIGNAL,
            "flat signal is explicit");

        SolverTimeWindow ramp = new SolverTimeWindow(8);
        ramp.append(0, 0);
        ramp.append(.1, 1);
        ramp.append(.2, 2);
        ramp.append(.3, 3);
        SignalMeasurementAnalysis.Policy falling = new SignalMeasurementAnalysis.Policy(
            2, .1, .2, Double.NaN, 5, 1e-10,
            SignalMeasurementAnalysis.Trigger.FALLING);
        SignalMeasurementAnalysis.Result noTrigger =
            SignalMeasurementAnalysis.measureFrequency(ramp, falling);
        check(noTrigger.getStatus() == SignalMeasurementAnalysis.Status.NO_TRIGGER,
            "missing requested trigger is explicit");

        SolverTimeWindow aliased = new SolverTimeWindow(32);
        for (int i = 0; i <= 20; i++) {
            double t = i * .01;
            aliased.append(t, Math.sin(2 * Math.PI * 60 * t));
        }
        SignalMeasurementAnalysis.Policy aliasPolicy =
            new SignalMeasurementAnalysis.Policy(3, .1, Double.NaN,
                20, 2, 1e-10, SignalMeasurementAnalysis.Trigger.ANY);
        SignalMeasurementAnalysis.Result alias =
            SignalMeasurementAnalysis.measureFrequency(aliased, aliasPolicy);
        check(alias.getStatus() == SignalMeasurementAnalysis.Status.ALIASED,
            "timestamp bandwidth alias is explicit");
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return !Double.isNaN(actual) && Math.abs(actual - expected) <= tolerance;
    }

    private static void reject(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError(label);
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
