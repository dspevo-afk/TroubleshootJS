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
    private final Vector<GeneratedDiagnosticPlan> plans;

    private GeneratedDiagnosticSolvabilityContract(String routeId, String familyId,
            String topologyVariantId, long seed, int admittedCandidateCount,
            int admittedPhysicalOwnerCount, Vector<GeneratedDiagnosticPlan> plans) {
        this.routeId = routeId;
        this.familyId = familyId;
        this.topologyVariantId = topologyVariantId;
        this.seed = seed;
        this.admittedCandidateCount = admittedCandidateCount;
        this.admittedPhysicalOwnerCount = admittedPhysicalOwnerCount;
        this.plans = new Vector<GeneratedDiagnosticPlan>(plans);
    }

    static GeneratedDiagnosticSolvabilityContract forGeneratedBoard(String familyId,
            String topologyVariantId, long seed, Vector<GeneratedFaultCandidate> candidates) {
        if (familyId == null || topologyVariantId == null || candidates == null)
            throw new IllegalArgumentException("Incomplete diagnostic solvability contract");
        Vector<GeneratedDiagnosticPlan> plans = GeneratedDiagnosticPlanCatalog.forFamily(familyId);
        return new GeneratedDiagnosticSolvabilityContract(familyId + "/" + topologyVariantId,
            familyId, topologyVariantId, seed,
            GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(candidates),
            GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(candidates), plans);
    }

    String getRouteId() { return routeId; }
    String getFamilyId() { return familyId; }
    String getTopologyVariantId() { return topologyVariantId; }
    long getSeed() { return seed; }
    int getAdmittedCandidateCount() { return admittedCandidateCount; }
    int getAdmittedPhysicalOwnerCount() { return admittedPhysicalOwnerCount; }
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
        if (actualCandidates != admittedCandidateCount || actualOwners != admittedPhysicalOwnerCount)
            throw new IllegalArgumentException("Diagnostic solvability candidate metrics changed");
        if (actualCandidates == 0 || plans.isEmpty())
            throw new IllegalArgumentException("Generated challenge has no diagnostic solvability proof");
        for (GeneratedDiagnosticPlan plan : plans)
            GeneratedDiagnosticSolvabilityAdmission.validatePlan(plan);
    }
}
