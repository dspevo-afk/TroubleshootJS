package com.lushprojects.circuitjs1.client;

/** Domain measurement boundary; solver transactions remain owned by CirSim. */
interface CircuitMeasurementBoundary {
    double measureDcVoltage(ProbeTarget redProbe, ProbeTarget blackProbe);
    VoltageMeasurementResult measureDcVoltageResult(ProbeTarget redProbe, ProbeTarget blackProbe);
    VoltageMeasurementResult measureAcVoltage(ProbeTarget redProbe, ProbeTarget blackProbe);
    MeasurementReferencePolicy.Result assessDifferentialReference(ProbeTarget redProbe,
        ProbeTarget blackProbe);
    SolverTimeObservationService.Subscription observeDifferentialVoltage(ProbeTarget redProbe,
        ProbeTarget blackProbe);
    void stopObservingDifferentialVoltage(SolverTimeObservationService.Subscription samples);
    boolean usesLiveDcVoltage(ProbeTarget redProbe, ProbeTarget blackProbe);
    ActiveMeasurementReadiness getActiveMeasurementReadiness(ProbeTarget redProbe,
        ProbeTarget blackProbe);
    boolean isActiveMeasurementAllowed(ProbeTarget redProbe, ProbeTarget blackProbe);
    double measureResistance(ProbeTarget redProbe, ProbeTarget blackProbe);
    DiodeMeasurementResult measureDiode(ProbeTarget redProbe, ProbeTarget blackProbe);
}
