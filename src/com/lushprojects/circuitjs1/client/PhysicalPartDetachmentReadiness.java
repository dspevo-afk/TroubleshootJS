package com.lushprojects.circuitjs1.client;

/** Optional physical-energy condition for disconnecting this board's existing graph. */
interface PhysicalPartDetachmentReadiness {
    boolean isDetachmentReady(CirSim sim, GeneratedBoardInstance owner);
}
