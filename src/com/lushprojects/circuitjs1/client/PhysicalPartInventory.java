package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Typed view over the physical inventory whose storage is owned by the board runtime. */
final class PhysicalPartInventory<P extends PhysicalPart<?>> {
    private final PhysicalBoardRuntime runtime;
    private final String inventoryId;
    private final PhysicalPartTypeAdapter<P> adapter;

    PhysicalPartInventory(PhysicalBoardRuntime runtime, String inventoryId,
            PhysicalPartTypeAdapter<P> adapter) {
        if (runtime == null || inventoryId == null || inventoryId.length() == 0 || adapter == null)
            throw new IllegalArgumentException("Invalid physical inventory view");
        this.runtime = runtime;
        this.inventoryId = inventoryId;
        this.adapter = adapter;
    }

    void add(P part) {
        adapter.require(part);
        runtime.addInventoryPart(inventoryId, part);
    }

    P acquire(String idNamespace, PhysicalPartIdentityFactory<P> factory) {
        return adapter.require(runtime.acquireInventoryPart(inventoryId, idNamespace, factory));
    }

    P get(String partId) {
        return adapter.require(runtime.getInventoryPart(inventoryId, partId));
    }

    Vector<P> getAll() {
        Vector<P> result = new Vector<P>();
        for (PhysicalPart<?> part : runtime.getInventoryParts(inventoryId))
            result.add(adapter.require(part));
        return result;
    }

    boolean contains(String partId) { return runtime.inventoryContains(inventoryId, partId); }
    int size() { return runtime.getInventoryParts(inventoryId).size(); }
}
