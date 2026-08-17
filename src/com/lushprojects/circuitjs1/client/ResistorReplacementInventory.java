package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ResistorReplacementInventory {
    private final PhysicalPartInventory<PhysicalResistorPart> inventory;

    ResistorReplacementInventory(PhysicalBoardRuntime runtime, String inventoryId) {
        inventory = new PhysicalPartInventory<PhysicalResistorPart>(runtime, inventoryId,
            new PhysicalPartTypeAdapter<PhysicalResistorPart>() {
                public PhysicalResistorPart require(PhysicalPart<?> part) {
                    if (!(part instanceof PhysicalResistorPart))
                        throw new IllegalArgumentException("Physical inventory part is not a resistor");
                    return (PhysicalResistorPart) part;
                }
            });
    }

    void add(PhysicalResistorPart part) {
        inventory.add(part);
    }

    PhysicalResistorPart acquire(String idNamespace,
            PhysicalPartIdentityFactory<PhysicalResistorPart> factory) {
        return inventory.acquire(idNamespace, factory);
    }

    PhysicalResistorPart get(String partId) {
        return inventory.get(partId);
    }

    Vector<PhysicalResistorPart> getAll() {
        Vector<PhysicalResistorPart> result = new Vector<PhysicalResistorPart>();
        result.addAll(inventory.getAll());
        return result;
    }

    Vector<PhysicalResistorPart> getLooseParts() {
        Vector<PhysicalResistorPart> result = new Vector<PhysicalResistorPart>();
        for (PhysicalResistorPart part : getAll())
            if (!part.isInstalled())
                result.add(part);
        return result;
    }

    boolean contains(String partId) { return inventory.contains(partId); }
    int size() { return inventory.size(); }
}
