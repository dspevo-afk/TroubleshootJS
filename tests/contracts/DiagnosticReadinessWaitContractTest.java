package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;

/** Control-flow contracts for bounded, exact-owner diagnostic readiness waits. */
public final class DiagnosticReadinessWaitContractTest {
    private static int assertions;

    private DiagnosticReadinessWaitContractTest() { }

    public static void main(String[] args) {
        assertions = 0;
        verifiesAlreadyReadyDoesNotAdvance();
        verifiesWaitingAndDischargeStopAtReadiness();
        verifiesNeverReadyUsesTheExactBound();
        verifiesRefusalsDoNotAdvance();
        verifiesInvalidInputsAreRejected();
        verifiesOwnerAndAdvanceFailuresStopThePrefix();
        verifiesImmutableReadinessWaitDeclarations();
        System.out.println("PASS: diagnostic readiness wait contracts assertions=" + assertions);
    }

    private static void verifiesAlreadyReadyDoesNotAdvance() {
        FakeHost host = new FakeHost(ActiveMeasurementReadiness.READY);
        double elapsed = runWait(host, .35);
        check(elapsed == 0.0, "already-ready target returns zero requested time");
        check(host.readinessQueries == 1 && host.advances.isEmpty(),
            "already-ready target is queried once without solver advance");
        check(host.events.get(0).equals("current") &&
                host.events.get(1).equals("readiness") &&
                host.events.get(2).equals("current"),
            "initial readiness query is bracketed by current-owner checks");
    }

    private static void verifiesWaitingAndDischargeStopAtReadiness() {
        FakeHost host = new FakeHost(ActiveMeasurementReadiness.WAITING,
            ActiveMeasurementReadiness.DISCHARGE, ActiveMeasurementReadiness.READY);
        double elapsed = runWait(host, 1.0);
        check(close(elapsed, .2), "READY returns the requested elapsed boundary");
        check(host.readinessQueries == 3 && host.advances.size() == 2,
            "WAITING and DISCHARGE advance only until the READY query");
        check(close(host.advances.get(0), .1) && close(host.advances.get(1), .1),
            "each wait request advances one equal tenth-second segment");
        verifySuccessfulGuards(host);
        check(host.events.get(host.events.size() - 1).equals("current"),
            "READY return performs its post-query owner check and no advance");
    }

    private static void verifiesNeverReadyUsesTheExactBound() {
        FakeHost host = new FakeHost(ActiveMeasurementReadiness.WAITING,
            ActiveMeasurementReadiness.DISCHARGE);
        double elapsed = runWait(host, .35);
        check(close(elapsed, .35), "never-ready result reports the declared maximum");
        check(host.readinessQueries == 11 && host.advances.size() == 10,
            "never-ready host receives eleven queries and exactly ten advances");
        double total = 0;
        for (Double seconds : host.advances) {
            check(seconds > 0 && close(seconds, .035),
                "each fractional-bound segment is positive and equal");
            total += seconds;
        }
        check(close(total, .35), "requested advance total equals, and does not exceed, maximum");
        verifySuccessfulGuards(host);
    }

    private static void verifiesRefusalsDoNotAdvance() {
        ActiveMeasurementReadiness[] refused = new ActiveMeasurementReadiness[] {
            ActiveMeasurementReadiness.POWER_OFF,
            ActiveMeasurementReadiness.UNKNOWN,
            ActiveMeasurementReadiness.UNSUPPORTED,
            ActiveMeasurementReadiness.ISOLATE_COMPONENT,
            null
        };
        for (ActiveMeasurementReadiness readiness : refused) {
            final ActiveMeasurementReadiness refusedState = readiness;
            final FakeHost host = new FakeHost(refusedState);
            RuntimeException failure = capture(new Action() {
                public void run() { runWait(host, .5); }
            });
            check(failure instanceof IllegalStateException,
                "non-waitable readiness is refused immediately: " + refusedState);
            check(host.readinessQueries == 1 && host.advances.isEmpty(),
                "refused readiness causes no solver advance: " + refusedState);
            check(host.events.size() == 3 && host.events.get(0).equals("current") &&
                    host.events.get(1).equals("readiness") &&
                    host.events.get(2).equals("current"),
                "refused readiness has only its bracketed first query: " + refusedState);
        }
    }

