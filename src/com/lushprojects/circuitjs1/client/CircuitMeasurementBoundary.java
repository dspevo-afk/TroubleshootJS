package com.lushprojects.circuitjs1.client;

/** Domain measurement boundary; solver transactions remain owned by CirSim. */
interface CircuitMeasurementBoundary {
    double measureDcVoltage(ProbeTarget redProbe, ProbeTarget blackProbe);
    boolean isActiveMeasurementAllowed(ProbeTarget redProbe, ProbeTarget blackProbe);
    double measureResistance(ProbeTarget redProbe, ProbeTarget blackProbe);
    DiodeMeasurementResult measureDiode(ProbeTarget redProbe, ProbeTarget blackProbe);
}
