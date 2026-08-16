package com.lushprojects.circuitjs1.client;

interface GeneratedChallengeBehaviorContract {
    void verifyHealthy(GeneratedBoardInstance instance, BoardPowerState powerState);

    void verifyFaulted(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState);

    GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay);

    boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay);
}