    private static void verifiesInvalidInputsAreRejected() {
        check(capture(new Action() {
            public void run() { runWait(null, .5); }
        }) instanceof IllegalArgumentException, "null wait host is rejected");

        final double[] invalid = new double[] {
            0.0, -0.01, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, 1.000001
        };
        for (final double maximum : invalid) {
            check(capture(new Action() {
                public void run() { runWait(new FakeHost(ActiveMeasurementReadiness.READY), maximum); }
            }) instanceof IllegalArgumentException,
                "invalid maximum is rejected: " + maximum);
        }

        FakeHost lowerBoundary = new FakeHost(ActiveMeasurementReadiness.READY);
        FakeHost upperBoundary = new FakeHost(ActiveMeasurementReadiness.READY);
        check(runWait(lowerBoundary, .0001) == 0.0 && runWait(upperBoundary, 1.0) == 0.0,
            "positive finite lower and inclusive one-second upper bounds are accepted");
    }

    private static void verifiesOwnerAndAdvanceFailuresStopThePrefix() {
        final FakeHost initiallyStale = new FakeHost(ActiveMeasurementReadiness.WAITING);
        initiallyStale.failCurrentCall = 1;
        RuntimeException initialFailure = capture(new Action() {
            public void run() { runWait(initiallyStale, .5); }
        });
        check(initialFailure == initiallyStale.currentFailure,
            "initial owner-loss exception propagates unchanged");
        check(initiallyStale.readinessQueries == 0 && initiallyStale.advances.isEmpty(),
            "initial owner loss prevents readiness query and advance");

        final FakeHost lostDuringReadyQuery = new FakeHost(ActiveMeasurementReadiness.READY);
        lostDuringReadyQuery.failCurrentCall = 2;
        RuntimeException queryFailure = capture(new Action() {
            public void run() { runWait(lostDuringReadyQuery, .5); }
        });
        check(queryFailure == lostDuringReadyQuery.currentFailure,
            "owner loss immediately after a READY query propagates unchanged");
        check(lostDuringReadyQuery.readinessQueries == 1 &&
                lostDuringReadyQuery.advances.isEmpty(),
            "post-query owner loss cannot turn stale READY into success or advance");

        final FakeHost lostAfterAdvance = new FakeHost(ActiveMeasurementReadiness.WAITING);
        lostAfterAdvance.failCurrentCall = 3;
        RuntimeException lossFailure = capture(new Action() {
            public void run() { runWait(lostAfterAdvance, .5); }
        });
        check(lossFailure == lostAfterAdvance.currentFailure,
            "owner loss after advance propagates unchanged");
        check(lostAfterAdvance.readinessQueries == 1 && lostAfterAdvance.advances.size() == 1,
            "owner loss after first advance prevents successor query and advance");

        final FakeHost advanceFailure = new FakeHost(ActiveMeasurementReadiness.WAITING);
        advanceFailure.failAdvanceCall = 1;
        RuntimeException advanceException = capture(new Action() {
            public void run() { runWait(advanceFailure, .5); }
        });
        check(advanceException == advanceFailure.advanceFailure,
            "advance exception propagates unchanged");
        check(advanceFailure.readinessQueries == 1 && advanceFailure.advances.size() == 1,
            "failed advance prevents any successor readiness query or advance");
    }

    private static void verifySuccessfulGuards(FakeHost host) {
        int lastReadiness = -1;
        int lastAdvance = -1;
        for (int i = 0; i < host.events.size(); i++) {
            String event = host.events.get(i);
            if (event.equals("readiness")) {
                check(i > 0 && host.events.get(i - 1).equals("current"),
                    "every readiness query follows an exact-owner check");
                check(i + 1 < host.events.size() && host.events.get(i + 1).equals("current"),
                    "every readiness query is followed by an exact-owner check");
                lastReadiness = i;
            }
            if (event.equals("advance")) {
                check(lastReadiness > lastAdvance,
                    "solver advance follows a readiness result");
                check(i + 1 < host.events.size() && host.events.get(i + 1).equals("current"),
                    "every successful solver advance is followed by an exact-owner check");
                lastAdvance = i;
            }
        }
    }

