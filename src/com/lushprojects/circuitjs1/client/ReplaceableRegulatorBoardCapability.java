package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Runtime capability exposing the original/catalog four-terminal U1 regulator. */
final class ReplaceableRegulatorBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_REGULATOR";

    private final String capabilityId;
    private final RegulatorComponentSlot slot;
    private final PhysicalPartInventory<PhysicalRegulatorPart> inventory;
    private final RegulatorReplacementCatalog catalog;
    private RegulatorSlotController controller;

    ReplaceableRegulatorBoardCapability(RegulatorComponentSlot slot,
            PhysicalPartInventory<PhysicalRegulatorPart> inventory,
            RegulatorReplacementCatalog catalog) {
        this(ID, slot, inventory, catalog);
    }

    ReplaceableRegulatorBoardCapability(String capabilityId, RegulatorComponentSlot slot,
            PhysicalPartInventory<PhysicalRegulatorPart> inventory,
            RegulatorReplacementCatalog catalog) {
        if (capabilityId == null || capabilityId.length() == 0 || slot == null ||
                inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing replaceable regulator capability");
        if (catalog.getContract() != slot.getIntendedContract())
            throw new IllegalArgumentException("Regulator catalog does not retain selected contract");
        this.capabilityId = capabilityId;
        this.slot = slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    public String getCapabilityId() { return capabilityId; }
    RegulatorComponentSlot getSlot() { return slot; }
    PhysicalPartInventory<PhysicalRegulatorPart> getInventory() { return inventory; }
    RegulatorReplacementCatalog getCatalog() { return catalog; }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public PhysicalPartInventory<?> getMutationInventory() { return inventory; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        controller = new RegulatorSlotController(sim, instance, modifications, this);
        return controller;
    }

    RegulatorSlotController getController() { return controller; }
    public String getComponentId() { return slot.getComponentId(); }
    public String getCatalogTitle() { return "Regulator Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new regulator"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        for (RegulatorCatalogEntry entry : catalog.getEntries())
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
        if (!(part instanceof PhysicalRegulatorPart) || !ownsPart(part.getId()))
            throw new IllegalArgumentException("Physical part is not owned by regulator provider");
        PhysicalRegulatorPart regulator = (PhysicalRegulatorPart) part;
        return getComponentId() + " - " + (regulator.isOriginal() ? "Removed regulator" :
            regulator.getPlayerVisibleNameplate().getDisplayName());
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }

    static ReplaceableRegulatorBoardCapability find(GeneratedBoardInstance instance) {
        return instance == null ? null : find(instance.getPhysicalBoardRuntime());
    }

    static ReplaceableRegulatorBoardCapability find(PhysicalBoardRuntime runtime) {
        if (runtime == null) return null;
        PhysicalBoardRuntimeCapability capability = runtime.getCapability(ID);
        return capability instanceof ReplaceableRegulatorBoardCapability ?
            (ReplaceableRegulatorBoardCapability) capability : null;
    }

    static ReplaceableRegulatorBoardCapability find(PhysicalBoardRuntime runtime,
            String componentId) {
        if (runtime == null || componentId == null) return null;
        for (PhysicalBoardRuntimeCapability value : runtime.getCapabilities())
            if (value instanceof ReplaceableRegulatorBoardCapability &&
                    componentId.equals(((ReplaceableRegulatorBoardCapability) value)
                        .getComponentId()))
                return (ReplaceableRegulatorBoardCapability) value;
        return null;
    }

    static ReplaceableRegulatorBoardCapability require(GeneratedBoardInstance instance) {
        ReplaceableRegulatorBoardCapability capability = find(instance);
        if (capability == null)
            throw new IllegalStateException("Generated board has no replaceable regulator capability");
        return capability;
    }
}
