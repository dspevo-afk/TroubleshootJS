package com.lushprojects.circuitjs1.client;

/** Current U03 observation, trigger, and scope-provider contracts. */
public final class U03ObservationContractTest {
    private static int assertions;

    public static void main(String[] args) {
        scopeProviderIsOneChannelDifferential();
        acceptedReceiptPublishesOnlyAfterCompletion();
        triggerAndAliasStatesStayExplicit();
        traceValidityIsIndependentFromFrequencyValidity();
        selectedRejectedReferenceRemainsVisible();
        voltageResultDoesNotCoerceUnavailableOrReferenceFailures();
        System.out.println("PASS: U03 observation contracts assertions=" + assertions);
    }

    private static void scopeProviderIsOneChannelDifferential() {
        InstrumentModeRegistry registry = StandardInstrumentModeProviders.createRegistry();
        InstrumentModeStrategy scope = registry.get("SCOPE");
        check(scope instanceof OscilloscopeInstrumentMode,
            "scope is a dedicated instrument provider, not a legacy CircuitJS scope");
        check(scope.isPlayerVisible() && scope.getProbeRequirements().requiresTwoProbes() &&
            scope.getProbeRequirements().getRedPolarity() == InstrumentProbePolarity.POSITIVE &&
            scope.getProbeRequirements().getBlackPolarity() == InstrumentProbePolarity.NEGATIVE,
            "scope declares a red-black differential channel");
        check(scope.getPowerPolicy() == InstrumentPowerPolicy.POWERED_OR_UNPOWERED &&
            registry.getPlayerVisibleModes().size() == 6,
            "scope remains a passive player-visible observer");
        check(SolverTimeObservationService.DEFAULT_CAPACITY > 1 &&
            SolverTimeObservationService.DEFAULT_CAPACITY <= 8192,
            "scope observation capacity is explicitly bounded");
    }

    private static void acceptedReceiptPublishesOnlyAfterCompletion() {
        SolverExecutionBoundary boundary = new SolverExecutionBoundary();
        Object owner = new Object();
        Object graph = new Object();
        boundary.bind(owner, graph);
        SolverExecutionBoundary.Operation pending = boundary.begin(4, 4, 0, 20, 0);
        boundary.beginTrial(pending, 1);
        boundary.accepted(pending, .01, 2);
        check(boundary.observation() == null,
            "in-flight accepted samples have no published observation");
        check(boundary.finish(pending, SolverExecutionBoundary.Outcome.COMPLETE) ==
            SolverExecutionBoundary.Outcome.COMPLETE && boundary.observation() != null,
            "completed accepted operation publishes one receipt");

        SolverExecutionBoundary.Operation failed = boundary.begin(4, 4, 3, 20, .01);
        boundary.beginTrial(failed, 4);
        boundary.accepted(failed, .02, 5);
        check(boundary.finish(failed, SolverExecutionBoundary.Outcome.CANCELLED) ==
                SolverExecutionBoundary.Outcome.CANCELLED && boundary.observation() == null,
            "cancelled accepted operation publishes no stale receipt");
    }

    private static void triggerAndAliasStatesStayExplicit() {
        SolverTimeWindow values = new SolverTimeWindow(64);
        for (int i = 0; i <= 40; i++) {
            double time = i * .01;
            values.append(time, Math.sin(2 * Math.PI * 60 * time));
        }
        SignalMeasurementAnalysis.Policy aliasPolicy = new SignalMeasurementAnalysis.Policy(
            8, .1, Double.NaN, 20, 2, 1e-10, SignalMeasurementAnalysis.Trigger.ANY);
        SignalMeasurementAnalysis.Result alias = SignalMeasurementAnalysis.measureFrequency(values,
            aliasPolicy);
        check(alias.getStatus() == SignalMeasurementAnalysis.Status.ALIASED,
            "scope frequency reports declared-bandwidth aliasing");

        SolverTimeWindow ramp = new SolverTimeWindow(8);
        ramp.append(0, 0); ramp.append(.1, 1); ramp.append(.2, 2); ramp.append(.3, 3);
        SignalMeasurementAnalysis.Result missing = SignalMeasurementAnalysis.measureFrequency(ramp,
            new SignalMeasurementAnalysis.Policy(2, .1, .2, Double.NaN, 5, 1e-10,
                SignalMeasurementAnalysis.Trigger.FALLING));
        check(missing.getStatus() == SignalMeasurementAnalysis.Status.NO_TRIGGER,
            "scope trigger absence is not converted into a frequency");
    }

