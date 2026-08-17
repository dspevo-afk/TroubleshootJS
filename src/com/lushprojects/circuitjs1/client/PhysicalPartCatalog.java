package com.lushprojects.circuitjs1.client;

import java.util.Vector;

interface PhysicalPartCatalog<E extends PhysicalCatalogEntry<?>> {
    E get(String entryId);
    Vector<E> getEntries();
}
