package com.lushprojects.circuitjs1.client;

/**
 * Runs the real CircuitJS update path until a generated challenge is both
 * ready and free of queued analysis, verification, mutation, and measurement
 * work.  Developer verifiers must use this boundary between dependent public
 * actions; direct analyze/run calls do not complete generated verification.
 */
final class GeneratedRuntimeDeveloperSettlement {
    private static final int MAX_UPDATE_ATTEMPTS = 20;

    private GeneratedRuntimeDeveloperSettlement() {
    }

    static void settle(CirSim sim, GeneratedBoardInstance expectedOwner, String label) {
        if (sim == null)
            throw new IllegalArgumentException("Cannot settle a null simulator: " + label);
        boolean wasRunning = sim.simIsRunning();
        if (!wasRunning)
            sim.setSimRunning(true);
        int attempts = 0;
        try {
            for (; attempts < MAX_UPDATE_ATTEMPTS; attempts++) {
                sim.updateCircuit();
                GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
                if (sim.getGeneratedBoardInstance() == expectedOwner &&
                        challenge != null && challenge.isReady() &&
                        sim.isGeneratedRuntimeSettled())
                    return;
                // A tight developer loop can finish within CircuitJS's UI
                // wall-clock throttle. Advance the actual, already-analyzed
                // solver once; the next ordinary update must still complete
                // verification and satisfy the authoritative settled predicate.
                if (canAdvanceAwaitedSolverStep(sim, expectedOwner, challenge))
                    sim.runCircuit(true);
            }
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            throw new IllegalStateException("Generated runtime did not settle after " +
                    MAX_UPDATE_ATTEMPTS + " updateCircuit attempts: " + label +
                    ", owner=" + (sim.getGeneratedBoardInstance() == expectedOwner) +
                    ", ready=" + (challenge != null && challenge.isReady()) +
                    ", settled=" + sim.isGeneratedRuntimeSettled());
        } finally {
            if (!wasRunning)
                sim.setSimRunning(false);
        }
    }

    static void settle(CirSim sim, String label) {
        settle(sim, sim.getGeneratedBoardInstance(), label);
    }

    private static boolean canAdvanceAwaitedSolverStep(CirSim sim,
            GeneratedBoardInstance expectedOwner, GeneratedChallengeController challenge) {
        return expectedOwner != null && sim.getGeneratedBoardInstance() == expectedOwner &&
            challenge != null && challenge.getInstanceForRuntimeValidation() == expectedOwner &&
            challenge.isReady() && !challenge.isOperationInProgress() &&
            sim.generatedBoardVerificationPending && sim.generatedBoardVerificationAnalyzed &&
            sim.t <= sim.generatedBoardVerificationStartTime &&
            !sim.analyzeFlag && !sim.dcAnalysisFlag && !sim.generatedVerificationRunning &&
            !sim.generatedRuntimeInstallationInProgress &&
            sim.failedGeneratedRuntimeOwner != expectedOwner && sim.stopMessage == null &&
            !sim.activeMeasurementOverlay && sim.pendingBoardPowerState == null &&
            sim.observationalValidationDepth == 0 &&
            !expectedOwner.getPhysicalBoardRuntime().isMutationInProgress();
    }
}
