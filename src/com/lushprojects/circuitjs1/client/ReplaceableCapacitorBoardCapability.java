package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Typed runtime adapter for the RC family’s fault-owning capacitor location. */
final class ReplaceableCapacitorBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_CAPACITOR";

    private final CapacitorComponentSlot slot;
    private final PhysicalPartInventory<PhysicalCapacitorPart> inventory;
    private final CapacitorReplacementCatalog catalog;
    private CapacitorSlotController controller;

    ReplaceableCapacitorBoardCapability(CapacitorComponentSlot slot,
            PhysicalPartInventory<PhysicalCapacitorPart> inventory,
            CapacitorReplacementCatalog catalog) {
        if (slot == null || inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing replaceable capacitor runtime capability");
        this.slot = slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    public String getCapabilityId() { return ID; }
    CapacitorComponentSlot getSlot() { return slot; }
    PhysicalPartInventory<PhysicalCapacitorPart> getInventory() { return inventory; }
    CapacitorReplacementCatalog getCatalog() { return catalog; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        controller = new CapacitorSlotController(sim, instance, modifications, this);
        return controller;
    }

    CapacitorSlotController getController() { return controller; }

    public String getComponentId() { return slot.getComponentId(); }
    public String getCatalogTitle() { return "Capacitor Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new capacitor"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        for (CapacitorCatalogEntry entry : catalog.getEntries())
            result.add(new WorkbenchCatalogEntry(entry.getId(),
                entry.getNameplate().getMarking()));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts());
        return result;
    }

    public String getPartLabel(PhysicalPart<?> part) {
        if (!(part instanceof PhysicalCapacitorPart) || !ownsPart(part.getId()))
            throw new IllegalArgumentException("Physical part is not owned by capacitor provider");
        PhysicalCapacitorPart capacitor = (PhysicalCapacitorPart) part;
        return capacitor.getId() + " - " + capacitor.getNameplate().getDisplayName();
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }

    static ReplaceableCapacitorBoardCapability find(GeneratedBoardInstance instance) {
        if (instance == null)
            return null;
        PhysicalBoardRuntimeCapability capability = instance.getPhysicalBoardRuntime()
            .getCapability(ID);
        return capability instanceof ReplaceableCapacitorBoardCapability ?
            (ReplaceableCapacitorBoardCapability) capability : null;
    }

    static ReplaceableCapacitorBoardCapability require(GeneratedBoardInstance instance) {
        ReplaceableCapacitorBoardCapability capability = find(instance);
        if (capability == null)
            throw new IllegalStateException("Generated board has no replaceable capacitor capability");
        return capability;
    }
}
