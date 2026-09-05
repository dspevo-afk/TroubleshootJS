package com.lushprojects.circuitjs1.client;

/**
 * Developer-only observation of the existing runtime mutation lanes. Each
 * route uses a fresh generated candidate and the existing solver-backed
 * verifier; Task41SimulationSnapshot owns restoration of the original board.
 */
final class Task43PRuntimeExistingLanesVerifier {
    private Task43PRuntimeExistingLanesVerifier() { }

    static String verify(CirSim sim) {
        require(sim != null, "task43p-runtime-existing-missing-simulator");
        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalChallenge =
            sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null && originalChallenge.isReady(),
            "task43p-runtime-existing-original-owner-not-ready");
        require(!sim.activeMeasurementOverlay,
            "task43p-runtime-existing-original-owner-has-measurement-overlay");

        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        StringBuilder cases = new StringBuilder();
        int caseCount = 0;
        try {
            CaseObservation observation = runCase(sim, snapshot, "A",
                QuickPlayFamilyRegistry.LED_INDICATOR, 3, originalOwner, originalChallenge);
            cases.append(observation.toJson());
            caseCount++;

            observation = runCase(sim, snapshot, "B/F",
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, originalOwner, originalChallenge);
            cases.append(',').append(observation.toJson());
            caseCount++;

            observation = runCase(sim, snapshot, "C",
                QuickPlayFamilyRegistry.LED_INDICATOR, 3, originalOwner, originalChallenge);
            cases.append(',').append(observation.toJson());
            caseCount++;

            observation = runCase(sim, snapshot, "E",
                QuickPlayFamilyRegistry.RC_DELAY, 2, originalOwner, originalChallenge);
            cases.append(',').append(observation.toJson());
            caseCount++;

            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-runtime-existing-original-owner-not-restored-at-end");
            return "{\"protocol\":\"TSJ-TASK43P-ABCEF-1\",\"status\":\"OBSERVED\"," +
                "\"caseCount\":" + caseCount + ",\"cases\":[" + cases.toString() +
                "],\"originalOwnerRestored\":true}";
        } finally {
            /* A failed candidate must never become the caller's active owner. */
            if (sim.getGeneratedBoardInstance() != originalOwner ||
                    sim.getGeneratedChallengeController() != originalChallenge) {
                snapshot.restore(sim);
                snapshot.assertRestored(sim);
            }
        }
    }

    private static CaseObservation runCase(CirSim sim, Task41SimulationSnapshot snapshot,
            String lane, String familyId, long seed, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge) {
        GeneratedBoardInstance candidate = null;
        GeneratedChallengeController challenge = null;
        PhysicalPart<?> originalPart = null;
        String faultIdBefore = null;
        String faultTypeBefore = null;
        GeneratedFaultBinding faultBindingBefore = null;
        PartObservation originalPartBefore = null;
        boolean faultAppliedBefore = false;
        CaseObservation observation = null;
        try {
            snapshot.beginProof(sim);
            candidate = QuickPlayFamilyRegistry.generate(familyId, seed);
            sim.installGeneratedChallengeForDeveloperVerification(candidate);
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "task43p-runtime-existing-candidate-retained-player-workbench-" + lane);
            settleReady(sim, candidate);
            challenge = sim.getGeneratedChallengeController();
            require(challenge != null && challenge.isReady(),
                "task43p-runtime-existing-candidate-not-ready-" + lane);

            GeneratedFaultBinding binding = candidate.getFaultBinding();
            require(binding != null && binding.getFault() != null,
                "task43p-runtime-existing-candidate-missing-fault-" + lane);
            faultBindingBefore = binding;
            faultIdBefore = binding.getFault().getId();
            faultTypeBefore = binding.getFault().getType().toString();
            faultAppliedBefore = binding.isApplied();
            originalPart = installedTargetPart(candidate, binding.getFault().getTargetComponentId());
            require(originalPart != null && originalPart.isInstalled(),
                "task43p-runtime-existing-candidate-missing-installed-target-" + lane);
            require(originalPart.isFaulted(),
                "task43p-runtime-existing-candidate-target-not-faulted-" + lane);
            originalPartBefore = PartObservation.capture(originalPart);

            if ("A".equals(lane))
                runOriginalRoundTripAndWrongRepair(sim, candidate, challenge, originalPart,
                    faultBindingBefore);
            else if ("B/F".equals(lane))
                MeterLifecycleDeveloperVerifier.verify(sim);
            else if ("C".equals(lane))
                ResistorStressDamageDeveloperVerifier.verify(sim);
            else if ("E".equals(lane))
                StoredEnergyDeveloperVerifier.verify(sim);
            else
                throw new IllegalArgumentException("Unknown Task 43P runtime lane: " + lane);

            observation = observe(sim, lane, candidate, challenge, faultIdBefore,
                faultTypeBefore, faultBindingBefore, faultAppliedBefore, originalPartBefore,
                originalOwner, originalChallenge);
        } finally {
            /* Restore the exact pre-proof graph even when a helper throws. */
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                    sim.getGeneratedChallengeController() == originalChallenge,
                "task43p-runtime-existing-original-owner-not-restored-" + lane);
        }
        require(observation != null, "task43p-runtime-existing-missing-observation-" + lane);
        observation.ownerRestored = true;
        return observation;
    }

    private static void runOriginalRoundTripAndWrongRepair(CirSim sim,
            GeneratedBoardInstance candidate, GeneratedChallengeController challenge,
            PhysicalPart<?> originalPart, GeneratedFaultBinding faultBinding) {
        require(candidate.getCircuitFamilyId().equals(QuickPlayFamilyRegistry.LED_INDICATOR) &&
                candidate.getSeed() == 3,
            "task43p-runtime-existing-A-candidate-mismatch");
        ReplaceableResistorBoardCapability family =
            ReplaceableResistorBoardCapability.require(candidate);
        ResistorSlotController slots = family.getController();
        require(slots != null && originalPart instanceof PhysicalResistorPart,
            "task43p-runtime-existing-A-missing-resistor-slot");
        PhysicalResistorPart original = (PhysicalResistorPart) originalPart;

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() && !original.isInstalled() && original.isFaulted(),
            "task43p-runtime-existing-A-original-remove-lost-fault-owner");
        require(slots.install(original.getId()) && original.isInstalled() &&
                original.ownsGeneratedFault(faultBinding),
            "task43p-runtime-existing-A-original-reinstall-lost-fault-owner");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settleReady(sim, candidate);
        require(challenge.getLiveRepairStatus() == GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
                !challenge.isCompleted() && !challenge.getDefinition().getBehaviorContract()
                    .isFunctionallyRepaired(candidate, sim.getBoardModificationController(),
                        BoardPowerState.POWERED, false),
            "task43p-runtime-existing-A-original-reinstall-appeared-repaired");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart(),
            "task43p-runtime-existing-A-original-remove-before-healthy-substitution-failed");
        require(slots.installNewFromCatalog("R_CATALOG_1000"),
            "task43p-runtime-existing-A-healthy-substitution-failed");
        PhysicalResistorPart healthy = family.getSlot().getInstalledPart();
        require(healthy != null && healthy != original && !healthy.isFaulted(),
            "task43p-runtime-existing-A-healthy-substitution-identity-failed");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settleReady(sim, candidate);
        require(challenge.getLiveRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED &&
                challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(candidate,
                    sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "task43p-runtime-existing-A-healthy-substitution-not-solver-restored");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart() && slots.install(original.getId()),
            "task43p-runtime-existing-A-original-restore-after-healthy-substitution-failed");
        require(original.isInstalled() && original.isFaulted() &&
                original.ownsGeneratedFault(faultBinding),
            "task43p-runtime-existing-A-original-fault-binding-changed");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settleReady(sim, candidate);
        require(challenge.getLiveRepairStatus() == GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
                !challenge.isCompleted(),
            "task43p-runtime-existing-A-original-not-nonfunctional-after-restore");

        /* The existing helper performs the real wrong-2200/correct-1000/
         * customer-retest completion sequence on this same candidate. */
        ReplacementDeveloperVerifier.verifyWrongRepair(sim);
    }

    private static CaseObservation observe(CirSim sim, String lane,
            GeneratedBoardInstance candidate, GeneratedChallengeController challenge,
            String faultIdBefore, String faultTypeBefore,
            GeneratedFaultBinding bindingBefore, boolean faultAppliedBefore,
            PartObservation originalPartBefore, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge) {
        GeneratedFaultBinding bindingAfter = candidate.getFaultBinding();
        GeneratedFault faultAfter = bindingAfter == null ? null : bindingAfter.getFault();
        PhysicalPart<?> installedAfter = faultAfter == null ? null : installedTargetPart(candidate,
            faultAfter.getTargetComponentId());
        return new CaseObservation(lane, candidate.getCircuitFamilyId(),
            candidate.getTopologyVariantId(), candidate.getSeed(), challenge.getState().toString(),
            challenge.getLiveRepairStatus().toString(), challenge.isCompleted(),
            challenge.getCustomerRetestResult() != null, faultIdBefore, faultTypeBefore,
            bindingBefore == candidate.getFaultBinding(), faultAppliedBefore,
            originalPartBefore, PartObservation.capture(installedAfter),
            faultAfter == null ? null : faultAfter.getId(),
            faultAfter == null ? null : faultAfter.getType().toString(),
            faultAfter == null ? null : faultAfter.getTargetComponentId(),
            bindingAfter == bindingBefore, bindingAfter != null && bindingAfter.isApplied(),
            sim.getGeneratedBoardInstance() == candidate &&
                sim.getGeneratedChallengeController() == challenge,
            sim.getGeneratedBoardInstance() != originalOwner &&
                sim.getGeneratedChallengeController() != originalChallenge,
            sim.getBoardPowerController().getState().toString(), sim.generatedBoardVerificationPending,
            sim.generatedBoardVerificationAnalyzed,
            sim.getBoardModificationController().isFullyRestored());
    }

    private static PhysicalPart<?> installedTargetPart(GeneratedBoardInstance instance,
            String componentId) {
        if (instance == null || componentId == null || instance.getPhysicalBoardRuntime() == null)
            return null;
        return instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
    }

    private static void settleReady(CirSim sim, GeneratedBoardInstance instance) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 14; attempt++) {
            sim.updateCircuit();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            if (challenge != null && challenge.isReady())
                break;
        }
        require(sim.getGeneratedBoardInstance() == instance &&
                sim.getGeneratedChallengeController() != null &&
                sim.getGeneratedChallengeController().isReady(),
            "task43p-runtime-existing-candidate-did-not-settle-" +
                (instance == null ? "null" : instance.getCircuitFamilyId()));
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static String q(String value) {
        if (value == null)
            return "null";
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

    private static final class CaseObservation {
        private final String lane;
        private final String family;
        private final String topology;
        private final long seed;
        private final String challengeStateAfter;
        private final String repairStatusAfter;
        private final boolean completedAfter;
        private final boolean retestPresentAfter;
        private final String faultIdBefore;
        private final String faultTypeBefore;
        private final boolean bindingIdentityBefore;
        private final boolean faultAppliedBefore;
        private final PartObservation originalPartBefore;
        private final PartObservation installedAfter;
        private final String faultIdAfter;
        private final String faultTypeAfter;
        private final String faultTargetAfter;
        private final boolean bindingIdentityPreserved;
        private final boolean faultAppliedAfter;
        private final boolean candidateOwnerCurrent;
        private final boolean candidateWasIsolated;
        private final String boardPowerAfter;
        private final boolean verificationPendingAfter;
        private final boolean verificationAnalyzedAfter;
        private final boolean modificationsFullyRestoredAfter;
        private boolean ownerRestored;

        CaseObservation(String lane, String family, String topology, long seed,
                String challengeStateAfter, String repairStatusAfter, boolean completedAfter,
                boolean retestPresentAfter, String faultIdBefore, String faultTypeBefore,
                boolean bindingIdentityBefore, boolean faultAppliedBefore,
                PartObservation originalPartBefore, PartObservation installedAfter,
                String faultIdAfter, String faultTypeAfter, String faultTargetAfter,
                boolean bindingIdentityPreserved, boolean faultAppliedAfter,
                boolean candidateOwnerCurrent, boolean candidateWasIsolated,
                String boardPowerAfter, boolean verificationPendingAfter,
                boolean verificationAnalyzedAfter, boolean modificationsFullyRestoredAfter) {
            this.lane = lane;
            this.family = family;
            this.topology = topology;
            this.seed = seed;
            this.challengeStateAfter = challengeStateAfter;
            this.repairStatusAfter = repairStatusAfter;
            this.completedAfter = completedAfter;
            this.retestPresentAfter = retestPresentAfter;
            this.installedAfter = installedAfter;
            this.faultIdBefore = faultIdBefore;
            this.faultTypeBefore = faultTypeBefore;
            this.bindingIdentityBefore = bindingIdentityBefore;
            this.faultAppliedBefore = faultAppliedBefore;
            this.originalPartBefore = originalPartBefore;
            this.faultIdAfter = faultIdAfter;
            this.faultTypeAfter = faultTypeAfter;
            this.faultTargetAfter = faultTargetAfter;
            this.bindingIdentityPreserved = bindingIdentityPreserved;
            this.faultAppliedAfter = faultAppliedAfter;
            this.candidateOwnerCurrent = candidateOwnerCurrent;
            this.candidateWasIsolated = candidateWasIsolated;
            this.boardPowerAfter = boardPowerAfter;
            this.verificationPendingAfter = verificationPendingAfter;
            this.verificationAnalyzedAfter = verificationAnalyzedAfter;
            this.modificationsFullyRestoredAfter = modificationsFullyRestoredAfter;
        }

        String toJson() {
            StringBuilder result = new StringBuilder();
            result.append("{\"lane\":").append(q(lane))
                .append(",\"family\":").append(q(family))
                .append(",\"topology\":").append(q(topology))
                .append(",\"seed\":").append(seed)
                .append(",\"helperAssertionsCompleted\":true")
                .append(",\"faultBefore\":{\"id\":").append(q(faultIdBefore))
                .append(",\"type\":").append(q(faultTypeBefore))
                .append(",\"bindingIdentity\":").append(bindingIdentityBefore)
                .append(",\"applied\":").append(faultAppliedBefore)
                .append("}")
                .append(",\"faultAfter\":{\"id\":").append(q(faultIdAfter))
                .append(",\"type\":").append(q(faultTypeAfter))
                .append(",\"target\":").append(q(faultTargetAfter))
                .append(",\"bindingIdentityPreserved\":").append(bindingIdentityPreserved)
                .append(",\"applied\":").append(faultAppliedAfter)
                .append("}")
                .append(",\"originalPartBefore\":").append(originalPartBefore.toJson())
                .append(",\"installedTargetAfter\":").append(installedAfter == null ? "null" :
                    installedAfter.toJson())
                .append(",\"challengeStateAfter\":").append(q(challengeStateAfter))
                .append(",\"repairStatusAfter\":").append(q(repairStatusAfter))
                .append(",\"completedAfter\":").append(completedAfter)
                .append(",\"retestPresentAfter\":").append(retestPresentAfter)
                .append(",\"boardPowerAfter\":").append(q(boardPowerAfter))
                .append(",\"verificationPendingAfter\":").append(verificationPendingAfter)
                .append(",\"verificationAnalyzedAfter\":").append(verificationAnalyzedAfter)
                .append(",\"modificationsFullyRestoredAfter\":").append(modificationsFullyRestoredAfter)
                .append(",\"candidateOwnerCurrent\":").append(candidateOwnerCurrent)
                .append(",\"candidateWasIsolated\":").append(candidateWasIsolated)
                .append(",\"ownerRestored\":").append(ownerRestored)
                .append("}");
            return result.toString();
        }
    }

    private static final class PartObservation {
        private final String id;
        private final boolean installed;
        private final boolean faulted;
        private final boolean original;

        private PartObservation(String id, boolean installed, boolean faulted,
                boolean original) {
            this.id = id;
            this.installed = installed;
            this.faulted = faulted;
            this.original = original;
        }

        static PartObservation capture(PhysicalPart<?> part) {
            if (part == null)
                return null;
            return new PartObservation(part.getId(), part.isInstalled(), part.isFaulted(),
                part.isOriginal());
        }

        String toJson() {
            return "{\"id\":" + q(id) + ",\"installed\":" + installed +
                ",\"faulted\":" + faulted + ",\"original\":" + original + "}";
        }
    }
}
