package com.lushprojects.circuitjs1.client;

/**
 * One bounded vocabulary for generated admission contracts.  The distinction
 * between a known/reserved action and an action executable by a normal player
 * is deliberately kept outside the individual family catalogs.
 */
final class GeneratedActionVocabulary {
    private GeneratedActionVocabulary() { }

    static boolean isKnownObservation(String actionId) {
        return GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS.equals(actionId) ||
            GeneratedFaultServiceability.OBSERVE_PUBLIC_TERMINALS.equals(actionId) ||
            GeneratedFaultServiceability.OBSERVE_CONNECTOR_CONTACT.equals(actionId);
    }

    static boolean isExecutableObservation(String actionId) {
        return GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS.equals(actionId) ||
            GeneratedFaultServiceability.OBSERVE_PUBLIC_TERMINALS.equals(actionId);
    }

    static boolean isKnownWorkbench(String actionId) {
        return WorkbenchOperation.REMOVE.equals(actionId) ||
            WorkbenchOperation.LIFT_LEAD.equals(actionId) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(actionId) ||
            WorkbenchOperation.RESTORE.equals(actionId) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(actionId) ||
            WorkbenchOperation.INSTALL.equals(actionId);
    }

    static boolean isExecutableWorkflow(String actionId) {
        return WorkbenchOperation.REMOVE.equals(actionId) ||
            WorkbenchOperation.LIFT_LEAD.equals(actionId) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(actionId) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(actionId) ||
            WorkbenchOperation.INSTALL.equals(actionId);
    }

    static boolean isExecutableIsolation(String actionId) {
        return WorkbenchOperation.REMOVE.equals(actionId) ||
            WorkbenchOperation.LIFT_LEAD.equals(actionId);
    }

    /** Only a catalog replacement clears a generated fault in the v1 contract. */
    static boolean isFaultClearingRepair(String actionId) {
        return WorkbenchOperation.CATALOG_INSTALL.equals(actionId);
    }

    static boolean isKnownPlanWorkflow(String actionId) {
        return isKnownWorkbench(actionId);
    }

    static boolean isKnownPlanRepair(String actionId) {
        return isKnownWorkbench(actionId);
    }
}
