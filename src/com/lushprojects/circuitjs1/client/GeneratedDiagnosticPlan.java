package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * One bounded, player-operable diagnostic plan template.  The plan contains
 * semantic board and operation IDs only; it never contains solver identities
 * or generated geometry.
 */
final class GeneratedDiagnosticPlan {
    private final String templateId;
    private final String referenceTargetId;
    private final Vector<String> probeTargetIds;
    private final Vector<String> meterModeIds;
    private final Vector<String> inputPowerTransitions;
    private final Vector<String> isolationActionIds;
    private final Vector<String> repairActionIds;
    private final Vector<String> workflowActionIds;
    private final Vector<String> playerOperationIds;
    private final Vector<String> temporalWaitSampleIds;
    private final Vector<String> railDomainIds;
    private final int depth;
    private final boolean parallelPathAmbiguity;
    private final boolean unaffectedFunctionRetestObservation;
    private final String equivalentRepairClass;

    GeneratedDiagnosticPlan(String templateId, String referenceTargetId,
            String[] probeTargetIds, String[] meterModeIds,
            String[] inputPowerTransitions, String[] isolationActionIds,
            String[] repairActionIds, String[] playerOperationIds,
            String[] temporalWaitSampleIds, String[] railDomainIds, int depth,
            boolean parallelPathAmbiguity, boolean unaffectedFunctionRetestObservation,
            String equivalentRepairClass) {
        this(templateId, referenceTargetId, probeTargetIds, meterModeIds,
            inputPowerTransitions, isolationActionIds, repairActionIds, new String[0],
            playerOperationIds, temporalWaitSampleIds, railDomainIds, depth,
            parallelPathAmbiguity, unaffectedFunctionRetestObservation, equivalentRepairClass);
    }

    GeneratedDiagnosticPlan(String templateId, String referenceTargetId,
            String[] probeTargetIds, String[] meterModeIds,
            String[] inputPowerTransitions, String[] isolationActionIds,
            String[] repairActionIds, String[] workflowActionIds,
            String[] playerOperationIds, String[] temporalWaitSampleIds,
            String[] railDomainIds, int depth, boolean parallelPathAmbiguity,
            boolean unaffectedFunctionRetestObservation, String equivalentRepairClass) {
        requireSemanticId(templateId, "diagnostic template ID");
        requireSemanticId(referenceTargetId, "diagnostic reference target ID");
        if (depth <= 0 || equivalentRepairClass == null || equivalentRepairClass.length() == 0)
            throw new IllegalArgumentException("Invalid diagnostic plan complexity metadata");
        this.templateId = templateId;
        this.referenceTargetId = referenceTargetId;
        this.probeTargetIds = copy(probeTargetIds, "probe target");
        this.meterModeIds = copy(meterModeIds, "meter mode");
        this.inputPowerTransitions = copy(inputPowerTransitions, "input/power transition");
        this.isolationActionIds = copy(isolationActionIds, "isolation action");
        this.repairActionIds = copy(repairActionIds, "repair action");
        this.workflowActionIds = copyOptional(workflowActionIds, "workflow action");
        this.playerOperationIds = copy(playerOperationIds, "player operation");
        this.temporalWaitSampleIds = copy(temporalWaitSampleIds, "temporal wait/sample");
        this.railDomainIds = copy(railDomainIds, "rail/domain");
        this.depth = depth;
        this.parallelPathAmbiguity = parallelPathAmbiguity;
        this.unaffectedFunctionRetestObservation = unaffectedFunctionRetestObservation;
        this.equivalentRepairClass = equivalentRepairClass;
    }

    String getTemplateId() { return templateId; }
    String getReferenceTargetId() { return referenceTargetId; }
    Vector<String> getProbeTargetIds() { return new Vector<String>(probeTargetIds); }
    Vector<String> getMeterModeIds() { return new Vector<String>(meterModeIds); }
    Vector<String> getInputPowerTransitions() {
        return new Vector<String>(inputPowerTransitions);
    }
    Vector<String> getIsolationActionIds() { return new Vector<String>(isolationActionIds); }
    Vector<String> getRepairActionIds() { return new Vector<String>(repairActionIds); }
    Vector<String> getWorkflowActionIds() { return new Vector<String>(workflowActionIds); }
    Vector<String> getPlayerOperationIds() { return new Vector<String>(playerOperationIds); }
    Vector<String> getTemporalWaitSampleIds() {
        return new Vector<String>(temporalWaitSampleIds);
    }
    Vector<String> getRailDomainIds() { return new Vector<String>(railDomainIds); }
    int getDepth() { return depth; }
    boolean hasParallelPathAmbiguity() { return parallelPathAmbiguity; }
    boolean hasUnaffectedFunctionRetestObservation() {
        return unaffectedFunctionRetestObservation;
    }
    String getEquivalentRepairClass() { return equivalentRepairClass; }

    private static Vector<String> copy(String[] values, String category) {
        if (values == null || values.length == 0)
            throw new IllegalArgumentException("Diagnostic plan has no " + category + " IDs");
        Vector<String> result = new Vector<String>();
        for (String value : values) {
            requireSemanticId(value, category + " ID");
            if (result.contains(value))
                throw new IllegalArgumentException("Duplicate diagnostic " + category + " ID");
            result.add(value);
        }
        return result;
    }

    private static Vector<String> copyOptional(String[] values, String category) {
        if (values == null)
            throw new IllegalArgumentException("Diagnostic plan has no " + category + " list");
        Vector<String> result = new Vector<String>();
        for (String value : values) {
            requireSemanticId(value, category + " ID");
            if (result.contains(value))
                throw new IllegalArgumentException("Duplicate diagnostic " + category + " ID");
            result.add(value);
        }
        return result;
    }

    private static void requireSemanticId(String value, String name) {
        if (value == null || value.length() == 0 || !value.matches("[A-Za-z0-9_.+\\-]+"))
            throw new IllegalArgumentException("Invalid semantic " + name);
        String upper = value.toUpperCase();
        String[] forbidden = { "COORD", "INDEX", "UUID", "SOLVER", "PRIVATE",
            "ANSWER", "HINT", "CANDIDATE", "FAULT_SWITCH", "NODE_NUMBER" };
        for (String token : forbidden)
            if (upper.indexOf(token) >= 0)
                throw new IllegalArgumentException("Diagnostic ID must not encode " + token);
    }
}
