package com.lushprojects.circuitjs1.client;

/** Optional lifecycle owned by a registered physical-board capability. */
interface PhysicalBoardRuntimeLifecycle {
    void observeSimulationTime(double simulationTime);
    void resetForBoardReset();
    void synchronizeSimulationTime(double simulationTime);
}
