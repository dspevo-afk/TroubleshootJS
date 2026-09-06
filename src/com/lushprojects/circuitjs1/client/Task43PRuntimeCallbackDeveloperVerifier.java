package com.lushprojects.circuitjs1.client;

/** Observes an actual queued repaint across a generated-owner switch. */
final class Task43PRuntimeCallbackDeveloperVerifier {
    interface Completion {
        void completed(String evidence);
        void failed(RuntimeException failure);
    }

    static final class RepaintObservation {
        final Probe proof;
        final int sequence;
        final GeneratedBoardInstance scheduledOwner;

        RepaintObservation(Probe proof, int sequence, GeneratedBoardInstance owner) {
            this.proof = proof;
            this.sequence = sequence;
            this.scheduledOwner = owner;
        }
    }

    private static Probe active;

    private Task43PRuntimeCallbackDeveloperVerifier() { }

    static void start(CirSim sim, Completion completion) {
        require(sim.troubleshootTask43PVerification && sim.developerVerifierRunning &&
                active == null && completion != null,
            "task43p-callback-proof-invalid-entry");
        Probe proof = new Probe(sim, completion);
        active = proof;
        try {
            proof.snapshot.beginProof(sim);
            proof.oldOwner = QuickPlayFamilyRegistry.generate(
                QuickPlayFamilyRegistry.LED_INDICATOR, 3);
            sim.installGeneratedChallengeForDeveloperVerification(proof.oldOwner);
            settleReady(sim);
            proof.oldChallenge = sim.getGeneratedChallengeController();
            CircuitElm resistor = proof.oldOwner.getComponentBindings().getSingleElement("R1");
            proof.oldRed = new CircuitPostProbeTarget(sim, resistor, 0);
            proof.oldBlack = new CircuitPostProbeTarget(sim, resistor, 1);
            require(proof.oldRed.isValid() && proof.oldBlack.isValid(),
                "task43p-callback-old-target-not-live");
            sim.instrumentController.setDcVoltageProbesForDeveloperVerification(
                proof.oldRed, proof.oldBlack);
            sim.setSimRunning(false);
            proof.waitingForPreparedRepaint = true;
            // An already queued repaint is allowed to drain first. Its actual
            // completion supplies a clear needsRepaint flag for the next one.
            sim.repaint();
        } catch (RuntimeException failure) {
            fail(proof, failure);
        }
    }

    static RepaintObservation scheduled(CirSim sim) {
        Probe proof = active;
        if (proof == null || proof.sim != sim)
            return null;
        RepaintObservation observation = new RepaintObservation(proof,
            ++proof.sequence, sim.getGeneratedBoardInstance());
        if (proof.captureNextSchedule) {
            require(proof.expected == null, "task43p-callback-schedule-was-duplicated");
            proof.expected = observation;
            proof.captureNextSchedule = false;
        }
        return observation;
    }

    static void discarded(CirSim sim, RepaintObservation observation) {
        Probe proof = active;
        if (proof == null || proof.sim != sim || observation == null ||
                observation != proof.expected)
            return;
        proof.callbackDiscarded = true;
        started(sim, observation);
        completed(sim, observation);
    }

    static void started(CirSim sim, RepaintObservation observation) {
        Probe proof = active;
        if (proof == null || proof.sim != sim)
            return;
        proof.previousDeveloperRunning = sim.developerVerifierRunning;
        sim.developerVerifierRunning = true;
        if (observation == proof.expected && observation != null) {
            proof.callbackEntered = true;
            proof.currentOwnerAtEntry = sim.getGeneratedBoardInstance() == proof.newOwner;
            proof.oldFingerprintAtEntry = ownerFingerprint(proof.oldOwner);
        }
    }

