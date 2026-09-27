package com.lushprojects.circuitjs1.client;

/** Immutable physical-evidence contract carried by a normal generated owner. */
interface GeneratedPhysicalAdmission {
    String identity();
    String canonical();

    /** Checks a private candidate before board-owner construction begins. */
    void requireConstruction(TroubleshootBoard board, PcbBoardLayout layout);

    /** Rechecks the sealed physical realization at every normal admission gate. */
    void requireNormal(GeneratedBoardInstance owner);
}
