package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Family-agnostic admission boundary for the Task 41 solvability contract.
 * This runs before a normal generated challenge can become READY.
 */
final class GeneratedDiagnosticSolvabilityAdmission {
    private static int internalProofDepth;

    private GeneratedDiagnosticSolvabilityAdmission() { }

    static boolean isInternalProofRunning() { return internalProofDepth != 0; }

    static void beginInternalProof() { internalProofDepth++; }

    static void endInternalProof() {
        if (internalProofDepth <= 0)
            throw new IllegalStateException("Task 41 diagnostic proof guard underflow");
        internalProofDepth--;
    }

    static void validateLive(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController ownerController) {
        validate(sim, instance);
        if (instance.isDeveloperOnlyFaultRoute() || isInternalProofRunning()) return;
        try {
            beginInternalProof();
            Task41DeveloperVerifier.verifyAdmissionRoute(sim, instance, ownerController);
        } finally {
            // verifyAdmissionRoute owns its nested guard.  This guard protects the
            // lifecycle boundary itself from re-entry while candidate boards load.
            endInternalProof();
        }
    }

    static void validate(GeneratedBoardInstance instance) {
        if (instance == null)
            throw new IllegalArgumentException("Missing diagnostic solvability board");
        GeneratedDiagnosticSolvabilityContract contract =
            instance.getDiagnosticSolvabilityContract();
        if (contract == null)
            throw new IllegalArgumentException("Generated challenge has no solvability contract");
        contract.validate(instance);
        for (GeneratedDiagnosticPlan plan : contract.getPlans()) {
            for (String targetId : plan.getProbeTargetIds())
                if (instance.getBoard().getPad(targetId) == null ||
                        instance.getSimulationBindings().getEndpoint(targetId) == null)
                    throw new IllegalArgumentException("Diagnostic plan has no board probe target: " +
                        targetId);
        }
    }

    static void validate(CirSim sim, GeneratedBoardInstance instance) {
        validate(instance);
        if (sim == null)
            throw new IllegalArgumentException("Diagnostic plan has no simulation owner");
        if (sim.getGeneratedBoardInstance() != instance) {
            GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
            throw new IllegalArgumentException("Diagnostic plan has no current board owner: expected=" +
                instance.getCircuitFamilyId() + "/" + instance.getSeed() + ", actual=" +
                (current == null ? "null" : current.getCircuitFamilyId() + "/" + current.getSeed()));
        }
        if (sim.pcbWorkbenchController == null)
            throw new IllegalArgumentException("Diagnostic plan has no rendered workbench");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        for (GeneratedDiagnosticPlan plan : instance.getDiagnosticSolvabilityContract().getPlans())
            for (String targetId : plan.getProbeTargetIds()) {
                if (!renderer.hasPad(targetId))
                    throw new IllegalArgumentException("Diagnostic plan target is not rendered: " +
                        targetId);
                Point point = renderer.getPadPoint(targetId);
                ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
                if (!(target instanceof BoardPadProbeTarget) || !target.isValid() ||
                        !targetId.equals(((BoardPadProbeTarget) target).getPadId()))
                    throw new IllegalArgumentException("Diagnostic plan target is not player probeable: " +
                        targetId);
            }
    }

    static void validatePlan(GeneratedDiagnosticPlan plan) {
        if (plan == null)
            throw new IllegalArgumentException("Missing diagnostic plan");
        validateAllowed(plan.getMeterModeIds(), new String[] { "DC_VOLTAGE", "RESISTANCE",
            "CONTINUITY", "DIODE" }, "meter mode");
        validateAllowed(plan.getInputPowerTransitions(), new String[] { "BOARD_POWER_ON",
            "BOARD_POWER_ON_INITIAL", "BOARD_POWER_ON_RETEST", "BOARD_POWER_OFF",
            "BOARD_POWER_OFF_INITIAL",
            "BOARD_POWER_OFF_FINAL", "RC_POWER_ON", "CONTROL_INPUT_HIGH",
            "CONTROL_INPUT_LOW", "RC_RESIDUAL_SAMPLE", "RC_EARLY_SAMPLE",
            "RC_LATE_SAMPLE" },
            "input/power transition");
        validateIsolationActions(plan.getIsolationActionIds());
        validateFaultClearingRepairActions(plan.getRepairActionIds());
        validateWorkflowActions(plan.getWorkflowActionIds());
        validateAllowed(plan.getPlayerOperationIds(), new String[] {
            GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,
            GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
            GeneratedBoardOperationIds.CUSTOMER_RETEST }, "player operation");
        validateAllowed(plan.getTemporalWaitSampleIds(), new String[] { "STEADY_STATE_SAMPLE",
            "FORWARD_DROP_SAMPLE", "BRANCH1_SAMPLE", "BRANCH2_SAMPLE", "RC_RESIDUAL_SAMPLE",
            "RC_EARLY_SAMPLE", "RC_LATE_SAMPLE", "CONTROL_HIGH_SAMPLE", "CONTROL_LOW_SAMPLE" },
            "temporal wait/sample");
        if (!plan.getPlayerOperationIds().contains(GeneratedBoardOperationIds.CUSTOMER_RETEST))
            throw new IllegalArgumentException("Diagnostic plan omits CUSTOMER_RETEST");
        for (String targetId : plan.getProbeTargetIds())
            validatePublicSemanticId(targetId, "probe target");
        for (String domainId : plan.getRailDomainIds())
            validatePublicSemanticId(domainId, "rail/domain");
    }

