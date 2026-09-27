package com.lushprojects.circuitjs1.client;

/** Registered family owner for the bounded multi-rail board. */
final class Rb30PlayerFamilyCapability implements StagedFamilyCapability {
    public String familyId() { return Rb30Plan.FAMILY_ID; }
    public String displayName() { return "Multi-rail control board"; }
    public String routeId() { return "multirail-control"; }
    public DifficultyProfile candidateProfile() { return DifficultyProfile.MEDIUM; }
    public GenerationExecutionPolicy executionPolicy() { return GenerationExecutionPolicy.NORMAL_MEDIUM; }
    public String physicalAdmissionIdentity() { return MediumBoardNormalAdmission.IDENTITY; }
    public boolean supportsPrivateQualification() { return true; }

    public Plan resolve(long seed) {
        return new ResolvedPlan(Rb30Plan.resolve(seed));
    }

    public String canonicalIdentity(Plan plan) {
        return resolved(plan).plan.canonical();
    }

    public String canonicalRequest(Plan plan, RequestIdentity request) {
        Rb30Plan resolved = resolved(plan).plan;
        if (request.privateQualification) {
            return "tsj-generation-request/4;native;qualification-only;" +
                request.descriptorCanonical + ";quickPlay=false;layout=" +
                request.layoutVersion + ";qualification=true;explicitCompletion=true;planEpoch=" +
                Rb30Plan.PLAN_VERSION + ";plan=" + resolved.canonical() +
                ";physicalPolicy=" + MediumBoardPhysicalPolicy.identity() +
                ";physicalAdmission=" + MediumBoardNormalAdmission.IDENTITY;
        }
        if (request.difficulty == null)
            throw new IllegalArgumentException("Staged normal-family request requires a difficulty profile");
        return "tsj-generation-request/5;normal-medium@1;native;" +
            request.descriptorCanonical + ";replay=" + request.replayEpoch + "/" +
            request.difficulty.name() + "/" + request.familyId + "/" +
            Long.toString(request.rootSeed) + ";quickPlay=" + request.quickPlay +
            ";layout=" + request.layoutVersion + ";planEpoch=" + Rb30Plan.PLAN_VERSION +
            ";plan=" + resolved.canonical() + ";route=" + MediumBoardPhysicalPolicy.identity() +
            ";physicalAdmission=" + request.physicalAdmissionIdentity +
            ";executionPolicy=" + request.executionPolicyCanonical +
            ";familyExecution=" + request.familyExecutionCanonical +
            ";admission=" + request.admissionVersion + ";search=" + request.candidateSearch +
            ";difficulty=" + request.difficulty.name() + "@" + request.difficultyVersion +
            ";assessment=" + request.assessmentVersion;
    }

    public ConstructionSession beginConstruction(Plan plan) {
        final Rb30Plan resolved = resolved(plan).plan;
        final SeededPcbLayoutGenerator.Session routing = new SeededPcbLayoutGenerator().begin(
            resolved.board(), resolved.layoutSeed, resolved.routingSeed, null);
        return new ConstructionSession() {
            private GenerationRequest.Construction result;

            public boolean advance() {
                if (result != null) throw new IllegalStateException("Construction already completed");
                if (!routing.advance()) return false;
                MediumBoardPhysicalPolicy.Result route = routing.mediumResult();
                if (route == null || !route.accepted())
                    throw new IllegalStateException("Family construction requires its accepted physical route");
                result = new Rb30Generator().constructFromAcceptedRoute(resolved, route);
                return true;
            }

            public GenerationRequest.Construction result() {
                if (result == null) throw new IllegalStateException("Construction is incomplete");
                return result;
            }
        };
    }

    private static ResolvedPlan resolved(Plan plan) {
        if (!(plan instanceof ResolvedPlan))
            throw new IllegalArgumentException("Resolved plan belongs to a different family capability");
        return (ResolvedPlan) plan;
    }

    private static final class ResolvedPlan implements Plan {
        final Rb30Plan plan;
        ResolvedPlan(Rb30Plan plan) {
            if (plan == null) throw new IllegalArgumentException("Missing family plan");
            this.plan = plan;
        }
    }
}
