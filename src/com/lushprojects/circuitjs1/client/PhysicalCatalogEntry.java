package com.lushprojects.circuitjs1.client;

/** Immutable catalog row shared by replaceable physical-part families. */
interface PhysicalCatalogEntry<S extends PhysicalSpecification> {
    String getId();
    S getSpecification();
    PhysicalNameplate getPlayerVisibleNameplate();
    PhysicalPartOrientation getOrientation();
}
