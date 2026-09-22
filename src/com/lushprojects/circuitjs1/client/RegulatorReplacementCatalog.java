package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Bounded regulator stock: one replacement retaining the selected rail contract. */
final class RegulatorReplacementCatalog implements PhysicalPartCatalog<RegulatorCatalogEntry> {
    static final String CORRECT = "REGULATOR_CATALOG_SAME_CONTRACT";
    static final String STANDARD = CORRECT;
    static final String SAME_CONTRACT = CORRECT;

    private final HashMap<String, RegulatorCatalogEntry> entries =
        new HashMap<String, RegulatorCatalogEntry>();
    private final Vector<String> order = new Vector<String>();
    private final RailRegulationContract contract;

    RegulatorReplacementCatalog(RailRegulationContract contract) {
        if (contract == null)
            throw new IllegalArgumentException("Missing selected rail contract");
        this.contract = contract;
        add(new RegulatorCatalogEntry(CORRECT, contract));
    }

    /** Developer fixtures may use the canonical linear rail when no selection exists. */
    RegulatorReplacementCatalog() {
        this(RailRegulationContract.linear5V());
    }

    RailRegulationContract getContract() { return contract; }

    public RegulatorCatalogEntry get(String id) {
        RegulatorCatalogEntry entry = entries.get(id);
        if (entry == null)
            throw new IllegalArgumentException("Unknown regulator catalog entry: " + id);
        return entry;
    }

    public Vector<RegulatorCatalogEntry> getEntries() {
        Vector<RegulatorCatalogEntry> result = new Vector<RegulatorCatalogEntry>();
        for (String id : order) result.add(entries.get(id));
        return result;
    }

    private void add(RegulatorCatalogEntry entry) {
        if (entries.put(entry.getId(), entry) != null)
            throw new IllegalArgumentException("Duplicate regulator catalog entry: " + entry.getId());
        order.add(entry.getId());
    }
}

/** Catalog row carrying the exact immutable E02 contract used by the replacement. */
final class RegulatorCatalogEntry extends AbstractPhysicalCatalogEntry<PhysicalSpecification> {
    private final RailRegulationContract contract;

    RegulatorCatalogEntry(String id, RailRegulationContract contract) {
        super(id, new BasicPhysicalSpecification("REGULATOR_" + requireContract(contract).getVariantId()),
            new PhysicalNameplate(id, "Four-terminal rail regulator", "Contract",
                requireContract(contract).getVariantId()), PhysicalPartOrientation.NON_POLARIZED);
        this.contract = contract;
    }

    RailRegulationContract getContract() { return contract; }

    private static RailRegulationContract requireContract(RailRegulationContract contract) {
        if (contract == null)
            throw new IllegalArgumentException("Missing regulator catalog contract");
        return contract;
    }
}
