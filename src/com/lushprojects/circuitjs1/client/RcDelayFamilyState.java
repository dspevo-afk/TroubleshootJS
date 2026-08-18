package com.lushprojects.circuitjs1.client;

/** RC family keeps selected-fault ownership with the physical C1 instance. */
final class RcDelayFamilyState implements GeneratedBoardFamilyState {
    public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
    }
}
