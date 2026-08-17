package com.lushprojects.circuitjs1.client;

/** Immutable, typed-neutral capability metadata for workbench discovery. */
final class WorkbenchCapabilityMetadata {
    private final String id;
    private final String displayName;
    private final String operationId;

    WorkbenchCapabilityMetadata(String id, String displayName, String operationId) {
        if (id == null || id.length() == 0 || displayName == null || displayName.length() == 0 ||
                operationId == null || operationId.length() == 0)
            throw new IllegalArgumentException("Invalid workbench capability metadata");
        this.id = id;
        this.displayName = displayName;
        this.operationId = operationId;
    }

    String getId() { return id; }
    String getDisplayName() { return displayName; }
    String getOperationId() { return operationId; }
}
