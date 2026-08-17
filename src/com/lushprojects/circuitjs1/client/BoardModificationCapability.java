package com.lushprojects.circuitjs1.client;

/** Capability adapter for board-owned connections and catalog slot actions. */
final class BoardModificationCapability implements WorkbenchCapabilityStrategy {
    private static final WorkbenchCapabilityMetadata METADATA =
        new WorkbenchCapabilityMetadata("BOARD_MODIFICATION", "Board modification",
            "BOARD_OPERATION");

    public WorkbenchCapabilityMetadata getMetadata() { return METADATA; }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify component";
        if (WorkbenchOperation.REMOVE.equals(operation.getId())) return "Remove component";
        if (WorkbenchOperation.LIFT_LEAD.equals(operation.getId())) return "Lift lead";
        if (WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId())) return "Reconnect lead";
        if (WorkbenchOperation.RESTORE.equals(operation.getId())) return "Restore component";
        if (WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId()))
            return "Install replacement";
        return "Modify component";
    }

    public boolean supports(WorkbenchOperation operation) {
        if (operation == null)
            return false;
        return WorkbenchOperation.REMOVE.equals(operation.getId()) ||
            WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId()) ||
            WorkbenchOperation.RESTORE.equals(operation.getId()) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId());
    }

    public boolean isAvailable(WorkbenchOperation operation,
            WorkbenchCapabilityContext context) {
        return supports(operation) && context != null && context.isAvailable(operation);
    }

    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        if (!supports(operation) || context == null)
            return false;
        return context.dispatch(operation);
    }
}
