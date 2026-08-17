package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Typed runtime adapter for a board's replaceable LED position. */
final class ReplaceableLedBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_LED";

    private final LedComponentSlot slot;
    private final PhysicalPartInventory<PhysicalLedPart> inventory;
    private final LedReplacementCatalog catalog;
    private LedSlotController controller;

    ReplaceableLedBoardCapability(LedComponentSlot slot,
            PhysicalPartInventory<PhysicalLedPart> inventory,
            LedReplacementCatalog catalog) {
        if (slot == null || inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing replaceable LED runtime capability");
        this.slot = slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    public String getCapabilityId() { return ID; }
    LedComponentSlot getSlot() { return slot; }
    PhysicalPartInventory<PhysicalLedPart> getInventory() { return inventory; }
    LedReplacementCatalog getCatalog() { return catalog; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        controller = new LedSlotController(sim, instance, modifications, this);
        return controller;
    }

    LedSlotController getController() { return controller; }

    public String getComponentId() { return slot.getComponentId(); }
    public String getCatalogTitle() { return "LED Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new LED"; }
    public boolean showOccupiedMessageWhenPowered() { return true; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        for (LedCatalogEntry entry : catalog.getEntries())
            result.add(new WorkbenchCatalogEntry(entry.getId(), entry.getDisplayName()));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts());
        return result;
    }

    public String getPartLabel(PhysicalPart<?> part) {
        if (!(part instanceof PhysicalLedPart) || !ownsPart(part.getId()))
            throw new IllegalArgumentException("Physical part is not owned by LED provider");
        PhysicalLedPart led = (PhysicalLedPart) part;
        return led.getId() + " - " + led.getNameplate().getDisplayName();
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }

    static ReplaceableLedBoardCapability find(GeneratedBoardInstance instance) {
        if (instance == null)
            return null;
        PhysicalBoardRuntimeCapability capability = instance.getPhysicalBoardRuntime()
            .getCapability(ID);
        return capability instanceof ReplaceableLedBoardCapability ?
            (ReplaceableLedBoardCapability) capability : null;
    }

    static ReplaceableLedBoardCapability require(GeneratedBoardInstance instance) {
        ReplaceableLedBoardCapability capability = find(instance);
        if (capability == null)
            throw new IllegalStateException("Generated board has no replaceable LED capability");
        return capability;
    }
}
