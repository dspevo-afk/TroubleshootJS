package com.lushprojects.circuitjs1.client;

/** Optional instrument-only injection eligibility; never a service-energy guard. */
interface ActiveInstrumentAdmissionCapability extends PhysicalBoardRuntimeCapability {
    ActiveMeasurementReadiness getActiveInstrumentAdmission(CircuitPostMeasurementEndpoint red,
        CircuitPostMeasurementEndpoint black);
}
