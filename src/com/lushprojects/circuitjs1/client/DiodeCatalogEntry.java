package com.lushprojects.circuitjs1.client;

final class DiodeCatalogEntry {
    private final String id;
    private final DiodeNameplate nameplate;
    private final boolean reversedInstallation;

    DiodeCatalogEntry(String id, DiodeNameplate nameplate, boolean reversedInstallation) {
        if (id == null || id.length() == 0 || nameplate == null)
            throw new IllegalArgumentException("Invalid diode catalog entry");
        this.id = id;
        this.nameplate = nameplate;
        this.reversedInstallation = reversedInstallation;
    }

    String getId() { return id; }
    DiodeNameplate getNameplate() { return nameplate; }
    boolean isReversedInstallation() { return reversedInstallation; }
}
