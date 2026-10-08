package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Catalog and service ownership for declared finite service packages. */
final class ReplaceableServiceBoardCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    private final ServiceComponentSlot slot;
    private final PhysicalPartInventory<PhysicalServicePart> inventory;
    private final PhysicalServicePart original;

    ReplaceableServiceBoardCapability(ServiceComponentSlot slot,
            PhysicalPartInventory<PhysicalServicePart> inventory, PhysicalServicePart original) {
        this.slot = slot; this.inventory = inventory; this.original = original;
    }
    public String getCapabilityId() { return "SERVICE_" + getComponentId(); }
    public String getComponentId() { return slot.getComponentId(); }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public PhysicalPartInventory<?> getMutationInventory() { return inventory; }
    PhysicalPartInventory<PhysicalServicePart> inventory() { return inventory; }
    PhysicalServicePart original() { return original; }
    String catalogId() {
        return (original.hasTypedPowerRecipe() || original.getSpecification() instanceof FuseSpecification ?
            original.getSpecification().getSpecificationId() :
            original.getPackage().getId()) + "_STANDARD";
    }
    String catalogLabel() {
        if (original.getSpecification() instanceof InductorSpecification)
            return ((InductorSpecification)original.getSpecification()).catalogLabel();
        if (original.getSpecification() instanceof ZenerSpecification)
            return ((ZenerSpecification)original.getSpecification()).catalogLabel();
        if (original.getSpecification() instanceof OptocouplerSpecification)
            return ((OptocouplerSpecification)original.getSpecification()).catalogLabel();
        if (original.getSpecification() instanceof FuseSpecification)
            return ((FuseSpecification)original.getSpecification()).catalogLabel();
        if (original.isFactoryLink()) return "Raised insulated factory link (50 mOhm)";
        if (!original.isConnector()) return "250 mA fuse";
        return original.getPackage().isEquivalentTo(PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2) ?
            "2-pin output header" : "2-pin terminal connector";
    }
    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        return new ServiceSlotController(sim, instance, modifications, this);
    }
    public String getCatalogTitle() { return "Replacement Catalog"; }
    public String getInstallNewLabel() {
        String kind = original.getSpecification() instanceof InductorSpecification ? "inductor" :
            original.getSpecification() instanceof ZenerSpecification ? "zener diode" :
            original.getSpecification() instanceof OptocouplerSpecification ? "optocoupler" :
            original.isFactoryLink() ? "factory link" : original.isConnector() ? "connector" : "fuse";
        return "Install new " + kind;
    }
    public boolean showOccupiedMessageWhenPowered() { return false; }
    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        result.add(new WorkbenchCatalogEntry(catalogId(), catalogLabel())); return result;
    }
    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts()); return result;
    }
    public String getPartLabel(PhysicalPart<?> part) {
        if (part == null || inventory.get(part.getId()) != part)
            throw new IllegalArgumentException("Foreign service inventory part");
        return part.getPlayerVisibleNameplate().getDisplayName();
    }
    public PhysicalPart<?> getPart(String id) { return inventory.get(id); }
    public boolean ownsPart(String id) { return inventory.contains(id); }
}
