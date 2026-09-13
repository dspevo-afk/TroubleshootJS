package com.lushprojects.circuitjs1.client;

/** Runtime-owned acquisition of a real loose part; mounting is a separate operation. */
interface CatalogAcquisitionProvider {
    PhysicalPart<?> acquireFromCatalog(String catalogEntryId);
}
