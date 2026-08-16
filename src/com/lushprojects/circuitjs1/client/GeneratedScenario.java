package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Immutable player scenario metadata with a solver-backed compatibility
 * predicate.  The observed behavior is deliberately generic so scenario
 * wording never has to know a GeneratedFaultType.
 */
final class GeneratedScenario<T> {
    private final String scenarioId;
    private final String complaintId;
    private final String complaintText;
    private final T observedBehavior;
    private final GeneratedScenarioCompatibility<T> compatibility;

    GeneratedScenario(String scenarioId, String complaintId, String complaintText,
            T observedBehavior, GeneratedScenarioCompatibility<T> compatibility) {
        requireText(scenarioId, "scenario ID");
        requireText(complaintId, "complaint ID");
        requireText(complaintText, "complaint text");
        if (observedBehavior == null || compatibility == null)
            throw new IllegalArgumentException("Scenario requires observed behavior and compatibility");
        this.scenarioId = scenarioId;
        this.complaintId = complaintId;
        this.complaintText = complaintText;
        this.observedBehavior = observedBehavior;
        this.compatibility = compatibility;
    }

    String getScenarioId() { return scenarioId; }
    String getComplaintId() { return complaintId; }
    String getComplaintText() { return complaintText; }
    T getObservedBehavior() { return observedBehavior; }

    boolean isCompatible(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        return compatibility.matches(instance, modifications, powerState, observedBehavior);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + name);
    }
}

interface GeneratedScenarioCompatibility<T> {
    boolean matches(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState, T observedBehavior);
}

/** Immutable candidate set; selection never consumes topology randomness. */
final class GeneratedScenarioCatalog<T> {
    private final Vector<GeneratedScenario<T>> candidates;

    GeneratedScenarioCatalog(Vector<GeneratedScenario<T>> candidates) {
        if (candidates == null || candidates.isEmpty())
            throw new IllegalArgumentException("Scenario catalog requires candidates");
        this.candidates = new Vector<GeneratedScenario<T>>(candidates);
        for (GeneratedScenario<T> candidate : this.candidates)
            if (candidate == null)
                throw new IllegalArgumentException("Scenario catalog contains a null candidate");
    }

    GeneratedScenario<T> select(long seed, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        Vector<GeneratedScenario<T>> compatible = new Vector<GeneratedScenario<T>>();
        for (GeneratedScenario<T> candidate : candidates)
            if (candidate.isCompatible(instance, modifications, powerState))
                compatible.add(candidate);
        if (compatible.isEmpty())
            throw new IllegalStateException("No solver-compatible generated scenarios");
        int index = (int) (stableSelectionValue(seed) % compatible.size());
        if (index < 0)
            index += compatible.size();
        return compatible.elementAt(index);
    }

    private long stableSelectionValue(long seed) {
        // Keep complaint phrasing on a separate deterministic stream.  This
        // must not consume or reseed the generator's topology Random.
        long value = seed ^ 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