    private static void verifiesImmutableReadinessWaitDeclarations() {
        check(GeneratedDiagnosticProgram.VERSION == 2,
            "readiness-bound declaration is versioned in diagnostic program schema");
        final GeneratedDiagnosticPlan plan = declarationPlan(
            new String[] { "R1.1", "R1.2" },
            new String[] { "RESISTANCE", "CONTINUITY", "DIODE" });
        GeneratedDiagnosticProgram readyWait = GeneratedDiagnosticProgram.builder(plan)
            .waitForReadinessSample("READY_WAIT", GeneratedDiagnosticProgram.Kind.RESISTANCE,
                "R1.1", "R1.2", 1.0)
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_SAMPLE", "R1.1", "R1.2")
            .build();
        GeneratedDiagnosticProgram fixedWait = GeneratedDiagnosticProgram.builder(plan)
            .waitSample("READY_WAIT", 1.0)
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_SAMPLE", "R1.1", "R1.2")
            .build();
        GeneratedDiagnosticProgram differentMode = GeneratedDiagnosticProgram.builder(plan)
            .waitForReadinessSample("READY_WAIT", GeneratedDiagnosticProgram.Kind.CONTINUITY,
                "R1.1", "R1.2", 1.0)
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_SAMPLE", "R1.1", "R1.2")
            .build();
        GeneratedDiagnosticProgram zeroWait = GeneratedDiagnosticProgram.builder(plan)
            .waitSample("READY_WAIT", 0.0)
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_SAMPLE", "R1.1", "R1.2")
            .build();
        GeneratedDiagnosticProgram.Step ready = readyWait.getSteps().firstElement();
        GeneratedDiagnosticProgram.Step fixed = zeroWait.getSteps().firstElement();
        check(!readyWait.canonical().equals(fixedWait.canonical()),
            "readiness predicate changes immutable program identity from fixed WAIT");
        check(!readyWait.canonical().equals(differentMode.canonical()),
            "readiness mode alone changes canonical identity with every other field equal");
        check(ready.kind == GeneratedDiagnosticProgram.Kind.WAIT &&
                ready.readinessMode == GeneratedDiagnosticProgram.Kind.RESISTANCE &&
                "R1.1".equals(ready.red) && "R1.2".equals(ready.black) &&
                ready.seconds == 1.0,
            "readiness WAIT retains its mode, public probes, and declared maximum");
        verifiesDeclaredWaitMode(plan, GeneratedDiagnosticProgram.Kind.CONTINUITY);
        verifiesDeclaredWaitMode(plan, GeneratedDiagnosticProgram.Kind.DIODE);
        check(fixed.kind == GeneratedDiagnosticProgram.Kind.WAIT &&
                fixed.readinessMode == null && fixed.seconds == 0.0,
            "fixed zero-duration WAIT remains legal and has no readiness predicate");

        java.util.Vector<GeneratedDiagnosticProgram.Step> exposed = readyWait.getSteps();
        exposed.removeElementAt(0);
        check(readyWait.getSteps().size() == 2 &&
                readyWait.getSteps().firstElement().readinessMode ==
                    GeneratedDiagnosticProgram.Kind.RESISTANCE,
            "mutating returned steps vector cannot alter immutable readiness declaration");

        reject(new Action() {
            public void run() {
                GeneratedDiagnosticProgram.builder(plan).waitForReadinessSample(
                    "READY_WAIT", null, "R1.1", "R1.2", .5);
            }
        }, "null readiness mode rejected");
        rejectReadinessWait(plan, GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
            "R1.1", "R1.2", .5, "DC voltage cannot declare readiness wait");

        final GeneratedDiagnosticPlan continuityOnly = declarationPlan(
            new String[] { "R1.1", "R1.2" }, new String[] { "CONTINUITY" });
        rejectReadinessWait(continuityOnly, GeneratedDiagnosticProgram.Kind.RESISTANCE,
            "R1.1", "R1.2", .5, "undeclared active meter mode rejected");
        rejectReadinessWait(plan, GeneratedDiagnosticProgram.Kind.RESISTANCE,
            "R1.1", "R9.1", .5, "undeclared public probe rejected");

        final double[] invalidBounds = new double[] {
            0.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, 1.000001
        };
        for (final double seconds : invalidBounds) {
            rejectReadinessWait(plan, GeneratedDiagnosticProgram.Kind.RESISTANCE,
                "R1.1", "R1.2", seconds, "invalid readiness wait maximum rejected: " + seconds);
        }
    }

