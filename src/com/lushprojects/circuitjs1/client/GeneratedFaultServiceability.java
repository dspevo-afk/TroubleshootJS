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
    private final String customerRetestOperationId;

    GeneratedFaultServiceability(GeneratedFaultLocus locus, String[] observations,
            String[] isolations, String[] repairs, String customerRetestOperationId) {
        if (locus == null || observations == null || isolations == null || repairs == null)
            throw new IllegalArgumentException("Incomplete generated fault serviceability");
        GeneratedBoardOperation.requireStableSemanticId(customerRetestOperationId,
            "fault retest operation ID");
        this.locus = locus;
        observationActionIds = copyActionIds(observations, "observation");
        isolationActionIds = copyActionIds(isolations, "isolation");
        repairActionIds = copyActionIds(repairs, "repair");
        this.customerRetestOperationId = customerRetestOperationId;
    }

    GeneratedFaultLocus getLocus() { return locus; }
    Vector<String> getObservationActionIds() { return new Vector<String>(observationActionIds); }
    Vector<String> getIsolationActionIds() { return new Vector<String>(isolationActionIds); }
    Vector<String> getRepairActionIds() { return new Vector<String>(repairActionIds); }
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
            hasOnlyKnownActionIds() &&
            GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(customerRetestOperationId);
    }

    private boolean hasOnlyKnownActionIds() {
        for (String actionId : observationActionIds)
            if (!isKnownObservationAction(actionId)) return false;
        for (String actionId : isolationActionIds)
            if (!isKnownWorkbenchAction(actionId)) return false;
        for (String actionId : repairActionIds)
            if (!isKnownWorkbenchAction(actionId)) return false;
        return true;
    }

    private static boolean isKnownObservationAction(String actionId) {
        return OBSERVE_COMPONENT_TERMINALS.equals(actionId) ||
            OBSERVE_PUBLIC_TERMINALS.equals(actionId) ||
            OBSERVE_CONNECTOR_CONTACT.equals(actionId);
    }

    private static boolean isKnownWorkbenchAction(String actionId) {
        return WorkbenchOperation.REMOVE.equals(actionId) ||
            WorkbenchOperation.LIFT_LEAD.equals(actionId) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(actionId) ||
            WorkbenchOperation.RESTORE.equals(actionId) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(actionId) ||
            WorkbenchOperation.INSTALL.equals(actionId);
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
