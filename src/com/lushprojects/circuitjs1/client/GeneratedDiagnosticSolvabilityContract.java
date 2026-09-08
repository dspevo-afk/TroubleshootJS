package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import java.util.Collections;

/** Immutable v1 contract carried by every generated board challenge. */
final class GeneratedDiagnosticSolvabilityContract {
    static final int VERSION = 1;

    private enum AdmissionMode {
        NORMAL,
        DEVELOPER_FIXTURE
    }

    private final String routeId;
    private final String familyId;
    private final String topologyVariantId;
    private final long seed;
    private final int admittedCandidateCount;
    private final int admittedPhysicalOwnerCount;
    private final GeneratedDiagnosticOwnerDiversity ownerDiversity;
    private final Vector<String> hypothesisKeys;
    private final Vector<GeneratedDiagnosticPlan> plans;
    private final AdmissionMode admissionMode;

    private GeneratedDiagnosticSolvabilityContract(String routeId, String familyId,
            String topologyVariantId, long seed, int admittedCandidateCount,
            int admittedPhysicalOwnerCount, GeneratedDiagnosticOwnerDiversity ownerDiversity,
            Vector<String> hypothesisKeys, Vector<GeneratedDiagnosticPlan> plans,
            AdmissionMode admissionMode) {
        this.routeId = routeId;
        this.familyId = familyId;
        this.topologyVariantId = topologyVariantId;
        this.seed = seed;
        this.admittedCandidateCount = admittedCandidateCount;
        this.admittedPhysicalOwnerCount = admittedPhysicalOwnerCount;
        this.ownerDiversity = ownerDiversity;
        this.hypothesisKeys = new Vector<String>(hypothesisKeys);
        Collections.sort(this.hypothesisKeys);
        this.plans = new Vector<GeneratedDiagnosticPlan>(plans);
        this.admissionMode = admissionMode;
    }

    static GeneratedDiagnosticSolvabilityContract forGeneratedBoard(String familyId,
            String topologyVariantId, long seed, Vector<GeneratedFaultCandidate> candidates) {
        if (familyId == null || topologyVariantId == null || candidates == null)
            throw new IllegalArgumentException("Incomplete diagnostic solvability contract");
        Vector<GeneratedDiagnosticPlan> plans = GeneratedDiagnosticPlanCatalog.forFamily(familyId);
        Vector<String> hypothesisKeys = GeneratedDiagnosticSolvabilityAdmission
            .getHypothesisKeys(candidates);
        GeneratedDiagnosticOwnerDiversity ownerDiversity =
            GeneratedDiagnosticSolvabilityAdmission.getOwnerDiversity(candidates);
        return new GeneratedDiagnosticSolvabilityContract(familyId + "/" + topologyVariantId,
            familyId, topologyVariantId, seed,
            hypothesisKeys.size(),
            GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(candidates),
            ownerDiversity, hypothesisKeys, plans, AdmissionMode.NORMAL);
    }

    /**
     * Metadata-only identity/ownership envelope for an explicit developer
     * composition fixture.  It deliberately has no Task 41 diagnostic plan.
     */
    static GeneratedDiagnosticSolvabilityContract forDeveloperFixture(String familyId,
            String topologyVariantId, long seed, Vector<GeneratedFaultCandidate> candidates) {
        if (familyId == null || topologyVariantId == null || candidates == null)
            throw new IllegalArgumentException("Incomplete developer diagnostic fixture");
        Vector<String> hypothesisKeys = GeneratedDiagnosticSolvabilityAdmission
            .getHypothesisKeys(candidates);
        GeneratedDiagnosticOwnerDiversity ownerDiversity =
            GeneratedDiagnosticSolvabilityAdmission.getOwnerDiversity(candidates);
        return new GeneratedDiagnosticSolvabilityContract(familyId + "/" + topologyVariantId,
            familyId, topologyVariantId, seed,
            hypothesisKeys.size(),
            GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(candidates),
            ownerDiversity, hypothesisKeys, new Vector<GeneratedDiagnosticPlan>(),
            AdmissionMode.DEVELOPER_FIXTURE);
    }

    String getRouteId() { return routeId; }
    String getFamilyId() { return familyId; }
    String getTopologyVariantId() { return topologyVariantId; }
    long getSeed() { return seed; }
    int getAdmittedCandidateCount() { return admittedCandidateCount; }
    int getAdmittedPhysicalOwnerCount() { return admittedPhysicalOwnerCount; }
    GeneratedDiagnosticOwnerDiversity getOwnerDiversity() { return ownerDiversity; }
    Vector<String> getHypothesisKeys() {
        return new Vector<String>(hypothesisKeys);
    }
    boolean isDeveloperFixture() { return admissionMode == AdmissionMode.DEVELOPER_FIXTURE; }
    Vector<GeneratedDiagnosticPlan> getPlans() {
        return new Vector<GeneratedDiagnosticPlan>(plans);
    }

    void validate(GeneratedBoardInstance instance) {
        validateIdentityAndMetrics(instance);
        if (isDeveloperFixture())
            throw new IllegalArgumentException(
                "Developer diagnostic fixture is not a Task 41 solvability contract");
        if (GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(
                instance.getFaultCandidates()) == 0 || plans.isEmpty())
            throw new IllegalArgumentException("Generated challenge has no diagnostic solvability proof");
        for (GeneratedDiagnosticPlan plan : plans)
            GeneratedDiagnosticSolvabilityAdmission.validatePlan(plan);
    }

    /**
     * Validates only the structural attachment needed by a developer fixture.
     * This method must remain distinct from {@link #validate}, which is the
     * normal Task 41 admission contract and rejects fixture mode.
     */
    void validateDeveloperFixture(GeneratedBoardInstance instance) {
        if (!isDeveloperFixture())
            throw new IllegalArgumentException("Normal diagnostic contract is not a developer fixture");
        if (instance == null || !instance.isDeveloperOnlyFaultRoute())
            throw new IllegalArgumentException(
                "Developer diagnostic fixture requires a developer-only fault route");
        if (instance.getDiagnosticSolvabilityContract() != this)
            throw new IllegalArgumentException("Developer diagnostic fixture is not owned by board");
        if (!plans.isEmpty())
            throw new IllegalArgumentException("Developer diagnostic fixture cannot contain Task 41 plans");
        validateIdentityAndMetrics(instance);
    }

    private void validateIdentityAndMetrics(GeneratedBoardInstance instance) {
        if (instance == null || !familyId.equals(instance.getCircuitFamilyId()) ||
                !topologyVariantId.equals(instance.getTopologyVariantId()) ||
                seed != instance.getSeed())
            throw new IllegalArgumentException("Diagnostic solvability contract is not owned by board");
        int actualCandidates = GeneratedDiagnosticSolvabilityAdmission
            .getAdmittedCandidateCount(instance.getFaultCandidates());
        int actualOwners = GeneratedDiagnosticSolvabilityAdmission
            .getPhysicalOwnerCount(instance.getFaultCandidates());
        Vector<String> actualHypothesisKeys = GeneratedDiagnosticSolvabilityAdmission
            .getHypothesisKeys(instance.getFaultCandidates());
        GeneratedDiagnosticOwnerDiversity actualDiversity =
            GeneratedDiagnosticSolvabilityAdmission.getOwnerDiversity(
                instance.getFaultCandidates());
        if (actualCandidates != admittedCandidateCount || actualOwners != admittedPhysicalOwnerCount ||
                actualDiversity != ownerDiversity || !actualHypothesisKeys.equals(hypothesisKeys))
            throw new IllegalArgumentException("Diagnostic solvability candidate metrics changed");
    }
}
