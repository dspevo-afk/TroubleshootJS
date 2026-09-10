package com.lushprojects.circuitjs1.client;

/** Optional reference policy owned by the same physical runtime as the targets. */
interface MeasurementReferenceCapability extends PhysicalBoardRuntimeCapability {
    MeasurementReferencePolicy.Result assessReference(MeasurementReferencePolicy.Mode mode,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black);
}
