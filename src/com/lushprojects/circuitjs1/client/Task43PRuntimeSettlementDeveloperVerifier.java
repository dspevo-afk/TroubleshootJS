package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * Developer-only runtime canaries for the Quick Play settlement and reset
 * boundaries.  The canaries use the ordinary generated-board controllers and
 * CircuitJS update path; they do not add a player-facing transaction or a
 * second electrical model.
 */
final class Task43PRuntimeSettlementDeveloperVerifier {
    private static final String LED = QuickPlayFamilyRegistry.LED_INDICATOR;
    private static final String NPN = QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH;
    private static final String RC = QuickPlayFamilyRegistry.RC_DELAY;

    private Task43PRuntimeSettlementDeveloperVerifier() { }

    static String verify(CirSim sim) {
        require(sim != null, "task43p-runtime-settlement-missing-simulator");
        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalChallenge =
            sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null && originalChallenge.isReady(),
            "task43p-runtime-settlement-original-owner-not-ready");
        require(!sim.activeMeasurementOverlay,
            "task43p-runtime-settlement-original-owner-has-measurement-overlay");

        boolean originalQuickPlayActive = sim.quickPlayActive;
        QuickPlaySession originalQuickPlaySession = sim.quickPlaySession;
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        String g;
        String h;
        String omission;
        try {
            g = runSettlementCases(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge);
            h = runResetCases(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge);
            omission = runSnapshotOmissionCase(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-runtime-settlement-original-owner-not-restored-at-end");
            return "{\"protocol\":\"TSJ-TASK43P-GH-1\",\"status\":\"OBSERVED\"," +
                "\"originalOwnerRestored\":true,\"G\":" + g +
                ",\"H\":" + h + ",\"snapshotCalibration\":" + omission + "}";
        } finally {
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            if (sim.getGeneratedBoardInstance() != originalOwner ||
                    sim.getGeneratedChallengeController() != originalChallenge) {
                snapshot.restore(sim);
            }
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-runtime-settlement-final-owner-mismatch");
        }
    }

    /**
     * Narrow entry point for the source-falsifier route.  The result labels a
     * post-restore sentinel check as a runtime simulated omission; it does not
     * claim that a Java source field was physically deleted or recompiled.
     */
    static String verifySnapshotOmission(CirSim sim) {
        require(sim != null, "task43p-runtime-settlement-omission-missing-simulator");
        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalChallenge =
            sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null && originalChallenge.isReady(),
            "task43p-runtime-settlement-omission-original-owner-not-ready");
        boolean originalQuickPlayActive = sim.quickPlayActive;
        QuickPlaySession originalQuickPlaySession = sim.quickPlaySession;
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        try {
            String result = runSnapshotOmissionCase(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-runtime-settlement-omission-owner-not-restored");
            return result;
        } finally {
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            if (sim.getGeneratedBoardInstance() != originalOwner ||
                    sim.getGeneratedChallengeController() != originalChallenge)
                snapshot.restore(sim);
            snapshot.assertRestored(sim);
        }
    }

