package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class LedReplacementInventory {
    private final PhysicalPartInventory<PhysicalLedPart> inventory;

    LedReplacementInventory(PhysicalBoardRuntime runtime, String inventoryId) {
        inventory = new PhysicalPartInventory<PhysicalLedPart>(runtime, inventoryId,
            new PhysicalPartTypeAdapter<PhysicalLedPart>() {
                public PhysicalLedPart require(PhysicalPart<?> part) {
                    if (!(part instanceof PhysicalLedPart))
                        throw new IllegalArgumentException("Physical inventory part is not an LED");
                    return (PhysicalLedPart) part;
                }
            });
    }

    void add(PhysicalLedPart part) {
        inventory.add(part);
    }

    PhysicalLedPart acquire(String idNamespace,
            PhysicalPartIdentityFactory<PhysicalLedPart> factory) {
        return inventory.acquire(idNamespace, factory);
    }

    PhysicalLedPart get(String id) {
        return inventory.get(id);
    }

    boolean contains(String id) { return inventory.contains(id); }
    Vector<PhysicalLedPart> getAll() { return inventory.getAll(); }
    Vector<PhysicalLedPart> getLooseParts() {
        Vector<PhysicalLedPart> result = new Vector<PhysicalLedPart>();
        for (PhysicalLedPart part : inventory.getAll())
            if (!part.isInstalled())
                result.add(part);
        return result;
    }
}
