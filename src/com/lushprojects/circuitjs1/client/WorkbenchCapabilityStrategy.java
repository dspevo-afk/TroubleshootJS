package com.lushprojects.circuitjs1.client;

/** Executable, type-neutral operation contract for a workbench capability. */
interface WorkbenchCapabilityStrategy {
    WorkbenchCapabilityMetadata getMetadata();
    /** User-facing label for the concrete operation represented by this strategy. */
    String getOperationLabel(WorkbenchOperation operation);
    boolean supports(WorkbenchOperation operation);
    boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context);
    boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context);
}
