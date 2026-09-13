package com.lushprojects.circuitjs1.client;

/** One family-owned operation exposed through the common player/validator seam. */
final class GeneratedBoardOperation {
    interface Executor {
        GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance);
    }

    abstract static class ResumableExecutor implements Executor {
        abstract GeneratedWork<GeneratedCustomerRetestResult> begin(CirSim sim,
            GeneratedBoardInstance instance);
        abstract int getWorkUnits(GeneratedBoardInstance instance);
        public final GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
            return GeneratedWork.complete(begin(sim, instance));
        }
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

    void appendExecutionOwners(java.util.Vector<Object> owners) {
        owners.add(this); owners.add(executor);
    }

    GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
        return executor.execute(sim, instance);
    }

    int getWorkUnits(GeneratedBoardInstance instance) {
        return executor instanceof ResumableExecutor ?
            ((ResumableExecutor) executor).getWorkUnits(instance) : 1;
    }

    GeneratedWork<GeneratedCustomerRetestResult> begin(final CirSim sim,
            final GeneratedBoardInstance instance) {
        if (executor instanceof ResumableExecutor)
            return ((ResumableExecutor) executor).begin(sim, instance);
        return new GeneratedWork<GeneratedCustomerRetestResult>() {
            private boolean complete, cancelled;
            private GeneratedCustomerRetestResult result;
            boolean step() {
                if (cancelled) throw new IllegalStateException("Board operation was cancelled");
                if (!complete) { result = executor.execute(sim, instance); complete = true; }
                return false;
            }
            GeneratedCustomerRetestResult finish() {
                if (!complete || cancelled) throw new IllegalStateException("Board operation is incomplete");
                return result;
            }
            void cancel() { if (!complete) cancelled = true; }
            int getWorkUnits() { return 1; }
        };
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
