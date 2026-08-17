package com.lushprojects.circuitjs1.client;

final class RemovablePartCapability implements PhysicalPartCapability {
    private static final WorkbenchCapabilityMetadata METADATA =
        new WorkbenchCapabilityMetadata("REMOVABLE", "Removable", "INSTALL_OR_REMOVE");
    public WorkbenchCapabilityMetadata getMetadata() { return METADATA; }
    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation != null && WorkbenchOperation.INSTALL.equals(operation.getId()))
            return "Install component";
        if (operation != null && WorkbenchOperation.RESTORE.equals(operation.getId()))
            return "Restore component";
        return "Remove component";
    }
    public boolean supports(WorkbenchOperation operation) {
        return operation != null && operation.getPart() != null &&
            (WorkbenchOperation.INSTALL.equals(operation.getId()) ||
            WorkbenchOperation.REMOVE.equals(operation.getId()) ||
            WorkbenchOperation.RESTORE.equals(operation.getId()));
    }
    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation) && context != null && context.isAvailable(operation);
    }
    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation) && context != null && context.dispatch(operation);
    }
}
