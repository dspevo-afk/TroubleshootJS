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
    private final PhysicalPackage physicalPackage;

    CapacitorReplacementCatalog() {
        this(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR);
    }

    CapacitorReplacementCatalog(PhysicalPackage physicalPackage) {
        this.physicalPackage = physicalPackage;
        if (physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR)) {
            add("C_CERAMIC_10NF_25V", 1e-8, "10 nF 25 V");
            add("C_CERAMIC_100NF_25V", 1e-7, "100 nF 25 V");
            add("C_CERAMIC_1UF_25V", 1e-6, "1 uF 25 V");
            return;
        }
        if (!physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR))
            throw new IllegalArgumentException("Unsupported capacitor service package");
        add(CORRECT, 33e-6, "33 uF 16 V");
        add(WRONG_LOW, 1e-6, "1 uF 16 V");
        // This is intentionally much larger than the timing value so the
        // solver-backed functional check remains visibly low at the late
        // RC sample, rather than merely a little slow.
        add(WRONG_HIGH, 220e-6, "220 uF 16 V");
    }

    private void add(String id, double capacitanceFarads, String marking) {
        CapacitorSpecification specification = new CapacitorSpecification(id, capacitanceFarads,
            20, physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR) ? 25 : 16, physicalPackage,
            new CapacitorNameplate(physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR) ?
                "Ceramic capacitor" : "Electrolytic capacitor", marking));
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
