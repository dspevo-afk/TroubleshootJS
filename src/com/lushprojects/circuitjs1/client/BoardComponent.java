package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class BoardComponent {
    private final String id;
    private final String type;
    private final Vector<String> padIds = new Vector<String>();

    BoardComponent(String id, String type) {
        this.id = id;
        this.type = type;
    }

    String getId() {
        return id;
    }

    String getType() {
        return type;
    }

    Vector<String> getPadIds() {
        return padIds;
    }

    void addPadId(String padId) {
        if (padIds.contains(padId))
            throw new IllegalArgumentException("Duplicate component pad ID: " + padId);
        padIds.add(padId);
    }
}
