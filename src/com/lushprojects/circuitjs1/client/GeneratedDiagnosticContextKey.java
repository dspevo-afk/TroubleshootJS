package com.lushprojects.circuitjs1.client;

/**
 * Complete, immutable identity for a diagnostic proof-reuse context.
 *
 * <p>The dependency capture itself owns the list of model, recipe, source,
 * physical, scenario and diagnostic inputs.  This wrapper is deliberately
 * value-only and compares the complete canonical value, not just its Java
 * hash.  It is therefore safe to use as the key of a bounded proof cache.
 * No board, simulator, controller, solver element or listener is retained.</p>
 */
final class GeneratedDiagnosticContextKey {
    static final int VERSION = 3;
    private static final int MAX_CANONICAL_LENGTH = 1024 * 1024 + 128;

    private final String canonical;
    private final int hash;
    private final boolean trustedCapture;

    /**
     * Package-private value constructor for contract fixtures and adapters.
     * Production callers should use {@link #capture} or {@link #from} so the
     * complete generation dependency envelope is assembled at one boundary.
     */
    GeneratedDiagnosticContextKey(String completeCanonical) {
        this(completeCanonical, false);
    }

    private GeneratedDiagnosticContextKey(String completeCanonical, boolean trustedCapture) {
        if (completeCanonical == null || completeCanonical.length() == 0 ||
                completeCanonical.length() > MAX_CANONICAL_LENGTH)
            throw new IllegalArgumentException("Missing or oversized diagnostic context key");
        canonical = "tsj-d01-diagnostic-context-v" + VERSION + ";" + completeCanonical;
        hash = canonical.hashCode();
        this.trustedCapture = trustedCapture;
    }

    static GeneratedDiagnosticContextKey from(GenerationDependencyContext context) {
        if (context == null)
            throw new IllegalArgumentException("Missing generation dependency context");
        return new GeneratedDiagnosticContextKey(context.canonical(), true);
    }

    static GeneratedDiagnosticContextKey capture(CirSim sim, GeneratedBoardInstance owner,
            String requestManifest, String realizationManifest) {
        return from(GenerationDependencyContext.capture(sim, owner, requestManifest,
            realizationManifest));
    }

    String canonical() { return canonical; }

    /** Only complete dependency captures may enter the service reuse boundary. */
    boolean isTrustedCapture() { return trustedCapture; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof GeneratedDiagnosticContextKey &&
            canonical.equals(((GeneratedDiagnosticContextKey) other).canonical));
    }

    @Override
    public int hashCode() { return hash; }
}
