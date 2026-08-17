package com.lushprojects.circuitjs1.client;

/** Hidden, coherent failure state; the electrical effect remains CircuitJS-backed. */
final class PhysicalFailureState {
    static final String HEALTHY = "HEALTHY";
    static final String GENERATED_FAULT = "GENERATED_FAULT";
    static final String SECONDARY_FAILURE = "SECONDARY_FAILURE";

    private final String kind;
    private final boolean failed;

    PhysicalFailureState(String kind, boolean failed) {
        if (kind == null || kind.length() == 0)
            throw new IllegalArgumentException("Invalid physical failure state");
        this.kind = kind;
        this.failed = failed;
    }

    String getKind() { return kind; }
    boolean isFailed() { return failed; }
}
