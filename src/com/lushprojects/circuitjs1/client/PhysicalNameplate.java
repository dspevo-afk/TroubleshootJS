package com.lushprojects.circuitjs1.client;

/** Immutable player-visible markings; never a catalog entry or solver specification. */
final class PhysicalNameplate {
    private final String id;
    private final String displayName;
    private final String workbenchDetailLabel;
    private final String workbenchDetailValue;

    PhysicalNameplate(String id, String displayName) {
        this(id, displayName, null, null);
    }

    PhysicalNameplate(String id, String displayName, String workbenchDetailLabel,
            String workbenchDetailValue) {
        if (id == null || id.length() == 0 || displayName == null || displayName.length() == 0)
            throw new IllegalArgumentException("Invalid physical nameplate");
        if ((workbenchDetailLabel == null) != (workbenchDetailValue == null) ||
                (workbenchDetailLabel != null && (workbenchDetailLabel.length() == 0 ||
                workbenchDetailValue.length() == 0)))
            throw new IllegalArgumentException("Invalid physical workbench markings");
        this.id = id;
        this.displayName = displayName;
        this.workbenchDetailLabel = workbenchDetailLabel;
        this.workbenchDetailValue = workbenchDetailValue;
    }

    String getId() { return id; }
    String getDisplayName() { return displayName; }
    boolean hasWorkbenchDetail() { return workbenchDetailLabel != null; }
    String getWorkbenchDetailLabel() { return workbenchDetailLabel; }
    String getWorkbenchDetailValue() { return workbenchDetailValue; }

    /** Copies catalog-visible markings onto a newly acquired physical identity. */
    PhysicalNameplate forPhysicalPartId(String physicalPartId) {
        return new PhysicalNameplate(physicalPartId, displayName, workbenchDetailLabel,
            workbenchDetailValue);
    }
}
