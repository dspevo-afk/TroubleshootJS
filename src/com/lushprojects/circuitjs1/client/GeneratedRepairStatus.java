package com.lushprojects.circuitjs1.client;

/** Internal solver-backed repair result; never exposed in the player UI. */
enum GeneratedRepairStatus {
    STILL_FAULTED_OR_NONFUNCTIONAL,
    DEGRADED_BUT_OPERATING,
    CORRECTLY_RESTORED
}
