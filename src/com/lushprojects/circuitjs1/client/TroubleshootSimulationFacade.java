package com.lushprojects.circuitjs1.client;

/** Narrow graph-facing seam for TroubleshootJS physical-domain checks. */
interface TroubleshootSimulationFacade {
    boolean ownsBacking(PhysicalPartElectricalBacking backing);
    void validateBacking(PhysicalPart<?> part);
}
