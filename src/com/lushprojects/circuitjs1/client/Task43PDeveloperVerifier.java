package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Developer-only Task 43P evidence collector.  This is deliberately a
 * canary/evidence route over the existing generated-board lifecycle; it does
 * not add a gameplay transaction, epoch, or alternate electrical model.
 */
final class Task43PDeveloperVerifier {
    private Task43PDeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null)
            throw new IllegalStateException("task43p-missing-generated-board");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        if (challenge == null || !challenge.isReady())
            throw new IllegalStateException("task43p-challenge-not-ready");
        if (instance.getPcbLayout() == null || sim.pcbWorkbenchController == null)
            throw new IllegalStateException("task43p-missing-physical-runtime");

        String beforeState = verifierDesignState(sim, instance, challenge);
        boolean earlyFinish = challenge.finishJob();
        String afterEarlyFinishState = verifierDesignState(sim, instance, challenge);
        if (earlyFinish || !beforeState.equals(afterEarlyFinishState))
            throw new IllegalStateException("task43p-early-finish-latched-or-mutated-state");

        String physicalEvidence = Task43PPhysicalTruthDeveloperVerifier.verify(sim);
        String afterState = verifierDesignState(sim, instance, challenge);
        if (!beforeState.equals(afterState))
            throw new IllegalStateException("task43p-read-only-evidence-mutated-owner-state");

        String evidence = buildEvidence(sim, instance, challenge, beforeState, afterState,
            earlyFinish, physicalEvidence);
        sim.publishTask43PEvidenceForDeveloperVerification(evidence);
        if (sim.isTask43PForcedFailureActive())
            throw new IllegalStateException("task43p-forced-negative-canary");
        // The current architecture intentionally has no request/board/session
        // epoch.  The route therefore reports typed unproven evidence instead
        // of claiming a stronger settlement contract than the code provides.
        sim.publishTask43PResultForDeveloperVerification("UNPROVEN:task43p");
    }

    private static String buildEvidence(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, String beforeState, String afterState,
            boolean earlyFinish, String physicalEvidence) {
        GeneratedFault fault = challenge.getDefinition().getFault();
        GeneratedFaultLocus locus = instance.getFaultLocus();
        GeneratedChallengeLifecycleEvidence lifecycle = challenge.getLifecycleEvidence();
        StringBuilder result = new StringBuilder();
        result.append("{\"protocol\":\"TSJ-TASK43P-2\",\"status\":\"UNPROVEN\",");
        result.append("\"developerOnly\":true,\"forcedNegativeRequested\":")
            .append(sim.isTask43PForcedFailureActive()).append(',');
        result.append("\"family\":").append(q(instance.getCircuitFamilyId()))
            .append(",\"topology\":").append(q(instance.getTopologyVariantId()))
            .append(",\"seed\":").append(instance.getSeed())
            .append(",\"fault\":").append(q(fault.getId()))
            .append(",\"faultType\":").append(q(fault.getType().toString()))
            .append(",\"faultOwner\":").append(q(locus == null ? null : locus.getOwnerId()))
            .append(",\"faultApplied\":").append(challenge.getFaultController().isApplied())
            .append(",\"earlyFinishBlocked\":").append(!earlyFinish).append(',');
        result.append("\"state\":").append(q(challenge.getState().toString()))
            .append(",\"ready\":").append(challenge.isReady())
            .append(",\"completed\":").append(challenge.isCompleted())
            .append(",\"repairStatus\":").append(q(challenge.getLiveRepairStatus().toString()))
            .append(",\"retestPresent\":").append(challenge.getCustomerRetestResult() != null)
            .append(",\"generatedVerificationPending\":")
            .append(sim.generatedBoardVerificationPending)
            .append(",\"generatedVerificationAnalyzed\":")
            .append(sim.generatedBoardVerificationAnalyzed)
            .append(",\"developerVerifierRunning\":")
            .append(sim.developerVerifierRunning).append(',');
        result.append("\"lifecycle\":{\"healthyInstalled\":")
            .append(lifecycle.healthyGenerationInstalled)
            .append(",\"healthyAnalyzed\":").append(lifecycle.healthyGraphAnalyzedAfterTimeAdvance)
            .append(",\"faultApplied\":").append(lifecycle.selectedFaultApplied)
            .append(",\"faultAnalyzed\":").append(lifecycle.faultedGraphAnalyzedAfterTimeAdvance)
            .append(",\"faultValidated\":").append(lifecycle.selectedFaultValidated)
            .append(",\"readyAfterValidation\":").append(lifecycle.readyAfterValidation)
            .append("},");
        result.append("\"verifierDesignStateBefore\":").append(q(beforeState))
            .append(",\"verifierDesignStateAfter\":").append(q(afterState))
            .append(",\"mutationCleanup\":{\"readOnly\":true,\"activeMeasurementOverlay\":")
            .append(sim.activeMeasurementOverlay)
            .append(",\"pendingBoardPowerState\":")
            .append(q(sim.pendingBoardPowerState == null ? null :
                sim.pendingBoardPowerState.toString()))
            .append(",\"temporarySolverRestored\":")
            .append(sim.isActiveMeasurementSolverRestoredForDeveloperVerification())
            .append(",\"fullyRestoredAtEntry\":")
            .append(sim.getBoardModificationController().isFullyRestored())
            .append("},");
        result.append("\"lanes\":{");
        appendLane(result, "A", "PARTIAL", "early Finish blocked; isolation/repair/wrong-repair round trip not exercised by this route");
        appendLane(result, "B", "PARTIAL", "stable current part/slot identities checked; replacement A/B invalidation not exercised by this route");
        appendLane(result, "C", "PARTIAL", "fault/private owner separation checked; solver-derived secondary causality not exercised");
        appendLane(result, "D", "PARTIAL", "no current overlay/pending-power residue; rapid action and failure cleanup sequence not exercised");
        appendLane(result, "E", instance.getTemporalBehavior() == null ? "UNPROVEN" : "PARTIAL",
            instance.getTemporalBehavior() == null ? "current family has no stored-energy owner" :
                "temporal owner/readiness is present; full charge/off/residual/decay mutation profile not exercised");
        appendLane(result, "F", "PARTIAL", "installed/board-side physical identity and live triad checked; lift/reconnect/remove/reinstall sequence not exercised");
        appendLane(result, "G", "PARTIAL", "early Finish settlement guard checked; repair/retest/terminal Finish sequence not exercised");
        appendLane(result, "H", "UNPROVEN", "fresh-session and route-order isolation not exercised; Task 41 snapshot remains its narrower fresh-candidate proof");
        appendLane(result, "I", "UNPROVEN", "request/board/session epoch is absent; no stale callback effect reproduced");
        result.append("},\"epochContract\":{\"requestEpoch\":false,\"boardEpoch\":false,\"sessionEpoch\":false},");
        result.append("\"triad\":").append(physicalEvidence).append('}');
        return result.toString();
    }

    private static void appendLane(StringBuilder result, String id, String status,
            String observation) {
        if (result.charAt(result.length() - 1) != '{')
            result.append(',');
        result.append(q(id)).append(":{\"status\":").append(q(status))
            .append(",\"observation\":").append(q(observation)).append('}');
    }

    private static String verifierDesignState(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        StringBuilder result = new StringBuilder();
        result.append("board=").append(instance.getBoard().getId())
            .append("|family=").append(instance.getCircuitFamilyId())
            .append("|topology=").append(instance.getTopologyVariantId())
            .append("|seed=").append(instance.getSeed())
            .append("|state=").append(challenge.getState())
            .append("|fault=").append(challenge.getFaultController().isApplied())
            .append("|power=").append(sim.getBoardPowerController().getState())
            .append("|pendingPower=").append(sim.pendingBoardPowerState)
            .append("|overlay=").append(sim.activeMeasurementOverlay)
            .append("|pendingVerification=").append(sim.generatedBoardVerificationPending)
            .append("|analyzed=").append(sim.generatedBoardVerificationAnalyzed);
        Vector<String> componentIds = new Vector<String>(instance.getBoard().getComponentIds());
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
            PhysicalPart<?> part = slot == null ? null : slot.getInstalledPart();
            result.append("|slot=").append(slot == null ? "null" : slot.getId())
                .append(':').append(componentId).append(':')
                .append(part == null ? "empty" : part.getId()).append(':')
                .append(part == null ? "none" : part.getMountState().toString());
            Vector<GeneratedComponentConnectionBinding> bindings = instance.getConnectionBindings()
                .getForComponentOrEmpty(componentId);
            for (GeneratedComponentConnectionBinding binding : bindings)
                result.append("|lead=").append(binding.getPadId()).append(':')
                    .append(sim.getBoardModificationController().isLeadConnected(componentId,
                        binding.getPadId()));
        }
        Vector<PhysicalPart> parts = instance.getPhysicalBoardRuntime().getPhysicalParts();
        Vector<String> partIds = new Vector<String>();
        for (PhysicalPart part : parts)
            partIds.add(part.getId());
        Collections.sort(partIds);
        for (String partId : partIds) {
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getPart(partId);
            result.append("|part=").append(partId).append(':').append(part.isInstalled())
                .append(':').append(part.isOriginal()).append(':').append(part.isFaulted());
        }
        Vector<GeneratedComponentConnectionBinding> bindings =
            new Vector<GeneratedComponentConnectionBinding>(
                instance.getConnectionBindings().getAll());
        Collections.sort(bindings, new java.util.Comparator<GeneratedComponentConnectionBinding>() {
            public int compare(GeneratedComponentConnectionBinding first,
                    GeneratedComponentConnectionBinding second) {
                return first.getPadId().compareTo(second.getPadId());
            }
        });
        for (GeneratedComponentConnectionBinding binding : bindings)
            result.append("|connection=").append(binding.getPadId()).append(':')
                .append(instance.getConnectionBindings().get(binding.getComponentId(),
                    binding.getPadId()) == binding);
        return result.toString();
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
}
