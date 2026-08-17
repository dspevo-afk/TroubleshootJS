package com.lushprojects.circuitjs1.client;

/** Execution boundary between type-neutral capabilities and board mutation owners. */
interface WorkbenchCapabilityContext {
    boolean isAvailable(WorkbenchOperation operation);
    boolean dispatch(WorkbenchOperation operation);
}
