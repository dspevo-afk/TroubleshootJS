package com.lushprojects.circuitjs1.client;

/** Runtime capability hook for installing a board-owned mutation provider. */
interface PhysicalBoardInstallationProvider {
    PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime);
}
