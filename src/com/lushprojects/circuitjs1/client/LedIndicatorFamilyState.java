package com.lushprojects.circuitjs1.client;

class LedIndicatorFamilyState implements GeneratedBoardFamilyState {
    private final ReplaceableComponentSlot r1Slot;
    private final ResistorReplacementInventory resistorInventory;
    private final ResistorReplacementCatalog resistorCatalog;
    private int nextCatalogPartSerial;

    LedIndicatorFamilyState(ReplaceableComponentSlot r1Slot,
            ResistorReplacementInventory resistorInventory,
            ResistorReplacementCatalog resistorCatalog) {
        if (r1Slot == null || resistorInventory == null || resistorCatalog == null)
            throw new IllegalArgumentException("Missing LED indicator family replacement state");
        this.r1Slot = r1Slot;
        this.resistorInventory = resistorInventory;
        this.resistorCatalog = resistorCatalog;
    }

    static LedIndicatorFamilyState require(GeneratedBoardInstance instance) {
        if (instance == null || !(instance.getFamilyState() instanceof LedIndicatorFamilyState))
            throw new IllegalStateException("LED resistor replacement requires LED indicator family state");
        return (LedIndicatorFamilyState) instance.getFamilyState();
    }

    ReplaceableComponentSlot getR1Slot() { return r1Slot; }
    ResistorReplacementInventory getResistorInventory() { return resistorInventory; }
    ResistorReplacementCatalog getResistorCatalog() { return resistorCatalog; }
    String allocateCatalogPartId() { return "R1_CATALOG_PART_" + nextCatalogPartSerial++; }
}
