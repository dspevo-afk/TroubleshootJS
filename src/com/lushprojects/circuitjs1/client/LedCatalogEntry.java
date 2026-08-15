package com.lushprojects.circuitjs1.client;

final class LedCatalogEntry {
    private final String id;
    private final String displayName;
    private final LedNameplate nameplate;
    private final boolean reversedInstallation;

    LedCatalogEntry(String id, String displayName, LedNameplate nameplate,
            boolean reversedInstallation) {
        if (id == null || id.length() == 0 || displayName == null || displayName.length() == 0 ||
                nameplate == null)
            throw new IllegalArgumentException("Invalid LED catalog entry");
        this.id = id;
        this.displayName = displayName;
        this.nameplate = nameplate;
        this.reversedInstallation = reversedInstallation;
    }

    String getId() { return id; }
    String getDisplayName() { return displayName; }
    LedNameplate getNameplate() { return nameplate; }
    boolean isReversedInstallation() { return reversedInstallation; }
}
