package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * One-request authority for isolated normal-player qualification. This scope
 * bypasses only the disabled-family catalog projection; it does not provide
 * an alternate generation, physical, proof, or publication path.
 */
final class IsolatedGenerationQualification {
    private CirSim sim;
    private GenerationCoordinator coordinator;
    private GenerationRequest request;
    private GenerationJob boundJob;
    private boolean closed;
    private int startChecks;
    private int publicationChecks;
    private final Vector<CandidateEvidence> candidates = new Vector<CandidateEvidence>();

    static IsolatedGenerationQualification attach(CirSim sim,
            GenerationCoordinator coordinator, GenerationRequest request) {
        if (sim == null || coordinator == null || request == null)
            throw new IllegalArgumentException("Isolated qualification requires an exact simulator, coordinator, and request");
        validateNormalCandidateSearch(request);
        if (PlayerFamilyCatalog.isNormalPlayerEnabled(request.getFamilyId()))
            throw new IllegalArgumentException("Isolated qualification applies only to a disabled player family");
        if (sim.generationCoordinator != coordinator || !coordinator.isFreshForIsolatedQualification())
            throw new IllegalStateException("Isolated qualification requires the simulator's fresh idle coordinator");
        IsolatedGenerationQualification result =
            new IsolatedGenerationQualification(sim, coordinator, request);
        coordinator.attachIsolatedQualification(result);
        return result;
    }

    private IsolatedGenerationQualification(CirSim sim,
            GenerationCoordinator coordinator, GenerationRequest request) {
        this.sim = sim;
        this.coordinator = coordinator;
        this.request = request;
    }

    private static void validateNormalCandidateSearch(GenerationRequest request) {
        GenerationExecutionPolicy policy = request.getExecutionPolicy();
        policy.requireRequest(request);
        if (request.isPrivateDiagnosticQualification() || request.isComposition() ||
                request.isQuickPlay() || !request.isCandidateSearch() ||
                request.candidateCount() != QuickPlayAdmission.MAX_CANDIDATES ||
                request.getDifficulty() == null ||
                policy != GenerationExecutionPolicy.NORMAL_MEDIUM)
            throw new IllegalArgumentException(
                "Isolated qualification requires a normal medium player candidate-search request");
    }

    void close() {
        if (closed) return;
        GenerationCoordinator previousCoordinator = coordinator;
        closed = true;
        if (previousCoordinator != null) previousCoordinator.detachIsolatedQualification(this);
        sim = null;
        coordinator = null;
        request = null;
        boundJob = null;
    }

    boolean isClosed() { return closed; }
    int getStartChecks() { return startChecks; }
    int getPublicationChecks() { return publicationChecks; }
    GenerationJob getBoundJob() { return boundJob; }
    Vector<CandidateEvidence> getCandidates() {
        return new Vector<CandidateEvidence>(candidates);
    }

    void requireStart(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest) {
        startChecks++;
        if (!isAttachedTo(callerCoordinator, callerSim, candidateRequest) || boundJob != null ||
                callerCoordinator.getJob() != null || callerCoordinator.isRunning() ||
                callerCoordinator.isAdvancing() || !callerCoordinator.isFreshForIsolatedQualification())
            throw new IllegalStateException("Isolated generation authority is stale, reused, or bound to another request");
        if (PlayerFamilyCatalog.isNormalPlayerEnabled(candidateRequest.getFamilyId()))
            throw new IllegalStateException("Isolated authority cannot replace the normal family catalog gate");
    }

    void bindJob(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job) {
        if (job == null || boundJob != null ||
                !isAttachedTo(callerCoordinator, callerSim, candidateRequest) ||
                callerCoordinator.getJob() != job)
            throw new IllegalStateException("Isolated generation job binding is invalid or reused");
        boundJob = job;
    }

    void requirePublication(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job) {
        publicationChecks++;
        if (!isCurrentForJob(callerCoordinator, callerSim, request, job) ||
                !matchesCandidate(candidateRequest, job) ||
                PlayerFamilyCatalog.isNormalPlayerEnabled(candidateRequest.getFamilyId()))
            throw new GenerationJob.Stale("Isolated qualification scope changed before publication");
    }

    boolean isCurrentForJob(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest selectionRequest, GenerationJob job) {
        return isAttachedTo(callerCoordinator, callerSim, request) && boundJob == job &&
            callerCoordinator.getJob() == job && selectionRequest == request &&
            !callerSim.troubleshootDebug;
    }

    void beginCandidate(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job, int ordinal, String seed) {
        requireCurrentCandidateScope(callerCoordinator, callerSim, candidateRequest, job);
        if (ordinal < 0 || seed == null || find(ordinal) >= 0)
            throw new IllegalStateException("Isolated candidate evidence identity is invalid or repeated");
        candidates.add(new CandidateEvidence(ordinal, seed, -1, false, false,
            null, false, false));
    }

