package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Real controller/strategy cleanup failures through the existing instrument registry. */
final class GeneratedCleanupDeveloperVerifier {
    private static int assertions;
    private GeneratedCleanupDeveloperVerifier() { }

    static String verify(CirSim sim) {
        assertions = 0;
        final boolean[] failExit = {false};
        final int[] exits = {0};
        String id = "DEVELOPER_OBSERVATION_CLEANUP";
        InstrumentController.DeveloperState saved = sim.instrumentController.captureForDeveloperVerification();
        InstrumentModeProvider mode = new AbstractInstrumentModeStrategy(id, "Cleanup canary", "---", 109,
                new InstrumentProbeRequirements(false, InstrumentProbePolarity.POSITIVE,
                    InstrumentProbePolarity.NEGATIVE), InstrumentPowerPolicy.POWERED_OR_UNPOWERED, false) {
            public void deactivate(InstrumentController controller) {
                exits[0]++;
                if (failExit[0]) throw new IllegalStateException("observation-cleanup-canary");
            }
        };
        sim.instrumentController.registerDeveloperInstrumentModeForVerification(mode);
        try {
            verifyCursor(sim, id, failExit, exits);
            verifySession(sim, id, failExit, exits);
        } finally {
            failExit[0] = false;
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            sim.instrumentController.restoreForDeveloperVerification(saved);
        }
        return "{\"assertions\":" + assertions + ",\"cursorRetry\":true,\"closedSessionRetry\":true," +
            "\"retainedObservation\":true,\"successorUntouched\":true,\"leaseRetained\":true," +
            "\"privateGraphDisposed\":true}";
    }

    private static void verifyCursor(CirSim sim, String id, boolean[] failExit, int[] exits) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        Vector<CircuitElm> graph = sim.elmList;
        GeneratedExternalPowerBindings.SavedControls power = owner.getExternalPowerBindings().saveControls();
        GeneratedDiagnosticObservationExecutor.Cursor cursor = GeneratedDiagnosticObservationExecutor.begin(
            sim, owner, owner.getDiagnosticProvider().getObservationProgram(), GeneratedDiagnosticExecutionTrace.builder());
        try {
            sim.instrumentController.activateDeveloperInstrumentModeForVerification(id);
            failExit[0] = true;
            boolean rejected = false;
            try { cursor.cancel(); } catch (IllegalStateException expected) {
                rejected = "observation-cleanup-canary".equals(expected.getMessage());
            }
            require(rejected && sim.instrumentController.getActiveModeForDeveloperVerification() == 109,
                "failed cancellation retains the actual selected instrument");
            rejected = false;
            try { cursor.step(); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "failed cancellation cannot resume measurement");
            rejected = false;
            try { cursor.finish(); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "failed cancellation cannot publish samples");
            int attempts = exits[0];
            sim.elmList = new Vector<CircuitElm>(graph);
            rejected = false;
            try { cursor.cancel(); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected && exits[0] == attempts && power.matches(),
                "stale cursor cannot exit an instrument over a successor graph");
            sim.elmList = graph;
            failExit[0] = false;
            double before = sim.t;
            cursor.cancel();
            require(exits[0] == attempts + 1 && sim.instrumentController.getActiveModeForDeveloperVerification() == 0,
                "same cursor retries the real failed exit");
            cursor.cancel();
            require(exits[0] == attempts + 1 && sim.t == before && power.matches(),
                "successful retry is idempotent and does not solve or change source commands");
        } finally {
            sim.elmList = graph;
            failExit[0] = false;
            cursor.cancel();
        }
    }

    private static void verifySession(CirSim sim, String id, boolean[] failExit, int[] exits) {
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalController = sim.getGeneratedChallengeController();
        Vector<CircuitElm> originalGraph = sim.elmList;
        GenerationCoordinator coordinator = sim.generationCoordinator;
        Vector<CircuitElm> privateGraph = null;
        try {
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            for (int count = 0; count < 8 && coordinator.isRunning() &&
                    coordinator.getJob().getStage() != GenerationJob.Stage.HYPOTHESES; count++)
                coordinator.advanceForDeveloperVerification();
            require(coordinator.isRunning() && coordinator.getJob().getStage() == GenerationJob.Stage.HYPOTHESES,
                "real proof reaches hypothesis preparation");
            coordinator.advanceForDeveloperVerification(); // replay/install
            coordinator.advanceForDeveloperVerification(); // settle/create observation cursor
            require(coordinator.isRunning() && coordinator.retainsObservationForDeveloperVerification(),
                "real proof owns a partial observation");
            privateGraph = sim.elmList;
            GeneratedBoardInstance privateOwner = sim.getGeneratedBoardInstance();
            sim.instrumentController.activateDeveloperInstrumentModeForVerification(id);
            failExit[0] = true;
            coordinator.cancel();
            require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE &&
                coordinator.getJob().getReceipt() == null && coordinator.retainsSavedOwnersForDeveloperVerification() &&
                coordinator.retainsObservationForDeveloperVerification() && sim.elmList == privateGraph &&
                sim.getGeneratedBoardInstance() == privateOwner && original.getExternalPowerBindings().areAllDisconnected(),
                "failed cleanup retains the observation, private graph and protected-owner isolation");
            boolean rejected = false;
            try { coordinator.startForDeveloperVerification(GenerationRequest.leaf(QuickPlayFamilyRegistry.LED_INDICATOR, 1, false)); }
            catch (IllegalStateException expected) { rejected = "Previous generation has incomplete cleanup".equals(expected.getMessage()); }
            require(rejected, "new generation cannot overwrite pending observation cleanup");
            rejected = false;
            try { GeneratedDiagnosticProofService.begin(sim, privateOwner, sim.getGeneratedChallengeController()); }
            catch (IllegalStateException expected) { rejected = expected.getMessage().indexOf("session") >= 0; }
            require(rejected, "failed observation retains the exclusive proof lease");
            int attempts = exits[0];
            sim.elmList = new Vector<CircuitElm>(privateGraph);
            rejected = false;
            try { coordinator.retryFailedCleanupForDeveloperVerification(); }
            catch (IllegalStateException expected) { rejected = true; }
            require(rejected && exits[0] == attempts && coordinator.retainsObservationForDeveloperVerification(),
                "closed-session retry refuses a successor graph without losing the cursor");
            sim.elmList = privateGraph;
            failExit[0] = false;
            coordinator.retryFailedCleanupForDeveloperVerification();
            require(sim.getGeneratedBoardInstance() == original && sim.getGeneratedChallengeController() == originalController &&
                sim.elmList == originalGraph && !coordinator.retainsSavedOwnersForDeveloperVerification() &&
                !coordinator.retainsObservationForDeveloperVerification() && !sim.activeMeasurementOverlay,
                "closed-session retry restores the exact original and releases cleanup owners");
            GeneratedDiagnosticProofService.CleanupAudit audit = coordinator.getCleanupAuditForDeveloperVerification();
            require(audit != null && audit.wereLastElementsActuallyDeleted() && audit.wasLastGraphDetached() &&
                audit.wasLastCleanupComplete() && privateOwner.getExternalPowerBindings().areAllDisconnected(),
                "retried observation cleanup precedes real private graph disposal");
        } finally {
            if (privateGraph != null && sim.getGeneratedBoardInstance() != original) sim.elmList = privateGraph;
            failExit[0] = false;
            if (coordinator.isRunning()) coordinator.cancel();
            if (coordinator.retainsSavedOwnersForDeveloperVerification()) coordinator.retryFailedCleanupForDeveloperVerification();
        }
    }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new IllegalStateException("Observation cleanup: " + message);
    }
}