    private static void voltageResultDoesNotCoerceUnavailableOrReferenceFailures() {
        MeasurementReferencePolicy.Result rejected = new MeasurementReferencePolicy.Result(
            MeasurementReferencePolicy.Decision.REJECTED, "ISOLATION_BOUNDARY");
        check(VoltageMeasurementResult.reference(rejected).getStatus() ==
                VoltageMeasurementResult.Status.REFERENCE_REJECTED,
            "cross-reference measurements remain rejected");
        check(!VoltageMeasurementResult.unavailable(null).isNumeric(),
            "unavailable readings cannot become fake zero volts");
        check(VoltageMeasurementResult.numeric(1001,
                MeasurementReferencePolicy.notApplicable(), 1000).getStatus() ==
                VoltageMeasurementResult.Status.OVER_RANGE,
            "declared voltage clipping is explicit");
    }

    private static void traceValidityIsIndependentFromFrequencyValidity() {
        SignalMeasurementAnalysis.Policy scopePolicy = new SignalMeasurementAnalysis.Policy(
            16, .004, .0005, 250, 1000, 1e-9,
            SignalMeasurementAnalysis.Trigger.RISING);
        SolverTimeWindow dc = new SolverTimeWindow(128);
        for (int i = 0; i <= 50; i++) dc.append(i * .0002, 2.4);
        SignalMeasurementAnalysis.Result dcWaveform = SignalMeasurementAnalysis.measureDcMean(dc,
            scopePolicy);
        SignalMeasurementAnalysis.Result dcFrequency = SignalMeasurementAnalysis.measureFrequency(dc,
            scopePolicy);
        check(dcWaveform.getStatus() == SignalMeasurementAnalysis.Status.OK &&
                dcFrequency.getStatus() == SignalMeasurementAnalysis.Status.NO_SIGNAL &&
                OscilloscopeInstrumentMode.permitsWaveformRendering(dcWaveform, dcFrequency),
            "a qualified DC sample window draws a horizontal trace without a frequency");

        SolverTimeWindow pulse = new SolverTimeWindow(128);
        for (int i = 0; i <= 50; i++) {
            double time = i * .0002;
            pulse.append(time, time >= .003 && time < .004 ? 4 : 0);
        }
        SignalMeasurementAnalysis.Result pulseWaveform = SignalMeasurementAnalysis.measureDcMean(pulse,
            scopePolicy);
        SignalMeasurementAnalysis.Result pulseFrequency = SignalMeasurementAnalysis.measureFrequency(pulse,
            scopePolicy);
        check(pulseWaveform.getStatus() == SignalMeasurementAnalysis.Status.OK &&
                pulseFrequency.getStatus() == SignalMeasurementAnalysis.Status.INSUFFICIENT_WINDOW &&
                OscilloscopeInstrumentMode.permitsWaveformRendering(pulseWaveform, pulseFrequency) &&
                "FREQ?".equals(OscilloscopeInstrumentMode.scopeStatusForContract(
                    pulseWaveform, pulseFrequency)),
            "a real one-shot remains drawable while its frequency is explicitly unavailable");

        SolverTimeWindow periodic = new SolverTimeWindow(1024);
        for (int i = 0; i <= 600; i++) {
            double time = i * .0002;
            periodic.append(time, 3 * Math.sin(2 * Math.PI * 60 * time));
        }
        SignalMeasurementAnalysis.Result periodicWaveform = SignalMeasurementAnalysis.measureDcMean(
            periodic, scopePolicy);
        SignalMeasurementAnalysis.Result periodicFrequency = SignalMeasurementAnalysis.measureFrequency(
            periodic, scopePolicy);
        check(periodicWaveform.isOk() && periodicFrequency.isOk() &&
                Math.abs(periodicFrequency.getValue() - 60) < .01 &&
                OscilloscopeInstrumentMode.permitsWaveformRendering(periodicWaveform,
                    periodicFrequency),
            "periodic scope waveform and timestamp-derived frequency agree");
        check(InstrumentController.isSafeScopeSegment(.002, .0022, .0005) &&
                !InstrumentController.isSafeScopeSegment(.002, .003, .0005),
            "scope projects accepted neighboring samples but never bridges an unsafe gap");
    }

    private static void selectedRejectedReferenceRemainsVisible() {
        MeasurementReferencePolicy.Result rejected = new MeasurementReferencePolicy.Result(
            MeasurementReferencePolicy.Decision.REJECTED, "ISOLATION_BOUNDARY");
        check("SCOPE: REF?".equals(OscilloscopeInstrumentMode.displayTextForContract(true,
                rejected, null, null)) &&
                "SCOPE: PROBES".equals(OscilloscopeInstrumentMode.displayTextForContract(false,
                    rejected, null, null)),
            "selected rejected-reference probes remain REF? rather than being cleared to PROBES");
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