    private static GeneratedDiagnosticPlan declarationPlan(String[] probes, String[] modes) {
        return new GeneratedDiagnosticPlan("READINESS_WAIT_CONTRACT", "J1.2",
            probes, modes, new String[] { "BOARD_POWER_OFF" },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
            new String[] { "READY_WAIT", "FIXED_WAIT" }, new String[] { "GND" },
            4, false, false, "NONE");
    }

    private static void verifiesDeclaredWaitMode(GeneratedDiagnosticPlan plan,
            GeneratedDiagnosticProgram.Kind mode) {
        GeneratedDiagnosticProgram program = GeneratedDiagnosticProgram.builder(plan)
            .waitForReadinessSample("READY_WAIT", mode, "R1.1", "R1.2", .25)
            .measure(mode, "MODE_SAMPLE", "R1.1", "R1.2")
            .build();
        GeneratedDiagnosticProgram.Step waitStep = program.getSteps().firstElement();
        check(waitStep.kind == GeneratedDiagnosticProgram.Kind.WAIT &&
                waitStep.readinessMode == mode && waitStep.seconds == .25,
            "declared active mode supports a bounded readiness wait: " + mode);
    }

    private static void rejectReadinessWait(final GeneratedDiagnosticPlan plan,
            final GeneratedDiagnosticProgram.Kind mode, final String red, final String black,
            final double seconds, String message) {
        reject(new Action() {
            public void run() {
                GeneratedDiagnosticProgram.builder(plan)
                    .waitForReadinessSample("READY_WAIT", mode, red, black, seconds)
                    .measure(GeneratedDiagnosticProgram.Kind.valueOf(plan.getMeterModeIds().firstElement()),
                        "OHM_SAMPLE", "R1.1", "R1.2")
                    .build();
            }
        }, message);
    }

    private static void reject(Action action, String message) {
        check(capture(action) instanceof IllegalArgumentException, message);
    }

    private static double runWait(GeneratedDiagnosticObservationExecutor.ReadinessWaitHost host,
            double maximumSeconds) {
        return GeneratedDiagnosticObservationExecutor.waitForReadiness(host, maximumSeconds);
    }

    private static RuntimeException capture(Action action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= 1e-12;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }

    private static final class FakeHost implements
            GeneratedDiagnosticObservationExecutor.ReadinessWaitHost {
        private final ActiveMeasurementReadiness[] states;
        private final List<Double> advances = new ArrayList<Double>();
        private final List<String> events = new ArrayList<String>();
        private final IllegalStateException currentFailure =
            new IllegalStateException("fake owner lost");
        private final IllegalStateException advanceFailure =
            new IllegalStateException("fake advance failed");
        private int readinessQueries;
        private int currentChecks;
        private int failCurrentCall = -1;
        private int failAdvanceCall = -1;

        FakeHost(ActiveMeasurementReadiness... states) {
            this.states = states;
        }

        public void requireCurrent() {
            currentChecks++;
            events.add("current");
            if (currentChecks == failCurrentCall) throw currentFailure;
        }

        public ActiveMeasurementReadiness readiness() {
            events.add("readiness");
            int index = readinessQueries++;
            return states[Math.min(index, states.length - 1)];
        }

        public void advance(double seconds) {
            events.add("advance");
            advances.add(Double.valueOf(seconds));
            if (advances.size() == failAdvanceCall) throw advanceFailure;
        }
    }
}
