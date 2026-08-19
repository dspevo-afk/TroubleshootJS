package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorFamilyState implements GeneratedBoardFamilyState {
    private final GeneratedBoardOperationCatalog operations =
        new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retestProfile;

    ParallelDualIndicatorFamilyState() {
        retestProfile = GeneratedCustomerRetestProfiles.observation(
            "PARALLEL_INDICATOR_CUSTOMER_RETEST", "Observe both indicators after repair.",
            "Board powered for steady-state observation.", "No external input transition.",
            "Both indicator branches.", "Steady state; one observation.",
            "The other indicator branch remains part of the observation.");
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
