package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class DiodeReplacementCatalog implements PhysicalPartCatalog<DiodeCatalogEntry> {
    static final String CORRECT = "GENERIC_SILICON_FORWARD";
    static final String REVERSED = "GENERIC_SILICON_REVERSED";
    private final Vector<DiodeCatalogEntry> entries = new Vector<DiodeCatalogEntry>();
    private final HashMap<String, DiodeCatalogEntry> byId = new HashMap<String, DiodeCatalogEntry>();

    DiodeReplacementCatalog() {
        add(new DiodeCatalogEntry(CORRECT,
            new DiodeNameplate(CORRECT, "Generic silicon diode", "default"), false));
        add(new DiodeCatalogEntry(REVERSED,
            new DiodeNameplate(REVERSED, "Generic silicon diode (reversed)", "default"), true));
    }

    private void add(DiodeCatalogEntry entry) {
        entries.add(entry);
        byId.put(entry.getId(), entry);
    }

    public Vector<DiodeCatalogEntry> getEntries() { return new Vector<DiodeCatalogEntry>(entries); }
    public DiodeCatalogEntry get(String id) {
        DiodeCatalogEntry entry = byId.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown diode catalog entry: " + id);
        return entry;
    }
}
