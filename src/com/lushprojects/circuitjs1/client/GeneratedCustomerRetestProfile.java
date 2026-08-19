package com.lushprojects.circuitjs1.client;

/**
 * Family-owned customer retest contract.  Its descriptors are semantic and
 * player-safe; execution remains family-owned so observations come from the
 * existing CircuitJS-backed validators and temporal behavior.
 */
final class GeneratedCustomerRetestProfile {
    interface Executor {
        GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance);
    }

    private final String stableId;
    private final String playerInstruction;
    private final String requiredPowerTransition;
    private final String requiredInputTransition;
    private final String observableOutput;
    private final String timingAndRepetition;
    private final String unaffectedFunctions;
    private final Executor executor;

    GeneratedCustomerRetestProfile(String stableId, String playerInstruction,
            String requiredPowerTransition, String requiredInputTransition,
            String observableOutput, String timingAndRepetition,
            String unaffectedFunctions, Executor executor) {
        GeneratedBoardOperation.requireStableSemanticId(stableId, "retest profile ID");
        requireText(playerInstruction, "retest instruction");
        requireText(requiredPowerTransition, "retest power transition");
        requireText(requiredInputTransition, "retest input transition");
        requireText(observableOutput, "retest observable output");
        requireText(timingAndRepetition, "retest timing");
        requireText(unaffectedFunctions, "retest unaffected functions");
        if (executor == null)
            throw new IllegalArgumentException("Customer retest requires an executor");
        this.stableId = stableId;
        this.playerInstruction = playerInstruction;
        this.requiredPowerTransition = requiredPowerTransition;
        this.requiredInputTransition = requiredInputTransition;
        this.observableOutput = observableOutput;
        this.timingAndRepetition = timingAndRepetition;
        this.unaffectedFunctions = unaffectedFunctions;
        this.executor = executor;
    }

    String getStableId() { return stableId; }
    String getPlayerInstruction() { return playerInstruction; }
    String getRequiredPowerTransition() { return requiredPowerTransition; }
    String getRequiredInputTransition() { return requiredInputTransition; }
    String getObservableOutput() { return observableOutput; }
    String getTimingAndRepetition() { return timingAndRepetition; }
    String getUnaffectedFunctions() { return unaffectedFunctions; }

    GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
        return executor.execute(sim, instance);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + name);
    }
}
