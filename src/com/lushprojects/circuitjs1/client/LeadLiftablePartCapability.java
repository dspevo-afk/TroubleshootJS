package com.lushprojects.circuitjs1.client;

final class LeadLiftablePartCapability implements PhysicalPartCapability {
    private static final WorkbenchCapabilityMetadata METADATA =
        new WorkbenchCapabilityMetadata("LEAD_LIFTABLE", "Lead liftable", "LIFT_LEAD");
    public WorkbenchCapabilityMetadata getMetadata() { return METADATA; }
    public String getOperationLabel(WorkbenchOperation operation) {
        return operation != null && WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId()) ?
            "Reconnect lead" : "Lift lead";
    }
    public boolean supports(WorkbenchOperation operation) {
        return operation != null && operation.getPart() != null &&
            (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId()));
    }
    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation) && context != null && context.isAvailable(operation);
    }
    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation) && context != null && context.dispatch(operation);
    }
}