    static void completed(CirSim sim, RepaintObservation observation) {
        Probe proof = active;
        if (proof == null || proof.sim != sim)
            return;
        boolean previousDeveloperRunning = proof.previousDeveloperRunning;
        try {
            if (proof.waitingForPreparedRepaint) {
                require(sim.getGeneratedBoardInstance() == proof.oldOwner &&
                        sim.getGeneratedChallengeController() == proof.oldChallenge &&
                        !sim.simIsRunning() && !sim.needsRepaint,
                    "task43p-callback-old-owner-not-prepared");
                proof.waitingForPreparedRepaint = false;
                proof.captureNextSchedule = true;
                sim.repaint();
                require(proof.expected != null &&
                        proof.expected.scheduledOwner == proof.oldOwner &&
                        !proof.captureNextSchedule,
                    "task43p-callback-real-schedule-not-observed");
                proof.newOwner = QuickPlayFamilyRegistry.generate(
                    QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH, 0);
                sim.installGeneratedChallengeForDeveloperVerification(proof.newOwner);
                proof.newChallenge = sim.getGeneratedChallengeController();
                proof.oldTargetsInvalidAfterSwitch = !proof.oldRed.isValid() &&
                    !proof.oldBlack.isValid();
                proof.targetsClearedAfterSwitch =
                    sim.instrumentController.getRedProbeForStrategy() == null &&
                    sim.instrumentController.getBlackProbeForStrategy() == null;
                proof.oldFingerprintAfterSwitch = ownerFingerprint(proof.oldOwner);
                proof.pendingAfterSwitch = sim.generatedBoardVerificationPending;
                proof.analyzedAfterSwitch = sim.generatedBoardVerificationAnalyzed;
                return;
            }
            if (observation != proof.expected || observation == null)
                return;
            require(observation.proof == proof && proof.callbackEntered,
                "task43p-callback-entry-was-not-observed");
            boolean currentOwnerAtExit = sim.getGeneratedBoardInstance() == proof.newOwner &&
                sim.getGeneratedChallengeController() == proof.newChallenge;
            String after = ownerFingerprint(proof.oldOwner);
            boolean oldOwnerUnchanged = proof.oldFingerprintAfterSwitch.equals(
                proof.oldFingerprintAtEntry) && proof.oldFingerprintAtEntry.equals(after);
            boolean noOldTargets = !proof.oldRed.isValid() && !proof.oldBlack.isValid() &&
                sim.instrumentController.getRedProbeForStrategy() == null &&
                sim.instrumentController.getBlackProbeForStrategy() == null;
            boolean closed = proof.callbackDiscarded && proof.currentOwnerAtEntry && currentOwnerAtExit &&
                oldOwnerUnchanged && noOldTargets && proof.oldTargetsInvalidAfterSwitch &&
                proof.targetsClearedAfterSwitch && !sim.activeMeasurementOverlay;
            String evidence = "{\"protocol\":\"TSJ-TASK43P-I-1\",\"status\":\"OBSERVED\"," +
                "\"actualScheduledCallback\":true,\"callbackSequence\":" + observation.sequence +
                ",\"scheduledFamily\":" + q(proof.oldOwner.getCircuitFamilyId()) +
                ",\"scheduledSeed\":" + proof.oldOwner.getSeed() +
                ",\"currentFamily\":" + q(proof.newOwner.getCircuitFamilyId()) +
                ",\"currentSeed\":" + proof.newOwner.getSeed() +
                ",\"ownerAtEntryIsCurrent\":" + proof.currentOwnerAtEntry +
                ",\"ownerAtExitIsCurrent\":" + currentOwnerAtExit +
                ",\"pendingAfterSwitch\":" + proof.pendingAfterSwitch +
                ",\"analyzedAfterSwitch\":" + proof.analyzedAfterSwitch +
                ",\"pendingAfterCallback\":" + sim.generatedBoardVerificationPending +
                ",\"analyzedAfterCallback\":" + sim.generatedBoardVerificationAnalyzed +
                ",\"oldTargetsInvalidAfterSwitch\":" + proof.oldTargetsInvalidAfterSwitch +
                ",\"targetsClearedAfterSwitch\":" + proof.targetsClearedAfterSwitch +
                ",\"noOldTargetsAfterCallback\":" + noOldTargets +
                ",\"oldOwnerUnchanged\":" + oldOwnerUnchanged +
                ",\"oldOwnerBefore\":" + q(proof.oldFingerprintAfterSwitch) +
                ",\"oldOwnerAtEntry\":" + q(proof.oldFingerprintAtEntry) +
                ",\"oldOwnerAfter\":" + q(after) +
                ",\"overlayAfterCallback\":" + sim.activeMeasurementOverlay +
                ",\"disposition\":" + q(closed ? "CLOSED" : "OPEN_BLOCKER");
            // Finish the new owner's ordinary solver settlement only after
            // the callback observations have been copied into evidence.
            settleReady(sim);
            proof.restore();
            active = null;
            proof.completion.completed(evidence + ",\"originalOwnerRestored\":true}");
        } catch (RuntimeException failure) {
            fail(proof, failure);
        } finally {
            sim.developerVerifierRunning = previousDeveloperRunning;
        }
    }

