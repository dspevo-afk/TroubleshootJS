package com.lushprojects.circuitjs1.client;

/** Player-visible catalog row with no physical-part identity or solver backing. */
final class WorkbenchCatalogEntry {
    private final String id;
    private final String displayName;

    WorkbenchCatalogEntry(String id, String displayName) {
        if (id == null || id.length() == 0 || displayName == null || displayName.length() == 0)
            throw new IllegalArgumentException("Invalid workbench catalog entry");
        this.id = id;
        this.displayName = displayName;
    }

    String getId() { return id; }
    String getDisplayName() { return displayName; }
}