    void recordPackages(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job, int ordinal, int packages) {
        requireCurrentCandidateScope(callerCoordinator, callerSim, candidateRequest, job);
        if (packages < 0) throw new IllegalArgumentException("Invalid constructed package count");
        update(ordinal, packages, null, null, null, null, null);
    }

    void recordPhysical(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job, int ordinal) {
        requireCurrentCandidateScope(callerCoordinator, callerSim, candidateRequest, job);
        update(ordinal, null, Boolean.TRUE, null, null, null, null);
    }

    void recordDiagnostic(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job, int ordinal, String difficulty) {
        requireCurrentCandidateScope(callerCoordinator, callerSim, candidateRequest, job);
        update(ordinal, null, null, Boolean.TRUE, difficulty, null, null);
    }

    void recordPublished(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job, int ordinal) {
        if (!isCurrentForJob(callerCoordinator, callerSim, request, job) ||
                !matchesCandidate(candidateRequest, job) || ordinal != job.getCandidateIndex())
            throw new GenerationJob.Stale("Isolated qualification scope changed after publication");
        update(ordinal, null, null, null, null, Boolean.TRUE, null);
    }

    /** Called only after candidate graph cleanup has returned successfully. */
    void recordAborted(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest, GenerationJob job, int ordinal) {
        if (ordinal < 0) return;
        int index = find(ordinal);
        if (index < 0 || (boundJob != null && boundJob != job) ||
                (coordinator != null && callerCoordinator != coordinator) ||
                (sim != null && callerSim != sim) ||
                (request != null && !matchesCandidate(candidateRequest, job)) ||
                (request == null && candidateRequest != null &&
                    !candidates.get(index).seed.equals(
                        Long.toString(candidateRequest.getDescriptor().getRootSeed()))))
            throw new IllegalStateException("Isolated candidate cleanup does not match its bound job");
        update(ordinal, null, null, null, null, null, Boolean.TRUE);
    }

    private void requireCurrentCandidateScope(GenerationCoordinator callerCoordinator,
            CirSim callerSim, GenerationRequest candidateRequest, GenerationJob job) {
        if (!isCurrentForJob(callerCoordinator, callerSim, request, job) ||
                candidateRequest == null || candidateRequest.isCandidateSearch() ||
                !matchesCandidate(candidateRequest, job))
            throw new GenerationJob.Stale("Isolated candidate scope changed during generation");
    }

    private boolean matchesCandidate(GenerationRequest candidateRequest, GenerationJob job) {
        if (closed || request == null || candidateRequest == null || job == null ||
                job.getCandidateIndex() < 0 || job.getCandidateIndex() >= request.candidateCount())
            return false;
        return request.candidate(job.getCandidateIndex()).canonical().equals(candidateRequest.canonical());
    }

    private boolean isAttachedTo(GenerationCoordinator callerCoordinator, CirSim callerSim,
            GenerationRequest candidateRequest) {
        return !closed && coordinator != null && sim != null && request != null &&
            callerCoordinator == coordinator && callerSim == sim && candidateRequest == request &&
            callerSim.generationCoordinator == callerCoordinator &&
            callerCoordinator.isAttachedIsolatedQualification(this) &&
            !callerSim.troubleshootDebug &&
            // Standard Staged.prepare/enterStep owns this flag during normal
            // generation too. Fresh attachment/start still require it false.
            (!callerSim.developerVerifierRunning || boundJob != null);
    }

    private int find(int ordinal) {
        for (int i = 0; i < candidates.size(); i++)
            if (candidates.get(i).ordinal == ordinal) return i;
        return -1;
    }

    private void update(int ordinal, Integer packages, Boolean physical,
            Boolean diagnostic, String difficulty, Boolean published, Boolean aborted) {
        int index = find(ordinal);
        if (index < 0) throw new IllegalStateException("Missing isolated candidate evidence");
        CandidateEvidence prior = candidates.get(index);
        candidates.set(index, new CandidateEvidence(prior.ordinal, prior.seed,
            packages == null ? prior.packages : packages.intValue(),
            physical == null ? prior.physical : physical.booleanValue(),
            diagnostic == null ? prior.diagnostic : diagnostic.booleanValue(),
            difficulty == null ? prior.difficulty : difficulty,
            published == null ? prior.published : published.booleanValue(),
            aborted == null ? prior.aborted : aborted.booleanValue()));
    }

    /** Immutable per-candidate scalar evidence; contains no simulation owner. */
    static final class CandidateEvidence {
        final int ordinal;
        final String seed;
        final int packages;
        final boolean physical;
        final boolean diagnostic;
        final String difficulty;
        final boolean published;
        final boolean aborted;

        private CandidateEvidence(int ordinal, String seed, int packages, boolean physical,
                boolean diagnostic, String difficulty, boolean published, boolean aborted) {
            this.ordinal = ordinal;
            this.seed = seed;
            this.packages = packages;
            this.physical = physical;
            this.diagnostic = diagnostic;
            this.difficulty = difficulty;
            this.published = published;
            this.aborted = aborted;
        }
    }
}
