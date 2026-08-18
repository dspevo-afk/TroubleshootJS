package com.lushprojects.circuitjs1.client;

/** Optional physical-runtime hook for a real external power transition. */
interface PhysicalBoardRuntimePowerLifecycle {
    void onBoardPowerStateChanged(BoardPowerState state);
}
