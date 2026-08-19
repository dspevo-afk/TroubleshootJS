package com.lushprojects.circuitjs1.client;

class LedIndicatorFamilyState implements GeneratedBoardFamilyState {
    private final GeneratedBoardOperationCatalog operations =
        new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retestProfile;

    LedIndicatorFamilyState() {
        retestProfile = GeneratedCustomerRetestProfiles.observation(
            "LED_INDICATOR_CUSTOMER_RETEST", "Observe the indicator after repair.",
            "Board powered for steady-state observation.", "No external input transition.",
            "Indicator operation.", "Steady state; one observation.",
            "Power input and other board functions remain unchanged.");
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Retest Customer", new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance instance) {
                return retestProfile.execute(sim, instance);
            }
        }));
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }

    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
}
