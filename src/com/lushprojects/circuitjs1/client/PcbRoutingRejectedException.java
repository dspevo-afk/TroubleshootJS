package com.lushprojects.circuitjs1.client;

/**
 * Expected exhaustion of one bounded physical PCB search.
 *
 * <p>This is deliberately distinct from an invariant or provider failure.
 * The layout generator emits it only after all deterministic attempts have
 * been rejected by one of its bounded placement or routing searches.</p>
 */
final class PcbRoutingRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    enum Kind {
        PLACEMENT,
        ROUTING
    }

    private final Kind kind;
    private final int attemptIndex;
    private final int attemptCount;
    private final long seed;
    private final boolean exhausted;

    private PcbRoutingRejectedException(Kind kind, int attemptIndex, int attemptCount,
            long seed, boolean exhausted, String message, Throwable cause) {
        super(message, cause);
        if (kind == null || attemptIndex < 0 || attemptCount < 1 ||
                attemptIndex >= attemptCount)
            throw new IllegalArgumentException("Invalid PCB routing rejection metadata");
        this.kind = kind;
        this.attemptIndex = attemptIndex;
        this.attemptCount = attemptCount;
        this.seed = seed;
        this.exhausted = exhausted;
    }

    static PcbRoutingRejectedException attemptRejected(Kind kind, int attemptIndex,
            long seed, String detail) {
        String message = "PCB " + kindLabel(kind) + " candidate rejected at attempt " +
            attemptIndex + (detail == null ? "" : ": " + detail);
        return new PcbRoutingRejectedException(kind, attemptIndex, attemptIndex + 1,
            seed, false, message, null);
    }

    static PcbRoutingRejectedException exhausted(long seed, int attemptCount,
            PcbRoutingRejectedException lastRejection) {
        if (lastRejection == null)
            throw new IllegalArgumentException("Missing last PCB routing rejection");
        String message = "Unable to generate a routed PCB after " + attemptCount +
            " deterministic attempts for seed " + seed + ": " + lastRejection.getMessage();
        return new PcbRoutingRejectedException(lastRejection.kind, attemptCount - 1,
            attemptCount, seed, true, message, lastRejection);
    }

    /**
     * Normalizes a declared route-quality admission rejection at an assembly
     * boundary where there is no leaf-generator attempt to report.  The
     * quality exception remains the cause so the declared quality kind and
     * route evidence are available to diagnostics.
     */
    static PcbRoutingRejectedException routeQualityRejected(long seed,
            PcbBoardLayout.RouteQualityRejectedException qualityFailure) {
        if (qualityFailure == null)
            throw new IllegalArgumentException("Missing PCB route quality rejection");
        String message = "PCB routing candidate rejected for seed " + seed + ": " +
            qualityFailure.getMessage();
        return new PcbRoutingRejectedException(Kind.ROUTING, 0, 1, seed, false,
            message, qualityFailure);
    }

    private static String kindLabel(Kind kind) {
        return kind == Kind.PLACEMENT ? "placement" : "routing";
    }

    Kind getKind() { return kind; }
    int getAttemptIndex() { return attemptIndex; }
    int getAttemptCount() { return attemptCount; }
    long getSeed() { return seed; }
    boolean isExhausted() { return exhausted; }
}
