package com.lushprojects.circuitjs1.client;

/** Runtime-owned slot provider that also exposes its workbench operations. */
interface PhysicalSlotMutationProvider extends WorkbenchCapabilityStrategy {
    String getComponentId();
    boolean ownsPart(String partId);
    boolean removeInstalledPart();
    boolean install(String partId);
    boolean installNewFromCatalog(String catalogEntryId);
}
