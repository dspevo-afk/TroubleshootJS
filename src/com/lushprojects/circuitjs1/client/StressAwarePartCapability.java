package com.lushprojects.circuitjs1.client;

final class StressAwarePartCapability implements PhysicalPartCapability {
    private static final WorkbenchCapabilityMetadata METADATA =
        new WorkbenchCapabilityMetadata("STRESS_AWARE", "Stress aware", "OBSERVE_STRESS");
    public WorkbenchCapabilityMetadata getMetadata() { return METADATA; }
    public String getOperationLabel(WorkbenchOperation operation) { return "Observe stress"; }
    public boolean supports(WorkbenchOperation operation) {
        return operation != null && operation.getPart() != null &&
            "OBSERVE_STRESS".equals(operation.getId());
    }
    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return supports(operation);
    }
    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        return isAvailable(operation, context);
    }
}
