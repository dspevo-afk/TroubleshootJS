package com.lushprojects.circuitjs1.client;

/** One family-owned operation exposed through the common player/validator seam. */
final class GeneratedBoardOperation {
    interface Executor {
        GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance);
    }

    private final String stableId;
    private final String playerLabel;
    private final Executor executor;

    GeneratedBoardOperation(String stableId, String playerLabel, Executor executor) {
        requireStableSemanticId(stableId, "operation ID");
        if (playerLabel == null || playerLabel.length() == 0 || executor == null)
            throw new IllegalArgumentException("Invalid generated board operation");
        this.stableId = stableId;
        this.playerLabel = playerLabel;
        this.executor = executor;
    }

    String getStableId() { return stableId; }
    String getPlayerLabel() { return playerLabel; }

    GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
        return executor.execute(sim, instance);
    }

    static void requireStableSemanticId(String value, String name) {
        if (value == null || value.length() == 0 ||
                !value.matches("[A-Z][A-Z0-9_]*"))
            throw new IllegalArgumentException("Invalid stable semantic " + name);
        String[] forbiddenTokens = { "NODE", "PAD", "COORD", "INDEX", "UUID" };
        for (String token : forbiddenTokens)
            if (value.indexOf(token) >= 0)
                throw new IllegalArgumentException("Stable semantic " + name +
                    " must not encode physical identity");
    }
}