    static void failed(CirSim sim, RuntimeException failure) {
        Probe proof = active;
        if (proof == null || proof.sim != sim)
            return;
        boolean previousDeveloperRunning = proof.previousDeveloperRunning;
        try {
            fail(proof, failure);
        } finally {
            sim.developerVerifierRunning = previousDeveloperRunning;
        }
    }

    private static void fail(Probe proof, RuntimeException failure) {
        active = null;
        RuntimeException reported = failure;
        try {
            proof.restore();
        } catch (RuntimeException restoration) {
            reported = new IllegalStateException("task43p-callback-owner-restoration-failed: " +
                restoration.getMessage(), failure);
        }
        proof.completion.failed(reported);
    }

    private static final class Probe {
        final CirSim sim;
        final Completion completion;
        final Task41SimulationSnapshot snapshot;
        final GeneratedBoardInstance originalOwner;
        final GeneratedChallengeController originalChallenge;
        GeneratedBoardInstance oldOwner, newOwner;
        GeneratedChallengeController oldChallenge, newChallenge;
        CircuitPostProbeTarget oldRed, oldBlack;
        RepaintObservation expected;
        int sequence;
        boolean waitingForPreparedRepaint, captureNextSchedule, previousDeveloperRunning;
        boolean callbackEntered, callbackDiscarded, currentOwnerAtEntry, oldTargetsInvalidAfterSwitch;
        boolean targetsClearedAfterSwitch, pendingAfterSwitch, analyzedAfterSwitch;
        String oldFingerprintAfterSwitch, oldFingerprintAtEntry;

        Probe(CirSim sim, Completion completion) {
            this.sim = sim;
            this.completion = completion;
            originalOwner = sim.getGeneratedBoardInstance();
            originalChallenge = sim.getGeneratedChallengeController();
            snapshot = Task41SimulationSnapshot.capture(sim);
        }

        void restore() {
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-callback-original-owner-not-restored");
        }
    }

    private static void settleReady(CirSim sim) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, sim.getGeneratedBoardInstance(),
            "task43p-callback-owner");
    }

    private static String ownerFingerprint(GeneratedBoardInstance owner) {
        StringBuilder result = new StringBuilder(owner.getBoard().getId());
        result.append('|').append(owner.getCircuitFamilyId()).append('|').append(owner.getSeed())
            .append('|').append(owner.getFaultBinding().isApplied());
        for (CircuitElm element : owner.getSimulationElements()) {
            result.append('|').append(element.getClass().getName()).append(':')
                .append(element.dump()).append(':').append(element.getCurrent());
            for (int post = 0; post < element.getPostCount(); post++)
                result.append(':').append(element.getNode(post)).append('@')
                    .append(element.getPostVoltage(post));
        }
        return result.toString();
    }

    private static String q(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
            .replace("\t", "\\t") + "\"";
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
