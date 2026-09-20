package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.Timer;

/**
 * Developer-only compiled-browser fixture for the U02/U03 temporal paths.
 * It adds one real CircuitJS source between the existing physical VIN/GND
 * endpoints after the normal board is safely isolated.  The source is not a
 * generated-board component and is never reachable from a player route.
 */
final class U02U03TemporalDeveloperVerifier {
    interface Completion { void finished(String report, Throwable failure); }

    private U02U03TemporalDeveloperVerifier() { }

    static void start(final CirSim sim, String requestedMode, boolean visualHold,
            final Completion completion) {
        final Fixture fixture = new Fixture(sim, normalize(requestedMode), visualHold, completion);
        new Timer() {
            public void run() { fixture.begin(); }
        }.schedule(0);
    }

    static void publish(String report, Throwable failure) {
        String result = report;
        if (result == null) {
            String message = failure == null ? "unknown temporal fixture failure" :
                failure.toString();
            result = "{\"protocol\":\"TSJ-U02-U03-TEMPORAL-1\",\"status\":\"FAIL\",\"failure\":" +
                quote(message) + "}";
        }
        publishReport(result);
    }

    private static final class Fixture {
        private static final int VISUAL_HOLD_MILLIS = 15000;
        private final CirSim sim;
        private final String mode;
        private final boolean visualHold;
        private final Completion completion;
        private GeneratedBoardInstance owner;
        private VoltageElm source;
        private Task41SimulationSnapshot beforeFixture;
        private ProbeTarget vin, ground, r1Vin;
        private double initialRms = Double.NaN;
        private double updatedRms = Double.NaN;
        private double scopeFrequency = Double.NaN;
        private int finalScopeSamples;
        private boolean cancelledPublicationRetired;
        private boolean renderReadOnly;
        private boolean fixtureStateRestored;
        private boolean finished;

        Fixture(CirSim sim, String mode, boolean visualHold, Completion completion) {
            this.sim = sim;
            this.mode = mode;
            this.visualHold = visualHold;
            this.completion = completion;
        }

        void begin() {
            try {
                installSourceFixture();
                if ("periodic".equals(mode)) {
                    beginAcRefreshProof();
                    return;
                }
                prepareAndVerifyScope();
                finishAfterVisualHold();
            } catch (Throwable failure) {
                finish(failure);
            }
        }

        private void installSourceFixture() {
            require(sim != null && sim.troubleshootDebug && sim.troubleshootU02U03Verification &&
                sim.developerVerifierRunning, "fixture requires its explicit debug verifier route");
            owner = sim.getGeneratedBoardInstance();
            require(owner != null && sim.getGeneratedChallengeController() != null &&
                sim.getGeneratedChallengeController().isReady(),
                "fixture requires a settled generated board");
            require("LED_INDICATOR".equals(owner.getCircuitFamilyId()),
                "fixture uses the LED board's public VIN/GND pads");

            /* This developer fixture is allowed to add a real temporary
             * source only because it captures the complete owner/graph/UI
             * state first and restores it on every success or failure path. */
            beforeFixture = Task41SimulationSnapshot.capture(sim);

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "u02-u03 temporal isolation");
            vin = padTarget("J1.1");
            ground = padTarget("J1.2");
            r1Vin = padTarget("R1.1");
            CircuitPostMeasurementEndpoint positive = endpoint("J1.1");
            CircuitPostMeasurementEndpoint negative = endpoint("J1.2");
            Point first = positive.getElement().getPost(positive.getPostIndex());
            Point second = negative.getElement().getPost(negative.getPostIndex());
            source = new VoltageElm(first.x, first.y, waveform());
            source.setPosition(first.x, first.y, second.x, second.y);
            source.maxVoltage = "periodic".equals(mode) ? 3 : "dc".equals(mode) ? 2.4 : 4;
            source.bias = 0;
            source.frequency = "pulse".equals(mode) ? 1 : 60;
            source.dutyCycle = "pulse".equals(mode) ? .02 : .5;
            source.freqTimeZero = sim.t;
            sim.elmList.add(source);
            sim.solverExecutor.invalidate();
            sim.analyzeCircuit();
            sim.solverExecutor.advanceFor("pulse".equals(mode) ? .08 : .02);
            require(sim.elmList.contains(source) && !owner.getSimulationElements().contains(source),
                "developer temporal source leaked into generated board ownership");
        }

