package com.lushprojects.circuitjs1.client;

/** Catalog row for an ordinary, normally oriented electrolytic replacement. */
final class CapacitorCatalogEntry extends AbstractPhysicalCatalogEntry<CapacitorSpecification> {
    CapacitorCatalogEntry(String id, CapacitorSpecification specification) {
        super(id, specification, specification.getNameplate().forPhysicalPartId(id),
            specification.isPolarized() ? PhysicalPartOrientation.NORMAL : PhysicalPartOrientation.NON_POLARIZED);
    }

    CapacitorNameplate getNameplate() { return getSpecification().getNameplate(); }
}
