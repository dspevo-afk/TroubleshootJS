package com.lushprojects.circuitjs1.client;

import java.util.Map;

/** Compile-only deny stub. No authored DATA values are embedded or returned. */
public final class CohortTerminalTelemetryInput {
    private CohortTerminalTelemetryInput() { }

    private static IllegalStateException denied() {
        return new IllegalStateException("COHORT_TERMINAL_INPUT_DENIED");
    }

    public static String descriptorSha256() { throw denied(); }
    public static int dataEntries() { throw denied(); }
    public static Map<String,Object> data() { throw denied(); }
}
