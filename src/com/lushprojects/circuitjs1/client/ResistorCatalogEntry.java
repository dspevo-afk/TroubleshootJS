package com.lushprojects.circuitjs1.client;

class ResistorCatalogEntry {
    private final String id;
    private final ResistorNameplate nameplate;

    ResistorCatalogEntry(String id, double resistanceOhms) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Invalid resistor catalog entry");
        this.id = id;
        this.nameplate = new ResistorNameplate(id, resistanceOhms, 5);
    }

    String getId() { return id; }
    ResistorNameplate getNameplate() { return nameplate; }
}