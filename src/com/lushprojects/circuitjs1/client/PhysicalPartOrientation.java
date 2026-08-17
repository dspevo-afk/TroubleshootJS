package com.lushprojects.circuitjs1.client;

/** Generic physical orientation metadata; electrical meaning remains family-owned. */
enum PhysicalPartOrientation {
    NON_POLARIZED,
    NORMAL,
    REVERSED;

    static PhysicalPartOrientation polarized(boolean reversed) {
        return reversed ? REVERSED : NORMAL;
    }
}
