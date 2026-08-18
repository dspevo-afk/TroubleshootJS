package com.lushprojects.circuitjs1.client;

/** Catalog row for a normally oriented, real NPN transistor replacement. */
final class NpnCatalogEntry extends AbstractPhysicalCatalogEntry<NpnSpecification> {
    NpnCatalogEntry(String id, NpnSpecification specification, String displayName) {
        super(id, specification, new PhysicalNameplate(id, displayName, "Part", displayName),
            PhysicalPartOrientation.NON_POLARIZED);
    }
}
