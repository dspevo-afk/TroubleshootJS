package com.lushprojects.circuitjs1.client;

/** Catalog row for a real, normally oriented N-channel MOSFET backing. */
final class NmosCatalogEntry extends AbstractPhysicalCatalogEntry<NmosSpecification> {
    NmosCatalogEntry(String id, NmosSpecification specification, String displayName) {
        super(id, specification, new PhysicalNameplate(id, displayName, "Part", displayName),
            PhysicalPartOrientation.NON_POLARIZED);
    }
}
