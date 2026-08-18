package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Small deterministic catalog used by the NPN workbench slot. */
final class NpnReplacementCatalog implements PhysicalPartCatalog<NpnCatalogEntry> {
    static final String CORRECT = "NPN_CATALOG_STANDARD";
    static final String WRONG_LOW_BETA = "NPN_CATALOG_LOW_BETA";
    private final HashMap<String, NpnCatalogEntry> entries =
        new HashMap<String, NpnCatalogEntry>();
    private final Vector<String> order = new Vector<String>();

    NpnReplacementCatalog() {
        add(new NpnCatalogEntry(CORRECT, new NpnSpecification("NPN_2N3904", 100),
            "Generic NPN transistor"));
        add(new NpnCatalogEntry(WRONG_LOW_BETA, new NpnSpecification("NPN_LOW_BETA", .1),
            "Low-gain NPN transistor"));
    }

    public NpnCatalogEntry get(String id) {
        NpnCatalogEntry entry = entries.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown NPN catalog entry: " + id);
        return entry;
    }

    public Vector<NpnCatalogEntry> getEntries() {
        Vector<NpnCatalogEntry> result = new Vector<NpnCatalogEntry>();
        for (String id : order) result.add(entries.get(id));
        return result;
    }

    private void add(NpnCatalogEntry entry) {
        if (entries.put(entry.getId(), entry) != null)
            throw new IllegalArgumentException("Duplicate NPN catalog entry: " + entry.getId());
        order.add(entry.getId());
    }
}
