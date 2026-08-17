package com.lushprojects.circuitjs1.client;

final class LoosePartInspectableCapability implements PhysicalPartCapability {
    private static final WorkbenchCapabilityMetadata METADATA =
        new WorkbenchCapabilityMetadata("LOOSE_PART_INSPECTABLE", "Loose part inspectable",
            "INSPECT_LOOSE");
    public WorkbenchCapabilityMetadata getMetadata() { return METADATA; }
    public String getOperationLabel(WorkbenchOperation operation) { return "Inspect loose part"; }
    public boolean supports(WorkbenchOperation operation) {
        return operation != null && operation.getPart() != null &&
            WorkbenchOperation.INSPECT_LOOSE.equals(operation.getId());
    }
    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation) && !operation.getPart().isInstalled();
    }
    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return isAvailable(operation, context);
    }
}
