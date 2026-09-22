package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * Immutable evidence of one completed, restored admission attempt. Not a cache:
 * the originating controller consumes its opaque attempt exactly once.
 * Detailed observations stay internal unless an explicit developer client publishes them.
 */
final class GeneratedDiagnosticProofReceipt {
    private final GeneratedBoardInstance owner;
    private final GeneratedChallengeController controller;
    private final GeneratedDiagnosticSolvabilityContract contract;
    private final GeneratedDiagnosticProvider provider;
    private final Object attempt;
    private final String programIdentity;
    private final String providerId;
    private final Vector<GeneratedDiagnosticSolvabilityEvidence> evidence;
    private final GeneratedDiagnosticPartitionPlan partitionPlan;
    private final GeneratedDiagnosticContextKey contextKey;
    private final boolean warmReuse;
    private final long elapsedMillis;

    GeneratedDiagnosticProofReceipt(GeneratedBoardInstance owner,
            GeneratedChallengeController controller, Object attempt,
            GeneratedDiagnosticProgram program, Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            long elapsedMillis) {
        this(owner, controller, attempt, program, evidence, elapsedMillis, null, null, false);
    }

    GeneratedDiagnosticProofReceipt(GeneratedBoardInstance owner,
            GeneratedChallengeController controller, Object attempt,
            GeneratedDiagnosticProgram program, Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            long elapsedMillis, GeneratedDiagnosticPartitionPlan partitionPlan,
            GeneratedDiagnosticContextKey contextKey, boolean warmReuse) {
        if (owner == null || controller == null || attempt == null || program == null ||
                evidence == null || evidence.isEmpty() || owner.isDeveloperOnlyFaultRoute() ||
                owner.getDiagnosticProvider() == null)
            throw new IllegalArgumentException("Cannot issue an empty or developer-fixture production proof");
        this.owner = owner; this.controller = controller; this.attempt = attempt;
        this.provider = owner.getDiagnosticProvider();
        this.contract = owner.getDiagnosticSolvabilityContract();
        this.programIdentity = program.canonical();
        this.providerId = provider.getProviderId();
        Vector<GeneratedDiagnosticSolvabilityEvidence> orderedEvidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        Collections.sort(orderedEvidence,
            new Comparator<GeneratedDiagnosticSolvabilityEvidence>() {
                public int compare(GeneratedDiagnosticSolvabilityEvidence first,
                        GeneratedDiagnosticSolvabilityEvidence second) {
                    if (first == null || second == null)
                        return first == second ? 0 : (first == null ? -1 : 1);
                    return first.getHypothesisKey().compareTo(second.getHypothesisKey());
                }
            });
        this.evidence = GeneratedDiagnosticEquivalence.classify(orderedEvidence,
            owner.getCircuitFamilyId(), owner.getSeed());
        this.partitionPlan = partitionPlan == null ?
            GeneratedDiagnosticPartitionPlan.build(owner.getFaultCandidates(), providerId,
                program, this.evidence) : partitionPlan;
        this.partitionPlan.validateAgainst(owner.getFaultCandidates(), program, this.evidence);
        this.contextKey = contextKey;
        this.warmReuse = warmReuse;
        this.elapsedMillis = Math.max(0, elapsedMillis);
        Vector<String> keys = new Vector<String>();
        for (GeneratedDiagnosticSolvabilityEvidence proof : orderedEvidence)
            keys.add(proof.getHypothesisKey());
        GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(owner.getFaultCandidates(), keys);
    }

    void validateForAdmission(CirSim sim, GeneratedBoardInstance expectedOwner,
            GeneratedChallengeController expectedController, Object expectedAttempt) {
        if (sim == null || expectedAttempt == null || attempt != expectedAttempt ||
                owner != expectedOwner || controller != expectedController ||
                sim.getGeneratedBoardInstance() != owner || sim.getGeneratedChallengeController() != controller ||
                owner.getDiagnosticProvider() != provider || owner.getDiagnosticSolvabilityContract() != contract ||
                !providerId.equals(provider.getProviderId()) ||
                !programIdentity.equals(provider.getObservationProgram().canonical()))
            throw new IllegalStateException("Stale or foreign production diagnostic proof");
        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        partitionPlan.validateAgainst(owner.getFaultCandidates(), provider.getObservationProgram(), evidence);
    }
    Vector<GeneratedDiagnosticSolvabilityEvidence> getEvidence() {
        return new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
    }
    long getElapsedMillis() { return elapsedMillis; }
    String getProviderId() { return providerId; }
    String getProgramIdentity() { return programIdentity; }
    GeneratedDiagnosticPartitionPlan getPartitionPlan() { return partitionPlan; }
    GeneratedDiagnosticContextKey getContextKey() { return contextKey; }
    boolean isWarmReuseForDeveloperVerification() { return warmReuse; }
    void requireContextKey(GeneratedDiagnosticContextKey expected) {
        if (expected == null || contextKey == null || !contextKey.equals(expected))
            throw new IllegalStateException("Diagnostic proof receipt has stale or foreign context");
    }
    void requireAssessmentOwner(GeneratedBoardInstance expected) {
        if (owner != expected || expected == null || expected.getDiagnosticProvider() != provider ||
                expected.getDiagnosticSolvabilityContract() != contract ||
                !programIdentity.equals(provider.getObservationProgram().canonical()))
            throw new IllegalArgumentException("Difficulty proof belongs to a different owner");
    }
}
