package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Actual CircuitJS accepted-state and rejected-trial contracts for E04 U2. */
public final class E04DecisionMemoryContractTest {
    private static final double WIDE_STEP = 1e-4;
    private static final double MIN_STEP = 1e-5;
    private static final int REJECTED_TRIALS = 100;
    private static int assertions, cases;

    private E04DecisionMemoryContractTest() { }

    public static void main(String[] args) {
        CirSim previousSim = CircuitElm.sim;
        CirSim previousSingleton = CirSim.theSim;
        Point previousPs1 = CircuitElm.ps1, previousPs2 = CircuitElm.ps2;
        HashMap<String, String> previousLocalization = CirSim.localizationMap;
        Throwable failure = null;
        try {
            runCase("rejected-trial-retry-and-reset", new CaseBody() {
                public String run(Fixture fixture) {
                    verifyRejectedTrialRetriesAndReset(fixture);
                    return "high->rejected-brownout->3.83V-high; " +
                        "brownout->rejected-high->3.83V-brownout; " +
                        "3.88V-recovery; 3.83V-hold; reset-at-3.83V-off";
                }
            });
            runCase("late-stepFinished-failure", new CaseBody() {
                public String run(Fixture fixture) {
                    verifyLateStepFinishedFailure(fixture);
                    return "solved-brownout-trial; accepted-high-retained; " +
                        "no-receipt-or-sample; fresh-3.83V-high";
                }
            });
            runCase("accepted-event-dispatch-failure", new CaseBody() {
                public String run(Fixture fixture) {
                    verifyEventDispatchFailure(fixture);
                    return "solved-high-trial; accepted-brownout-retained; " +
                        "no-receipt-or-sample";
                }
            });
        } catch (Throwable error) {
            failure = error;
        } finally {
            CircuitElm.sim = previousSim;
            CirSim.theSim = previousSingleton;
            CircuitElm.ps1 = previousPs1;
            CircuitElm.ps2 = previousPs2;
            CirSim.localizationMap = previousLocalization;
        }
        if (failure != null) raise(failure);
        check(CircuitElm.sim == previousSim && CirSim.theSim == previousSingleton &&
            CircuitElm.ps1 == previousPs1 && CircuitElm.ps2 == previousPs2 &&
            CirSim.localizationMap == previousLocalization,
            "outer native singleton and model defaults restored");
        System.out.println("PASS: E04 accepted decision memory contracts assertions=" + assertions +
            " cases=" + cases + " scope=REAL_SOLVER_REJECTED_TRIALS_AND_ACCEPTANCE_BOUNDARY");
    }

    private static void verifyRejectedTrialRetriesAndReset(Fixture fixture) {
        SolverTimeObservationService.Subscription samples = fixture.subscribeOutput();
        fixture.establishHigh();
        check(samples.getSampleCount() > 0, "accepted high operation publishes actual output samples");

        fixture.setRailAndAnalyze(3.79, WIDE_STEP);
        check(samples.getSampleCount() == 0, "reanalyze retires the preceding waveform before trial canary");
        fixture.canary.armRetry(3.83, E04SensorControlModel.ControlState.HIGH,
            DecisionMemoryCanaryElm.TRIAL_BROWNOUT, samples);
        double beforeHighRetry = fixture.sim.t;
        fixture.advance(1);
        checkRetry(fixture, E04SensorControlModel.ControlState.HIGH,
            DecisionMemoryCanaryElm.TRIAL_BROWNOUT, samples, beforeHighRetry,
            "rejected brownout must not poison accepted HIGH memory");
        check(fixture.model.getRailNodeVoltage() > 3.8 && fixture.model.getRailNodeVoltage() < 3.85 &&
            fixture.model.getOutputVoltage() > .1,
            "actual retry at 3.83 V retains high output above the falling threshold");

        fixture.analyze();
        fixture.advance(1);
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
            fixture.model.isOutputHigh(), "fresh analysis at 3.83 V preserves accepted HIGH memory");

