package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Family-neutral lookup and dispatch for stable semantic operations. */
final class GeneratedBoardOperationCatalog {
    private final Vector<GeneratedBoardOperation> operations =
        new Vector<GeneratedBoardOperation>();

    void add(GeneratedBoardOperation operation) {
        if (operation == null || find(operation.getStableId()) != null)
            throw new IllegalArgumentException("Duplicate generated board operation");
        operations.add(operation);
    }

    Vector<GeneratedBoardOperation> getAll() {
        return new Vector<GeneratedBoardOperation>(operations);
    }

    GeneratedBoardOperation find(String stableId) {
        for (GeneratedBoardOperation operation : operations)
            if (operation.getStableId().equals(stableId))
                return operation;
        return null;
    }

    GeneratedCustomerRetestResult invoke(String stableId, CirSim sim,
            GeneratedBoardInstance instance) {
        GeneratedBoardOperation operation = find(stableId);
        if (operation == null)
            throw new IllegalArgumentException("Unsupported generated board operation: " + stableId);
        return operation.execute(sim, instance);
    }
}
