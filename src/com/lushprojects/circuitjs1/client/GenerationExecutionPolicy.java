package com.lushprojects.circuitjs1.client;

/** Immutable execution allowance paired with a qualified physical contract.
 * The whole-job allowance is shared by every candidate; it never relaxes
 * routing, per-unit work, diagnostic proof or physical acceptance. */
final class GenerationExecutionPolicy {
    static final GenerationExecutionPolicy SMALL_BOARD = new GenerationExecutionPolicy(
        "SMALL_BOARD_EXECUTION@1", SupportedEnvelope.current().identity(), GenerationCoordinator.MAX_JOB_MILLIS);
    static final GenerationExecutionPolicy NORMAL_MEDIUM = new GenerationExecutionPolicy(
        "NORMAL_MEDIUM_EXECUTION@1", MediumBoardNormalAdmission.IDENTITY, 300000L);

    private final String identity;
    private final String physicalIdentity;
    final long maximumJobMillis;

    private GenerationExecutionPolicy(String identity, String physicalIdentity, long maximumJobMillis) {
        this.identity = identity;
        this.physicalIdentity = physicalIdentity;
        this.maximumJobMillis = maximumJobMillis;
    }

    String identity() { return identity; }

    String canonical() {
        return identity + ";physical=" + physicalIdentity + ";maxJobMillis=" + maximumJobMillis +
            ";maxJobSteps=" + GenerationCoordinator.MAX_JOB_STEPS +
            ";maxUnitMillis=" + GenerationCoordinator.MAX_STEP_MILLIS +
            ";candidateBudget=shared;clock=foreground";
    }

    /** Validate immutable request provenance before canceling another job. */
    void requireRequest(GenerationRequest request) {
        if ((this != SMALL_BOARD && this != NORMAL_MEDIUM) || request == null ||
                request.isPrivateDiagnosticQualification() || request.getExecutionPolicy() != this ||
                !physicalIdentity.equals(request.getRequiredPhysicalAdmissionIdentity()))
            throw new IllegalArgumentException("Generation execution contract does not match the normal request");
    }

    /** A declared allowance cannot substitute for the actual routed physical proof. */
    void requireOwner(GeneratedBoardInstance owner) {
        if (owner == null)
            throw new IllegalArgumentException("Generation execution contract requires a physical owner");
        GeneratedPhysicalAdmission admission = owner.getPhysicalAdmission();
        String actual = admission == null ? SupportedEnvelope.current().identity() : admission.identity();
        if (!physicalIdentity.equals(actual))
            throw new IllegalArgumentException("Constructed physical admission differs from the execution contract");
        owner.requireNormalPhysicalAdmission();
    }
}
