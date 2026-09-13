package com.lushprojects.circuitjs1.client;

/** Model-owned bounded solver settling around a temporary instrument source. */
interface ActiveMeasurementSettlingCapability extends PhysicalBoardRuntimeCapability {
    void settleMeasurement(CirSim sim, GeneratedBoardInstance owner, boolean stimulusInstalled);
}
