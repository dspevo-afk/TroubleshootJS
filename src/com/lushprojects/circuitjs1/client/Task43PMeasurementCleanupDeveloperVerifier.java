package com.lushprojects.circuitjs1.client;

/** Additional failure canaries over the actual temporary-measurement owner. */
final class Task43PMeasurementCleanupDeveloperVerifier {
    private Task43PMeasurementCleanupDeveloperVerifier() { }

    static int verify(CirSim sim) {
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        for (int kind = 0; kind < 7; kind++) {
            original.beginProof(sim);
            try {
                runCase(sim, kind);
            } finally {
                original.restore(sim);
                original.assertRestored(sim);
            }
        }
        return 7;
    }

    private static void runCase(final CirSim sim, final int kind) {
        GeneratedBoardInstance candidate = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR, 0);
        sim.installGeneratedChallengeForDeveloperVerification(candidate);
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 14; attempt++)
            sim.updateCircuit();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(challenge != null && challenge.isReady(), kind, "candidate-not-ready");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        CircuitElm resistor = candidate.getComponentBindings().getSingleElement("R2");
        final ResistanceMeasurementStimulus actual = new ResistanceMeasurementStimulus(sim,
            new CircuitPostMeasurementEndpoint(resistor, 0),
            new CircuitPostMeasurementEndpoint(resistor, 1));
        final RuntimeException readerFailure = new IllegalStateException("task43p-reader-primary");
        final Error removalFailure = new AssertionError("task43p-removal-secondary");
        final Error analysisFailure = new AssertionError("task43p-analysis-primary");
        final CircuitElm failedRestoration = new ResistorElm(8192, 8192) {
            public void stamp() {
                if (kind == 6)
                    throw new IllegalStateException("task43p-native-stamp-stop");
                if (kind == 3 || sim.getBoardPowerController().getState() == BoardPowerState.POWERED)
                    throw analysisFailure;
                super.stamp();
            }
        };
        failedRestoration.drag(8224, 8192);
        final ActiveMeasurementStimulus stimulus = new ActiveMeasurementStimulus() {
            public void install(CirSim current) {
                actual.install(current);
                if (kind >= 2)
                    current.setBoardPowerState(BoardPowerState.POWERED);
                if (kind == 4)
                    current.setBoardPowerState(BoardPowerState.UNPOWERED);
            }

            public void remove(CirSim current) {
                if (kind == 1)
                    throw removalFailure;
                if (kind != 2)
                    actual.remove(current);
                if (kind == 0)
                    throw removalFailure;
                if (kind == 3 || kind == 5 || kind == 6)
                    current.elmList.add(failedRestoration);
            }

            public CircuitElm[] getTemporaryElements() { return actual.getTemporaryElements(); }
        };
        Throwable caught = null;
        double result = Double.NaN;
        try {
            result = sim.runTemporaryActiveMeasurementForDeveloperVerification(stimulus,
                new ActiveMeasurementResultReader() {
                    public double readResult() {
                        if (kind == 0 || kind == 1)
                            throw readerFailure;
                        return 42;
                    }
                });
        } catch (Throwable failure) {
            caught = failure;
        }
        require(sim.getGeneratedBoardInstance() == candidate &&
            sim.getGeneratedChallengeController() == challenge, kind, "owner-changed");
        if (kind == 0 || kind == 1) {
            require(caught == readerFailure, kind, "original-failure-lost");
            require(caught.getSuppressed().length > 0 &&
                caught.getSuppressed()[0] == removalFailure, kind, "cleanup-failure-lost");
        }
        if (kind == 0 || kind == 4) {
            require(!sim.activeMeasurementOverlay && sim.activeMeasurementSolverRestored &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                sim.pendingBoardPowerState == null &&
                sim.getBoardPowerController().isElectricallyUnpowered(), kind, "cleanup-not-restored");
            if (kind == 4)
                require(caught == null && result == 42, kind, "latest-power-off-not-preserved");
        } else {
            require(caught != null, kind, "restoration-failure-hidden");
            if (kind == 3 || kind == 5)
                require(caught == analysisFailure, kind, "analysis-failure-lost");
            if (kind == 6)
                require(caught instanceof IllegalStateException &&
                    "Temporary measurement solver restoration failed: Exception in stampCircuit()"
                        .equals(caught.getMessage()), kind, "native-stop-not-surfaced");
            require(sim.activeMeasurementOverlay && !sim.activeMeasurementSolverRestored &&
                !sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                sim.circuitMatrix == null && !sim.simRunning && sim.stopMessage != null &&
                sim.stopMessage.indexOf("Measurement cleanup failed") >= 0 &&
                sim.getBoardPowerController().isElectricallyUnpowered(), kind, "unsafe-failed-restoration");
            require(sim.pendingBoardPowerState == (kind == 1 ? null : BoardPowerState.POWERED),
                kind, "queued-request-lost");
            if (kind == 1 || kind == 2)
                for (CircuitElm element : actual.getTemporaryElements())
                    require(sim.elmList.contains(element), kind, "retained-stimulus-canary-not-active");
            else
                for (CircuitElm element : actual.getTemporaryElements())
                    require(!sim.elmList.contains(element), kind, "analysis-canary-retained-stimulus");
            // The blocked owner must reject a subsequent measurement and may
            // record a later power-off request without reenergizing its graph.
            Throwable blocked = null;
            try {
                sim.runTemporaryActiveMeasurementForDeveloperVerification(stimulus, null);
            } catch (IllegalStateException expected) {
                blocked = expected;
            }
            require(blocked != null, kind, "blocked-owner-accepted-another-measurement");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(sim.pendingBoardPowerState == BoardPowerState.UNPOWERED &&
                sim.getBoardPowerController().isElectricallyUnpowered(), kind, "blocked-owner-lost-latest-power");
        }
    }

    private static void require(boolean condition, int kind, String message) {
        if (!condition)
            throw new IllegalStateException("task43p-cleanup-safety-" + kind + ":" + message);
    }
}
