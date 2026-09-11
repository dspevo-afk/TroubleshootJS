package com.lushprojects.circuitjs1.client;

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
    private final long elapsedMillis;

    GeneratedDiagnosticProofReceipt(GeneratedBoardInstance owner,
            GeneratedChallengeController controller, Object attempt,
            GeneratedDiagnosticProgram program, Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            long elapsedMillis) {
        if (owner == null || controller == null || attempt == null || program == null ||
                evidence == null || evidence.isEmpty() || owner.isDeveloperOnlyFaultRoute() ||
                owner.getDiagnosticProvider() == null)
            throw new IllegalArgumentException("Cannot issue an empty or developer-fixture production proof");
        this.owner = owner; this.controller = controller; this.attempt = attempt;
        this.provider = owner.getDiagnosticProvider();
        this.contract = owner.getDiagnosticSolvabilityContract();
        this.programIdentity = program.canonical();
        this.providerId = provider.getProviderId();
        this.evidence = GeneratedDiagnosticEquivalence.classify(evidence, owner.getCircuitFamilyId(), owner.getSeed());
        this.elapsedMillis = Math.max(0, elapsedMillis);
        Vector<String> keys = new Vector<String>();
        for (GeneratedDiagnosticSolvabilityEvidence proof : evidence) keys.add(proof.getHypothesisKey());
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
        contract.validate(owner);
    }
    Vector<GeneratedDiagnosticSolvabilityEvidence> getEvidence() {
        return new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
    }
    long getElapsedMillis() { return elapsedMillis; }
    String getProviderId() { return providerId; }
    String getProgramIdentity() { return programIdentity; }
}
