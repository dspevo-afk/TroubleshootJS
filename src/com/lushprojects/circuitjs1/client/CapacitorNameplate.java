package com.lushprojects.circuitjs1.client;

/** Player-permitted physical markings, kept separate from electrical fault state. */
final class CapacitorNameplate {
    private final String displayName;
    private final String marking;

    CapacitorNameplate(String displayName, String marking) {
        if (displayName == null || displayName.length() == 0 || marking == null ||
                marking.length() == 0)
            throw new IllegalArgumentException("Invalid capacitor nameplate");
        this.displayName = displayName;
        this.marking = marking;
    }

    String getDisplayName() { return displayName; }
    String getMarking() { return marking; }

    PhysicalNameplate forPhysicalPartId(String physicalPartId) {
        return new PhysicalNameplate(physicalPartId, displayName, "Marking", marking);
    }
}
