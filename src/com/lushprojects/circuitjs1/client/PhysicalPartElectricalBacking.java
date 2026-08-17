package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Narrow CircuitJS adapter owned by the simulation boundary, not part identity. */
interface PhysicalPartElectricalBacking {
    int getTerminalCount();
    CircuitMeasurementEndpoint getTerminalEndpoint(int terminal);
    Vector<CircuitElm> getCircuitElements();
}
