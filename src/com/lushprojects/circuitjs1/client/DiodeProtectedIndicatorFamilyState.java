package com.lushprojects.circuitjs1.client;

class DiodeProtectedIndicatorFamilyState implements GeneratedBoardFamilyState {
    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }
}
