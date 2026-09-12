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

    /**
     * Runs the ordinary CircuitJS update path through the healthy temporal
     * callback only.  Applying the generated fault queues a second verification
     * request; the caller's next full settlement owns that faulted callback.
     */
    static void settleHealthy(CirSim sim, GeneratedBoardInstance expectedOwner, String label) {
        if (sim == null)
            throw new IllegalArgumentException("Cannot settle a null simulator: " + label);
        boolean wasRunning = sim.simIsRunning();
        if (!wasRunning)
            sim.setSimRunning(true);
        int attempts = 0;
        try {
            for (; attempts < MAX_UPDATE_ATTEMPTS; attempts++) {
                GenerationWorkScope.check();
                sim.updateCircuit();
                GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
                GeneratedChallengeLifecycleEvidence lifecycle = challenge == null ? null :
                    challenge.getLifecycleEvidence();
                if (sim.getGeneratedBoardInstance() == expectedOwner &&
                        challenge != null && challenge.getInstanceForRuntimeValidation() == expectedOwner &&
                        !challenge.isHealthyValidationExpected() && lifecycle != null &&
                        lifecycle.healthyFamilyValidated && !sim.activeMeasurementOverlay &&
                        sim.failedGeneratedRuntimeOwner != expectedOwner && sim.stopMessage == null)
                    return;
                // Match settle()'s bounded solver nudge when CircuitJS's UI
                // throttle has not advanced the awaited verification yet.
                if (canAdvanceAwaitedSolverStep(sim, expectedOwner, challenge))
                    sim.solverExecutor.advanceSteps(1);
            }
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            GeneratedChallengeLifecycleEvidence lifecycle = challenge == null ? null :
                challenge.getLifecycleEvidence();
            throw new IllegalStateException("Generated runtime did not settle its healthy profile after " +
                MAX_UPDATE_ATTEMPTS + " updateCircuit attempts: " + label +
                ", owner=" + (sim.getGeneratedBoardInstance() == expectedOwner) +
                ", healthyExpected=" + (challenge != null && challenge.isHealthyValidationExpected()) +
                ", healthy=" + (lifecycle != null && lifecycle.healthyFamilyValidated) +
                ", state=" + (challenge == null ? "null" : challenge.getState()) +
                ", pending=" + sim.generatedBoardVerificationPending +
                ", analyzed=" + sim.generatedBoardVerificationAnalyzed +
                ", analyze=" + sim.analyzeFlag + ", dc=" + sim.dcAnalysisFlag +
                ", verificationRunning=" + sim.generatedVerificationRunning +
                ", installation=" + sim.generatedRuntimeInstallationInProgress +
                ", overlay=" + sim.activeMeasurementOverlay + ", stop=" + sim.stopMessage);
        } finally {
            if (!wasRunning)
                sim.setSimRunning(false);
        }
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
                GenerationWorkScope.check();
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
                    sim.solverExecutor.advanceSteps(1);
            }
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            throw new IllegalStateException("Generated runtime did not settle after " +
                    MAX_UPDATE_ATTEMPTS + " updateCircuit attempts: " + label +
                    ", owner=" + (sim.getGeneratedBoardInstance() == expectedOwner) +
                    ", ready=" + (challenge != null && challenge.isReady()) +
                    ", settled=" + sim.isGeneratedRuntimeSettled() +
                    ", pending=" + sim.generatedBoardVerificationPending +
                    ", analyzed=" + sim.generatedBoardVerificationAnalyzed +
                    ", analyze=" + sim.analyzeFlag + ", dc=" + sim.dcAnalysisFlag +
                    ", verificationRunning=" + sim.generatedVerificationRunning +
                    ", installation=" + sim.generatedRuntimeInstallationInProgress +
                    ", overlay=" + sim.activeMeasurementOverlay + ", pendingPower=" + sim.pendingBoardPowerState +
                    ", observationalDepth=" + sim.observationalValidationDepth +
                    ", operation=" + (challenge != null && challenge.isOperationInProgress()) +
                    ", mutation=" + (expectedOwner != null && expectedOwner.getPhysicalBoardRuntime().isMutationInProgress()) +
                    ", failedOwner=" + (sim.failedGeneratedRuntimeOwner == expectedOwner) +
                    ", stop=" + sim.stopMessage + ", time=" + sim.t +
                    ", requestedAt=" + sim.generatedBoardVerificationStartTime);
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