    private static String runSettlementCases(CirSim sim, Task41SimulationSnapshot snapshot,
            boolean originalQuickPlayActive, QuickPlaySession originalQuickPlaySession,
            GeneratedBoardInstance originalOwner, GeneratedChallengeController originalChallenge) {
        QuickPlaySession session = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 0, 3 }));
        GeneratedBoardInstance candidate = session.getInstance();
        GeneratedChallengeController challenge = null;
        String baseline;
        String removed;
        String wrong;
        String healthyBefore;
        String healthyAfter;
        boolean healthyPreRetest;
        boolean healthyPreFinish;
        boolean naturalRetest = false;
        boolean naturalFinish = false;
        boolean repeatedFinish;
        boolean repeatedRetest;
        try {
            snapshot.beginProof(sim);
            installQuickPlaySession(sim, session);
            challenge = requireReady(sim, candidate, "G-primary");
            baseline = point("readyOriginal", sim, challenge, null, null);

            ResistorSlotController slots = requireResistorSlots(sim, candidate, "G-primary");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.updateCircuit();
            sim.setSimRunning(false);
            require(slots.removeInstalledPart(), "task43p-G-original-remove-failed");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            boolean removedPendingBefore = sim.generatedBoardVerificationPending;
            boolean removedAnalyzedBefore = sim.generatedBoardVerificationAnalyzed;
            GeneratedCustomerRetestResult removedRetest = sim.performCustomerRetest();
            boolean removedFinish = sim.finishQuickPlayJob();
            require(removedRetest != null && !removedRetest.isPassed() && !removedFinish &&
                    !challenge.isCompleted(), "task43p-G-removed-board-falsely-completed");
            removed = point("removedPausedPowered", sim, challenge, removedRetest, removedFinish,
                removedPendingBefore, removedAnalyzedBefore);

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(slots.installNewFromCatalog("R_CATALOG_2200"),
                "task43p-G-wrong-2200-install-failed");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            boolean wrongPendingBefore = sim.generatedBoardVerificationPending;
            boolean wrongAnalyzedBefore = sim.generatedBoardVerificationAnalyzed;
            GeneratedCustomerRetestResult wrongRetest = sim.performCustomerRetest();
            boolean wrongFinish = sim.finishQuickPlayJob();
            require(wrongRetest != null && !wrongRetest.isPassed() && !wrongFinish &&
                    !challenge.isCompleted(), "task43p-G-wrong-board-falsely-completed");
            wrong = point("wrong2200PausedPowered", sim, challenge, wrongRetest, wrongFinish,
                wrongPendingBefore, wrongAnalyzedBefore);

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(slots.removeInstalledPart() &&
                    slots.installNewFromCatalog("R_CATALOG_1000"),
                "task43p-G-correct-1000-install-failed");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            boolean healthyPendingBefore = sim.generatedBoardVerificationPending;
            boolean healthyAnalyzedBefore = sim.generatedBoardVerificationAnalyzed;
            GeneratedCustomerRetestResult preRetestResult = sim.performCustomerRetest();
            healthyPreRetest = preRetestResult != null && preRetestResult.isPassed();
            healthyPreFinish = sim.finishQuickPlayJob();
            healthyBefore = point("healthy1000BeforeNaturalUpdate", sim, challenge,
                preRetestResult, healthyPreFinish, healthyPendingBefore,
                healthyAnalyzedBefore);

            /* Run the ordinary update path even when the pre-update call
             * happened to pass.  A queued verification bit alone is not
             * interpreted as a settlement defect. */
            sim.setSimRunning(true);
            for (int attempt = 0; attempt < 14; attempt++)
                sim.updateCircuit();
            if (!challenge.isCompleted()) {
                GeneratedCustomerRetestResult naturalRetestResult = sim.performCustomerRetest();
                naturalRetest = naturalRetestResult != null && naturalRetestResult.isPassed();
                naturalFinish = sim.finishQuickPlayJob();
            }
            healthyAfter = point("healthy1000AfterNaturalUpdate", sim, challenge,
                challenge.getCustomerRetestResult(),
                challenge.isCompleted() ? Boolean.TRUE :
                    (naturalFinish ? Boolean.TRUE : null));
            require(challenge.isCompleted(), "task43p-G-correct-board-did-not-complete");

            boolean ownerBeforeRepeated = sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() == challenge;
            boolean completedBeforeRepeated = challenge.isCompleted();
            repeatedFinish = sim.finishQuickPlayJob();
            GeneratedCustomerRetestResult repeatedRetestResult = sim.performCustomerRetest();
            repeatedRetest = repeatedRetestResult != null && repeatedRetestResult.isPassed();
            require(ownerBeforeRepeated && completedBeforeRepeated && challenge.isCompleted() &&
                    !repeatedFinish && !repeatedRetest &&
                    sim.getGeneratedBoardInstance() == candidate &&
                    sim.getGeneratedChallengeController() == challenge,
                "task43p-G-repeated-terminal-operation-changed-owner-or-state");
            boolean primaryOwnerCurrent = sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() == challenge;

            /* The rapid lane is a separate fresh-candidate case.  Return the
             * primary candidate to the exact owner boundary before asking the
             * shared snapshot to begin another proof. */
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-G-primary-owner-not-restored-before-rapid");
            String rapid = runRapidSequence(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge);
            return "{\"family\":" + q(candidate.getCircuitFamilyId()) +
                ",\"seed\":" + candidate.getSeed() +
                ",\"candidateOwnerCurrent\":" +
                primaryOwnerCurrent +
                ",\"healthyPreRetestPassed\":" + healthyPreRetest +
                ",\"healthyPreFinish\":" + healthyPreFinish +
                ",\"naturalRetestPassed\":" + naturalRetest +
                ",\"naturalFinish\":" + naturalFinish +
                ",\"repeatedFinish\":" + repeatedFinish +
                ",\"repeatedRetestPassed\":" + repeatedRetest +
                ",\"states\":[" + baseline + "," + removed + "," + wrong + "," +
                healthyBefore + "," + healthyAfter + "],\"rapid\":" + rapid +
                ",\"disposition\":\"CLOSED\"}";
        } finally {
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-G-primary-owner-not-restored");
        }
    }

    private static String runRapidSequence(CirSim sim, Task41SimulationSnapshot snapshot,
            boolean originalQuickPlayActive, QuickPlaySession originalQuickPlaySession,
            GeneratedBoardInstance originalOwner, GeneratedChallengeController originalChallenge) {
        QuickPlaySession session = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 0, 3 }));
        GeneratedBoardInstance candidate = session.getInstance();
        try {
            snapshot.beginProof(sim);
            installQuickPlaySession(sim, session);
            GeneratedChallengeController challenge = requireReady(sim, candidate, "G-rapid");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.updateCircuit();
            sim.setSimRunning(false);
            CircuitElm r1 = candidate.getComponentBindings().getSingleElement("R1");
            require(r1 != null && r1.getPostCount() >= 2, "task43p-G-rapid-R1-missing");
            ProbeTarget red = new CircuitPostProbeTarget(sim, r1, 0);
            ProbeTarget black = new CircuitPostProbeTarget(sim, r1, 1);
            sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
            double meterValue = sim.instrumentController
                .getLatestResistanceReadingForDeveloperVerification();
            require(!Double.isNaN(meterValue) && !Double.isInfinite(meterValue),
                "task43p-G-rapid-resistance-measurement-not-finite");
            require(sim.getBoardModificationController().liftLead("R1", "R1.1") &&
                    sim.getBoardModificationController().reconnectLead("R1", "R1.1"),
                "task43p-G-rapid-lift-reconnect-failed");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            int modeBeforeExit = sim.instrumentController.getActiveModeForDeveloperVerification();
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            GeneratedCustomerRetestResult retest = sim.performCustomerRetest();
            boolean finish = sim.finishQuickPlayJob();
            sim.setSimRunning(true);
            for (int attempt = 0; attempt < 14; attempt++)
                sim.updateCircuit();
            sim.instrumentController.clearTargets();
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            require(!sim.activeMeasurementOverlay && sim.pendingBoardPowerState == null &&
                    sim.instrumentController.getRedProbeForStrategy() == null &&
                    sim.instrumentController.getBlackProbeForStrategy() == null &&
                    !challenge.isCompleted(), "task43p-G-rapid-left-residue-or-completed");
            return "{\"family\":" + q(candidate.getCircuitFamilyId()) +
                ",\"seed\":" + candidate.getSeed() +
                ",\"meterResistance\":" + Double.toString(meterValue) +
                ",\"modeBeforeExit\":" + modeBeforeExit +
                ",\"retestPassed\":" + (retest != null && retest.isPassed()) +
                ",\"finish\":" + finish +
                ",\"pendingPower\":" + q(sim.pendingBoardPowerState == null ? null :
                    sim.pendingBoardPowerState.toString()) +
                ",\"activeMeasurementOverlay\":false,\"targetsEmpty\":true," +
                "\"disposition\":\"CLOSED\"}";
        } finally {
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-G-rapid-owner-not-restored");
        }
    }

    private static String runResetCases(CirSim sim, Task41SimulationSnapshot snapshot,
            boolean originalQuickPlayActive, QuickPlaySession originalQuickPlaySession,
            GeneratedBoardInstance originalOwner, GeneratedChallengeController originalChallenge) {
        String[] families = { LED, NPN, RC };
        long[] familyIndexes = { 0, 4, 3 };
        long[] seeds = { 3, 0, 2 };
        HObservation[] forward;
        HObservation[] reverse;
        StringBuilder cases = new StringBuilder();
        try {
            /* One proof begins the whole forward succession.  Each next
             * candidate is installed while the preceding candidate is still
             * current, so this observes the real owner handoff boundary. */
            forward = runResetOrder(sim, snapshot, families, familyIndexes, seeds,
                "forward", 0, 1);
            restoreOriginalAfterResetOrder(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge,
                "forward");

            /* The reverse succession gets its own proof boundary after the
             * exact original owner has been restored. */
            reverse = runResetOrder(sim, snapshot, families, familyIndexes, seeds,
                "reverse", families.length - 1, -1);
            for (int index = families.length - 1; index >= 0; index--) {
                require(forward[index].fingerprint.equals(reverse[index].fingerprint),
                    "task43p-H-forward-reverse-semantic-fingerprint-mismatch-" + families[index]);
            }
            for (int index = 0; index < families.length; index++) {
                if (index != 0) cases.append(',');
                cases.append(forward[index].toJson());
            }
            for (int index = families.length - 1; index >= 0; index--)
                cases.append(',').append(reverse[index].toJson());
            for (int index = 0; index < families.length; index++) {
                require(forward[index].session != reverse[index].session &&
                        forward[index].owner != reverse[index].owner &&
                        forward[index].owner.getBoard() != reverse[index].owner.getBoard() &&
                        forward[index].owner.getPhysicalBoardRuntime() !=
                            reverse[index].owner.getPhysicalBoardRuntime() &&
                        forward[index].challenge != reverse[index].challenge,
                    "task43p-H-fresh-session-boundary-reused-" + families[index]);
            }
            return "{\"caseCount\":6,\"cases\":[" + cases.toString() +
                "],\"forwardReverseExact\":true,\"disposition\":\"CLOSED\"}";
        } finally {
            restoreOriginalAfterResetOrder(sim, snapshot, originalQuickPlayActive,
                originalQuickPlaySession, originalOwner, originalChallenge, "final");
        }
    }

    private static HObservation[] runResetOrder(CirSim sim, Task41SimulationSnapshot snapshot,
            String[] families, long[] familyIndexes, long[] seeds, String order,
            int startIndex, int step) {
        snapshot.beginProof(sim);
        HObservation[] observations = new HObservation[families.length];
        HObservation previous = null;
        for (int offset = 0; offset < families.length; offset++) {
            int index = startIndex + offset * step;
            HObservation current = runResetRoute(sim, families[index], familyIndexes[index],
                seeds[index], order);
            if (previous != null)
                requireFreshOwnerSuccession(previous, current, order, families[index]);
            observations[index] = current;
            previous = current;
            if (offset + 1 < families.length) {
                /* Leave the dirty probe references in place until the next
                 * install owns the clear.  Only normalize the inherited mode,
                 * which keeps the fresh fingerprints comparable by order. */
                sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            }
        }
        return observations;
    }

    private static HObservation runResetRoute(CirSim sim, String family,
            long familyIndex, long seed, String order) {
        QuickPlaySession session = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { familyIndex, seed }));
        GeneratedBoardInstance candidate = session.getInstance();
        installQuickPlaySession(sim, session);
        GeneratedChallengeController challenge = requireReady(sim, candidate,
            "H-" + family + "-" + order);
        int modeAtFreshReady = sim.instrumentController.getActiveModeForDeveloperVerification();
        boolean freshRetestEmpty = challenge.getCustomerRetestResult() == null;
        boolean freshCompletionEmpty = !challenge.isCompleted();
        boolean freshInstrumentEmpty =
            sim.instrumentController.getRedProbeForStrategy() == null &&
            sim.instrumentController.getBlackProbeForStrategy() == null;
        /* Capture the clean candidate before any reset dirties it.  A fresh
         * install must clear the prior candidate's retest and probe objects;
         * the immediate observation is what proves that boundary. */
        require(freshRetestEmpty && freshCompletionEmpty && freshInstrumentEmpty,
            "task43p-H-fresh-state-not-empty-" + family);
        String freshFingerprint = semanticFingerprint(sim, session, candidate, challenge);

        /* Make a real failed retest and a real DC probe pair on this
         * family's resistor posts.  The references are retained so reset behavior
         * is recorded rather than assumed to be clean. */
        GeneratedCustomerRetestResult retestBeforeReset = sim.performCustomerRetest();
        require(retestBeforeReset != null && !retestBeforeReset.isPassed() &&
                challenge.getCustomerRetestResult() == retestBeforeReset &&
                sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() == challenge &&
                !challenge.isCompleted(),
            "task43p-H-real-failed-retest-precondition-" + family);
        String probeComponentId = NPN.equals(family) ? "RLOAD" : "R1";
        CircuitElm probeElement = candidate.getComponentBindings().getSingleElement(probeComponentId);
        require(probeElement instanceof ResistorElm && probeElement.getPostCount() == 2,
            "task43p-H-probe-resistor-missing-" + family + "-" + probeComponentId);
        ProbeTarget redBeforeReset = new CircuitPostProbeTarget(sim, probeElement, 0);
        ProbeTarget blackBeforeReset = new CircuitPostProbeTarget(sim, probeElement, 1);
        require(redBeforeReset.isValid() && blackBeforeReset.isValid(),
            "task43p-H-resistor-probe-target-invalid-" + family + "-" + probeComponentId);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(
            redBeforeReset, blackBeforeReset);
        require(sim.instrumentController.getRedProbeForStrategy() != null &&
                sim.instrumentController.getBlackProbeForStrategy() != null &&
                sim.instrumentController.getRedProbeForStrategy() == redBeforeReset &&
                sim.instrumentController.getBlackProbeForStrategy() == blackBeforeReset,
            "task43p-H-real-DC-probe-setup-failed-" + family);

        boolean resetHadRetest = challenge.getCustomerRetestResult() != null;
        boolean resetHadTargets = sim.instrumentController.getRedProbeForStrategy() != null &&
            sim.instrumentController.getBlackProbeForStrategy() != null;
        require(resetHadRetest && resetHadTargets && !challenge.isCompleted() &&
                challenge.getState() == GeneratedChallengeState.READY &&
                sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() == challenge &&
                sim.quickPlaySession == session,
            "task43p-H-dirty-precondition-not-real-" + family);

        sim.resetAction();
        settleAfterReset(sim, candidate, challenge, family);
        boolean resetOwnerPreserved = sim.getGeneratedBoardInstance() == candidate &&
            sim.getGeneratedChallengeController() == challenge && sim.quickPlaySession == session;
        boolean resetPreservedRetestReference =
            challenge.getCustomerRetestResult() == retestBeforeReset;
        boolean resetPreservedProbeReferences =
            sim.instrumentController.getRedProbeForStrategy() == redBeforeReset &&
            sim.instrumentController.getBlackProbeForStrategy() == blackBeforeReset;
        require(resetOwnerPreserved && challenge.getState() == GeneratedChallengeState.READY,
            "task43p-H-reset-replaced-owner-" + family);
        /* Reset is allowed to retain the dirty retest/probe state.  Fresh
         * installation is the separate boundary that must be empty. */
        return new HObservation(order, family, seed, session, candidate, challenge,
            freshFingerprint, modeAtFreshReady, resetOwnerPreserved, freshRetestEmpty,
            freshCompletionEmpty, freshInstrumentEmpty, resetHadRetest, resetHadTargets,
            resetPreservedRetestReference, resetPreservedProbeReferences);
    }

    private static void settleAfterReset(CirSim sim, GeneratedBoardInstance candidate,
            GeneratedChallengeController challenge, String family) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 14; attempt++)
            sim.updateCircuit();
        require(sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() == challenge && challenge.isReady(),
            "task43p-H-reset-did-not-settle-" + family);
    }

    private static void requireFreshOwnerSuccession(HObservation previous,
            HObservation current, String order, String family) {
        require(previous.session != current.session && previous.owner != current.owner &&
                previous.owner.getBoard() != current.owner.getBoard() &&
                previous.owner.getPhysicalBoardRuntime() !=
                    current.owner.getPhysicalBoardRuntime() &&
                previous.challenge != current.challenge,
            "task43p-H-" + order + "-fresh-owner-succession-" + family);
    }

    private static void restoreOriginalAfterResetOrder(CirSim sim,
            Task41SimulationSnapshot snapshot, boolean originalQuickPlayActive,
            QuickPlaySession originalQuickPlaySession, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge, String order) {
        sim.quickPlayActive = originalQuickPlayActive;
        sim.quickPlaySession = originalQuickPlaySession;
        if (sim.getGeneratedBoardInstance() != originalOwner ||
                sim.getGeneratedChallengeController() != originalChallenge)
            snapshot.restore(sim);
        snapshot.assertRestored(sim);
        require(sim.getGeneratedBoardInstance() == originalOwner &&
                sim.getGeneratedChallengeController() == originalChallenge,
            "task43p-H-owner-not-restored-" + order);
    }

    private static String runSnapshotOmissionCase(CirSim sim, Task41SimulationSnapshot snapshot,
            boolean originalQuickPlayActive, QuickPlaySession originalQuickPlaySession,
            GeneratedBoardInstance originalOwner, GeneratedChallengeController originalChallenge) {
        QuickPlaySession session = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 0, 3 }));
        GeneratedBoardInstance candidate = session.getInstance();
        boolean exactRoundTrip = false;
        boolean omissionRejected = false;
        String rejectionReason = null;
        try {
            snapshot.beginProof(sim);
            installQuickPlaySession(sim, session);
            requireReady(sim, candidate, "H-omission");
            int supportedInventoryCases = verifySupportedSnapshotInventory(sim);
            sim.lastResistanceTestCurrent = 123.456789;
            Task41SimulationSnapshot calibration = Task41SimulationSnapshot.capture(sim);
            sim.lastResistanceTestCurrent = -987.654321;
            calibration.restore(sim);
            calibration.assertRestored(sim);
            exactRoundTrip = sim.lastResistanceTestCurrent == 123.456789;
            require(exactRoundTrip, "task43p-H-snapshot-resistance-current-did-not-round-trip");

            sim.lastResistanceTestCurrent = -456.789;
            try {
                calibration.assertRestored(sim);
            } catch (IllegalStateException expected) {
                rejectionReason = expected.getMessage();
                omissionRejected = "Task 41 restore changed lastResistanceTestCurrent".equals(rejectionReason);
                if (!omissionRejected)
                    throw expected;
            }
            calibration.restore(sim);
            calibration.assertRestored(sim);
            return "{\"freshDetachedCandidate\":true,\"field\":\"lastResistanceTestCurrent\"," +
                "\"exactRoundTrip\":" + exactRoundTrip +
                ",\"task43pDetectorFieldComparison\":" + exactRoundTrip +
                ",\"runtimeSimulatedOmissionRejected\":" + omissionRejected +
                ",\"task41AssertRestoredRejectedPostRestoreSentinel\":" + omissionRejected +
                ",\"task41AssertRestoredAcceptedPostRestoreSentinel\":" + (!omissionRejected) +
                ",\"rejectionReason\":" + q(rejectionReason) +
                ",\"supportedInventoryCases\":" + supportedInventoryCases +
                ",\"sourceMutation\":false,\"disposition\":" +
                q(omissionRejected ? "CLOSED" : "OPEN_BLOCKER") + "}";
        } finally {
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-H-omission-owner-not-restored");
        }
    }

    private static int verifySupportedSnapshotInventory(CirSim sim) {
        Task41SimulationSnapshot baseline = Task41SimulationSnapshot.capture(sim);
        try {
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.updateCircuit();
            CircuitElm resistor = sim.getGeneratedBoardInstance().getComponentBindings().getSingleElement("R1");
            CircuitPostProbeTarget red = new CircuitPostProbeTarget(sim, resistor, 0);
            CircuitPostProbeTarget black = new CircuitPostProbeTarget(sim, resistor, 1);
            sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
            sim.setSimRunning(false);
            sim.mouseCursorX = -137;
            sim.lastTime = 24681357;
            sim.timeStepAccum = .03125;
            sim.timeStepCount = 73;
            sim.lastResistanceReferenceCurrent = Double.NaN;
            sim.lastDiodeMeasurementCurrent = .0125;
            sim.needsRepaint = false;
            sim.analyzeFlag = false;
            sim.circuitArea = new Rectangle(3, 5, 701, 409);
            Task41SimulationSnapshot supported = Task41SimulationSnapshot.capture(sim);
            sim.mouseCursorX = 7;
            sim.lastTime = 9;
            sim.timeStepAccum = 7;
            sim.timeStepCount = 4;
            sim.lastResistanceReferenceCurrent = 8;
            sim.lastDiodeMeasurementCurrent = 9;
            sim.instrumentController.clearTargets();
            sim.setSimRunning(true);
            supported.restore(sim);
            supported.assertRestored(sim);
            InstrumentController.DeveloperState state = sim.instrumentController.captureForDeveloperVerification();
            require(sim.mouseCursorX == -137 && sim.lastTime == 24681357 &&
                sim.timeStepAccum == .03125 && sim.timeStepCount == 73 &&
                Double.isNaN(sim.lastResistanceReferenceCurrent) && sim.lastDiodeMeasurementCurrent == .0125 &&
                !sim.simRunning && !sim.needsRepaint && !sim.analyzeFlag &&
                sim.circuitArea.x == 3 && sim.circuitArea.y == 5 &&
                sim.circuitArea.width == 701 && sim.circuitArea.height == 409 &&
                state.redProbe == red && state.blackProbe == black,
                "task43p-H-supported-snapshot-nondefault-round-trip-failed");
            Task41SimulationSnapshot.verifyInjectedFailureStagesForDeveloperVerification(sim);
            String[] groups = { "input", "render", "solver", "measurement/verification" };
            for (int group = 0; group < groups.length; group++) {
                if (group == 0) sim.mouseCursorX = 1;
                if (group == 1) sim.lastTime = 1;
                if (group == 2) sim.timeStepAccum = 1;
                if (group == 3) sim.lastResistanceReferenceCurrent = 1;
                boolean rejected = false;
                try {
                    supported.assertRestored(sim);
                } catch (IllegalStateException expected) {
                    rejected = ("Task 41 restore changed supported " + groups[group] + " state")
                        .equals(expected.getMessage());
                    if (!rejected)
                        throw expected;
                }
                require(rejected, "task43p-H-supported-snapshot-accepted-" + groups[group]);
                supported.restore(sim);
                supported.assertRestored(sim);
            }
            return groups.length;
        } finally {
            baseline.restore(sim);
            baseline.assertRestored(sim);
        }
    }

    private static void installQuickPlaySession(CirSim sim, QuickPlaySession session) {
        require(session != null && session.getSelection() != null && session.getInstance() != null,
            "task43p-runtime-settlement-missing-quick-play-session");
        sim.quickPlayActive = true;
        sim.quickPlaySession = session;
        sim.installGeneratedChallengeForDeveloperVerification(session.getInstance());
        require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
            "task43p-runtime-settlement-candidate-retained-player-workbench");
    }

    private static GeneratedChallengeController requireReady(CirSim sim,
            GeneratedBoardInstance candidate, String label) {
        settleReady(sim, candidate);
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(challenge != null && challenge.isReady(),
            "task43p-runtime-settlement-candidate-not-ready-" + label);
        return challenge;
    }

    private static void settleReady(CirSim sim, GeneratedBoardInstance candidate) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 14; attempt++) {
            sim.updateCircuit();
            if (sim.getGeneratedChallengeController() != null &&
                    sim.getGeneratedChallengeController().isReady())
                break;
        }
        require(sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() != null &&
                sim.getGeneratedChallengeController().isReady(),
            "task43p-runtime-settlement-candidate-did-not-settle");
    }

    private static ResistorSlotController requireResistorSlots(CirSim sim,
            GeneratedBoardInstance candidate, String label) {
        ReplaceableResistorBoardCapability capability =
            ReplaceableResistorBoardCapability.require(candidate);
        ResistorSlotController slots = capability.getController();
        require(slots != null, "task43p-runtime-settlement-missing-resistor-controller-" + label);
        return slots;
    }

    private static String point(String label, CirSim sim, GeneratedChallengeController challenge,
            GeneratedCustomerRetestResult retest, Boolean finish) {
        return point(label, sim, challenge, retest, finish, null, null);
    }

    private static String point(String label, CirSim sim, GeneratedChallengeController challenge,
            GeneratedCustomerRetestResult retest, Boolean finish,
            Boolean pendingBefore, Boolean analyzedBefore) {
        return "{\"label\":" + q(label) + ",\"ready\":" + challenge.isReady() +
            ",\"state\":" + q(challenge.getState().toString()) +
            ",\"completed\":" + challenge.isCompleted() +
            ",\"preGeneratedVerificationPending\":" + nullableBoolean(pendingBefore) +
            ",\"preGeneratedVerificationAnalyzed\":" + nullableBoolean(analyzedBefore) +
            ",\"analyzeFlag\":" + sim.analyzeFlag +
            ",\"generatedVerificationPending\":" + sim.generatedBoardVerificationPending +
            ",\"generatedVerificationAnalyzed\":" + sim.generatedBoardVerificationAnalyzed +
            ",\"t\":" + Double.toString(sim.t) +
            ",\"power\":" + q(sim.getBoardPowerController().getState().toString()) +
            ",\"retestPassed\":" + nullablePassed(retest) +
            ",\"finish\":" + nullableBoolean(finish) + "}";
    }

    private static String semanticFingerprint(CirSim sim, QuickPlaySession session,
            GeneratedBoardInstance instance, GeneratedChallengeController challenge) {
        StringBuilder result = new StringBuilder();
        result.append("board=").append(instance.getBoard().getId())
            .append("|family=").append(instance.getCircuitFamilyId())
            .append("|topology=").append(instance.getTopologyVariantId())
            .append("|seed=").append(instance.getSeed())
            .append("|description=").append(instance.getDescription())
            .append("|state=").append(challenge.getState())
            .append("|faultApplied=").append(challenge.getFaultController().isApplied())
            .append("|fault=").append(instance.getFaultBinding().getFault().getId())
            .append(':').append(instance.getFaultBinding().getFault().getType())
            .append(':').append(instance.getFaultBinding().getFault().getTargetComponentId())
            .append(':').append(Double.toString(instance.getFaultBinding().getFault().getHealthyValue()))
            .append(':').append(Double.toString(instance.getFaultBinding().getFault().getEffectiveValue()))
            .append("|power=").append(sim.getBoardPowerController().getState())
            .append("|fullyRestored=").append(sim.getBoardModificationController().isFullyRestored())
            .append("|retestPresent=").append(challenge.getCustomerRetestResult() != null)
            .append("|generatedPending=").append(sim.generatedBoardVerificationPending)
            .append("|generatedAnalyzed=").append(sim.generatedBoardVerificationAnalyzed)
            .append("|instrumentMode=").append(sim.instrumentController.getActiveModeForDeveloperVerification())
            .append("|redTarget=").append(sim.instrumentController.getRedProbeForStrategy() != null)
            .append("|blackTarget=").append(sim.instrumentController.getBlackProbeForStrategy() != null)
            .append("|quickPlayActive=").append(sim.quickPlayActive)
            .append("|selection=").append(session.getSelection().getFamilyId())
            .append(':').append(session.getSelection().getSeed());
        /* RcDelayTemporalBehavior.getRepairStatus deliberately performs a
         * real power-cycle sample.  A fingerprint must observe stable owner
         * state without advancing or mutating that temporal profile. */
        if (instance.getTemporalBehavior() != null)
            result.append("|temporalObserved=")
                .append(instance.getTemporalBehavior().getObservedBehavior());
        else
            result.append("|repair=").append(challenge.getDefinition().getBehaviorContract()
                .getRepairStatus(instance, sim.getBoardModificationController(),
                    sim.getBoardPowerController().getState(), sim.activeMeasurementOverlay));
        if (challenge.getScenario() != null)
            result.append("|scenario=").append(challenge.getScenario().getScenarioId())
                .append(':').append(challenge.getScenario().getComplaintId())
                .append(':').append(challenge.getScenario().getObservedBehavior());
        GeneratedChallengeLifecycleEvidence lifecycle = challenge.getLifecycleEvidence();
        result.append("|lifecycle=").append(lifecycle.healthyGenerationInstalled)
            .append(':').append(lifecycle.healthyGraphAnalyzedAfterTimeAdvance)
            .append(':').append(lifecycle.healthyFamilyValidated)
            .append(':').append(lifecycle.selectedFaultApplied)
            .append(':').append(lifecycle.faultedGraphAnalyzedAfterTimeAdvance)
            .append(':').append(lifecycle.selectedFaultValidated)
            .append(':').append(lifecycle.scenarioCompatibilityValidated)
            .append(':').append(lifecycle.readyAfterValidation);

        Vector<String> componentIds = instance.getBoard().getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
            PhysicalPart<?> part = slot == null ? null : slot.getInstalledPart();
            result.append("|component=").append(componentId)
                .append(':').append(part == null ? "empty" : part.getId())
                .append(':').append(part != null && part.isOriginal())
                .append(':').append(part != null && part.isFaulted())
                .append(':').append(part != null && part.isInstalled());
            Vector<GeneratedComponentConnectionBinding> bindings =
                instance.getConnectionBindings().getForComponentOrEmpty(componentId);
            Collections.sort(bindings, new Comparator<GeneratedComponentConnectionBinding>() {
                public int compare(GeneratedComponentConnectionBinding left,
                        GeneratedComponentConnectionBinding right) {
                    return left.getPadId().compareTo(right.getPadId());
                }
            });
            for (GeneratedComponentConnectionBinding binding : bindings)
                result.append("|lead=").append(binding.getPadId()).append(':')
                    .append(sim.getBoardModificationController().isLeadConnected(componentId,
                        binding.getPadId()));
        }

        Vector<String> padIds = instance.getBoard().getPadIds();
        Collections.sort(padIds);
        for (String padId : padIds) {
            BoardPad pad = instance.getBoard().getPad(padId);
            result.append("|pad=").append(pad.getId()).append(':')
                .append(pad.getComponentId()).append(':').append(pad.getTerminalId())
                .append(':').append(pad.getNetId());
        }
        Vector<String> netIds = instance.getBoard().getNetIds();
        Collections.sort(netIds);
        for (String netId : netIds)
            result.append("|net=").append(netId);
        Vector<PhysicalPart> parts = instance.getPhysicalBoardRuntime().getPhysicalParts();
        Vector<String> partIds = new Vector<String>();
        for (PhysicalPart part : parts)
            partIds.add(part.getId());
        Collections.sort(partIds);
        for (String partId : partIds) {
            PhysicalPart part = instance.getPhysicalBoardRuntime().getPart(partId);
            result.append("|part=").append(partId).append(':')
                .append(part.isOriginal()).append(':').append(part.isFaulted())
                .append(':').append(part.isInstalled()).append(':')
                .append(part.getBoardSlot() == null ? "none" : part.getBoardSlot().getId());
        }
        return result.toString();
    }

    private static String nullablePassed(GeneratedCustomerRetestResult result) {
        return result == null ? "null" : Boolean.toString(result.isPassed());
    }

    private static String nullableBoolean(Boolean value) {
        return value == null ? "null" : value.toString();
    }

    private static String q(String value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\') result.append("\\\\");
            else if (character == '"') result.append("\\\"");
            else if (character == '\n') result.append("\\n");
            else if (character == '\r') result.append("\\r");
            else if (character == '\t') result.append("\\t");
            else result.append(character);
        }
        return result.append('"').toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class HObservation {
        private final String order;
        private final String family;
        private final long seed;
        private final QuickPlaySession session;
        private final GeneratedBoardInstance owner;
        private final GeneratedChallengeController challenge;
        private final String fingerprint;
        private final int modeAtFreshReady;
        private final boolean resetOwnerPreserved;
        private final boolean freshRetestEmpty;
        private final boolean freshCompletionEmpty;
        private final boolean freshInstrumentEmpty;
        private final boolean resetHadRetest;
        private final boolean resetHadTargets;
        private final boolean resetPreservedRetestReference;
        private final boolean resetPreservedProbeReferences;

        HObservation(String order, String family, long seed, QuickPlaySession session,
                GeneratedBoardInstance owner, GeneratedChallengeController challenge,
                String fingerprint, int modeAtFreshReady, boolean resetOwnerPreserved,
                boolean freshRetestEmpty, boolean freshCompletionEmpty,
                boolean freshInstrumentEmpty, boolean resetHadRetest,
                boolean resetHadTargets, boolean resetPreservedRetestReference,
                boolean resetPreservedProbeReferences) {
            this.order = order;
            this.family = family;
            this.seed = seed;
            this.session = session;
            this.owner = owner;
            this.challenge = challenge;
            this.fingerprint = fingerprint;
            this.modeAtFreshReady = modeAtFreshReady;
            this.resetOwnerPreserved = resetOwnerPreserved;
            this.freshRetestEmpty = freshRetestEmpty;
            this.freshCompletionEmpty = freshCompletionEmpty;
            this.freshInstrumentEmpty = freshInstrumentEmpty;
            this.resetHadRetest = resetHadRetest;
            this.resetHadTargets = resetHadTargets;
            this.resetPreservedRetestReference = resetPreservedRetestReference;
            this.resetPreservedProbeReferences = resetPreservedProbeReferences;
        }

        String toJson() {
            return "{\"order\":" + q(order) + ",\"family\":" + q(family) +
                ",\"seed\":" + seed + ",\"resetOwnerPreserved\":" +
                resetOwnerPreserved + ",\"freshRetestEmpty\":" + freshRetestEmpty +
                ",\"freshCompletionEmpty\":" + freshCompletionEmpty +
                ",\"freshInstrumentTargetsEmpty\":" + freshInstrumentEmpty +
                ",\"resetHadRetest\":" + resetHadRetest +
                ",\"resetHadTargets\":" + resetHadTargets +
                ",\"resetPreservedRetestReference\":" +
                    resetPreservedRetestReference +
                ",\"resetPreservedProbeReferences\":" +
                    resetPreservedProbeReferences +
                ",\"modeAtFreshReady\":" + modeAtFreshReady +
                ",\"fingerprint\":" + q(fingerprint) + "}";
        }
    }
}
