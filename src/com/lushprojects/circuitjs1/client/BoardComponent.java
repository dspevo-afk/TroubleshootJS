package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class BoardComponent {
    private final String id;
    private final String type;
    private final PhysicalPackage physicalPackage;
    private final Vector<String> padIds = new Vector<String>();

    BoardComponent(String id, String type) {
        this(id, type, PhysicalPackages.forLegacyComponentType(type));
    }

    BoardComponent(String id, String type, PhysicalPackage physicalPackage) {
        this.id = id;
        this.type = type;
        if (physicalPackage == null)
            throw new IllegalArgumentException("Missing typed package for board component: " + id);
        this.physicalPackage = physicalPackage;
    }

    String getId() {
        return id;
    }

    String getType() {
        return type;
    }

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
