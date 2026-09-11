package com.lushprojects.circuitjs1.client;

/** Runtime capability hook for installing a board-owned mutation provider. */
interface PhysicalBoardInstallationProvider {
    /** Pre-install ownership declaration for providers using bounded mutation. */
    interface Scoped extends PhysicalBoardInstallationProvider {
        PhysicalMutationSlot getMutationSlot();
        PhysicalPartInventory<?> getMutationInventory();
    }

    PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime);
}
