package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class DiodeReplacementInventory {
    private final PhysicalPartInventory<PhysicalDiodePart> inventory;

    DiodeReplacementInventory(PhysicalBoardRuntime runtime, String inventoryId) {
        inventory = new PhysicalPartInventory<PhysicalDiodePart>(runtime, inventoryId,
            new PhysicalPartTypeAdapter<PhysicalDiodePart>() {
                public PhysicalDiodePart require(PhysicalPart<?> part) {
                    if (!(part instanceof PhysicalDiodePart))
                        throw new IllegalArgumentException("Physical inventory part is not a diode");
                    return (PhysicalDiodePart) part;
                }
            });
    }

    void add(PhysicalDiodePart part) {
        inventory.add(part);
    }

    PhysicalDiodePart acquire(String idNamespace,
            PhysicalPartIdentityFactory<PhysicalDiodePart> factory) {
        return inventory.acquire(idNamespace, factory);
    }

    PhysicalDiodePart get(String id) {
        return inventory.get(id);
    }

    boolean contains(String id) { return inventory.contains(id); }
    Vector<PhysicalDiodePart> getAll() { return inventory.getAll(); }
    Vector<PhysicalDiodePart> getLooseParts() {
        Vector<PhysicalDiodePart> result = new Vector<PhysicalDiodePart>();
        for (PhysicalDiodePart part : inventory.getAll())
            if (!part.isInstalled())
                result.add(part);
        return result;
    }
}
