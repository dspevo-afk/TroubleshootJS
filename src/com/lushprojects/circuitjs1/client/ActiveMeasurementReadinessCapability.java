package com.lushprojects.circuitjs1.client;

/** Optional runtime policy for physical stored-energy measurement safety. */
interface ActiveMeasurementReadinessCapability extends PhysicalBoardRuntimeCapability {
    ActiveMeasurementReadiness getActiveMeasurementReadiness(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black, BoardPowerState powerState,
            boolean electricallyUnpowered);
    boolean usesLiveDcVoltage(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black);
}
