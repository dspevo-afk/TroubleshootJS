package com.lushprojects.circuitjs1.client;

/** Timer board operations: an optional momentary START press and the customer retest. */
final class Timer555FamilyState implements GeneratedBoardFamilyState {
    private final GeneratedBoardOperationCatalog operations = new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retestProfile;
    private final Timer555TemporalBehavior temporal;

    Timer555FamilyState(final Timer555TemporalBehavior temporal) {
        if (temporal == null)
            throw new IllegalArgumentException("Missing timer temporal behavior");
        this.temporal = temporal;
        boolean triggered = temporal.getMode() == Timer555TemporalBehavior.Mode.TRIGGERED;
        if (triggered)
            operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.START_BUTTON_PRESS,
                "Press START button", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                    temporal.pressStartButton(sim);
                    return null;
                }
            }));
        retestProfile = new GeneratedCustomerRetestProfile(
            "TIMER_555_CUSTOMER_RETEST",
            triggered ? "Power-cycle the board, press START once and time the output pulse." :
                "Power-cycle the board and time the flashing output.",
            "Board Power OFF, then ON.",
            triggered ? "One momentary START press after power-up." : "No external input transition.",
            "The timer output driving the indicator.",
            "One power cycle and three solver-time observation windows.",
            "Input power and START remain the only exercised board functions.",
            new GeneratedCustomerRetestProfile.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                return GeneratedWork.complete(temporal.beginCustomerRetest(sim, instance));
            }
        });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Power-cycle and Retest Customer", new GeneratedBoardOperation.ResumableExecutor() {
            public GeneratedWork<GeneratedCustomerRetestResult> begin(CirSim sim,
                    GeneratedBoardInstance instance) {
                return temporal.beginCustomerRetest(sim, instance);
            }
            public int getWorkUnits(GeneratedBoardInstance instance) {
                return temporal.getProfileWorkUnits();
            }
        }));
    }

    public void requireOwnedBy(GeneratedBoardInstance instance) {
        if (temporal != instance.getTemporalBehavior())
            throw new IllegalArgumentException("Fresh family state captures a foreign temporal owner");
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance, String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }

    /** The START contact is momentary, so a settled board always has it released. */
    public String getSessionInputSignature() {
        if (!temporal.isStartButtonReleased())
            throw new IllegalStateException("START button is held outside a press");
        return "NO_SEMANTIC_INPUTS@1";
    }

    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
}
