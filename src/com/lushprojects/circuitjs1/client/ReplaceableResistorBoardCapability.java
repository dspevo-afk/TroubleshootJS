package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Typed runtime adapter for a board's replaceable resistor position. */
final class ReplaceableResistorBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider, PhysicalBoardRuntimeLifecycle, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_RESISTOR";

    private final ReplaceableComponentSlot slot;
    private final PhysicalPartInventory<PhysicalResistorPart> inventory;
    private final ResistorReplacementCatalog catalog;
    private ResistorSlotController controller;
    private ResistorStressDamageSystem stressDamageSystem;

    ReplaceableResistorBoardCapability(ReplaceableComponentSlot slot,
            PhysicalPartInventory<PhysicalResistorPart> inventory,
            ResistorReplacementCatalog catalog) {
        if (slot == null || inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing replaceable resistor runtime capability");
        this.slot = slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    public String getCapabilityId() { return ID; }
    ReplaceableComponentSlot getSlot() { return slot; }
    PhysicalPartInventory<PhysicalResistorPart> getInventory() { return inventory; }
    ResistorReplacementCatalog getCatalog() { return catalog; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        stressDamageSystem = new ResistorStressDamageSystem(sim, this, initialSimulationTime);
        controller = new ResistorSlotController(sim, instance, modifications, this);
        return controller;
    }

    ResistorSlotController getController() { return controller; }

    ResistorStressDamageSystem getStressDamageSystem() {
        if (stressDamageSystem == null)
            throw new IllegalStateException("Replaceable resistor capability is not installed");
        return stressDamageSystem;
    }

    public void observeSimulationTime(double simulationTime) {
        if (stressDamageSystem != null)
            stressDamageSystem.observeSimulationTime(simulationTime);
    }

    public void resetForBoardReset() {
        if (stressDamageSystem != null)
            stressDamageSystem.resetForBoardReset();
    }

    public void synchronizeSimulationTime(double simulationTime) {
        if (stressDamageSystem != null)
            stressDamageSystem.synchronizeSimulationTime(simulationTime);
    }

    public String getComponentId() { return slot.getComponentId(); }
    public String getCatalogTitle() { return "Resistor Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new resistor"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        for (ResistorCatalogEntry entry : catalog.getEntries())
            result.add(new WorkbenchCatalogEntry(entry.getId(),
                entry.getNameplate().getDisplayValue()));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts());
        return result;
    }

    public String getPartLabel(PhysicalPart<?> part) {
        if (!(part instanceof PhysicalResistorPart) || !ownsPart(part.getId()))
            throw new IllegalArgumentException("Physical part is not owned by resistor provider");
        PhysicalResistorPart resistor = (PhysicalResistorPart) part;
        return resistor.getId() + " - " + (resistor.isOriginal() ? "Removed resistor" :
            resistor.getNameplate().getDisplayValue());
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }

    static ReplaceableResistorBoardCapability find(GeneratedBoardInstance instance) {
        return instance == null ? null : find(instance.getPhysicalBoardRuntime());
    }

    static ReplaceableResistorBoardCapability find(PhysicalBoardRuntime runtime) {
        if (runtime == null)
            return null;
        PhysicalBoardRuntimeCapability capability = runtime.getCapability(ID);
        return capability instanceof ReplaceableResistorBoardCapability ?
            (ReplaceableResistorBoardCapability) capability : null;
    }

    static ReplaceableResistorBoardCapability require(GeneratedBoardInstance instance) {
        ReplaceableResistorBoardCapability capability = find(instance);
        if (capability == null)
            throw new IllegalStateException("Generated board has no replaceable resistor capability");
        return capability;
    }
}
