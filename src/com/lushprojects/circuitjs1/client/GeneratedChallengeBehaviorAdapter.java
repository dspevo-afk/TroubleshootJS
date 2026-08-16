package com.lushprojects.circuitjs1.client;

/**
 * Keeps family-specific electrical validators behind the generic challenge
 * lifecycle boundary.
 */
class GeneratedChallengeBehaviorAdapter implements GeneratedChallengeBehaviorContract {
    private final GeneratedBoardValidator healthyValidator;
    private final GeneratedFaultValidator faultedValidator;
    private final GeneratedRepairValidator repairedValidator;

    GeneratedChallengeBehaviorAdapter(GeneratedBoardValidator healthyValidator,
            GeneratedFaultValidator faultedValidator, GeneratedRepairValidator repairedValidator) {
        if (healthyValidator == null || faultedValidator == null || repairedValidator == null)
            throw new IllegalArgumentException("Generated challenge behavior requires all phase validators");
        this.healthyValidator = healthyValidator;
        this.faultedValidator = faultedValidator;
        this.repairedValidator = repairedValidator;
    }

    public void verifyHealthy(GeneratedBoardInstance instance, BoardPowerState powerState) {
        healthyValidator.verify(instance, powerState);
    }

    public void verifyFaulted(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        faultedValidator.verify(instance, modifications, powerState);
    }

    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return repairedValidator.getRepairStatus(instance, modifications, powerState,
            activeMeasurementOverlay);
    }

    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState, activeMeasurementOverlay) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
    }
}
