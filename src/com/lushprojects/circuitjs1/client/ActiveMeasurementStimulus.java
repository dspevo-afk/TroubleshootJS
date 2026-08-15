package com.lushprojects.circuitjs1.client;

interface ActiveMeasurementStimulus {
    void install(CirSim sim);
    void remove(CirSim sim);
    CircuitElm[] getTemporaryElements();
}

interface ActiveMeasurementResultReader {
    double readResult();
}