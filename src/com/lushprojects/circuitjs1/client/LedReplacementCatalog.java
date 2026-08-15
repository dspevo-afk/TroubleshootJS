package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class LedReplacementCatalog {
    static final String CORRECT = "GENERIC_RED_LED_FORWARD";
    static final String REVERSED = "GENERIC_RED_LED_REVERSED";
    private final Vector<LedCatalogEntry> entries = new Vector<LedCatalogEntry>();
    private final HashMap<String, LedCatalogEntry> byId = new HashMap<String, LedCatalogEntry>();

    LedReplacementCatalog() {
        LedNameplate nameplate = new LedNameplate("LED1", "Generic red LED", "default-led",
            1, 0, 0);
        add(new LedCatalogEntry(CORRECT, "Generic red LED", nameplate, false));
        add(new LedCatalogEntry(REVERSED, "Generic red LED (reversed)", nameplate, true));
    }

    private void add(LedCatalogEntry entry) {
        entries.add(entry);
        byId.put(entry.getId(), entry);
    }

    Vector<LedCatalogEntry> getEntries() { return new Vector<LedCatalogEntry>(entries); }
    LedCatalogEntry get(String id) {
        LedCatalogEntry entry = byId.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown LED catalog entry: " + id);
        return entry;
    }
}
