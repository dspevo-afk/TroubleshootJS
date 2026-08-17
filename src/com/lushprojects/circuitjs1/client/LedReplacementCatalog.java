package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class LedReplacementCatalog implements PhysicalPartCatalog<LedCatalogEntry> {
    static final String CORRECT = "GENERIC_RED_LED_FORWARD";
    static final String REVERSED = "GENERIC_RED_LED_REVERSED";
    private final Vector<LedCatalogEntry> entries = new Vector<LedCatalogEntry>();
    private final HashMap<String, LedCatalogEntry> byId = new HashMap<String, LedCatalogEntry>();

    LedReplacementCatalog() {
        add(new LedCatalogEntry(CORRECT, "Generic red LED",
            new LedNameplate(CORRECT, "Generic red LED", "default-led", 1, 0, 0), false));
        add(new LedCatalogEntry(REVERSED, "Generic red LED (reversed)",
            new LedNameplate(REVERSED, "Generic red LED", "default-led", 1, 0, 0), true));
    }

    private void add(LedCatalogEntry entry) {
        entries.add(entry);
        byId.put(entry.getId(), entry);
    }

    public Vector<LedCatalogEntry> getEntries() { return new Vector<LedCatalogEntry>(entries); }
    public LedCatalogEntry get(String id) {
        LedCatalogEntry entry = byId.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown LED catalog entry: " + id);
        return entry;
    }
}
