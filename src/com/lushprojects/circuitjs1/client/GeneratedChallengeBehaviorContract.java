package com.lushprojects.circuitjs1.client;

interface GeneratedChallengeBehaviorContract {
    void verifyHealthy(GeneratedBoardInstance instance, BoardPowerState powerState);

    void verifyFaulted(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState);

    boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay);
}
