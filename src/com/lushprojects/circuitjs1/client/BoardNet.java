package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class BoardNet {
    private final String id;
    private final Vector<String> padIds = new Vector<String>();

    BoardNet(String id) {
        this.id = id;
    }

    String getId() {
        return id;
    }

    Vector<String> getPadIds() {
        return new Vector<String>(padIds);
    }

    void addPadId(String padId) {
        if (padIds.contains(padId))
            throw new IllegalArgumentException("Duplicate net pad ID: " + padId);
        padIds.add(padId);
    }
}
