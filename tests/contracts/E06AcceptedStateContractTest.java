package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Vector;

/** Actual CircuitJS accepted-state and failure-boundary contracts for E06. */
public final class E06AcceptedStateContractTest {
    private static final double DT = .000025;
    private static int assertions, cases;

    private E06AcceptedStateContractTest() { }

    public static void main(String[] args) {
        runCase("valid-acceptance", new CaseBody() {
            public void run(FixtureCase c) {
                Snapshot before = Snapshot.capture(c.converter);
                E06AveragedConverterElm.AcceptedStateSnapshot modelBefore =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore),
                    "model-owned snapshot matches its exact source element");
                c.capture.arm(null);
                c.advance(1);
                check(c.capture.expected != null, "actual E06 stepFinished produced a candidate");
                check(!pending(c.converter), "global acceptance consumed the pending candidate");
                check(!c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore) &&
                    c.converter.hasAcceptedPredictorForDeveloperVerification(),
                    "accepted callback replaces the model-owned receipt, controller and predictor snapshot");
                assertCommitted(c, c.capture.expected);
                check(c.sim.solverExecutor.hasAcceptedStepAt(c.sim.t),
                    "the accepted receipt agrees with the executor's committed time");
                check(before.receipt, "valid canary began with an independently retained prior receipt");
                c.observer = c.sim.solverTimeObservations.subscribe(
                    new CircuitPostMeasurementEndpoint(c.converter, E06ConverterContract.IN_PLUS),
                    new CircuitPostMeasurementEndpoint(c.converter, E06ConverterContract.IN_MINUS), 8, false);
                c.startProbe.arm(PredictorSnapshot.capture(c.converter));
                c.capture.arm(null, Snapshot.capture(c.converter));
                c.capture.armCurrentPathCanary();
                c.advance(1);
                check(c.startProbe.observed && c.startProbe.clearedPending &&
                    c.startProbe.retainedPredictor && c.startProbe.usedPredictor,
                    "the next real startIteration retains and uses the just-committed predictor");
                check(c.capture.currentPathObserved,
                    "CircuitJS scope and wire-current paths observe the staged solved current before acceptance");
                assertCommitted(c, c.capture.expected);
                int compatibleSteps = 0;
                while (compatibleSteps < 8 && !c.converter.canUseAcceptedNewtonPredictor()) {
                    c.capture.arm(null);
                    c.advance(1);
                    compatibleSteps++;
                }
                if (compatibleSteps > 0) assertCommitted(c, c.capture.expected);
                check(c.observer.getSampleCount() > 0 && c.sim.solverExecutor.hasAcceptedStepAt(c.sim.t) &&
                    c.converter.canUseAcceptedNewtonPredictor(),
                    "bounded real accepted steps establish current executor reuse authority and observer samples; " +
                    describeState(c));
                E06AveragedConverterElm.AcceptedStateSnapshot beforeExecutorRestore =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                CircuitSolverExecutor.PrivateState solverSnapshot = c.sim.solverExecutor.snapshotState();
                c.sim.solverExecutor.snapshotRestored(solverSnapshot);
                check(!c.sim.solverExecutor.hasAcceptedStepAt(c.sim.t) &&
                    !c.converter.canUseAcceptedNewtonPredictor() && c.observer.getSampleCount() == 0,
                    "actual snapshotRestored retires executor reuse authority and invalidates observer samples");
                check(c.converter.matchesAcceptedStateForDeveloperVerification(beforeExecutorRestore) &&
                    c.converter.hasAcceptedPredictorForDeveloperVerification(),
                    "snapshotRestored preserves stored E06 receipt/controller/predictor data without reviving authority");

                c.fixture.enable.setVoltage(0);
                c.sim.solverExecutor.analyze();
                check(receiptReady(c.converter) && !predictorReady(c.converter),
                    "reanalysis invalidates predictor eligibility but retains the last committed receipt");
                E06AveragedConverterElm.AcceptedStateSnapshot beforeDisableStep =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                c.capture.arm(null);
                c.advance(1);
                check(!c.converter.matchesAcceptedStateForDeveloperVerification(beforeDisableStep) &&
                    c.converter.hasAcceptedPredictorForDeveloperVerification() &&
                    !c.converter.canUseAcceptedNewtonPredictor() &&
                    !fieldBoolean(c.converter, "active") && fieldBoolean(c.converter, "acceptedActive"),
                    "committed predictor memory survives a controller transition while next-solve reuse is ineligible");
                assertCommitted(c, c.capture.expected);
            }
        });
        runCase("envelope-reject-and-recovery", new CaseBody() {
            public void run(FixtureCase c) {
                Snapshot before = Snapshot.capture(c.converter);
                E06AveragedConverterElm.AcceptedStateSnapshot modelBefore =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                double beforeTime = c.sim.t;
                c.corruptor.armReverseInput();
                RuntimeException error = advanceFailure(c, 1);
                c.corruptor.restore();
                check(error instanceof IllegalArgumentException &&
                    error.getMessage().contains("reverse input"),
                    "actual E06 accepted-envelope validation rejects the injected reverse-input candidate; caught " +
                    describe(error));
                check(c.corruptor.didCorrupt && !pending(c.converter),
                    "envelope rejection publishes no staged accepted state");
                check(c.sim.t > beforeTime && !c.sim.solverExecutor.hasAcceptedStepAt(c.sim.t),
                    "envelope rejection does not claim rollback of already advanced simulation time");
                before.assertUnchanged(c.converter, "envelope rejection");
                check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore),
                    "envelope rejection retains the exact model-owned committed snapshot");
                recover(c, c.sim.t);

                settleDisabledController(c);
                Snapshot beforeOverflow = Snapshot.capture(c.converter);
                E06AveragedConverterElm.AcceptedStateSnapshot modelBeforeOverflow =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                double beforeOverflowTime = c.sim.t;
                c.corruptor.armOverflowOutput();
                RuntimeException overflowError = advanceFailure(c, 1);
                ExpectedStep overflowCandidate = calculateExpected(c);
                double derivedPowerLoss = overflowCandidate.inputVolts * overflowCandidate.inputCurrent +
                    overflowCandidate.enableVolts * overflowCandidate.enableCurrent +
                    overflowCandidate.feedbackVolts * overflowCandidate.feedbackCurrent -
                    overflowCandidate.outputVolts * overflowCandidate.outputCurrent;
                check(!overflowCandidate.stepActive && fieldDouble(c.converter, "inputOutputGain") == 0,
                    "overflow candidate uses the actually settled disabled controller; " + describeState(c));
                check(finite(overflowCandidate.inputCurrent) && finite(overflowCandidate.outputVolts) &&
                    overflowCandidate.outputVolts > Double.MAX_VALUE / 2 && finite(overflowCandidate.outputCurrent) &&
                    !finite(derivedPowerLoss),
                    "finite extreme positive output and finite currents independently derive nonfinite power; " +
                    describeState(c));
                c.corruptor.restore();
                check(overflowError instanceof IllegalArgumentException &&
                    overflowError.getMessage().contains("Nonfinite accepted E06 power flow"),
                    "accepted-envelope validation rejects overflowed derived power; caught " + describe(overflowError) +
                    "; " + describeState(c));
                check(c.corruptor.didCorrupt && !pending(c.converter),
                    "overflow rejection publishes no staged accepted state");
                check(c.sim.t > beforeOverflowTime && !c.sim.solverExecutor.hasAcceptedStepAt(c.sim.t),
                    "derived-power rejection does not claim rollback of already advanced simulation time");
                beforeOverflow.assertUnchanged(c.converter, "derived-power overflow rejection");
                check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBeforeOverflow),
                    "derived-power rejection retains the exact model-owned committed snapshot");
                recover(c, c.sim.t);
            }
        });
        runCase("later-step-finished-throw-and-recovery", new CaseBody() {
            public void run(FixtureCase c) {
                Snapshot before = Snapshot.capture(c.converter);
                E06AveragedConverterElm.AcceptedStateSnapshot modelBefore =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                double beforeTime = c.sim.t;
                RuntimeException marker = new IllegalStateException("e06-later-step-finished-failure");
                c.capture.arm(marker, before);
                RuntimeException observed = advanceFailure(c, 1);
                check(observed == marker && c.capture.callbackCount == 1,
                    "later actual element stepFinished failure propagates after E06 stages its candidate");
                assertRejectedCandidate(c, before, beforeTime, "later stepFinished failure");
                check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore),
                    "later callback failure leaves all committed E06 memory unchanged");
                recover(c, c.capture.expected.time);
            }
        });
        runCase("accepted-event-throw-and-recovery", new CaseBody() {
            public void run(final FixtureCase c) {
                final Snapshot before = Snapshot.capture(c.converter);
                final E06AveragedConverterElm.AcceptedStateSnapshot modelBefore =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                final double beforeTime = c.sim.t;
                final RuntimeException marker = new IllegalStateException("e06-accepted-event-failure");
                final int[] fired = { 0 };
                c.capture.arm(null);
                c.sim.solverExecutor.events(null).schedule(beforeTime + DT / 2.0,
                    new SolverEventQueue.Action() {
                        public void fire(double scheduledTime, double acceptedTime) {
                            fired[0]++;
                            check(acceptedTime == c.sim.t && c.capture.expected != null &&
                                c.capture.expected.time == acceptedTime,
                                "accepted event observes the solved candidate time");
                            assertProvisional(c, before, c.capture.expected,
                                "accepted event before global boundary");
                            check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore),
                                "accepted event still sees the exact previously committed E06 memory");
                            throw marker;
                        }
                    });
                RuntimeException observed = advanceFailure(c, 1);
                check(observed == marker && fired[0] == 1,
                    "actual accepted-event dispatch failure propagates before global acceptance");
                assertRejectedCandidate(c, before, beforeTime, "accepted event failure");
                check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore),
                    "accepted-event failure retains the exact model-owned committed snapshot");
                resetFailedTestEventQueue(c, "accepted-event failure");
                recover(c, c.capture.expected.time);
            }
        });
        runCase("reset-discards-failed-stage", new CaseBody() {
            public void run(final FixtureCase c) {
                final Snapshot before = Snapshot.capture(c.converter);
                final E06AveragedConverterElm.AcceptedStateSnapshot modelBefore =
                    c.converter.captureAcceptedStateForDeveloperVerification();
                final double beforeTime = c.sim.t;
                final RuntimeException marker = new IllegalStateException("e06-reset-stage-failure");
                c.capture.arm(null);
                c.sim.solverExecutor.events(null).schedule(beforeTime + DT / 2.0,
                    new SolverEventQueue.Action() {
                        public void fire(double scheduledTime, double acceptedTime) {
                            assertProvisional(c, before, c.capture.expected,
                                "reset case accepted event before global boundary");
                            throw marker;
                        }
                    });
                check(advanceFailure(c, 1) == marker, "reset case creates a real unaccepted E06 stage");
                check(pending(c.converter), "failed event left only a private provisional stage");
                before.assertUnchanged(c.converter, "pre-reset event failure");
                check(c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore),
                    "pre-reset state still matches the last committed E06 memory");
                resetFailedTestEventQueue(c, "reset-case accepted-event failure");
                c.converter.reset();
                check(!pending(c.converter) && !pendingPredictor(c.converter) &&
                    !receiptReady(c.converter) && !predictorReady(c.converter),
                    "reset invalidates pending and committed E06 receipts and predictors");
                check(!c.converter.matchesAcceptedStateForDeveloperVerification(modelBefore) &&
                    !c.converter.hasAcceptedPredictorForDeveloperVerification(),
                    "reset no longer matches the previous committed snapshot and invalidates predictor reuse");
                check(!c.converter.isActive() && c.converter.getDuty() == 0 &&
                    c.converter.getIntegral() == 0 && c.converter.getInputCurrent() == 0 &&
                    c.converter.getOutputCurrent() == 0,
                    "reset restores the independent cold accepted-state expectation");
                c.converter.solverStepAccepted();
                check(!receiptReady(c.converter) && !predictorReady(c.converter) &&
                    c.converter.getInputCurrent() == 0 && c.converter.getOutputCurrent() == 0,
                    "a stale acceptance callback cannot publish a stage cleared by reset");
            }
        });
        System.out.println("PASS: E06 accepted-state contracts assertions=" + assertions +
            " cases=" + cases + " scope=REAL_CIRCUITJS_STEP_FINISHED_AND_GLOBAL_ACCEPTANCE");
    }

    private static void recover(FixtureCase c, double staleCandidateTime) {
        PredictorSnapshot predictor = PredictorSnapshot.capture(c.converter);
        c.startProbe.arm(predictor);
        c.capture.arm(null);
        double beforeRecovery = c.sim.t;
        c.advance(1);
        check(c.startProbe.observed && c.startProbe.clearedPending && c.startProbe.retainedPredictor,
            "next actual startIteration clears the failed stage and preserves the prior committed predictor");
        check(c.startProbe.rejectedStalePredictor,
            "recovery does not seed Newton from a committed predictor whose accepted time is stale");
        check(c.sim.t > beforeRecovery && c.sim.t > staleCandidateTime,
            "recovery commits a fresh solver step after the rejected candidate time");
        check(c.capture.expected != null && c.capture.expected.time == c.sim.t,
            "recovery captured its own current solved candidate");
        assertCommitted(c, c.capture.expected);
    }

    private static void assertRejectedCandidate(FixtureCase c, Snapshot before,
            double beforeTime, String label) {
        check(pending(c.converter), label + " leaves an isolated candidate for later start/reset cleanup");
        check(c.capture.expected != null && c.capture.expected.time > beforeTime,
            label + " reached E06 stepFinished on the actual solved graph");
        check(c.sim.t > beforeTime && !c.sim.solverExecutor.hasAcceptedStepAt(c.sim.t),
            label + " is not reported as globally accepted");
        before.assertUnchanged(c.converter, label);
        check(pendingPredictor(c.converter), label + " cannot replace the committed predictor early");
    }

    private static void assertProvisional(FixtureCase c, Snapshot before,
            ExpectedStep expected, String label) {
        check(expected != null && pending(c.converter) && pendingPredictor(c.converter),
            label + " sees a validated private candidate");
        check(!c.sim.solverExecutor.hasAcceptedStepAt(expected.time),
            label + " runs before the executor acceptance boundary");
        before.assertUnchanged(c.converter, label);
        // Physical readers follow the staged raw solution. Snapshot's input/output
        // receipt checks above remain committed; a failed boundary does not roll
        // back simulation time or scope sampling.
        near(c.converter.getCurrent(), expected.inputCurrent, 1e-7,
            label + " physical current getter exposes the provisional solved candidate");
        near(c.converter.getCurrentIntoNode(E06ConverterContract.IN_PLUS), -expected.inputCurrent, 1e-7,
            label + " terminal current getter exposes the provisional solved candidate");
        near(fieldDouble(c.converter, "pendingInputVolts"), expected.inputVolts, 1e-12,
            label + " has the independently measured pending input voltage");
        near(fieldDouble(c.converter, "pendingOutputVolts"), expected.outputVolts, 1e-12,
            label + " has the independently measured pending output voltage");
    }

    private static void assertCommitted(FixtureCase c, ExpectedStep e) {
        E06AveragedConverterElm model = c.converter;
        check(receiptReady(model) && !pending(model),
            "only executor acceptance publishes and consumes the new E06 receipt");
        check(model.isActive() == e.stepActive, "accepted control snapshot matches the state used by this solve");
        check(model.getCycleIndex() == e.stepCycleIndex, "accepted cycle snapshot matches the solved step");
        near(model.getDuty(), e.stepDuty, 1e-12, "accepted duty snapshot");
        near(model.getIntegral(), e.stepIntegral, 1e-12, "accepted integral snapshot");
        near(model.getActiveSeconds(), e.stepActiveSeconds, 1e-12, "accepted soft-start snapshot");
        near(fieldDouble(model, "acceptedInputVolts"), e.inputVolts, 1e-12, "accepted input voltage");
        near(fieldDouble(model, "acceptedOutputVolts"), e.outputVolts, 1e-12, "accepted output voltage");
        near(fieldDouble(model, "acceptedEnableVolts"), e.enableVolts, 1e-12, "accepted enable voltage");
        near(fieldDouble(model, "acceptedFeedbackVolts"), e.feedbackVolts, 1e-12, "accepted feedback voltage");
        near(model.getInputCurrent(), e.inputCurrent, 1e-7, "accepted input current from independent converter law");
        near(model.getOutputCurrent(), e.outputCurrent, 1e-7, "accepted output current from independent converter law");
        near(model.getCurrentIntoNode(4), -e.enableCurrent, 1e-12, "accepted enable terminal current");
        near(model.getCurrentIntoNode(5), -e.feedbackCurrent, 1e-12, "accepted feedback terminal current");
        near(model.getInputPowerWatts(), e.inputVolts * e.inputCurrent, 1e-6, "accepted input power");
        near(model.getOutputPowerWatts(), e.outputVolts * e.outputCurrent, 1e-6, "accepted output power");
        near(model.getDeclaredResistanceLossWatts(), .5 * e.driveCurrent * e.driveCurrent,
            1e-8, "accepted explicit resistance loss");
        near(model.getDeclaredConversionLossWatts(), Math.max(0, e.outputVolts * e.driveCurrent) *
            (1 / .85 - 1), 1e-7, "accepted positive-power conversion loss");
        check(predictorReady(model) && fieldDouble(model, "predictorTime") == e.time &&
            fieldDouble(model, "predictorTimeStep") == e.dt &&
            model.hasAcceptedPredictorForDeveloperVerification(),
            "the stored predictor is valid only after global acceptance");
        near(fieldDouble(model, "predictorTarget"), e.lastTarget, 1e-12, "accepted Newton target predictor");
        near(fieldDouble(model, "predictorDrop"), e.lastDrop, 1e-12, "accepted Newton drop predictor");
        near(fieldDouble(model, "predictorInput"), e.lastInput, 1e-12, "accepted Newton input predictor");
        check(fieldBoolean(model, "active") == e.nextActive &&
            fieldLong(model, "cycleIndex") == e.nextCycleIndex,
            "accepted callback commits the independently calculated next control state");
        near(fieldDouble(model, "activeSeconds"), e.nextActiveSeconds, 1e-12,
            "accepted callback commits next soft-start state");
        near(fieldDouble(model, "integral"), e.nextIntegral, 1e-12,
            "accepted callback commits next integral state");
        near(fieldDouble(model, "duty"), e.nextDuty, 1e-12,
            "accepted callback commits next duty state");
    }

    private static void assertCurrentPathCanary(FixtureCase c, ExpectedStep expected) {
        ScopePlot plot = new ScopePlot(c.converter, Scope.UNITS_A, Scope.VAL_CURRENT, 1);
        plot.reset(8, 1, true);
        plot.timeStep();
        near(plot.lastValue, expected.inputCurrent, 1e-7,
            "real ScopePlot.timeStep samples the staged input current before acceptance");

        c.sim.calcWireCurrents();
        Point inputPost = c.converter.getPost(E06ConverterContract.IN_PLUS);
        CirSim.WireInfo inputInfo = null;
        for (CirSim.WireInfo info : c.sim.wireInfoList)
            if (info.wire == c.inputLead) { inputInfo = info; break; }
        check(inputInfo != null && inputInfo.post == 0 && samePoint(c.inputLead.getPost(0), inputPost) &&
            inputInfo.currentNeighbors.length == 1 && inputInfo.currentNeighbors[0] == c.converter &&
            inputInfo.currentNeighborPosts[0] == E06ConverterContract.IN_PLUS,
            "selected wire-current path reads the E06 input post directly at its unbranched lead");
        near(c.inputLead.getCurrent(), -expected.inputCurrent, 1e-7,
            "real CirSim.calcWireCurrents preserves signed staged current at the E06 IN+ endpoint");

        double[] expectedPostCurrents = {
            -expected.inputCurrent,
            expected.inputCurrent + expected.enableCurrent + expected.feedbackCurrent,
            expected.outputCurrent,
            -expected.outputCurrent,
            -expected.enableCurrent,
            -expected.feedbackCurrent
        };
        double independentCurrentSum = 0;
        for (int post = 0; post < expectedPostCurrents.length; post++) {
            double tolerance = post == E06ConverterContract.ENABLE || post == E06ConverterContract.FEEDBACK ?
                1e-12 : 1e-7;
            near(c.converter.getCurrentIntoNode(post), expectedPostCurrents[post], tolerance,
                "staged E06 terminal injection post " + post + " matches independent current law");
            independentCurrentSum += expectedPostCurrents[post];
        }
        near(independentCurrentSum, 0, 1e-12,
            "independent six-post E06 current equations satisfy terminal KCL before acceptance");
    }

    private static boolean samePoint(Point first, Point second) {
        return first.x == second.x && first.y == second.y;
    }

    private static WireElm findInputLead(Vector<CircuitElm> elements, E06AveragedConverterElm converter) {
        Point inputPost = converter.getPost(E06ConverterContract.IN_PLUS);
        for (CircuitElm element : elements) if (element instanceof WireElm) {
            WireElm wire = (WireElm)element;
            if (samePoint(wire.getPost(0), inputPost) || samePoint(wire.getPost(1), inputPost)) return wire;
        }
        return null;
    }

    private static void settleDisabledController(FixtureCase c) {
        c.fixture.enable.setVoltage(0);
        c.sim.solverExecutor.analyze();
        int accepted = 0;
        while (accepted < 8000 && !disabledControllerReady(c)) {
            c.capture.arm(null);
            c.advance(1);
            accepted++;
        }
        check(accepted > 0 && disabledControllerReady(c),
            "bounded real control settlement reaches disabled E06 state in " + accepted +
            " accepted steps; " + describeState(c));
    }

    private static boolean disabledControllerReady(FixtureCase c) {
        double enableVolts = c.converter.getEnableVoltage();
        return finite(enableVolts) && enableVolts < 2 && !fieldBoolean(c.converter, "active") &&
            !fieldBoolean(c.converter, "stepActive") && !c.converter.isActive();
    }

    private static String describeState(FixtureCase c) {
        return "time=" + c.sim.t + ",sourceEnable=" + c.fixture.enable.getSourceVoltage() +
            ",enableVdiff=" + c.converter.getEnableVoltage() + ",controllerActive=" +
            fieldBoolean(c.converter, "active") + ",stepActive=" + fieldBoolean(c.converter, "stepActive") +
            ",acceptedActive=" + c.converter.isActive() + ",inputV=" + c.converter.getInputVoltage() +
            ",outputV=" + c.converter.getOutputVoltage() + ",duty=" + c.converter.getDuty();
    }

    private static ExpectedStep calculateExpected(FixtureCase c) {
        E06AveragedConverterElm model = c.converter;
        ExpectedStep e = new ExpectedStep();
        e.time = c.sim.t; e.dt = c.sim.timeStep;
        e.inputVolts = model.getInputVoltage(); e.outputVolts = model.getOutputVoltage();
        e.enableVolts = model.getEnableVoltage(); e.feedbackVolts = model.getFeedbackVoltage();
        e.stepActive = fieldBoolean(model, "stepActive");
        e.stepDuty = fieldDouble(model, "stepDuty");
        e.stepIntegral = fieldDouble(model, "stepIntegral");
        e.stepActiveSeconds = fieldDouble(model, "stepActiveSeconds");
        e.stepCycleIndex = fieldLong(model, "stepCycleIndex");
        e.lastTarget = fieldDouble(model, "lastTarget");
        e.lastDrop = fieldDouble(model, "lastDrop");
        e.lastInput = fieldDouble(model, "lastInput");
        boolean priorActive = fieldBoolean(model, "active");
        double priorActiveSeconds = fieldDouble(model, "activeSeconds");
        double priorIntegral = fieldDouble(model, "integral");
        double priorDuty = fieldDouble(model, "duty");
        long priorCycle = fieldLong(model, "cycleIndex");
        double requestedDrive = e.stepActive && e.inputVolts > 0 ?
            e.stepDuty * .25 * e.inputVolts - e.outputVolts : -1;
        e.driveCurrent = e.stepActive && e.inputVolts > 0 ? clamp(requestedDrive / .5, 0, 1) : 0;
        e.inputCurrent = e.inputVolts / 1000000.0 + .00015 * clamp(e.inputVolts / 10, 0, 1);
        if (e.stepActive && e.inputVolts > 0) {
            e.inputCurrent += (Math.max(0, e.outputVolts * e.driveCurrent) / .85 +
                .5 * e.driveCurrent * e.driveCurrent) / e.inputVolts;
        }
        e.outputCurrent = e.driveCurrent - e.outputVolts / 1000000.0;
        e.enableCurrent = e.enableVolts / 1000000.0;
        e.feedbackCurrent = e.feedbackVolts / 1000000.0;
        e.nextActive = priorActive ? e.inputVolts > 90 && e.enableVolts > 2 :
            e.inputVolts >= 95 && e.enableVolts >= 3;
        e.nextActiveSeconds = e.nextActive ? (priorActive ? priorActiveSeconds : 0) + e.dt : 0;
        e.nextIntegral = expectedIntegral(priorIntegral, e.inputVolts, e.feedbackVolts,
            e.nextActiveSeconds, e.dt, e.nextActive);
        e.nextCycleIndex = (long)Math.floor((e.time + 1e-12) / .0005);
        e.nextDuty = priorDuty;
        if (!e.nextActive) e.nextDuty = 0;
        else if (e.nextCycleIndex != priorCycle) e.nextDuty = expectedDuty(e.inputVolts,
            e.feedbackVolts, e.nextActiveSeconds, e.nextIntegral);
        return e;
    }

    private static double expectedIntegral(double previous, double input, double feedback,
            double activeSeconds, double dt, boolean active) {
        if (!active || activeSeconds <= .020) return 0;
        double error = feedback - 6;
        double raw = 12 / (.25 * input) + .00025 * error + previous;
        double increment = .025 * error * dt;
        if (raw >= .45 && increment > 0 || raw <= 0 && increment < 0) return previous;
        return clamp(previous + increment, -.45, .45);
    }

    private static double expectedDuty(double input, double feedback,
            double activeSeconds, double integral) {
        double raw = 12 / (.25 * input) + .00025 * (feedback - 6) + integral;
        return clamp(raw, 0, .45) * clamp(activeSeconds / .020, 0, 1);
    }

    private static void runCase(String id, CaseBody body) {
        FixtureCase c = null;
        Throwable failure = null;
        long started = System.currentTimeMillis();
        try {
            c = new FixtureCase();
            c.initialize();
            c.prime();
            body.run(c);
        } catch (Throwable error) { failure = error; }
        finally {
            if (c != null) {
                Throwable cleanup = c.close();
                if (cleanup != null) {
                    if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
                }
            }
        }
        if (failure != null) raise(failure);
        cases++;
        System.out.println("E06_ACCEPTED_STATE_CASE id=" + id + " status=PASS elapsedMs=" +
            (System.currentTimeMillis() - started));
    }

    private static RuntimeException advanceFailure(FixtureCase c, int steps) {
        try { c.advance(steps); }
        catch (RuntimeException error) { return error; }
        throw new AssertionError("Expected the real CircuitJS step to fail before acceptance");
    }

    private static void resetFailedTestEventQueue(FixtureCase c, String label) {
        SolverEventQueue queue = c.sim.solverExecutor.events(null);
        boolean rejected = false;
        try { queue.dispatchAccepted(c.sim.t); }
        catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains("requires explicit reset");
        }
        check(rejected, label + " leaves only its test-owned event queue failed until explicit reset");
        queue.reset(c.sim.t);
        check(queue.dispatchAccepted(c.sim.t) == 0,
            label + " resets its test-owned queue at the advanced simulation time without rolling back time");
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError("E06 accepted-state contract: " + label);
    }

    private static String describe(Throwable error) {
        return error == null ? "no RuntimeException" : error.getClass().getName() + ": " + error.getMessage();
    }

    private static void near(double actual, double expected, double tolerance, String label) {
        check(finite(actual) && finite(expected) && Math.abs(actual - expected) <= tolerance,
            label + " actual=" + actual + " expected=" + expected + " tolerance=" + tolerance);
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }

    private static Field field(String name) {
        try {
            Field field = E06AveragedConverterElm.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception error) { throw new AssertionError("Missing E06 accepted-state oracle field " + name, error); }
    }
    private static boolean fieldBoolean(E06AveragedConverterElm model, String name) {
        try { return field(name).getBoolean(model); } catch (Exception error) { throw new AssertionError(error); }
    }
    private static long fieldLong(E06AveragedConverterElm model, String name) {
        try { return field(name).getLong(model); } catch (Exception error) { throw new AssertionError(error); }
    }
    private static double fieldDouble(E06AveragedConverterElm model, String name) {
        try { return field(name).getDouble(model); } catch (Exception error) { throw new AssertionError(error); }
    }
    private static boolean pending(E06AveragedConverterElm model) { return fieldBoolean(model, "pendingAcceptedReady"); }
    private static boolean pendingPredictor(E06AveragedConverterElm model) { return fieldBoolean(model, "pendingPredictorReady"); }
    private static boolean receiptReady(E06AveragedConverterElm model) { return fieldBoolean(model, "receiptReady"); }
    private static boolean predictorReady(E06AveragedConverterElm model) { return fieldBoolean(model, "predictorReady"); }

    private static void raise(Throwable error) {
        if (error instanceof Error) throw (Error)error;
        if (error instanceof RuntimeException) throw (RuntimeException)error;
        throw new IllegalStateException(error);
    }

    private interface CaseBody { void run(FixtureCase c); }

    private static final class ExpectedStep {
        double time, dt, inputVolts, outputVolts, enableVolts, feedbackVolts;
        double driveCurrent, inputCurrent, outputCurrent, enableCurrent, feedbackCurrent;
        boolean stepActive, nextActive;
        double stepDuty, stepIntegral, stepActiveSeconds;
        long stepCycleIndex, nextCycleIndex;
        double nextActiveSeconds, nextIntegral, nextDuty;
        double lastTarget, lastDrop, lastInput;
    }

    private static final class Snapshot {
        boolean receipt, predictor, active, internalActive;
        double inputCurrent, outputCurrent, duty, integral, activeSeconds;
        double internalDuty, internalIntegral, internalActiveSeconds;
        double acceptedInput, acceptedOutput, acceptedEnable, acceptedFeedback;
        double acceptedDuty, acceptedIntegral, acceptedActiveSeconds;
        double predictorTarget, predictorDrop, predictorInput, predictorTime, predictorTimeStep;
        long cycleIndex, acceptedCycleIndex, internalCycleIndex;

        static Snapshot capture(E06AveragedConverterElm model) {
            Snapshot s = new Snapshot();
            s.receipt = receiptReady(model); s.predictor = predictorReady(model);
            s.active = model.isActive(); s.internalActive = fieldBoolean(model, "active");
            s.inputCurrent = model.getInputCurrent(); s.outputCurrent = model.getOutputCurrent();
            s.duty = model.getDuty(); s.integral = model.getIntegral(); s.activeSeconds = model.getActiveSeconds();
            s.cycleIndex = model.getCycleIndex();
            s.internalDuty = fieldDouble(model, "duty");
            s.internalIntegral = fieldDouble(model, "integral");
            s.internalActiveSeconds = fieldDouble(model, "activeSeconds");
            s.internalCycleIndex = fieldLong(model, "cycleIndex");
            s.acceptedInput = fieldDouble(model, "acceptedInputVolts");
            s.acceptedOutput = fieldDouble(model, "acceptedOutputVolts");
            s.acceptedEnable = fieldDouble(model, "acceptedEnableVolts");
            s.acceptedFeedback = fieldDouble(model, "acceptedFeedbackVolts");
            s.acceptedDuty = fieldDouble(model, "acceptedDuty");
            s.acceptedIntegral = fieldDouble(model, "acceptedIntegral");
            s.acceptedActiveSeconds = fieldDouble(model, "acceptedActiveSeconds");
            s.acceptedCycleIndex = fieldLong(model, "acceptedCycleIndex");
            s.predictorTarget = fieldDouble(model, "predictorTarget");
            s.predictorDrop = fieldDouble(model, "predictorDrop");
            s.predictorInput = fieldDouble(model, "predictorInput");
            s.predictorTime = fieldDouble(model, "predictorTime");
            s.predictorTimeStep = fieldDouble(model, "predictorTimeStep");
            return s;
        }

        void assertUnchanged(E06AveragedConverterElm model, String label) {
            Snapshot now = capture(model);
            check(receipt == now.receipt && predictor == now.predictor && active == now.active &&
                internalActive == now.internalActive && cycleIndex == now.cycleIndex &&
                acceptedCycleIndex == now.acceptedCycleIndex && internalCycleIndex == now.internalCycleIndex,
                label + " leaves receipt readiness and committed active/cycle state unchanged");
            same(inputCurrent, now.inputCurrent, label + " input receipt");
            same(outputCurrent, now.outputCurrent, label + " output receipt");
            same(duty, now.duty, label + " duty receipt");
            same(integral, now.integral, label + " integral receipt");
            same(activeSeconds, now.activeSeconds, label + " active-seconds receipt");
            same(internalDuty, now.internalDuty, label + " committed next duty");
            same(internalIntegral, now.internalIntegral, label + " committed next integral");
            same(internalActiveSeconds, now.internalActiveSeconds, label + " committed next active seconds");
            same(acceptedInput, now.acceptedInput, label + " accepted input voltage");
            same(acceptedOutput, now.acceptedOutput, label + " accepted output voltage");
            same(acceptedEnable, now.acceptedEnable, label + " accepted enable voltage");
            same(acceptedFeedback, now.acceptedFeedback, label + " accepted feedback voltage");
            same(acceptedDuty, now.acceptedDuty, label + " accepted duty field");
            same(acceptedIntegral, now.acceptedIntegral, label + " accepted integral field");
            same(acceptedActiveSeconds, now.acceptedActiveSeconds, label + " accepted active-seconds field");
            same(predictorTarget, now.predictorTarget, label + " committed predictor target");
            same(predictorDrop, now.predictorDrop, label + " committed predictor drop");
            same(predictorInput, now.predictorInput, label + " committed predictor input");
            same(predictorTime, now.predictorTime, label + " committed predictor time");
            same(predictorTimeStep, now.predictorTimeStep, label + " committed predictor timestep");
        }
    }

    private static void same(double before, double after, String label) {
        check(Double.doubleToLongBits(before) == Double.doubleToLongBits(after),
            label + " changed from " + before + " to " + after);
    }

    private static final class PredictorSnapshot {
        boolean ready;
        double target, drop, input, time, timeStep;
        static PredictorSnapshot capture(E06AveragedConverterElm model) {
            PredictorSnapshot s = new PredictorSnapshot();
            s.ready = predictorReady(model);
            s.target = fieldDouble(model, "predictorTarget");
            s.drop = fieldDouble(model, "predictorDrop");
            s.input = fieldDouble(model, "predictorInput");
            s.time = fieldDouble(model, "predictorTime");
            s.timeStep = fieldDouble(model, "predictorTimeStep");
            return s;
        }
        boolean matches(E06AveragedConverterElm model) {
            return ready == predictorReady(model) &&
                Double.doubleToLongBits(target) == Double.doubleToLongBits(fieldDouble(model, "predictorTarget")) &&
                Double.doubleToLongBits(drop) == Double.doubleToLongBits(fieldDouble(model, "predictorDrop")) &&
                Double.doubleToLongBits(input) == Double.doubleToLongBits(fieldDouble(model, "predictorInput")) &&
                Double.doubleToLongBits(time) == Double.doubleToLongBits(fieldDouble(model, "predictorTime")) &&
                Double.doubleToLongBits(timeStep) == Double.doubleToLongBits(fieldDouble(model, "predictorTimeStep"));
        }
    }

    private static final class FixtureCase {
        final CirSim previousSim = CircuitElm.sim, previousSingleton = CirSim.theSim;
        final String previousDiode = DiodeElm.lastModelName, previousZener = ZenerElm.lastZenerModelName;
        final String previousTransistor = TransistorElm.lastModelName;
        final int previousMosfetFlags = MosfetElm.globalFlags;
        final double previousBeta = MosfetElm.lastBeta;
        final TestSim sim = new TestSim(DT);
        final Vector<CircuitElm> original = sim.elmList;
        E06ConverterFixtures.Fixture fixture;
        EnvelopeCorruptor corruptor;
        WireElm inputLead;
        SolverTimeObservationService.Subscription observer;
        StartIterationProbe startProbe;
        CaptureElm capture;
        E06AveragedConverterElm converter;

        void activate() { CircuitElm.sim = sim; CirSim.theSim = sim; }

        void initialize() {
            activate();
            fixture = E06ConverterFixtures.averaged();
            converter = fixture.averaged;
            int at = fixture.elements.indexOf(converter);
            if (at < 0) throw new AssertionError("E06 fixture omitted its averaged converter");
            inputLead = findInputLead(fixture.elements, converter);
            if (inputLead == null) throw new AssertionError("E06 fixture omitted its input lead");
            // Reverse only this wire's orientation, preserving its geometry and graph,
            // so CircuitJS resolves current from the unbranched E06 endpoint itself.
            int x = inputLead.x, y = inputLead.y;
            inputLead.x = inputLead.x2; inputLead.y = inputLead.y2;
            inputLead.x2 = x; inputLead.y2 = y;
            inputLead.setPoints();
            corruptor = new EnvelopeCorruptor(converter);
            fixture.elements.add(at, corruptor);
            at = fixture.elements.indexOf(converter);
            startProbe = new StartIterationProbe(converter);
            fixture.elements.add(at + 1, startProbe);
            capture = new CaptureElm(this);
            fixture.elements.add(capture);
            fixture.enable.setVoltage(5);
            sim.elmList = fixture.elements;
            sim.a01MeasurementRunning = true;
            sim.solverExecutor.analyze();
        }

        void prime() {
            int steps = 0;
            while (steps < 8000 && !(sim.t >= .030 && converter.getOutputVoltage() > .1 &&
                    converter.canUseAcceptedNewtonPredictor() &&
                    E06ConverterContract.cycleIndex(sim.t + DT) == E06ConverterContract.cycleIndex(sim.t))) {
                advance(1); steps++;
            }
            check(steps < 8000 && converter.canUseAcceptedNewtonPredictor(),
                "actual powered E06 circuit reaches a compatible accepted predictor");
        }

        void advance(int count) { activate(); sim.solverExecutor.advanceSteps(count); }

        Throwable close() {
            Throwable failure = null;
            activate();
            if (observer != null) {
                sim.solverTimeObservations.unsubscribe(observer);
                observer = null;
            }
            if (fixture != null) {
                sim.elmList = original;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (Throwable error) { failure = error; }
                sim.circuitMatrix = null;
                for (CircuitElm element : new Vector<CircuitElm>(fixture.elements)) {
                    try { element.delete(); }
                    catch (Throwable error) {
                        if (failure == null) failure = error; else failure.addSuppressed(error);
                    }
                }
                fixture.elements.clear();
            }
            sim.a01MeasurementRunning = false;
            DiodeElm.lastModelName = previousDiode;
            ZenerElm.lastZenerModelName = previousZener;
            TransistorElm.lastModelName = previousTransistor;
            MosfetElm.globalFlags = previousMosfetFlags;
            MosfetElm.lastBeta = previousBeta;
            CircuitElm.sim = previousSim;
            CirSim.theSim = previousSingleton;
            if (failure == null && (sim.elmList != original || !original.isEmpty() ||
                    sim.solverExecutor.isUnavailable() || CircuitElm.sim != previousSim ||
                    CirSim.theSim != previousSingleton))
                failure = new AssertionError("E06 accepted-state fixture cleanup failed");
            return failure;
        }
    }

    private static final class CaptureElm extends CallbackElm {
        final FixtureCase fixture;
        boolean armed, currentPathArmed, currentPathObserved;
        int callbackCount;
        RuntimeException failure;
        Snapshot unchangedBefore;
        ExpectedStep expected;
        CaptureElm(FixtureCase fixture) { this.fixture = fixture; }
        void arm(RuntimeException failure) {
            arm(failure, null);
        }
        void arm(RuntimeException failure, Snapshot unchangedBefore) {
            this.failure = failure; this.unchangedBefore = unchangedBefore;
            armed = true; expected = null; callbackCount = 0;
        }
        void armCurrentPathCanary() {
            currentPathArmed = true;
            currentPathObserved = false;
        }
        void stepFinished() {
            if (!armed && !currentPathArmed) return;
            if (armed) {
                armed = false; callbackCount++;
                expected = calculateExpected(fixture);
                if (unchangedBefore != null)
                    unchangedBefore.assertUnchanged(fixture.converter,
                        "later stepFinished callback before global acceptance");
                if (failure != null) throw failure;
            } else {
                expected = calculateExpected(fixture);
            }
            if (currentPathArmed) {
                currentPathArmed = false;
                assertCurrentPathCanary(fixture, expected);
                currentPathObserved = true;
            }
        }
    }

    private static final class EnvelopeCorruptor extends CallbackElm {
        private static final int REVERSE_INPUT = 0, OVERFLOW_OUTPUT = 1;
        final E06AveragedConverterElm converter;
        boolean armed, didCorrupt;
        int mode, voltageIndex;
        double originalVoltage;
        EnvelopeCorruptor(E06AveragedConverterElm converter) { this.converter = converter; }
        void armReverseInput() {
            mode = REVERSE_INPUT; armed = true; didCorrupt = false;
        }
        void armOverflowOutput() {
            mode = OVERFLOW_OUTPUT; armed = true; didCorrupt = false;
        }
        void stepFinished() {
            if (!armed) return;
            armed = false;
            voltageIndex = mode == REVERSE_INPUT ? 0 : 2;
            originalVoltage = converter.volts[voltageIndex];
            converter.volts[voltageIndex] = mode == REVERSE_INPUT ? converter.volts[1] - 1 : Double.MAX_VALUE;
            didCorrupt = true;
        }
        void restore() {
            if (didCorrupt) converter.volts[voltageIndex] = originalVoltage;
        }
    }

    private static final class StartIterationProbe extends CallbackElm {
        final E06AveragedConverterElm converter;
        boolean armed, observed, clearedPending, retainedPredictor;
        boolean usedPredictor, rejectedStalePredictor;
        PredictorSnapshot expected;
        StartIterationProbe(E06AveragedConverterElm converter) { this.converter = converter; }
        void arm(PredictorSnapshot expected) {
            this.expected = expected; armed = true; observed = false;
            clearedPending = retainedPredictor = usedPredictor = rejectedStalePredictor = false;
        }
        void startIteration() {
            if (!armed) return;
            armed = false; observed = true;
            clearedPending = !pending(converter) && !pendingPredictor(converter);
            retainedPredictor = expected.matches(converter);
            boolean compatible = converter.canUseAcceptedNewtonPredictor();
            usedPredictor = compatible &&
                Double.doubleToLongBits(fieldDouble(converter, "lastTarget")) ==
                    Double.doubleToLongBits(expected.target) &&
                Double.doubleToLongBits(fieldDouble(converter, "lastDrop")) ==
                    Double.doubleToLongBits(expected.drop) &&
                Double.doubleToLongBits(fieldDouble(converter, "lastInput")) ==
                    Double.doubleToLongBits(expected.input);
            rejectedStalePredictor = !compatible &&
                Double.isNaN(fieldDouble(converter, "lastTarget")) &&
                Double.isNaN(fieldDouble(converter, "lastDrop")) &&
                Double.isNaN(fieldDouble(converter, "lastInput"));
        }
    }

    private static class CallbackElm extends CircuitElm {
        CallbackElm() { super(0, 0); }
        int getDumpType() { return 'x'; }
        int getPostCount() { return 0; }
        void setPoints() {
            point1 = new Point(x, y); point2 = point1;
            boundingBox = new Rectangle(x, y, 1, 1);
        }
    }

    private static final class TestSim extends CirSim {
        TestSim(double dt) {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = dt; adjustTimeStep = false;
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message; circuitMatrix = null; stopElm = element;
            setSimRunning(false); analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
