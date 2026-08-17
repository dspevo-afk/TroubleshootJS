package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Typed runtime adapter for a board's replaceable diode position. */
final class ReplaceableDiodeBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_DIODE";

    private final DiodeComponentSlot slot;
    private final PhysicalPartInventory<PhysicalDiodePart> inventory;
    private final DiodeReplacementCatalog catalog;
    private DiodeSlotController controller;

    ReplaceableDiodeBoardCapability(DiodeComponentSlot slot,
            PhysicalPartInventory<PhysicalDiodePart> inventory,
            DiodeReplacementCatalog catalog) {
        if (slot == null || inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing replaceable diode runtime capability");
        this.slot = slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    public String getCapabilityId() { return ID; }
    DiodeComponentSlot getSlot() { return slot; }
    PhysicalPartInventory<PhysicalDiodePart> getInventory() { return inventory; }
    DiodeReplacementCatalog getCatalog() { return catalog; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        controller = new DiodeSlotController(sim, instance, modifications, this);
        return controller;
    }

    DiodeSlotController getController() { return controller; }

    public String getComponentId() { return slot.getComponentId(); }
    public String getCatalogTitle() { return "Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new diode"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        for (DiodeCatalogEntry entry : catalog.getEntries())
            result.add(new WorkbenchCatalogEntry(entry.getId(),
                entry.getNameplate().getDisplayName()));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts());
        return result;
    }

    public String getPartLabel(PhysicalPart<?> part) {
        if (!(part instanceof PhysicalDiodePart) || !ownsPart(part.getId()))
            throw new IllegalArgumentException("Physical part is not owned by diode provider");
        PhysicalDiodePart diode = (PhysicalDiodePart) part;
        return diode.getId() + " - " + diode.getNameplate().getDisplayName();
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }

    static ReplaceableDiodeBoardCapability find(GeneratedBoardInstance instance) {
        if (instance == null)
            return null;
        PhysicalBoardRuntimeCapability capability = instance.getPhysicalBoardRuntime()
            .getCapability(ID);
        return capability instanceof ReplaceableDiodeBoardCapability ?
            (ReplaceableDiodeBoardCapability) capability : null;
    }

    static ReplaceableDiodeBoardCapability require(GeneratedBoardInstance instance) {
        ReplaceableDiodeBoardCapability capability = find(instance);
        if (capability == null)
            throw new IllegalStateException("Generated board has no replaceable diode capability");
        return capability;
    }
}
