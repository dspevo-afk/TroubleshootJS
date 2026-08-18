package com.lushprojects.circuitjs1.client;

/** Catalog row for an ordinary, normally oriented electrolytic replacement. */
final class CapacitorCatalogEntry extends AbstractPhysicalCatalogEntry<CapacitorSpecification> {
    CapacitorCatalogEntry(String id, CapacitorSpecification specification) {
        super(id, specification, specification.getNameplate().forPhysicalPartId(id),
            PhysicalPartOrientation.NORMAL);
        if (!specification.isPolarized())
            throw new IllegalArgumentException("Capacitor replacement catalog requires polarity");
    }

    CapacitorNameplate getNameplate() { return getSpecification().getNameplate(); }
}