        fixture.setRailAndAnalyze(3.79, WIDE_STEP);
        fixture.advance(1);
        check(fixture.model.getRailNodeVoltage() < 3.8,
            "actual accepted brownout solve has a measured rail below its falling threshold");
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
            !fixture.model.isOutputHigh(),
            "actual accepted 3.79 V solve establishes BROWNOUT memory");
        near(fixture.model.getDecisionElement().getOutputDriveCurrent(), 0.0,
            "accepted brownout disables the actual rail-fed output branch");
        check(finite(fixture.model.getOutputVoltage()) && fixture.model.getOutputVoltage() > 0.0,
            "brownout output post remains finite and positively backfed by the live 22 kOhm " +
                "output-to-sensor path: " + fixture.model.getOutputVoltage());

        fixture.setRailAndAnalyze(5.0, WIDE_STEP);
        fixture.canary.armRetry(3.83, E04SensorControlModel.ControlState.BROWNOUT,
            DecisionMemoryCanaryElm.TRIAL_HIGH, samples);
        double beforeBrownoutRetry = fixture.sim.t;
        fixture.advance(1);
        checkRetry(fixture, E04SensorControlModel.ControlState.BROWNOUT,
            DecisionMemoryCanaryElm.TRIAL_HIGH, samples, beforeBrownoutRetry,
            "rejected high trial must not erase brownout recovery memory");
        check(fixture.model.getRailNodeVoltage() > 3.8 && fixture.model.getRailNodeVoltage() < 3.85 &&
            fixture.model.getOutputVoltage() > 0.0 && finite(fixture.model.getOutputVoltage()),
            "actual retry at 3.83 V retains finite passive feedback below the recovery threshold");
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
            !fixture.model.isOutputHigh(),
            "actual retry at 3.83 V retains accepted BROWNOUT memory below recovery");
        near(fixture.model.getDecisionElement().getOutputDriveCurrent(), 0.0,
            "brownout retry keeps the actual rail-fed output branch disabled");
        fixture.analyze();
        fixture.advance(1);
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
            !fixture.model.isOutputHigh(), "fresh analysis at 3.83 V retains accepted BROWNOUT memory");

        fixture.setRailAndAnalyze(3.88, WIDE_STEP);
        fixture.canary.captureNextStepFinishedState();
        fixture.advance(1);
        check(fixture.canary.stepFinishedState == E04SensorControlModel.ControlState.BROWNOUT &&
            fixture.canary.stepFinishedRail > 3.85 && fixture.canary.stepFinishedOutput > .1,
            "late stepFinished sees solved recovery trial before global acceptance");
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
            fixture.model.isOutputHigh() && fixture.sim.solverExecutor.hasAcceptedStepAt(fixture.sim.t),
            "executor acceptance promotes the real 3.88 V recovery to HIGH");

        fixture.setRailAndAnalyze(3.83, WIDE_STEP);
        fixture.advance(1);
        check(fixture.model.getRailNodeVoltage() > 3.8 && fixture.model.getRailNodeVoltage() < 3.85 &&
            fixture.model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
            fixture.model.getOutputVoltage() > .1,
            "accepted HIGH remains on inside the hysteretic recovery band");

        fixture.model.getDecisionElement().reset();
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.UNSETTLED &&
            !fixture.model.isOutputHigh(), "reset clears accepted decision memory to UNSETTLED");
        fixture.analyze();
        fixture.advance(1);
        check(fixture.model.getRailNodeVoltage() > 3.8 && fixture.model.getRailNodeVoltage() < 3.85 &&
            fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
            !fixture.model.isOutputHigh(),
            "cold UNSETTLED start at 3.83 V requires the 3.85 V recovery threshold");
        near(fixture.model.getDecisionElement().getOutputDriveCurrent(), 0.0,
            "cold-start brownout keeps the actual rail-fed output branch disabled");
        check(finite(fixture.model.getOutputVoltage()) && fixture.model.getOutputVoltage() > 0.0,
            "cold-start output post reflects finite passive backfeed from the sensor path");
    }

    private static void checkRetry(Fixture fixture,
            E04SensorControlModel.ControlState acceptedState,
            int trialKind, SolverTimeObservationService.Subscription samples,
            double beforeTime, String label) {
        DecisionMemoryCanaryElm canary = fixture.canary;
        check(canary.wideTrials == REJECTED_TRIALS && canary.retryTrials > 0 &&
            canary.retryStepFinishedCount == 1,
            label + " exercised 100 rejected wide trials and a real accepted retry");
        System.out.println("E04_DECISION_MEMORY_TRIAL {\"trialKind\":" + canary.trialKind +
            ",\"expectedAcceptedState\":" + quote(acceptedState.name()) +
            ",\"sawTargetTrial\":" + canary.sawTargetTrial +
            ",\"targetTrialState\":" + quote(canary.targetTrialState == null ? null :
                canary.targetTrialState.name()) +
            ",\"targetTrialRail\":" + numberOrNull(canary.targetTrialRail) +
            ",\"targetTrialOutput\":" + numberOrNull(canary.targetTrialOutput) +
            ",\"targetDriveCurrent\":" + numberOrNull(canary.targetDriveCurrent) +
            ",\"targetRailTerminalCurrent\":" + numberOrNull(canary.targetRailTerminalCurrent) +
            ",\"acceptedStatePreserved\":" + canary.acceptedStatePreservedDuringTrial +
            ",\"receiptObserved\":" + canary.receiptObservedDuringTrial +
            ",\"observationCount\":" + canary.observationCountDuringTrial +
            ",\"noReceiptDuringTrial\":" + canary.noReceiptDuringTrial +
            ",\"noObservationDuringTrial\":" + canary.noObservationDuringTrial +
            ",\"trialRailMin\":" + numberOrNull(canary.trialRailMin) +
            ",\"trialRailMax\":" + numberOrNull(canary.trialRailMax) +
            ",\"trialOutputMin\":" + numberOrNull(canary.trialOutputMin) +
            ",\"trialOutputMax\":" + numberOrNull(canary.trialOutputMax) +
            ",\"trialDriveCurrentMin\":" + numberOrNull(canary.trialDriveCurrentMin) +
            ",\"trialDriveCurrentMax\":" + numberOrNull(canary.trialDriveCurrentMax) +
            ",\"wideTrials\":" + canary.wideTrials + ",\"retryTrials\":" + canary.retryTrials + "}");
        check(canary.sawTargetTrial,
            label + " no actual solved rail and decision-drive-current pair met the requested mode target: rail=[" +
                canary.trialRailMin + "," + canary.trialRailMax + "] output=[" +
                canary.trialOutputMin + "," + canary.trialOutputMax + "] driveCurrent=[" +
                canary.trialDriveCurrentMin + "," + canary.trialDriveCurrentMax + "]");
        check(canary.acceptedStatePreservedDuringTrial,
            label + " accepted getter changed during target trial: expected=" + acceptedState +
                " observed=" + canary.targetTrialState);
        check(canary.noReceiptDuringTrial,
            label + " solver reported an accepted receipt during target trial");
        check(canary.noObservationDuringTrial,
            label + " observation subscription published during target trial: count=" +
                canary.observationCountDuringTrial);
        check(finite(canary.targetTrialOutput),
            label + " actual output terminal was nonfinite during the witnessed branch trial");
        check(finite(canary.targetDriveCurrent) && finite(canary.targetRailTerminalCurrent),
            label + " actual decision branch or rail-terminal current was nonfinite");
        check(canary.trialKind == trialKind && canary.acceptedStateAtRetryStepFinished == acceptedState,
            label + " accepted memory remained unchanged through late retry callbacks");
        check(finite(canary.retryStepFinishedRail) && finite(canary.retryStepFinishedOutput) &&
            canary.retryStepFinishedRail > 3.8 && canary.retryStepFinishedOutput >= 0.0,
            label + " retry callback observed finite solved terminals");
        near(fixture.sim.t - beforeTime, WIDE_STEP / 2.0,
            label + " one accepted half-step after the rejected wide attempt");
        check(Math.abs(fixture.sim.timeStep - WIDE_STEP / 2.0) < 1e-12,
            label + " solver accepted the bounded half-step retry");
        check(fixture.sim.solverExecutor.hasAcceptedStepAt(fixture.sim.t),
            label + " publishes accepted solver state only after the retry commits");
        check(samples.getSampleCount() == 1,
            label + " publishes exactly one actual output sample for the accepted retry");
        check(fixture.model.getControlState() == acceptedState &&
            fixture.model.isOutputHigh() == (acceptedState == E04SensorControlModel.ControlState.HIGH),
            label + " public semantic getters expose accepted memory");
    }

    private static void verifyLateStepFinishedFailure(Fixture fixture) {
        fixture.establishHigh();
        SolverTimeObservationService.Subscription samples = fixture.subscribeOutput();
        fixture.setRailAndAnalyze(3.79, WIDE_STEP);
        check(samples.getSampleCount() == 0, "analysis clears old samples before late callback failure");
        RuntimeException marker = new IllegalStateException("e04-late-step-finished-before-global-acceptance");
        fixture.canary.armLateStepFinishedFailure(marker);
        double beforeTime = fixture.sim.t;
        RuntimeException observed = advanceFailure(fixture, 1);
        check(observed == marker && fixture.canary.lateStepFinishedCount == 1,
            "last-in-graph stepFinished failure propagates after E04 callbacks");
        check(fixture.canary.lateRail < 3.8 &&
            fixture.canary.latePublicState == E04SensorControlModel.ControlState.HIGH,
            "failed callback saw the solved brownout rail while public accepted state stayed HIGH");
        near(fixture.canary.lateDriveCurrent, 0.0,
            "failed callback saw the brownout trial's disabled rail-fed branch");
        check(finite(fixture.canary.lateOutput) && fixture.canary.lateOutput > 0.0,
            "failed callback retained the finite output-to-sensor passive backfeed");
        check(fixture.sim.t > beforeTime && !fixture.sim.solverExecutor.hasAcceptedStepAt(fixture.sim.t) &&
            samples.getSampleCount() == 0,
            "failed step callbacks advance no accepted receipt or observation window");
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
            fixture.model.isOutputHigh(), "late callback failure cannot promote brownout trial memory");

        fixture.setRailAndAnalyze(3.83, WIDE_STEP);
        fixture.advance(1);
        check(fixture.model.getRailNodeVoltage() > 3.8 && fixture.model.getRailNodeVoltage() < 3.85 &&
            fixture.model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
            fixture.model.getDecisionElement().getOutputDriveCurrent() > 0.0 &&
            finite(fixture.model.getOutputVoltage()),
            "fresh actual solve at 3.83 V still uses the pre-failure accepted HIGH memory");
    }

    private static void verifyEventDispatchFailure(final Fixture fixture) {
        fixture.model.setSensorVoltage(4.5);
        fixture.setRailAndAnalyze(3.79, WIDE_STEP);
        final SolverTimeObservationService.Subscription samples = fixture.subscribeOutput();
        fixture.advance(1);
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
            !fixture.model.isOutputHigh(),
            "actual low-rail solve establishes the accepted brownout before event failure");
        near(fixture.model.getDecisionElement().getOutputDriveCurrent(), 0.0,
            "accepted brownout before event failure has no rail-fed output drive");
        check(finite(fixture.model.getOutputVoltage()) && fixture.model.getOutputVoltage() > 0.0,
            "accepted brownout output remains passively backfed through the sensor path");
        check(samples.getSampleCount() == 1, "pre-failure accepted operation has one committed output sample");

        fixture.setRailAndAnalyze(5.0, WIDE_STEP);
        check(samples.getSampleCount() == 0, "analysis clears previous accepted samples before event failure");
        final double beforeTime = fixture.sim.t;
        final RuntimeException marker = new IllegalStateException("e04-event-before-global-acceptance");
        final int[] fired = { 0 };
        final boolean[] observedHighTrial = { false };
        final boolean[] noReceipt = { false };
        fixture.sim.solverExecutor.events(null).schedule(beforeTime + WIDE_STEP / 2.0,
            new SolverEventQueue.Action() {
                public void fire(double scheduledTime, double acceptedTime) {
                    fired[0]++;
                    observedHighTrial[0] = acceptedTime == fixture.sim.t && acceptedTime > beforeTime &&
                        fixture.model.getRailNodeVoltage() > 3.85 &&
                        fixture.model.getDecisionElement().getOutputDriveCurrent() > 0.0 &&
                        finite(fixture.model.getOutputVoltage()) &&
                        fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
                        !fixture.model.isOutputHigh();
                    noReceipt[0] = !fixture.sim.solverExecutor.hasAcceptedStepAt(acceptedTime) &&
                        samples.getSampleCount() == 0;
                    throw marker;
                }
            });
        RuntimeException observed = advanceFailure(fixture, 1);
        check(observed == marker && fired[0] == 1,
            "actual accepted-time event failure propagates after model callbacks");
        check(observedHighTrial[0] && noReceipt[0],
            "event saw solved high terminals but no committed state, receipt, or sample");
        check(fixture.model.getControlState() == E04SensorControlModel.ControlState.BROWNOUT &&
            !fixture.model.isOutputHigh() &&
            fixture.model.getDecisionElement().getOutputDriveCurrent() > 0.0 &&
            finite(fixture.model.getOutputVoltage()),
            "event failure leaves accepted brownout memory distinct from the unaccepted high output");
        check(!fixture.sim.solverExecutor.hasAcceptedStepAt(fixture.sim.t) &&
            samples.getSampleCount() == 0,
            "event failure before boundary acceptance publishes no receipt or observation");
    }

    private static RuntimeException advanceFailure(Fixture fixture, int steps) {
        try {
            fixture.advance(steps);
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new AssertionError("Expected actual solver operation to fail before acceptance");
    }

    private static void runCase(String id, CaseBody body) {
        long operationStarted = System.currentTimeMillis();
        Fixture fixture = null;
        String detail = "";
        Throwable operationFailure = null;
        Throwable cleanupFailure = null;
        try {
            fixture = new Fixture();
            fixture.initialize();
            detail = body.run(fixture);
        } catch (Throwable problem) {
            operationFailure = problem;
        }
        long operationMillis = System.currentTimeMillis() - operationStarted;
        long cleanupStarted = System.currentTimeMillis();
        if (fixture != null) {
            try { fixture.close(); }
            catch (Throwable cleanup) {
                cleanupFailure = cleanup;
            }
        }
        long cleanupMillis = System.currentTimeMillis() - cleanupStarted;
        boolean clean = cleanupFailure == null;
        boolean passed = operationFailure == null && clean;
        Throwable reportedFailure = operationFailure == null ? cleanupFailure : operationFailure;
        if (operationFailure != null && cleanupFailure != null)
            operationFailure.addSuppressed(cleanupFailure);
        System.out.println("E04_DECISION_MEMORY_CASE {\"case\":" + quote(id) +
            ",\"status\":" + quote(passed ? "PASS" : "FAIL") +
            ",\"operationMs\":" + operationMillis + ",\"cleanupMs\":" + cleanupMillis +
            ",\"cleanup\":" + quote(clean ? "PASS" : "FAIL") +
            ",\"detail\":" + quote(detail) + ",\"error\":" +
            quote(reportedFailure == null ? null : reportedFailure.getMessage()) + "}");
        if (!passed) raise(reportedFailure);
        cases++;
    }

    private static final class Fixture {
        final CirSim savedSim = CircuitElm.sim;
        final CirSim savedSingleton = CirSim.theSim;
        final Point savedPs1 = CircuitElm.ps1, savedPs2 = CircuitElm.ps2;
        final HashMap<String, String> savedLocalization = CirSim.localizationMap;
        final NativeSim sim = new NativeSim();
        final Vector<CircuitElm> originalGraph;
        final Vector<SolverTimeObservationService.Subscription> subscriptions =
            new Vector<SolverTimeObservationService.Subscription>();
        E04SensorControlModel model;
        Vector<CircuitElm> graph;
        DecisionMemoryCanaryElm canary;
        boolean closed;

        Fixture() {
            originalGraph = sim.elmList == null ? new Vector<CircuitElm>() : sim.elmList;
            activate();
            sim.elmList = new Vector<CircuitElm>();
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
            sim.maxTimeStep = sim.timeStep = WIDE_STEP;
            sim.minTimeStep = MIN_STEP;
            sim.adjustTimeStep = true;
        }

        void initialize() {
            activate();
            model = new E04SensorControlModel(E04SensorControlModel.RailDeclaration.fiveVolt(),
                E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE);
            graph = model.getSimulationElements();
            sim.elmList = graph;
            CircuitPostMeasurementEndpoint returned =
                model.getSensorControlBindings().getReturnEndpoint();
            Point returnPost = returned.getElement().getPost(returned.getPostIndex());
            canary = new DecisionMemoryCanaryElm(this, returnPost);
            graph.add(canary);
            analyze();
        }

        void establishHigh() {
            model.setSensorVoltage(4.5);
            setRailAndAnalyze(5.0, WIDE_STEP);
            advance(8);
            check(model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
                model.isOutputHigh(), "actual CircuitJS solve establishes accepted HIGH state");
            check(model.getRailNodeVoltage() > 4.5 && model.getOutputVoltage() > .1 &&
                finite(model.getRailSourceCurrent()) && Math.abs(model.getRailSourceCurrent()) > 1e-6 &&
                finite(model.getSensorSourceCurrent()) && Math.abs(model.getSensorSourceCurrent()) > 1e-6,
                "actual finite rail and sensor source impedances carry solved current");
            check(sim.solverExecutor.hasAcceptedStepAt(sim.t),
                "healthy HIGH setup has an accepted CircuitJS solver receipt");
        }

        SolverTimeObservationService.Subscription subscribeOutput() {
            E04SensorControlModel.SensorControlBindings bindings = model.getSensorControlBindings();
            SolverTimeObservationService.Subscription result = sim.solverTimeObservations.subscribe(
                bindings.getOutputDriveEndpoint(), bindings.getReturnEndpoint(), 32, false);
            subscriptions.add(result);
            return result;
        }

        void setRailAndAnalyze(double voltage, double maximumStep) {
            model.setRailVoltage(voltage);
            configureStep(maximumStep);
            analyze();
            configureStep(maximumStep);
        }

        void configureStep(double maximumStep) {
            sim.maxTimeStep = maximumStep;
            sim.timeStep = maximumStep;
            sim.minTimeStep = MIN_STEP;
            sim.adjustTimeStep = true;
        }

        void analyze() {
            activate();
            sim.solverExecutor.analyze();
        }

        void advance(int count) {
            activate();
            sim.solverExecutor.advanceSteps(count);
        }

        void activate() {
            CircuitElm.sim = sim;
            CirSim.theSim = sim;
        }

        void close() {
            if (closed) return;
            closed = true;
            Throwable failure = null;
            activate();
            for (SolverTimeObservationService.Subscription subscription : subscriptions) {
                try { sim.solverTimeObservations.unsubscribe(subscription); }
                catch (Throwable problem) { failure = retain(failure, problem); }
            }
            try { sim.solverExecutor.retire(); }
            catch (Throwable problem) { failure = retain(failure, problem); }
            boolean canaryRemoved = canary == null || (graph != null && graph.remove(canary));
            sim.elmList = originalGraph;
            if (model != null) {
                try { model.dispose(); }
                catch (Throwable problem) { failure = retain(failure, problem); }
            }
            if (failure != null && graph != null) {
                for (CircuitElm element : graph) {
                    try { element.delete(); }
                    catch (Throwable problem) { failure = retain(failure, problem); }
                }
            }
            if (canary != null) {
                try { canary.delete(); }
                catch (Throwable problem) { failure = retain(failure, problem); }
            }
            if (graph != null) graph.removeAllElements();
            sim.circuitMatrix = null;
            sim.wireInfoList = null;
            boolean clean = failure == null && sim.elmList == originalGraph &&
                canaryRemoved && (graph == null || graph.isEmpty()) && !sim.solverExecutor.isUnavailable() &&
                sim.generatedBoardInstance == null && !sim.activeMeasurementOverlay &&
                subscriptionsClosed();
            CircuitElm.sim = savedSim;
            CirSim.theSim = savedSingleton;
            CircuitElm.ps1 = savedPs1;
            CircuitElm.ps2 = savedPs2;
            CirSim.localizationMap = savedLocalization;
            clean &= CircuitElm.sim == savedSim && CirSim.theSim == savedSingleton &&
                CircuitElm.ps1 == savedPs1 && CircuitElm.ps2 == savedPs2 &&
                CirSim.localizationMap == savedLocalization;
            if (!clean) {
                AssertionError problem = new AssertionError("E04 decision-memory fixture cleanup failed");
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            }
            if (failure != null) raise(failure);
        }

        boolean subscriptionsClosed() {
            for (SolverTimeObservationService.Subscription subscription : subscriptions)
                if (!subscription.isClosed()) return false;
            return true;
        }
    }

    private static final class DecisionMemoryCanaryElm extends CircuitElm {
        static final int TRIAL_BROWNOUT = 1, TRIAL_HIGH = 2;
        private final Fixture fixture;
        private final Point attachedPost;
        private boolean retryArmed, sourceSwitched, captureNextStepFinished;
        private boolean lateFailureArmed;
        private int trialKind;
        private double retryRail;
        private E04SensorControlModel.ControlState expectedAcceptedState;
        private SolverTimeObservationService.Subscription samples;
        int wideTrials, retryTrials, retryStepFinishedCount, lateStepFinishedCount;
        boolean sawTargetTrial, acceptedStatePreservedDuringTrial = true;
        boolean noReceiptDuringTrial = true, noObservationDuringTrial = true;
        boolean receiptObservedDuringTrial;
        E04SensorControlModel.ControlState targetTrialState;
        double targetTrialRail = Double.NaN, targetTrialOutput = Double.NaN;
        double targetDriveCurrent = Double.NaN, targetRailTerminalCurrent = Double.NaN;
        double trialRailMin = Double.POSITIVE_INFINITY, trialRailMax = Double.NEGATIVE_INFINITY;
        double trialOutputMin = Double.POSITIVE_INFINITY, trialOutputMax = Double.NEGATIVE_INFINITY;
        double trialDriveCurrentMin = Double.POSITIVE_INFINITY, trialDriveCurrentMax = Double.NEGATIVE_INFINITY;
        int observationCountDuringTrial = -1;
        E04SensorControlModel.ControlState acceptedStateAtRetryStepFinished;
        double retryStepFinishedRail, retryStepFinishedOutput;
        E04SensorControlModel.ControlState stepFinishedState;
        double stepFinishedRail, stepFinishedOutput;
        RuntimeException lateMarker;
        double lateRail, lateOutput;
        double lateDriveCurrent;
        E04SensorControlModel.ControlState latePublicState;

        DecisionMemoryCanaryElm(Fixture fixture, Point attachedPost) {
            super(attachedPost.x, attachedPost.y);
            this.fixture = fixture;
            this.attachedPost = attachedPost;
            setPoints();
        }

        int getDumpType() { return 'x'; }
        int getPostCount() { return 1; }
        boolean nonLinear() { return true; }
        void setPoints() {
            point1 = attachedPost;
            point2 = attachedPost;
            boundingBox = new Rectangle(x, y, 1, 1);
        }
        Point getPost(int n) {
            if (n != 0) throw new IndexOutOfBoundsException("E04 canary has one post");
            return attachedPost;
        }
        void stamp() { sim.stampNonLinear(nodes[0]); }

        void armRetry(double railAfterWideTrials,
                E04SensorControlModel.ControlState acceptedState, int expectedTrialKind,
                SolverTimeObservationService.Subscription subscription) {
            retryArmed = true;
            sourceSwitched = false;
            retryRail = railAfterWideTrials;
            expectedAcceptedState = acceptedState;
            trialKind = expectedTrialKind;
            samples = subscription;
            wideTrials = retryTrials = retryStepFinishedCount = 0;
            sawTargetTrial = false;
            acceptedStatePreservedDuringTrial = true;
            noReceiptDuringTrial = true;
            noObservationDuringTrial = true;
            receiptObservedDuringTrial = false;
            targetTrialState = null;
            targetTrialRail = targetTrialOutput = Double.NaN;
            targetDriveCurrent = targetRailTerminalCurrent = Double.NaN;
            trialRailMin = trialOutputMin = Double.POSITIVE_INFINITY;
            trialRailMax = trialOutputMax = Double.NEGATIVE_INFINITY;
            trialDriveCurrentMin = Double.POSITIVE_INFINITY;
            trialDriveCurrentMax = Double.NEGATIVE_INFINITY;
            observationCountDuringTrial = -1;
            acceptedStateAtRetryStepFinished = null;
        }

        void captureNextStepFinishedState() {
            captureNextStepFinished = true;
            stepFinishedState = null;
        }

        void armLateStepFinishedFailure(RuntimeException marker) {
            lateFailureArmed = true;
            lateMarker = marker;
            lateStepFinishedCount = 0;
        }

        void doStep() {
            if (!retryArmed) return;
            double rail = fixture.model.getRailNodeVoltage();
            double output = fixture.model.getOutputVoltage();
            boolean wide = sim.timeStep > WIDE_STEP * .75;
            if (wide && wideTrials < REJECTED_TRIALS) {
                wideTrials++;
                recordTrial(rail, output);
                sim.converged = false;
                if (wideTrials == REJECTED_TRIALS) {
                    fixture.model.setRailVoltage(retryRail);
                    sourceSwitched = true;
                }
            } else if (sourceSwitched) {
                retryTrials++;
                recordTrial(rail, output);
            }
        }

        private void recordTrial(double rail, double output) {
            E04SensorControlModel.DecisionElement decision = fixture.model.getDecisionElement();
            double driveCurrent = decision.getOutputDriveCurrent();
            double railTerminalCurrent = decision.getCurrentIntoNode(2);
            if (finite(rail)) {
                trialRailMin = Math.min(trialRailMin, rail);
                trialRailMax = Math.max(trialRailMax, rail);
            }
            if (finite(output)) {
                trialOutputMin = Math.min(trialOutputMin, output);
                trialOutputMax = Math.max(trialOutputMax, output);
            }
            if (finite(driveCurrent)) {
                trialDriveCurrentMin = Math.min(trialDriveCurrentMin, driveCurrent);
                trialDriveCurrentMax = Math.max(trialDriveCurrentMax, driveCurrent);
            }
            boolean target = trialKind == TRIAL_BROWNOUT ? rail < 3.8 && driveCurrent == 0.0 :
                rail > 3.85 && driveCurrent > 0.0;
            if (target) {
                if (!sawTargetTrial) {
                    targetTrialState = fixture.model.getControlState();
                    targetTrialRail = rail;
                    targetTrialOutput = output;
                    targetDriveCurrent = driveCurrent;
                    targetRailTerminalCurrent = railTerminalCurrent;
                    receiptObservedDuringTrial = sim.solverExecutor.hasAcceptedStepAt(sim.t);
                    observationCountDuringTrial = samples == null ? -1 : samples.getSampleCount();
                }
                sawTargetTrial = true;
                acceptedStatePreservedDuringTrial &=
                    fixture.model.getControlState() == expectedAcceptedState;
                noReceiptDuringTrial &= !receiptObservedDuringTrial &&
                    !sim.solverExecutor.hasAcceptedStepAt(sim.t);
                noObservationDuringTrial &= samples == null || samples.getSampleCount() == 0;
            }
        }

        void stepFinished() {
            if (retryArmed && sourceSwitched) {
                retryStepFinishedCount++;
                acceptedStateAtRetryStepFinished = fixture.model.getControlState();
                retryStepFinishedRail = fixture.model.getRailNodeVoltage();
                retryStepFinishedOutput = fixture.model.getOutputVoltage();
                retryArmed = false;
            }
            if (captureNextStepFinished) {
                captureNextStepFinished = false;
                stepFinishedState = fixture.model.getControlState();
                stepFinishedRail = fixture.model.getRailNodeVoltage();
                stepFinishedOutput = fixture.model.getOutputVoltage();
            }
            if (lateFailureArmed) {
                lateFailureArmed = false;
                lateStepFinishedCount++;
                lateRail = fixture.model.getRailNodeVoltage();
                lateOutput = fixture.model.getOutputVoltage();
                lateDriveCurrent = fixture.model.getDecisionElement().getOutputDriveCurrent();
                latePublicState = fixture.model.getControlState();
                throw lateMarker;
            }
        }
    }

    private static final class NativeSim extends CirSim {
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void refreshBoardModificationControls() { }
        @Override void repaint() { }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message;
            stopElm = element;
            circuitMatrix = null;
            simRunning = false;
            analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
    }

    private interface CaseBody { String run(Fixture fixture); }

    private static void near(double actual, double expected, String label) {
        check(finite(actual) && finite(expected) &&
            Math.abs(actual - expected) <= 1e-12 + 1e-10 * Math.max(Math.abs(actual), Math.abs(expected)),
            label + ": expected " + expected + ", observed " + actual);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String numberOrNull(double value) {
        return finite(value) ? Double.toString(value) : "null";
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static Throwable retain(Throwable original, Throwable next) {
        if (original == null) return next;
        if (original != next) original.addSuppressed(next);
        return original;
    }

    private static void raise(Throwable error) {
        if (error instanceof Error) throw (Error)error;
        if (error instanceof RuntimeException) throw (RuntimeException)error;
        throw new IllegalStateException(error);
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
