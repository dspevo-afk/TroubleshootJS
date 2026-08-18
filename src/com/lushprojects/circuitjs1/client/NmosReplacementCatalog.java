package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Small deterministic NMOS catalog with one correct and one intentionally wrong part. */
final class NmosReplacementCatalog implements PhysicalPartCatalog<NmosCatalogEntry> {
    static final String CORRECT = "NMOS_CATALOG_STANDARD";
    static final String WRONG_HIGH_THRESHOLD = "NMOS_CATALOG_HIGH_THRESHOLD";
    private final HashMap<String, NmosCatalogEntry> entries =
        new HashMap<String, NmosCatalogEntry>();
    private final Vector<String> order = new Vector<String>();

    NmosReplacementCatalog() {
        add(new NmosCatalogEntry(CORRECT, new NmosSpecification("NMOS_2N7000", 1.5, 10),
            "Generic N-channel MOSFET"));
        add(new NmosCatalogEntry(WRONG_HIGH_THRESHOLD,
            new NmosSpecification("NMOS_HIGH_THRESHOLD", 8, 10),
            "High-threshold N-channel MOSFET"));
    }

    public NmosCatalogEntry get(String id) {
        NmosCatalogEntry entry = entries.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown NMOS catalog entry: " + id);
        return entry;
    }

    public Vector<NmosCatalogEntry> getEntries() {
        Vector<NmosCatalogEntry> result = new Vector<NmosCatalogEntry>();
        for (String id : order) result.add(entries.get(id));
        return result;
    }

    private void add(NmosCatalogEntry entry) {
        if (entries.put(entry.getId(), entry) != null)
            throw new IllegalArgumentException("Duplicate NMOS catalog entry: " + entry.getId());
        order.add(entry.getId());
    }
}
