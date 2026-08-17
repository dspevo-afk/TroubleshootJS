package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Type-neutral catalog and loose-part view contributed by a runtime capability. */
interface WorkbenchPartsProvider {
    String getComponentId();
    String getCatalogTitle();
    String getInstallNewLabel();
    boolean showOccupiedMessageWhenPowered();
    Vector<WorkbenchCatalogEntry> getCatalogEntries();
    Vector<PhysicalPart<?>> getLooseParts();
    String getPartLabel(PhysicalPart<?> part);
    PhysicalPart<?> getPart(String partId);
    boolean ownsPart(String partId);
}
