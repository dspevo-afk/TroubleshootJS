package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Runtime capability exposing Q1 identity, catalog, and loose-part lifecycle. */
final class ReplaceableNpnBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_NPN";
    private final NpnComponentSlot slot;
    private final PhysicalPartInventory<PhysicalNpnPart> inventory;
    private final NpnReplacementCatalog catalog;
    private NpnSlotController controller;

    ReplaceableNpnBoardCapability(NpnComponentSlot slot,
            PhysicalPartInventory<PhysicalNpnPart> inventory, NpnReplacementCatalog catalog) {
        if (slot == null || inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing replaceable NPN capability");
        this.slot = slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    public String getCapabilityId() { return ID; }
    NpnComponentSlot getSlot() { return slot; }
    PhysicalPartInventory<PhysicalNpnPart> getInventory() { return inventory; }
    NpnReplacementCatalog getCatalog() { return catalog; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        controller = new NpnSlotController(sim, instance, modifications, this);
        return controller;
    }

    NpnSlotController getController() { return controller; }
    public String getComponentId() { return "Q1"; }
    public String getCatalogTitle() { return "NPN Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new NPN"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        for (NpnCatalogEntry entry : catalog.getEntries())
            result.add(new WorkbenchCatalogEntry(entry.getId(),
                entry.getPlayerVisibleNameplate().getWorkbenchDetailValue()));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts());
        return result;
    }

    public String getPartLabel(PhysicalPart<?> part) {
        if (!(part instanceof PhysicalNpnPart) || !ownsPart(part.getId()))
            throw new IllegalArgumentException("Physical part is not owned by NPN provider");
        return part.getId() + " - " + part.getPlayerVisibleNameplate().getDisplayName();
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }

    static ReplaceableNpnBoardCapability find(GeneratedBoardInstance instance) {
        if (instance == null) return null;
        PhysicalBoardRuntimeCapability capability = instance.getPhysicalBoardRuntime()
            .getCapability(ID);
        return capability instanceof ReplaceableNpnBoardCapability ?
            (ReplaceableNpnBoardCapability) capability : null;
    }

    static ReplaceableNpnBoardCapability require(GeneratedBoardInstance instance) {
        ReplaceableNpnBoardCapability capability = find(instance);
        if (capability == null)
            throw new IllegalStateException("Generated board has no replaceable NPN capability");
        return capability;
    }
}
