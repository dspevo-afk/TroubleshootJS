package com.lushprojects.circuitjs1.client;

class ParallelDualIndicatorFamilyState implements GeneratedBoardFamilyState {
    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }
}
