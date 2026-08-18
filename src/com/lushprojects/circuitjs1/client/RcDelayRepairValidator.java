package com.lushprojects.circuitjs1.client;

/** The generic controller delegates actual RC repair proof to the temporal contract. */
final class RcDelayRepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
    }
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) { return false; }
}
