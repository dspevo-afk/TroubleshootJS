package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorFamilyState implements GeneratedBoardFamilyState,
        ReplaceableResistorFamilyState {
    private final ReplaceableComponentSlot r1Slot;
    private final ResistorReplacementInventory resistorInventory;
    private final ResistorReplacementCatalog resistorCatalog;
    private int nextCatalogPartSerial;

    ParallelDualIndicatorFamilyState(ReplaceableComponentSlot r1Slot,
            ResistorReplacementInventory resistorInventory,
            ResistorReplacementCatalog resistorCatalog) {
        if (r1Slot == null || resistorInventory == null || resistorCatalog == null)
            throw new IllegalArgumentException("Missing parallel indicator family state");
        this.r1Slot = r1Slot;
        this.resistorInventory = resistorInventory;
        this.resistorCatalog = resistorCatalog;
    }

    static ParallelDualIndicatorFamilyState require(GeneratedBoardInstance instance) {
        if (instance == null || !(instance.getFamilyState() instanceof
                ParallelDualIndicatorFamilyState))
            throw new IllegalStateException("Parallel indicator family state is required");
        return (ParallelDualIndicatorFamilyState) instance.getFamilyState();
    }

    ReplaceableComponentSlot getR1Slot() { return r1Slot; }
    public ReplaceableComponentSlot getReplaceableResistorSlot() { return r1Slot; }
    public ResistorReplacementInventory getResistorInventory() { return resistorInventory; }
    public ResistorReplacementCatalog getResistorCatalog() { return resistorCatalog; }
    public String allocateCatalogPartId() { return "R1_CATALOG_PART_" + nextCatalogPartSerial++; }

    public boolean isFaultedTargetInstalled(String componentId) {
        return "J1".equals(componentId) || (r1Slot.getComponentId().equals(componentId) &&
            !r1Slot.isEmpty() && r1Slot.getInstalledPart().isFaulted());
    }
}
