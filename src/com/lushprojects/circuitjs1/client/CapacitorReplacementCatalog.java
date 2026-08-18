package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Deliberately small, typed RC replacement catalog with valid and wrong timing choices. */
final class CapacitorReplacementCatalog implements PhysicalPartCatalog<CapacitorCatalogEntry> {
    static final String CORRECT = "C_CATALOG_33UF_16V";
    static final String WRONG_LOW = "C_CATALOG_1UF_16V";
    static final String WRONG_HIGH = "C_CATALOG_220UF_16V";

    private final Vector<CapacitorCatalogEntry> entries =
        new Vector<CapacitorCatalogEntry>();
    private final HashMap<String, CapacitorCatalogEntry> byId =
        new HashMap<String, CapacitorCatalogEntry>();

    CapacitorReplacementCatalog() {
        add(CORRECT, 33e-6, "33 uF 16 V");
        add(WRONG_LOW, 1e-6, "1 uF 16 V");
        // This is intentionally much larger than the timing value so the
        // solver-backed functional check remains visibly low at the late
        // RC sample, rather than merely a little slow.
        add(WRONG_HIGH, 220e-6, "220 uF 16 V");
    }

    private void add(String id, double capacitanceFarads, String marking) {
        CapacitorSpecification specification = new CapacitorSpecification(id, capacitanceFarads,
            20, 16, PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new CapacitorNameplate("Electrolytic capacitor", marking));
        CapacitorCatalogEntry entry = new CapacitorCatalogEntry(id, specification);
        entries.add(entry);
        byId.put(id, entry);
    }

    public Vector<CapacitorCatalogEntry> getEntries() {
        return new Vector<CapacitorCatalogEntry>(entries);
    }

    public CapacitorCatalogEntry get(String id) {
        CapacitorCatalogEntry entry = byId.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown capacitor catalog entry: " + id);
        return entry;
    }
}
