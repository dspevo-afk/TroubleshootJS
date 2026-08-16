package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorFamilyState implements GeneratedBoardFamilyState {
    private final DiodeComponentSlot d1Slot;
    private final DiodeReplacementInventory inventory;
    private final DiodeReplacementCatalog catalog;
    private int nextPartSerial;

    DiodeProtectedIndicatorFamilyState(DiodeComponentSlot d1Slot,
            DiodeReplacementInventory inventory, DiodeReplacementCatalog catalog) {
        if (d1Slot == null || inventory == null || catalog == null)
            throw new IllegalArgumentException("Missing diode family state");
        this.d1Slot = d1Slot;
        this.inventory = inventory;
        this.catalog = catalog;
    }

    static DiodeProtectedIndicatorFamilyState require(GeneratedBoardInstance instance) {
        if (instance == null || !(instance.getFamilyState() instanceof DiodeProtectedIndicatorFamilyState))
            throw new IllegalStateException("Diode replacement requires diode family state");
        return (DiodeProtectedIndicatorFamilyState) instance.getFamilyState();
    }

    DiodeComponentSlot getD1Slot() { return d1Slot; }
    DiodeReplacementInventory getInventory() { return inventory; }
    DiodeReplacementCatalog getCatalog() { return catalog; }
    String allocatePartId() { return "D1_CATALOG_PART_" + nextPartSerial++; }

    public boolean isFaultedTargetInstalled(String componentId) {
        return "J1".equals(componentId) || ("D1".equals(componentId) &&
            !d1Slot.isEmpty() && d1Slot.getInstalledPart().isFaulted());
    }
}
