package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class BoardComponent {
    private final String id;
    private final String type;
    private final PhysicalPackage physicalPackage;
    private final String displayName;
    private final int referenceLabelCharacters;
    private final Vector<String> padIds = new Vector<String>();

    BoardComponent(String id, String type, PhysicalPackage physicalPackage) {
        this(id, type, physicalPackage, id);
    }

    BoardComponent(String id, String type, PhysicalPackage physicalPackage, String displayName) {
        this(id, type, physicalPackage, displayName, displayName == null ? 0 : displayName.length());
    }

    BoardComponent(String id, String type, PhysicalPackage physicalPackage, String displayName,
            int referenceLabelCharacters) {
        this.id = id;
        this.type = type;
        if (physicalPackage == null)
            throw new IllegalArgumentException("Missing typed package for board component: " + id);
        if (displayName == null || displayName.length() == 0)
            throw new IllegalArgumentException("Missing physical component marking");
        if (referenceLabelCharacters < displayName.length())
            throw new IllegalArgumentException("Reference reservation is smaller than its marking");
        this.physicalPackage = physicalPackage;
        this.displayName = displayName;
        this.referenceLabelCharacters = referenceLabelCharacters;
    }

    String getId() {
        return id;
    }

    String getType() {
        return type;
    }

    /** Explicit physical marking; identity remains the full authoritative ID. */
    String getDisplayName() { return displayName; }

    /** Existing physical legend reservation, independent of the current printed reference. */
    int getReferenceLabelCharacters() { return referenceLabelCharacters; }

    PhysicalPackage getPhysicalPackage() {
        return physicalPackage;
    }

    Vector<String> getPadIds() {
        return new Vector<String>(padIds);
    }

    void addPadId(String padId) {
        if (padIds.contains(padId))
            throw new IllegalArgumentException("Duplicate component pad ID: " + padId);
        padIds.add(padId);
    }
}
