package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable v1 contract carried by every generated board challenge. */
final class GeneratedDiagnosticSolvabilityContract {
    static final int VERSION = 1;

    private final String routeId;
    private final String familyId;
    private final String topologyVariantId;
    private final long seed;
    private final int admittedCandidateCount;
    private final int admittedPhysicalOwnerCount;
    private final GeneratedDiagnosticOwnerDiversity ownerDiversity;
    private final Vector<GeneratedDiagnosticPlan> plans;

    private GeneratedDiagnosticSolvabilityContract(String routeId, String familyId,
            String topologyVariantId, long seed, int admittedCandidateCount,
            int admittedPhysicalOwnerCount, GeneratedDiagnosticOwnerDiversity ownerDiversity,
            Vector<GeneratedDiagnosticPlan> plans) {
        this.routeId = routeId;
        this.familyId = familyId;
        this.topologyVariantId = topologyVariantId;
        this.seed = seed;
        this.admittedCandidateCount = admittedCandidateCount;
        this.admittedPhysicalOwnerCount = admittedPhysicalOwnerCount;
        this.ownerDiversity = ownerDiversity;
        this.plans = new Vector<GeneratedDiagnosticPlan>(plans);
    }

    static GeneratedDiagnosticSolvabilityContract forGeneratedBoard(String familyId,
            String topologyVariantId, long seed, Vector<GeneratedFaultCandidate> candidates) {
        if (familyId == null || topologyVariantId == null || candidates == null)
            throw new IllegalArgumentException("Incomplete diagnostic solvability contract");
        Vector<GeneratedDiagnosticPlan> plans = GeneratedDiagnosticPlanCatalog.forFamily(familyId);
        GeneratedDiagnosticOwnerDiversity ownerDiversity =
            GeneratedDiagnosticSolvabilityAdmission.getOwnerDiversity(candidates);
        return new GeneratedDiagnosticSolvabilityContract(familyId + "/" + topologyVariantId,
            familyId, topologyVariantId, seed,
            GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(candidates),
            GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(candidates),
            ownerDiversity, plans);
    }

    String getRouteId() { return routeId; }
    String getFamilyId() { return familyId; }
    String getTopologyVariantId() { return topologyVariantId; }
    long getSeed() { return seed; }
    int getAdmittedCandidateCount() { return admittedCandidateCount; }
    int getAdmittedPhysicalOwnerCount() { return admittedPhysicalOwnerCount; }
    GeneratedDiagnosticOwnerDiversity getOwnerDiversity() { return ownerDiversity; }
    Vector<GeneratedDiagnosticPlan> getPlans() {
        return new Vector<GeneratedDiagnosticPlan>(plans);
    }

    void validate(GeneratedBoardInstance instance) {
        if (instance == null || !familyId.equals(instance.getCircuitFamilyId()) ||
                !topologyVariantId.equals(instance.getTopologyVariantId()) ||
                seed != instance.getSeed())
            throw new IllegalArgumentException("Diagnostic solvability contract is not owned by board");
        int actualCandidates = GeneratedDiagnosticSolvabilityAdmission
            .getAdmittedCandidateCount(instance.getFaultCandidates());
        int actualOwners = GeneratedDiagnosticSolvabilityAdmission
            .getPhysicalOwnerCount(instance.getFaultCandidates());
        GeneratedDiagnosticOwnerDiversity actualDiversity =
            GeneratedDiagnosticSolvabilityAdmission.getOwnerDiversity(
                instance.getFaultCandidates());
        if (actualCandidates != admittedCandidateCount || actualOwners != admittedPhysicalOwnerCount ||
                actualDiversity != ownerDiversity)
            throw new IllegalArgumentException("Diagnostic solvability candidate metrics changed");
        if (actualCandidates == 0 || plans.isEmpty())
            throw new IllegalArgumentException("Generated challenge has no diagnostic solvability proof");
        for (GeneratedDiagnosticPlan plan : plans)
            GeneratedDiagnosticSolvabilityAdmission.validatePlan(plan);
    }
}
