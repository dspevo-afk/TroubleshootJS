package com.lushprojects.circuitjs1.client;

/** Family-neutral metadata mapping for the currently generated fault effects. */
final class GeneratedFaultServiceabilityCatalog {
    private static final String[] OBSERVE_COMPONENT = {
        GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS
    };
    private static final String[] OBSERVE_PUBLIC = {
        GeneratedFaultServiceability.OBSERVE_PUBLIC_TERMINALS
    };
    private static final String[] OBSERVE_CONNECTOR = {
        GeneratedFaultServiceability.OBSERVE_CONNECTOR_CONTACT
    };
    private static final String[] ISOLATE_REMOVE = { WorkbenchOperation.REMOVE };
    private static final String[] ISOLATE_LEAD = { WorkbenchOperation.LIFT_LEAD };
    private static final String[] REPAIR_REPLACE = {
        WorkbenchOperation.CATALOG_INSTALL
    };
    private static final String[] WORKFLOW_LEAD = {
        WorkbenchOperation.RECONNECT_LEAD
    };

    private GeneratedFaultServiceabilityCatalog() { }

    static GeneratedFaultServiceability forFault(GeneratedFault fault) {
        if (fault == null)
            throw new IllegalArgumentException("Missing fault for serviceability metadata");
        String componentId = fault.getTargetComponentId();
        switch (fault.getType()) {
        case CAPACITOR_OPEN:
            return serviceable(GeneratedFaultLocus.terminalAttachment(componentId, "+"),
                OBSERVE_COMPONENT, ISOLATE_LEAD, REPAIR_REPLACE, WORKFLOW_LEAD);
        case NMOS_GATE_OPEN:
            return serviceable(GeneratedFaultLocus.terminalAttachment(componentId, "G"),
                OBSERVE_PUBLIC, ISOLATE_LEAD, REPAIR_REPLACE, WORKFLOW_LEAD);
        case CONNECTOR_OPEN_PATH:
            // The contact has a semantic identity, but there is deliberately no
            // repair primitive yet.  The candidate remains incompatible.
            return new GeneratedFaultServiceability(
                GeneratedFaultLocus.connectorContact(componentId, "1"),
                OBSERVE_CONNECTOR, ISOLATE_REMOVE, new String[0],
                GeneratedBoardOperationIds.CUSTOMER_RETEST);
        case LOAD_PATH_OPEN:
            // Option B: preserve the solver effect for forced developer routes,
            // but do not claim a physical copper owner or player repair.
            return new GeneratedFaultServiceability(
                GeneratedFaultLocus.traceSegment("NPN_LOAD_PATH"),
                OBSERVE_COMPONENT, new String[0], new String[0],
                GeneratedBoardOperationIds.CUSTOMER_RETEST);
        case RESISTOR_OPEN:
        case RESISTOR_INCORRECT_VALUE:
        case DIODE_OPEN:
        case DIODE_SHORT:
        case CAPACITOR_SHORT:
        case TRANSISTOR_CE_OPEN:
        case TRANSISTOR_CE_SHORT:
        case BASE_RESISTOR_OPEN:
        case NMOS_DS_OPEN:
        case NMOS_DS_SHORT:
            return serviceable(GeneratedFaultLocus.componentInternal(componentId),
                fault.getType() == GeneratedFaultType.TRANSISTOR_CE_OPEN ||
                fault.getType() == GeneratedFaultType.TRANSISTOR_CE_SHORT ||
                fault.getType() == GeneratedFaultType.NMOS_DS_OPEN ||
                fault.getType() == GeneratedFaultType.NMOS_DS_SHORT ?
                    OBSERVE_PUBLIC : OBSERVE_COMPONENT,
                ISOLATE_REMOVE, REPAIR_REPLACE);
        default:
            return null;
        }
    }

    private static GeneratedFaultServiceability serviceable(GeneratedFaultLocus locus,
            String[] observations, String[] isolations, String[] repairs) {
        return new GeneratedFaultServiceability(locus, observations, isolations, repairs,
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
    }

    private static GeneratedFaultServiceability serviceable(GeneratedFaultLocus locus,
            String[] observations, String[] isolations, String[] repairs,
            String[] workflows) {
        return new GeneratedFaultServiceability(locus, observations, isolations, repairs,
            workflows, GeneratedBoardOperationIds.CUSTOMER_RETEST);
    }
}
