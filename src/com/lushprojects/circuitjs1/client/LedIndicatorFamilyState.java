package com.lushprojects.circuitjs1.client;

class LedIndicatorFamilyState implements GeneratedBoardFamilyState {
    private final ReplaceableComponentSlot r1Slot;
    private final ResistorReplacementInventory resistorInventory;
    private final ResistorReplacementCatalog resistorCatalog;
    private final LedComponentSlot led1Slot;
    private final LedReplacementInventory ledInventory;
    private final LedReplacementCatalog ledCatalog;
    private int nextCatalogPartSerial;
    private int nextLedPartSerial;

    LedIndicatorFamilyState(ReplaceableComponentSlot r1Slot,
            ResistorReplacementInventory resistorInventory,
            ResistorReplacementCatalog resistorCatalog, LedComponentSlot led1Slot,
            LedReplacementInventory ledInventory, LedReplacementCatalog ledCatalog) {
        if (r1Slot == null || resistorInventory == null || resistorCatalog == null ||
                led1Slot == null || ledInventory == null || ledCatalog == null)
            throw new IllegalArgumentException("Missing LED indicator family state");
        this.r1Slot = r1Slot;
        this.resistorInventory = resistorInventory;
        this.resistorCatalog = resistorCatalog;
        this.led1Slot = led1Slot;
        this.ledInventory = ledInventory;
        this.ledCatalog = ledCatalog;
    }

    static LedIndicatorFamilyState require(GeneratedBoardInstance instance) {
        if (instance == null || !(instance.getFamilyState() instanceof LedIndicatorFamilyState))
            throw new IllegalStateException("LED indicator family state is required");
        return (LedIndicatorFamilyState) instance.getFamilyState();
    }

    ReplaceableComponentSlot getR1Slot() { return r1Slot; }
    ResistorReplacementInventory getResistorInventory() { return resistorInventory; }
    ResistorReplacementCatalog getResistorCatalog() { return resistorCatalog; }
    String allocateCatalogPartId() { return "R1_CATALOG_PART_" + nextCatalogPartSerial++; }
    LedComponentSlot getLed1Slot() { return led1Slot; }
    LedReplacementInventory getLedInventory() { return ledInventory; }
    LedReplacementCatalog getLedCatalog() { return ledCatalog; }
    String allocateLedPartId() { return "LED1_CATALOG_PART_" + nextLedPartSerial++; }

    public boolean isFaultedTargetInstalled(String componentId) {
        return "R1".equals(componentId) && !r1Slot.isEmpty() &&
            r1Slot.getInstalledPart().isFaulted();
    }
}
