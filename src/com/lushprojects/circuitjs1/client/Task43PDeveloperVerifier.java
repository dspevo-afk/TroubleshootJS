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

    static void verifySourceExperimentBeforeAdmission(CirSim sim) {
        if (!sim.troubleshootTask43PVerification || !sim.isTask43PForcedFailureActive() ||
                isEmpty(sim.troubleshootTask43PSourceExperiment) ||
                sim.troubleshootTask43PSourceRequestValidated)
            return;
        validateForcedNegativeRequest(sim);
        // The real graph has already been analyzed and advanced. Run the
        // independent physical checker before another admission check can
        // mask the deliberately mutated producer. No admission is skipped.
        Task43PPhysicalTruthDeveloperVerifier.verify(sim);
    }

    static void verify(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null)
            throw new IllegalStateException("task43p-missing-generated-board");
        validateForcedNegativeRequest(sim);
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

        if ("snapshot-restore-resistance-current-omitted".equals(
                sim.troubleshootTask43PSourceExperiment))
            Task43PRuntimeSettlementDeveloperVerifier.verifySnapshotOmission(sim);
        if ("public-remove-action-disabled".equals(sim.troubleshootTask43PSourceExperiment))
            verifyPublicRemoveDirectControl(sim);

        String evidence = buildEvidence(sim, instance, challenge, beforeState, afterState,
            earlyFinish, physicalEvidence);
        sim.publishTask43PEvidenceForDeveloperVerification(evidence);
        if (sim.isTask43PForcedFailureActive())
            throw new IllegalStateException("task43p-forced-negative-canary nonce=" +
                sim.getTask43PForcedNonceForDeveloperVerification() + " route=" +
                sim.getTask43PForcedRouteIdForDeveloperVerification() + " request=" +
                sim.getTask43PForcedRequestIdForDeveloperVerification() + " execution=" +
                sim.getTask43PExecutionDigestForDeveloperVerification());
        if (sim.troubleshootTask43PRuntimeVerification) {
            verifyRuntime(sim, beforeState);
            return;
        }
        // The current architecture intentionally has no request/board/session
        // epoch.  The route therefore reports typed unproven evidence instead
        // of claiming a stronger settlement contract than the code provides.
        sim.publishTask43PResultForDeveloperVerification("UNPROVEN:task43p");
    }

    private static void verifyRuntime(final CirSim sim, String beforeState) {
        final String existing = Task43PRuntimeExistingLanesVerifier.verify(sim);
        final String measurement = Task43PRuntimeLifecycleDeveloperVerifier.verify(sim);
        final String settlement = Task43PRuntimeSettlementDeveloperVerifier.verify(sim);
        if (!beforeState.equals(verifierDesignState(sim, sim.getGeneratedBoardInstance(),
                sim.getGeneratedChallengeController())))
            throw new IllegalStateException("task43p-runtime-synchronous-owner-state-not-restored");
        Task43PRuntimeCallbackDeveloperVerifier.start(sim,
            new Task43PRuntimeCallbackDeveloperVerifier.Completion() {
                public void completed(String callback) {
                    String runtime = "{\"protocol\":\"TSJ-TASK43P-RUNTIME-1\"," +
                        "\"status\":\"OBSERVED\",\"developerOnly\":true,\"runId\":" +
                        q(sim.getVerifierRunIdForDeveloperVerification()) +
                        ",\"routeId\":" + q(sim.getVerifierRouteIdForDeveloperVerification()) +
                        ",\"sameOwnerAfterSynchronousCases\":true,\"existing\":" + existing +
                        ",\"measurement\":" + measurement + ",\"settlement\":" + settlement +
                        ",\"callback\":" + callback + "}";
                    sim.publishTask43PRuntimeEvidenceForDeveloperVerification(runtime);
                    sim.publishTask43PResultForDeveloperVerification("OBSERVED:task43p-runtime");
                }

                public void failed(RuntimeException failure) {
                    sim.publishTask43PResultForDeveloperVerification(
                        "FAIL:task43p-runtime-callback:" + failure.getMessage());
                    CirSim.console("Task43P runtime callback failed: " + failure.getMessage());
                }
            });
    }

    private static void verifyPublicRemoveDirectControl(CirSim sim) {
        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalChallenge = sim.getGeneratedChallengeController();
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        String observed = null;
        try {
            snapshot.beginProof(sim);
            GeneratedBoardInstance candidate = QuickPlayFamilyRegistry.generate(
                QuickPlayFamilyRegistry.LED_INDICATOR, 3);
            sim.installGeneratedChallengeForDeveloperVerification(candidate);
            sim.setSimRunning(true);
            for (int attempt = 0; attempt < 14 &&
                    !sim.getGeneratedChallengeController().isReady(); attempt++)
                sim.updateCircuit();
            if (sim.getGeneratedBoardInstance() != candidate ||
                    !sim.getGeneratedChallengeController().isReady())
                throw new IllegalStateException("task43p-public-remove-control-not-ready");
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.require(candidate);
            PhysicalResistorPart original = capability.getSlot().getInstalledPart();
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.updateCircuit();
            PcbWorkbenchController controller = sim.pcbWorkbenchController;
            if (original == null || controller == null)
                throw new IllegalStateException("task43p-public-remove-control-missing-part-or-controller");
            WorkbenchOperation remove = WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original);
            boolean available = controller.isAvailable(remove);
            boolean dispatched = available && controller.dispatch(remove);
            boolean removed = !original.isInstalled() && capability.getSlot().isEmpty() &&
                sim.getBoardModificationController().getComponentState("R1") ==
                    ComponentPhysicalState.REMOVED;
            if (!available || !dispatched || !removed)
                throw new IllegalStateException("task43p-public-remove-direct-control-failed");
            boolean installed = controller.dispatch(
                WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL, original,
                    capability.getSlot().getComponentId()));
            boolean restored = original.isInstalled() &&
                capability.getSlot().getInstalledPart() == original &&
                sim.getBoardModificationController().getComponentState("R1") ==
                    ComponentPhysicalState.INSTALLED &&
                sim.getBoardModificationController().isFullyRestored();
            if (!installed || !restored)
                throw new IllegalStateException("task43p-public-remove-direct-control-reinstall-failed");
            observed = "{\"protocol\":\"TSJ-TASK43P-PUBLIC-REMOVE-CONTROL-1\"," +
                "\"method\":\"PcbWorkbenchController.dispatch\",\"componentId\":\"R1\"," +
                "\"family\":\"LED_INDICATOR\",\"seed\":3,\"partId\":" + q(original.getId()) +
                ",\"availableBefore\":" + available +
                ",\"dispatchReturned\":" + dispatched + ",\"removedAfter\":" + removed +
                ",\"cleanupDispatchReturned\":" + installed + ",\"restoredAfter\":" + restored;
        } finally {
            snapshot.restore(sim);
            snapshot.assertRestored(sim);
            if (sim.getGeneratedBoardInstance() != originalOwner ||
                    sim.getGeneratedChallengeController() != originalChallenge)
                throw new IllegalStateException("task43p-public-remove-control-owner-not-restored");
        }
        sim.publishTask43PPublicRemoveControlForDeveloperVerification(observed +
            ",\"ownerRestored\":true,\"runId\":" + q(sim.getVerifierRunIdForDeveloperVerification()) +
            ",\"routeId\":" + q(sim.getVerifierRouteIdForDeveloperVerification()) +
            ",\"nonce\":" + q(sim.getTask43PForcedNonceForDeveloperVerification()) +
            ",\"requestId\":" + q(sim.getTask43PForcedRequestIdForDeveloperVerification()) +
            ",\"executionDigest\":" + q(sim.getTask43PExecutionDigestForDeveloperVerification()) + "}");
        // This anchored control is deliberately separate from the wrapper's
        // subsequent normal-player mouse input on the disabled public action.
        throw new IllegalStateException("task43p-public-remove-direct-control-passed");
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
        result.append("\"forcedNegativeProof\":")
            .append(forcedNegativeProof(sim)).append(',');
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

    private static void validateForcedNegativeRequest(CirSim sim) {
        sim.troubleshootTask43PSourceRequestValidated = false;
        String sourceExperiment = sim.troubleshootTask43PSourceExperiment;
        String nonce = sim.getTask43PForcedNonceForDeveloperVerification();
        String routeId = sim.getTask43PForcedRouteIdForDeveloperVerification();
        String requestId = sim.getTask43PForcedRequestIdForDeveloperVerification();
        String executionDigest = sim.getTask43PExecutionDigestForDeveloperVerification();
        if (!sim.isTask43PForcedFailureActive()) {
            if (!isEmpty(nonce) || !isEmpty(routeId) || !isEmpty(requestId) ||
                    !isEmpty(executionDigest) || !isEmpty(sourceExperiment))
                throw new IllegalStateException("task43p-unexpected-forced-negative-request");
            return;
        }
        if (!validOpaqueToken(nonce) || !validOpaqueToken(routeId) ||
                !validOpaqueToken(requestId) ||
                !executionDigest.matches("[0-9a-f]{64}") ||
                !sim.getVerifierRunIdForDeveloperVerification().matches(
                    "[A-Za-z0-9._-]{8,128}") ||
                !sim.getVerifierRouteIdForDeveloperVerification().equals(routeId))
            throw new IllegalStateException("task43p-forced-negative-request-invalid");
        if (!sim.getVerifierRunIdForDeveloperVerification().matches(
                "[A-Za-z0-9._-]{8,128}"))
            throw new IllegalStateException("task43p-forced-negative-run-invalid");
        if (!isEmpty(sourceExperiment)) {
            if (!knownSourceExperiment(sourceExperiment))
                throw new IllegalStateException("task43p-source-experiment-invalid");
            // The outer developer-only catch may bind a real validator failure
            // only after this same simulator accepted the complete request.
            sim.troubleshootTask43PSourceRequestValidated = true;
        }
    }

    private static boolean knownSourceExperiment(String id) {
        return "renderer-only-j1-1-plus-20px".equals(id) ||
            "renderer-only-j1-1-lead-plus-20px".equals(id) ||
            "raw-copper-j1-1-endpoint-gap".equals(id) ||
            "raw-net-mismatch".equals(id) ||
            "solver-binding-j1-1-post-mismatch".equals(id) ||
            "solver-detachable-c1-plus-identity-mismatch".equals(id) ||
            "package-mirror-mismatch".equals(id) ||
            "internally-self-consistent-wrong-mapping".equals(id) ||
            "omitted-manifest-terminal".equals(id) ||
            "snapshot-restore-resistance-current-omitted".equals(id) ||
            "public-remove-action-disabled".equals(id);
    }

    private static boolean validOpaqueToken(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{8,128}");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static String forcedNegativeProof(CirSim sim) {
        boolean requested = sim.isTask43PForcedFailureActive();
        return "{\"requested\":" + requested + ",\"nonce\":" +
            q(requested ? sim.getTask43PForcedNonceForDeveloperVerification() : "") +
            ",\"routeId\":" +
            q(requested ? sim.getTask43PForcedRouteIdForDeveloperVerification() : "") +
            ",\"requestId\":" +
            q(requested ? sim.getTask43PForcedRequestIdForDeveloperVerification() : "") +
            ",\"executionDigest\":" +
            q(requested ? sim.getTask43PExecutionDigestForDeveloperVerification() : "") + "}";
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
