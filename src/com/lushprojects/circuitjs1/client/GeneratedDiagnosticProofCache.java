package com.lushprojects.circuitjs1.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Bounded cache of completed diagnostic evidence and its immutable partition.
 *
 * <p>Only value data is retained.  A cache entry never owns a board,
 * controller, CircuitJS element/solver graph, listener, or mutable work
 * cursor.  A hit still has to pass current structural/global validation and
 * emits a new owner-bound receipt through the current controller.</p>
 */
final class GeneratedDiagnosticProofCache {
    static final int VERSION = 1;
    private static final int CAPACITY = 8;
    private final Map<GeneratedDiagnosticContextKey, Entry> entries =
        new LinkedHashMap<GeneratedDiagnosticContextKey, Entry>();
    private int hits;
    private int misses;

    Entry get(GeneratedDiagnosticContextKey key) {
        if (key == null) throw new IllegalArgumentException("Missing diagnostic cache key");
        Entry result = entries.get(key);
        if (result == null) misses++; else hits++;
        return result;
    }

    Entry lookup(GeneratedDiagnosticContextKey key) { return get(key); }

    /**
     * Builds and validates a value-only artifact while the candidate is still
     * private.  It intentionally does not alter the cache: callers must wait
     * until their final owner/publication transaction succeeds before calling
     * {@link #storePrepared(Entry)}.
     */
    Entry prepare(GeneratedDiagnosticContextKey key, GeneratedBoardInstance owner,
            GeneratedDiagnosticProofReceipt receipt) {
        if (key == null || owner == null || receipt == null)
            throw new IllegalArgumentException("Missing diagnostic cache publication input");
        receipt.requireContextKey(key);
        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        receipt.requireAssessmentOwner(owner);
        GeneratedDiagnosticPartitionPlan partition = receipt.getPartitionPlan();
        if (partition == null)
            throw new IllegalStateException("Diagnostic receipt has no immutable partition plan");
        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        partition.validateAgainst(owner.getFaultCandidates(), provider.getObservationProgram(), evidence);
        return new Entry(key, owner.getCircuitFamilyId(), owner.getTopologyVariantId(),
            owner.getSeed(), provider.getProviderId(), provider.getObservationProgram().canonical(),
            GeneratedDiagnosticSolvabilityAdmission.getHypothesisKeys(owner.getFaultCandidates()),
            evidence, partition);
    }

    void put(GeneratedDiagnosticContextKey key, GeneratedBoardInstance owner,
            GeneratedDiagnosticProofReceipt receipt) {
        storePrepared(prepare(key, owner, receipt));
    }

    /** Stores a previously validated value artifact after its owner published. */
    void storePrepared(Entry value) {
        if (value == null || value.getContextKey() == null)
            throw new IllegalArgumentException("Missing prepared diagnostic cache entry");
        GeneratedDiagnosticContextKey key = value.getContextKey();
        if (entries.size() >= CAPACITY && !entries.containsKey(key))
            entries.remove(entries.keySet().iterator().next());
        entries.put(key, value);
    }

    void clear() { entries.clear(); }
    int size() { return entries.size(); }
    int getHits() { return hits; }
    int getMisses() { return misses; }

    /** Explicit canary used by contract tests; this class has no live-object fields. */
    boolean retainsRuntimeObjectsForDeveloperVerification() { return false; }

    /** Immutable value-only cache payload. */
    static final class Entry {
        private final GeneratedDiagnosticContextKey contextKey;
        private final String familyId;
        private final String topologyVariantId;
        private final long seed;
        private final String providerId;
        private final String programIdentity;
        private final Vector<String> hypothesisKeys;
        private final Vector<GeneratedDiagnosticSolvabilityEvidence> evidence;
        private final GeneratedDiagnosticPartitionPlan partition;

        private Entry(GeneratedDiagnosticContextKey contextKey, String familyId,
                String topologyVariantId, long seed, String providerId,
                String programIdentity, Vector<String> hypothesisKeys,
                Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
                GeneratedDiagnosticPartitionPlan partition) {
            if (contextKey == null || familyId == null || topologyVariantId == null ||
                    providerId == null || programIdentity == null || hypothesisKeys == null ||
                    evidence == null || partition == null)
                throw new IllegalArgumentException("Incomplete diagnostic cache entry");
            this.contextKey = contextKey;
            this.familyId = familyId;
            this.topologyVariantId = topologyVariantId;
            this.seed = seed;
            this.providerId = providerId;
            this.programIdentity = programIdentity;
            this.hypothesisKeys = new Vector<String>(hypothesisKeys);
            this.evidence = new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
            this.partition = partition;
        }

        String getProviderId() { return providerId; }
        String getProgramIdentity() { return programIdentity; }
        String getFamilyId() { return familyId; }
        String getTopologyVariantId() { return topologyVariantId; }
        long getSeed() { return seed; }
        GeneratedDiagnosticContextKey getContextKey() { return contextKey; }
        Vector<String> getHypothesisKeys() { return new Vector<String>(hypothesisKeys); }
        Vector<GeneratedDiagnosticSolvabilityEvidence> getEvidence() {
            return new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        }
        GeneratedDiagnosticPartitionPlan getPartition() { return partition; }

        /**
         * Checks the complete current owner before any warm evidence is used.
         * This deliberately repeats the global population/repair validation;
         * a cache hit is an optimization, not an admission authority.
         */
        void validateForReuse(GeneratedDiagnosticContextKey expectedKey,
                GeneratedBoardInstance owner) {
            if (expectedKey == null || owner == null || !contextKey.equals(expectedKey))
                throw new IllegalStateException("Stale or foreign diagnostic cache entry");
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
            GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
            if (!familyId.equals(owner.getCircuitFamilyId()) ||
                    !topologyVariantId.equals(owner.getTopologyVariantId()) ||
                    seed != owner.getSeed() || provider == null ||
                    !providerId.equals(provider.getProviderId()) ||
                    !programIdentity.equals(provider.getObservationProgram().canonical()))
                throw new IllegalStateException("Diagnostic cache context does not match owner");
            Vector<String> currentKeys = GeneratedDiagnosticSolvabilityAdmission
                .getHypothesisKeys(owner.getFaultCandidates());
            if (!hypothesisKeys.equals(currentKeys))
                throw new IllegalStateException("Diagnostic cache hypothesis population changed");
            partition.validateAgainst(owner.getFaultCandidates(),
                provider.getObservationProgram(), evidence);
        }

        /** Issues a new receipt using only the current owner/controller/attempt. */
        GeneratedDiagnosticProofReceipt issueReceipt(GeneratedBoardInstance owner,
                GeneratedChallengeController controller, Object attempt,
                GeneratedDiagnosticContextKey expectedKey) {
            validateForReuse(expectedKey, owner);
            GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
            return new GeneratedDiagnosticProofReceipt(owner, controller, attempt,
                provider.getObservationProgram(), evidence, 0, partition, expectedKey, true);
        }

        /** This entry intentionally has no runtime object references. */
        boolean retainsRuntimeObjectsForDeveloperVerification() { return false; }
    }
}
