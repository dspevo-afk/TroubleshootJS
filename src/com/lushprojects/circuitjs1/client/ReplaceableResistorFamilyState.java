package com.lushprojects.circuitjs1.client;

/** Family contract shared by generated boards with one replaceable resistor. */
interface ReplaceableResistorFamilyState {
    ReplaceableComponentSlot getReplaceableResistorSlot();
    ResistorReplacementInventory getResistorInventory();
    ResistorReplacementCatalog getResistorCatalog();
    String allocateCatalogPartId();
}