        private void beginAcRefreshProof() {
            sim.instrumentController.clearTargets();
            sim.instrumentController.activateAcVoltageModeForDeveloperVerification();
            place(vin, ground);
            initialRms = sim.instrumentController.getLatestAcVoltageForDeveloperVerification();
            require(finite(initialRms) && near(initialRms, 3 / Math.sqrt(2), .08),
                "initial player AC RMS was not the accepted 60 Hz solver waveform: " + initialRms);
            require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                "initial AC capture left a temporary DMM burden installed");

            /* Change only the real source's solved behavior. The probes stay
             * put; a later completed solver operation must request one bounded
             * deferred capture rather than reading from paint code. */
            source.maxVoltage = 6;
            sim.solverExecutor.invalidate();
            sim.analyzeCircuit();
            sim.solverExecutor.advanceFor(CirSim.AC_VOLTAGE_CAPTURE_SECONDS + .01);
            sim.instrumentController.onSimulationStepComplete(true);
            new Timer() {
                public void run() { finishAcRefreshProof(); }
            }.schedule(20);
        }

        private void finishAcRefreshProof() {
            try {
                updatedRms = sim.instrumentController.getLatestAcVoltageForDeveloperVerification();
                require(finite(updatedRms) && near(updatedRms, 6 / Math.sqrt(2), .10) &&
                    updatedRms > initialRms * 1.8,
                    "retained probes did not refresh AC RMS after accepted waveform change: " +
                    initialRms + " -> " + updatedRms);
                require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                    "deferred AC refresh left a temporary DMM burden installed");
                prepareAndVerifyScope();
                finishAfterVisualHold();
            } catch (Throwable failure) {
                finish(failure);
            }
        }

        private void prepareAndVerifyScope() {
            sim.instrumentController.clearTargets();
            sim.instrumentController.activateScopeModeForDeveloperVerification();
            place(vin, ground);
            SolverTimeObservationService.Subscription first =
                sim.instrumentController.getScopeSubscriptionForDeveloperVerification();
            require(first != null && !first.isClosed(), "scope did not subscribe after valid selected probes");
            positionOneShotForNextCapture();
            sim.solverExecutor.advanceFor("pulse".equals(mode) ? .08 : .12);
            sim.instrumentController.updateReadingForStrategy();
            assertScopePresentation();
            require(first.getSampleCount() > 0 &&
                first.getSampleCount() <= SolverTimeObservationService.MAX_CAPACITY,
                "scope observation did not retain bounded accepted samples");
            verifyScopeDisplayDoesNotAdvanceSolver(first);
            verifyCancelledOperationRetiresPublishedWaveform();

            /* A physically distinct pad selection must retire the prior
             * subscription rather than continue appending under new probes. */
            sim.instrumentController.handlePointerInput(com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT,
                r1Vin);
            require(first.isClosed(), "changing a scope probe retained its old subscription");
            sim.instrumentController.handlePointerInput(com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT,
                vin);
            SolverTimeObservationService.Subscription beforeEpoch =
                sim.instrumentController.getScopeSubscriptionForDeveloperVerification();
            require(beforeEpoch != null && !beforeEpoch.isClosed(),
                "scope did not bind a successor subscription after probe change");

            /* This is the same retirement operation used by graph replacement,
             * topology mutation, source/power revision, and owner changes.
             * Refresh must bind a successor instead of showing old history. */
            sim.solverTimeObservations.invalidate();
            sim.instrumentController.refreshActiveMeasurement();
            SolverTimeObservationService.Subscription afterEpoch =
                sim.instrumentController.getScopeSubscriptionForDeveloperVerification();
            require(beforeEpoch.isClosed() && afterEpoch != null && !afterEpoch.isClosed() &&
                afterEpoch.getSampleCount() == 0,
                "scope lifecycle epoch did not retire stale waveform history");
            positionOneShotForNextCapture();
            sim.solverExecutor.advanceFor("pulse".equals(mode) ? .08 : .12);
            sim.instrumentController.updateReadingForStrategy();
            finalScopeSamples = afterEpoch.getSampleCount();
            require(finalScopeSamples > 0 &&
                finalScopeSamples <= SolverTimeObservationService.MAX_CAPACITY,
                "successor scope history was not bounded or accepted");
        }

        /** Display/canvas projection consumes the committed ring only; it must
         * neither advance solver time nor append a sample. */
        private void verifyScopeDisplayDoesNotAdvanceSolver(
                SolverTimeObservationService.Subscription subscription) {
            double timeBefore = sim.t;
            int samplesBefore = subscription.getSampleCount();
            sim.instrumentController.updateReadingForStrategy();
            require(sim.t == timeBefore && subscription.getSampleCount() == samplesBefore,
                "scope display advanced CircuitJS or fabricated an observation sample");
            renderReadOnly = true;
        }

        /**
         * Exercise the real service with its current CircuitJS endpoints and a
         * completed then cancelled bounded solver receipt. Staging from the
         * cancelled operation must never be published, and the older committed
         * waveform must not survive as if it described the rejected epoch.
         */
        private void verifyCancelledOperationRetiresPublishedWaveform() {
            CircuitPostMeasurementEndpoint positive = endpoint("J1.1");
            CircuitPostMeasurementEndpoint negative = endpoint("J1.2");
            SolverTimeObservationService.Subscription subscription =
                sim.solverTimeObservations.subscribe(positive, negative, 8, false);
            try {
                SolverExecutionBoundary boundary = new SolverExecutionBoundary();
                boundary.bind(owner, sim.elmList);
                double start = sim.t;

                SolverExecutionBoundary.Operation complete = boundary.begin(2, 2, 0, 20, start);
                boundary.beginTrial(complete, 1);
                boundary.accepted(complete, start + .0001, 2);
                sim.solverTimeObservations.accepted(complete);
                boundary.finish(complete, SolverExecutionBoundary.Outcome.COMPLETE);
                sim.solverTimeObservations.finished(complete,
                    SolverExecutionBoundary.Outcome.COMPLETE);
                require(subscription.getSampleCount() == 1,
                    "completed receipt did not publish its accepted waveform sample");

                SolverExecutionBoundary.Operation cancelled = boundary.begin(2, 2, 3, 20,
                    start + .0001);
                boundary.beginTrial(cancelled, 4);
                boundary.accepted(cancelled, start + .0002, 5);
                sim.solverTimeObservations.accepted(cancelled);
                require(subscription.getSampleCount() == 1,
                    "in-flight cancelled receipt leaked a partial waveform");
                boundary.finish(cancelled, SolverExecutionBoundary.Outcome.CANCELLED);
                sim.solverTimeObservations.finished(cancelled,
                    SolverExecutionBoundary.Outcome.CANCELLED);
                require(subscription.getSampleCount() == 0,
                    "cancelled receipt retained stale or partial waveform samples");
                cancelledPublicationRetired = true;
            } finally {
                sim.solverTimeObservations.unsubscribe(subscription);
            }
        }

        private void assertScopePresentation() {
            String display = sim.instrumentController.getReadingForDeveloperVerification();
            if ("periodic".equals(mode)) {
                scopeFrequency = sim.instrumentController.getLatestScopeFrequencyForDeveloperVerification();
                require(finite(scopeFrequency) && near(scopeFrequency, 60, .5) &&
                    display.indexOf("SCOPE:") == 0,
                    "scope did not render the periodic solver waveform/frequency: " + display +
                    " / " + scopeFrequency);
            } else if ("dc".equals(mode)) {
                require("SCOPE: NO SIGNAL".equals(display),
                    "DC scope trace was incorrectly gated on frequency success: " + display);
            } else {
                require("SCOPE: FREQ?".equals(display) || "SCOPE: NO TRIGGER".equals(display),
                    "one-shot scope trace did not retain explicit unavailable frequency: " + display);
            }
        }

        private int waveform() {
            if ("dc".equals(mode)) return VoltageElm.WF_DC;
            if ("pulse".equals(mode)) return VoltageElm.WF_PULSE;
            return VoltageElm.WF_AC;
        }

        /**
         * Put exactly one rising and falling edge inside the next accepted
         * scope window.  This uses the live source's solver-time phase, not a
         * configured-frequency lookup by the measurement code.  One rising
         * edge is deliberately insufficient for the timestamp-derived
         * frequency estimator, while the real capture still contains a
         * drawable transient.
         */
        private void positionOneShotForNextCapture() {
            if (!"pulse".equals(mode)) return;
            double period = 1 / source.frequency;
            /* The subscription retains a bounded final portion of the .08 s
             * advance, so retain both edges rather than ending the pulse just
             * before that physical history window. */
            source.freqTimeZero = sim.t - (period - .055);
        }

        private ProbeTarget padTarget(String id) {
            PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController == null ? null :
                sim.pcbWorkbenchController.getRenderer();
            require(renderer != null, "fixture has no physical workbench renderer");
            Point point = renderer.getPadPoint(id);
            ProbeTarget result = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
            require(result instanceof BoardPadProbeTarget, "fixture pad target missing: " + id);
            return result;
        }

        private CircuitPostMeasurementEndpoint endpoint(String id) {
            CircuitMeasurementEndpoint result = owner.getSimulationBindings().getEndpoint(id);
            require(result instanceof CircuitPostMeasurementEndpoint,
                "fixture board pad is not CircuitJS-backed: " + id);
            return (CircuitPostMeasurementEndpoint) result;
        }

        private void place(ProbeTarget red, ProbeTarget black) {
            sim.instrumentController.handlePointerInput(com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT, red);
            sim.instrumentController.handlePointerInput(com.google.gwt.dom.client.NativeEvent.BUTTON_RIGHT, black);
        }

        /**
         * The normal verifier retires the temporary graph immediately.  An
         * explicit debug-only visual route may retain the already-validated
         * scope for one bounded interval so compiled-browser evidence can use
         * its actual scale and trigger controls.  The timer always uses the
         * same cleanup path; it is never a player-accessible fixture.
         */
        private void finishAfterVisualHold() {
            if (!visualHold) {
                finish(null);
                return;
            }
            new Timer() {
                public void run() { finish(null); }
            }.schedule(VISUAL_HOLD_MILLIS);
        }

        private void finish(Throwable failure) {
            if (finished) return;
            finished = true;
            Throwable finalFailure = restoreFixtureState(failure);
            String report = finalFailure == null ? "{\"protocol\":\"TSJ-U02-U03-TEMPORAL-1\",\"status\":\"PASS\",\"mode\":" +
                quote(mode) + ",\"initialRms\":" + number(initialRms) + ",\"updatedRms\":" + number(updatedRms) +
                ",\"scopeFrequencyHz\":" + number(scopeFrequency) + ",\"scopeSamples\":" + finalScopeSamples +
                ",\"boundedCapacity\":" + SolverTimeObservationService.MAX_CAPACITY +
                ",\"fixtureSourceOutsideLogicalBoard\":true,\"fixtureStateRestored\":" +
                fixtureStateRestored + ",\"cancelledPublicationRetired\":" +
                cancelledPublicationRetired + ",\"renderReadOnly\":" + renderReadOnly +
                ",\"temporaryMeterClean\":true}" : null;
            completion.finished(report, finalFailure);
        }

        /**
         * Retire the scope subscription first, then restore the exact pre-fixture
         * graph, solver, power, probes, and UI state.  `Task41SimulationSnapshot`
         * restores the original element vector, so the developer-only source
         * cannot survive in the player graph even when an assertion failed.
         */
        private Throwable restoreFixtureState(Throwable priorFailure) {
            Throwable result = priorFailure;
            if (beforeFixture == null)
                return result;
            try {
                sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            } catch (Throwable exitFailure) {
                result = retain(result, exitFailure);
            }
            try {
                beforeFixture.restore(sim);
            } catch (Throwable restoreFailure) {
                result = retain(result, restoreFailure);
            }
            try {
                beforeFixture.assertRestored(sim);
                require(source == null || !sim.elmList.contains(source),
                    "temporary temporal source survived fixture cleanup");
                require(!sim.activeMeasurementOverlay,
                    "temporary temporal fixture left a measurement overlay");
                fixtureStateRestored = true;
            } catch (Throwable assertionFailure) {
                result = retain(result, assertionFailure);
            }
            return result;
        }
    }

    private static String normalize(String raw) {
        if (raw == null || raw.length() == 0 || "periodic".equals(raw)) return "periodic";
        if ("dc".equals(raw) || "pulse".equals(raw)) return raw;
        throw new IllegalArgumentException("Unsupported U02/U03 temporal fixture: " + raw);
    }

    private static boolean near(double actual, double expected, double tolerance) {
        return finite(actual) && Math.abs(actual - expected) <= tolerance;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String number(double value) {
        return finite(value) ? Double.toString(value) : "null";
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("U02/U03 temporal fixture: " + message);
    }

    private static Throwable retain(Throwable prior, Throwable next) {
        if (prior == null)
            return next;
        if (prior != next)
            prior.addSuppressed(next);
        return prior;
    }

    private static native String quote(String value) /*-{
        return JSON.stringify(value);
    }-*/;

    private static native void publishReport(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-u02-u03-temporal-report", report);
    }-*/;
}