    private static void validateAllowed(Vector<String> actual, String[] allowed, String category) {
        for (String value : actual) {
            boolean known = false;
            for (String candidate : allowed)
                if (candidate.equals(value)) { known = true; break; }
            if (!known)
                throw new IllegalArgumentException("Diagnostic plan contains unsupported " +
                    category + ": " + value);
        }
    }

    private static void validateIsolationActions(Vector<String> actionIds) {
        for (String actionId : actionIds)
            if (!GeneratedActionVocabulary.isExecutableIsolation(actionId))
                throw new IllegalArgumentException("Diagnostic plan contains unsupported or reserved " +
                    "isolation action: " + actionId);
    }

    private static void validateFaultClearingRepairActions(Vector<String> actionIds) {
        for (String actionId : actionIds)
            if (!GeneratedActionVocabulary.isFaultClearingRepair(actionId))
                throw new IllegalArgumentException("Diagnostic plan repair action is not an executable " +
                    "fault-clearing repair: " + actionId);
    }

    private static void validateWorkflowActions(Vector<String> actionIds) {
        for (String actionId : actionIds)
            if (!GeneratedActionVocabulary.isExecutableWorkflow(actionId))
                throw new IllegalArgumentException("Diagnostic plan contains unsupported or reserved " +
                    "workflow action: " + actionId);
    }

    private static void validatePublicSemanticId(String value, String category) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Diagnostic plan has empty " + category);
        String upper = value.toUpperCase();
        String[] forbidden = { "DEVELOPER", "PRIVATE", "SOLVER", "FAULT", "ANSWER", "HINT",
            "CANDIDATE", "NODE_NUMBER", "COORD", "INDEX", "UUID" };
        for (String token : forbidden)
            if (upper.indexOf(token) >= 0)
                throw new IllegalArgumentException("Diagnostic plan contains hidden " + category);
    }

    static int getAdmittedCandidateCount(Vector<GeneratedFaultCandidate> candidates) {
        int count = 0;
        if (candidates != null)
            for (GeneratedFaultCandidate candidate : candidates)
                if (isAdmitted(candidate)) count++;
        return count;
    }

    static boolean isAdmitted(GeneratedFaultCandidate candidate) {
        return candidate != null && candidate.isCompatible() &&
            candidate.getServiceability() != null && candidate.getServiceability().isAdmissible();
    }

    static int getPhysicalOwnerCount(Vector<GeneratedFaultCandidate> candidates) {
        Vector<String> owners = new Vector<String>();
        if (candidates != null)
            for (GeneratedFaultCandidate candidate : candidates)
                if (isAdmitted(candidate)) {
                    String owner = candidate.getServiceability().getLocus().getOwnerId();
                    if (!owners.contains(owner)) owners.add(owner);
                }
        return owners.size();
    }

    static GeneratedDiagnosticOwnerDiversity getOwnerDiversity(
            Vector<GeneratedFaultCandidate> candidates) {
        return getPhysicalOwnerCount(candidates) > 1 ?
            GeneratedDiagnosticOwnerDiversity.MULTI_OWNER_DIAGNOSTIC :
            GeneratedDiagnosticOwnerDiversity.GUIDED_EASY_SINGLE_OWNER;
    }
}
