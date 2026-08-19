package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Hidden admission metadata connecting a physical fault owner to legal
 * workbench actions and the existing Task 39 customer retest operation.
 */
final class GeneratedFaultServiceability {
    static final String OBSERVE_COMPONENT_TERMINALS = "OBSERVE_COMPONENT_TERMINALS";
    static final String OBSERVE_PUBLIC_TERMINALS = "OBSERVE_PUBLIC_TERMINALS";
    static final String OBSERVE_CONNECTOR_CONTACT = "OBSERVE_CONNECTOR_CONTACT";

    private final GeneratedFaultLocus locus;
    private final Vector<String> observationActionIds;
    private final Vector<String> isolationActionIds;
    private final Vector<String> repairActionIds;
    private final Vector<String> workflowActionIds;
    private final String customerRetestOperationId;

    GeneratedFaultServiceability(GeneratedFaultLocus locus, String[] observations,
            String[] isolations, String[] repairs, String customerRetestOperationId) {
        this(locus, observations, isolations, repairs, new String[0], customerRetestOperationId);
    }

    GeneratedFaultServiceability(GeneratedFaultLocus locus, String[] observations,
            String[] isolations, String[] repairs, String[] workflows,
            String customerRetestOperationId) {
        if (locus == null || observations == null || isolations == null || repairs == null ||
                workflows == null)
            throw new IllegalArgumentException("Incomplete generated fault serviceability");
        GeneratedBoardOperation.requireStableSemanticId(customerRetestOperationId,
            "fault retest operation ID");
        this.locus = locus;
        observationActionIds = copyActionIds(observations, "observation");
        isolationActionIds = copyActionIds(isolations, "isolation");
        repairActionIds = copyActionIds(repairs, "repair");
        workflowActionIds = copyActionIds(workflows, "workflow");
        this.customerRetestOperationId = customerRetestOperationId;
    }

    GeneratedFaultLocus getLocus() { return locus; }
    Vector<String> getObservationActionIds() { return new Vector<String>(observationActionIds); }
    Vector<String> getIsolationActionIds() { return new Vector<String>(isolationActionIds); }
    Vector<String> getRepairActionIds() { return new Vector<String>(repairActionIds); }
    Vector<String> getFaultClearingRepairActionIds() {
        return new Vector<String>(repairActionIds);
    }
    Vector<String> getWorkflowActionIds() { return new Vector<String>(workflowActionIds); }
    String getCustomerRetestOperationId() { return customerRetestOperationId; }
    String getRetestOperationId() { return customerRetestOperationId; }
    int getObservationActionCount() { return observationActionIds.size(); }
    int getIsolationActionCount() { return isolationActionIds.size(); }
    int getRepairActionCount() { return repairActionIds.size(); }
    boolean hasRepairPrimitive() { return !repairActionIds.isEmpty(); }

    /** A candidate is not admitted merely because its solver effect exists. */
    boolean isAdmissible() {
        return locus != null && !observationActionIds.isEmpty() &&
            !isolationActionIds.isEmpty() && !repairActionIds.isEmpty() &&
            hasExecutableObservationContract() && hasExecutableRepairContract() &&
            hasExecutableWorkflowIds() &&
            GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(customerRetestOperationId);
    }

    private boolean hasExecutableObservationContract() {
        for (String actionId : observationActionIds)
            if (!GeneratedActionVocabulary.isExecutableObservation(actionId)) return false;
        for (String actionId : isolationActionIds)
            if (!GeneratedActionVocabulary.isExecutableIsolation(actionId)) return false;
        return true;
    }

    private boolean hasExecutableRepairContract() {
        for (String actionId : repairActionIds)
            if (!GeneratedActionVocabulary.isFaultClearingRepair(actionId)) return false;
        return true;
    }

    private boolean hasExecutableWorkflowIds() {
        for (String actionId : workflowActionIds)
            if (!GeneratedActionVocabulary.isExecutableWorkflow(actionId)) return false;
        return true;
    }

    boolean hasOnlyKnownActionIds() {
        for (String actionId : observationActionIds)
            if (!GeneratedActionVocabulary.isKnownObservation(actionId)) return false;
        for (String actionId : isolationActionIds)
            if (!GeneratedActionVocabulary.isKnownWorkbench(actionId)) return false;
        for (String actionId : repairActionIds)
            if (!GeneratedActionVocabulary.isKnownWorkbench(actionId)) return false;
        for (String actionId : workflowActionIds)
            if (!GeneratedActionVocabulary.isKnownWorkbench(actionId)) return false;
        return true;
    }

    private static Vector<String> copyActionIds(String[] values, String category) {
        Vector<String> result = new Vector<String>();
        for (String value : values) {
            GeneratedBoardOperation.requireStableSemanticId(value, category + " action ID");
            if (result.contains(value))
                throw new IllegalArgumentException("Duplicate " + category + " action ID");
            result.add(value);
        }
        return result;
    }
}
