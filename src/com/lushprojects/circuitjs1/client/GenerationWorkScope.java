package com.lushprojects.circuitjs1.client;

/** Synchronous checkpoint seam for reusable routing and solver services. */
final class GenerationWorkScope {
    private static GenerationJob active;
    private GenerationWorkScope() { }

    static void enter(GenerationJob job) {
        if (job == null || active != null)
            throw new IllegalStateException("Generation work scope is already owned");
        active = job;
    }
    static void check() {
        if (active != null) active.checkpoint();
    }
    static void exit(GenerationJob job) {
        if (active != job)
            throw new IllegalStateException("Generation work scope owner changed");
        active = null;
    }
}
