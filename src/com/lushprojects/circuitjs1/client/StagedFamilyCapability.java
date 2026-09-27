package com.lushprojects.circuitjs1.client;

/** Family-owned staged resolution, admission, construction and provenance. */
interface StagedFamilyCapability {
    String familyId();
    String displayName();
    /** Public route alias, or null when the provider is private-only. */
    String routeId();
    DifficultyProfile candidateProfile();
    GenerationExecutionPolicy executionPolicy();
    String physicalAdmissionIdentity();
    boolean supportsPrivateQualification();

    Plan resolve(long seed);
    String canonicalIdentity(Plan plan);
    String canonicalRequest(Plan plan, RequestIdentity request);
    ConstructionSession beginConstruction(Plan plan);

    interface Plan { }

    interface ConstructionSession {
        boolean advance();
        GenerationRequest.Construction result();
    }

    /** Request facts needed for a provider-owned exact canonical identity. */
    final class RequestIdentity {
        final String familyId;
        final long rootSeed;
        final String descriptorCanonical;
        final boolean privateQualification;
        final boolean quickPlay;
        final boolean candidateSearch;
        final DifficultyProfile difficulty;
        final String executionPolicyCanonical;
        final String physicalAdmissionIdentity;
        final String familyExecutionCanonical;
        final String layoutVersion;
        final String replayEpoch;
        final String admissionVersion;
        final String difficultyVersion;
        final String assessmentVersion;

        RequestIdentity(String familyId, long rootSeed, String descriptorCanonical,
                boolean privateQualification, boolean quickPlay, boolean candidateSearch,
                DifficultyProfile difficulty, String executionPolicyCanonical,
                String physicalAdmissionIdentity, String familyExecutionCanonical,
                String layoutVersion, String replayEpoch, String admissionVersion,
                String difficultyVersion, String assessmentVersion) {
            if (familyId == null || familyId.length() == 0 || descriptorCanonical == null ||
                    executionPolicyCanonical == null || physicalAdmissionIdentity == null ||
                    familyExecutionCanonical == null || layoutVersion == null || replayEpoch == null ||
                    admissionVersion == null || difficultyVersion == null || assessmentVersion == null)
                throw new IllegalArgumentException("Incomplete staged-family request identity");
            this.familyId = familyId;
            this.rootSeed = rootSeed;
            this.descriptorCanonical = descriptorCanonical;
            this.privateQualification = privateQualification;
            this.quickPlay = quickPlay;
            this.candidateSearch = candidateSearch;
            this.difficulty = difficulty;
            this.executionPolicyCanonical = executionPolicyCanonical;
            this.physicalAdmissionIdentity = physicalAdmissionIdentity;
            this.familyExecutionCanonical = familyExecutionCanonical;
            this.layoutVersion = layoutVersion;
            this.replayEpoch = replayEpoch;
            this.admissionVersion = admissionVersion;
            this.difficultyVersion = difficultyVersion;
            this.assessmentVersion = assessmentVersion;
        }
    }
}
