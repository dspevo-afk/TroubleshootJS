package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.Timer;

/** One serial generation owner per CirSim; browser turns never race candidates. */
final class GenerationCoordinator {
    // Temporal profiles yield between their existing completed solver calls.
    // Pilot evidence rerates this whole-job guard independently from A01's
    // 30s aggregate corpus; the frozen benchmark retains 5s per full attempt.
    static final long MAX_JOB_MILLIS = 90000;
    static final long MAX_STEP_MILLIS = 5000;
    // Current two-channel proof has 4 hypotheses and 132 samples in each.
    // Count its individual operations, not a whole hypothesis as one unit.
    static final int MAX_JOB_STEPS = 640;
    private static final int MAX_UNITS_PER_TURN = 16;
    private static final long TURN_TARGET_MILLIS = 8;
    interface Completion { void complete(GenerationJob job, GeneratedBoardInstance published); }
    private final CirSim sim;
    private final GenerationRequest.PlanCache plans = new GenerationRequest.PlanCache();
    private final QuickPlayBoardHistory recentBoards = new QuickPlayBoardHistory();
    private GenerationJob job;
    private Services services;
    private Timer continuation, watchdog;
    private long startedAt, lastProgressAt;
    private int expectedProofUnits;
    private ForegroundGenerationClock foregroundClock;
    private boolean foregroundBudget;
    private boolean advancing;
    private int yields;
    private long cancelledAt, cancellationLatency, maxAdvanceMillis;
    private GeneratedDiagnosticProofService.CleanupAudit lastCleanupAudit;
    enum TurnFailure { ENTER, PAUSE }
    private static TurnFailure injectedTurnFailure;
    static void setTurnFailureForDeveloperVerification(TurnFailure failure) {
        injectedTurnFailure = failure;
    }

    GenerationCoordinator(CirSim sim) {
        if (sim == null) throw new IllegalArgumentException("Missing generation simulator");
        this.sim = sim;
        if (com.google.gwt.core.client.GWT.isClient()) installVisibilityListener();
    }
    boolean isRunning() { return job != null && job.isRunning(); }
    boolean isBetweenSteps() { return isRunning() && !advancing; }
    boolean isAdvancing() { return advancing; }
    GenerationJob getJob() { return job; }
    int getYieldCount() { return yields; }
    int getProgressPercent() { return job == null ? 0 : GenerationProgress.percent(job.getStage(),
        job.getCandidateStageWorkCount(GenerationJob.Stage.HYPOTHESES), expectedProofUnits,
        job.getOutcome() == GenerationJob.Outcome.PASS); }
    int getCandidateCount() { return services == null ? 0 : services.selection.candidateCount(); }
    String getProgressLabel() {
        String label = GenerationProgress.label(job == null ? null : job.getStage());
        return getCandidateCount() > 1 ? "Candidate " + Math.max(1, job.getCandidateIndex() + 1) +
            " of " + getCandidateCount() + ": " + label : label;
    }
    long getProgressElapsedMillis() { return Math.max(0, System.currentTimeMillis() - startedAt); }
    long getProgressAgeMillis() { return Math.max(0, System.currentTimeMillis() - lastProgressAt); }

    boolean isProgressPaused() { return foregroundBudget && foregroundClock != null && foregroundClock.isPaused(); }
    long getProgressActiveMillis() { return foregroundClock == null ? 0 : foregroundClock.elapsedMillis(System.currentTimeMillis()); }
    int getPlanCacheHits() { return plans.getHits(); }
    int getPlanCacheMisses() { return plans.getMisses(); }
    long getCancellationLatencyMillis() { return cancellationLatency; }
    long getMaxAdvanceMillis() { return maxAdvanceMillis; }
    GeneratedDiagnosticProofService.CleanupAudit getCleanupAuditForDeveloperVerification() {
        return lastCleanupAudit;
    }
    boolean retainsSavedOwnersForDeveloperVerification() {
        return services != null && services.hasSavedOwners();
    }
    boolean retainsObservationForDeveloperVerification() {
        return services != null && services.proof != null &&
            services.proof.retainsObservationForDeveloperVerification();
    }
    void retryFailedCleanupForDeveloperVerification() {
        if (isRunning() || advancing || services == null || !services.hasSavedOwners())
            throw new IllegalStateException("No terminal generation cleanup to retry");
        services.abort();
        services.releaseSavedOwners();
    }

    /** Developer driver of the exact same stage implementation, with explicit turns. */
    void startForDeveloperVerification(GenerationRequest request) {
        start(request, null, true);
        if (watchdog != null) { watchdog.cancel(); watchdog = null; }
        if (continuation != null) { continuation.cancel(); continuation = null; }
    }
    void advanceForDeveloperVerification() {
        if (continuation != null) { continuation.cancel(); continuation = null; }
        advance();
    }

    void start(GenerationRequest request, Completion completion, boolean asynchronous) {
        if (request == null || advancing)
            throw new IllegalStateException("Generation cannot start inside an active stage");
        cancel();
        if (services != null && services.hasSavedOwners())
            throw new IllegalStateException("Previous generation has incomplete cleanup");
        if (!sim.isGeneratedRuntimeSettled() || sim.activeMeasurementOverlay)
            throw new IllegalStateException("Generation requires an idle settled owner");
        foregroundBudget = asynchronous && !sim.troubleshootDebug;
        foregroundClock = new ForegroundGenerationClock(System.currentTimeMillis(),
            foregroundBudget && pageHidden());
        services = new Services(request, completion);
        job = new GenerationJob(services, MAX_JOB_MILLIS, MAX_JOB_STEPS, MAX_STEP_MILLIS);
        yields = 0; cancelledAt = 0; cancellationLatency = 0; maxAdvanceMillis = 0;
        lastCleanupAudit = null;
        startedAt = lastProgressAt = System.currentTimeMillis(); expectedProofUnits = 0;
        sim.setGenerationBusy(true, "Preparing board...");
        try {
            if (asynchronous) { if (!isProgressPaused()) { schedule(); watch(job); } }
            else while (isRunning()) advance();
        } catch (Throwable failure) {
            job.failInfrastructure(failure);
            completed();
        }
    }

    private void visibilityChanged() {
        if (!foregroundBudget || !isRunning() || advancing) return;
        boolean hidden = pageHidden();
        foregroundClock.setPaused(System.currentTimeMillis(), hidden);
        if (hidden) {
            if (continuation != null) { continuation.cancel(); continuation = null; }
            if (watchdog != null) { watchdog.cancel(); watchdog = null; }
        } else {
            if (continuation == null) schedule();
            if (watchdog == null) watch(job);
        }
        if (sim.playerSessionController != null) sim.playerSessionController.refresh();
    }
    private static boolean pageHidden() {
        return com.google.gwt.core.client.GWT.isClient() && nativePageHidden();
    }
    private static native boolean nativePageHidden() /*-{ return !!$doc.hidden; }-*/;
    private native void installVisibilityListener() /*-{
        var owner = this;
        $doc.addEventListener('visibilitychange', $entry(function() {
            owner.@com.lushprojects.circuitjs1.client.GenerationCoordinator::visibilityChanged()();
        }));
    }-*/;

    private void watch(final GenerationJob watched) {
        if (watchdog != null) watchdog.cancel();
        watchdog = new Timer() {
            public void run() {
                if (job != watched || advancing || isProgressPaused()) return;
                if (!watched.isRunning()) { completed(); return; }
                try { watched.checkpoint(); }
                catch (Throwable failure) {
                    watched.advance(); // Complete the marked terminal job's exact abort.
                    completed(); return;
                }
                if (continuation == null) GenerationCoordinator.this.schedule();
            }
        };
        watchdog.scheduleRepeating(1000);
    }

    private void schedule() {
        final GenerationJob scheduledJob = job;
        continuation = new Timer() {
            public void run() {
                if (job != scheduledJob || !scheduledJob.isRunning()) return;
                continuation = null;
                if (foregroundBudget && pageHidden()) { visibilityChanged(); return; }
                long turnStarted = System.currentTimeMillis();
                for (int unit = 0; unit < MAX_UNITS_PER_TURN && scheduledJob.isRunning(); unit++) {
                    advance();
                    if (System.currentTimeMillis() - turnStarted >= TURN_TARGET_MILLIS) break;
                }
                if (scheduledJob.isRunning()) { yields++; GenerationCoordinator.this.schedule(); }
            }
        };
        continuation.schedule(1);
    }
    private void advance() {
        if (!isRunning() || advancing) return;
        advancing = true;
        long began = System.currentTimeMillis();
        boolean entered = false;
        try {
            try {
                if (injectedTurnFailure == TurnFailure.ENTER)
                    throw new IllegalStateException("Injected generation scope entry failure");
                GenerationWorkScope.enter(job);
                entered = true;
                job.advance();
            } catch (Throwable failure) {
                job.failInfrastructure(failure);
            } finally {
                if (entered) {
                    try { GenerationWorkScope.exit(job); }
                    catch (Throwable failure) { job.failInfrastructure(failure); }
                }
            }
            if (isRunning()) {
                try {
                    if (injectedTurnFailure == TurnFailure.PAUSE)
                        throw new IllegalStateException("Injected generation pause failure");
                    services.pause();
                    sim.setGenerationBusy(true, "Preparing board...");
                } catch (Throwable failure) { job.failInfrastructure(failure); }
            }
        } finally {
            maxAdvanceMillis = Math.max(maxAdvanceMillis, Math.max(0, System.currentTimeMillis() - began));
            lastProgressAt = System.currentTimeMillis();
            advancing = false;
        }
        if (!isRunning()) completed();
    }
    void cancel() {
        if (!isRunning()) return;
        if (continuation != null) { continuation.cancel(); continuation = null; }
        cancelledAt = System.currentTimeMillis();
        // UI cancellation runs between stages; checkpoint cancellation also works
        // inside a stage without ever issuing a partial receipt.
        boolean wasAdvancing = advancing;
        advancing = true;
        try { job.cancel(); }
        finally { advancing = wasAdvancing; }
        if (!wasAdvancing) completed();
    }
    private void completed() {
        if (services == null || services.notified) return;
        final Services finished = services;
        final GenerationJob terminal = job;
        finished.notified = true;
        if (continuation != null) { continuation.cancel(); continuation = null; }
        if (watchdog != null) { watchdog.cancel(); watchdog = null; }
        if (cancelledAt != 0) cancellationLatency = Math.max(0, System.currentTimeMillis() - cancelledAt);
        // Presentation failure must not strand the public session in PREPARING.
        // Deliver exactly once even when restoring old UI controls throws.
        try {
            sim.setGenerationBusy(false, terminal.getOutcome() == GenerationJob.Outcome.PASS ? "" :
                terminal.getOutcome() == GenerationJob.Outcome.CANCELLED ? "Board preparation cancelled." :
                "The board could not be prepared.");
        } finally {
            try {
                if (terminal.getOutcome() != GenerationJob.Outcome.INFRASTRUCTURE_FAILURE || finished.cleanupComplete)
                    finished.releaseSavedOwners();
            } finally {
                if (finished.completion != null)
                    finished.completion.complete(terminal,
                        terminal.getOutcome() == GenerationJob.Outcome.PASS ? finished.candidate : null);
            }
        }
    }

    private final class Services implements GenerationJob.Services {
        private final GenerationRequest selection;
        private GenerationRequest request;
        private final Completion completion;
        private GeneratedBoardInstance original;
        private GeneratedChallengeController originalController;
        private Object originalGraph;
        private GenerationRequest.Prepared prepared;
        private String realizationManifest;
        private GeneratedBoardInstance candidate;
        private GenerationRequest.ConstructionSession constructionSession;
        private FreshGeneratedRuntimeInstallation.Staged installation;
        private GeneratedDiagnosticProofService.Session proof;
        private DifficultyAssessment difficulty;
        private boolean notified, cleanupComplete;

        Services(GenerationRequest request, Completion completion) {
            this.selection = request; this.request = request.candidate(0); this.completion = completion;
            original = sim.getGeneratedBoardInstance();
            originalController = sim.getGeneratedChallengeController();
            originalGraph = sim.elmList;
        }
        public int candidateCount() { return selection.candidateCount(); }
        public String manifest(int index) { return selection.candidateManifest(index); }
        public void beginCandidate(int index) {
            if (installation != null || candidate != null || proof != null || constructionSession != null)
                throw new IllegalStateException("Previous candidate still owns resources");
            request = selection.candidate(index);
            expectedProofUnits = 0; cleanupComplete = false;
        }
        public String resolve() {
            try { prepared = request.resolve(plans); }
            catch (ChallengeContractException incompatible) {
                switch (incompatible.getCode()) {
                case UNSUPPORTED_VERSION:
                case UNSUPPORTED_ID:
                case UNSUPPORTED_CONSTRAINT:
                case CONTRADICTORY_CONSTRAINT:
                case EMPTY_CANDIDATES:
                    throw new GenerationJob.Rejected(incompatible.getMessage(), incompatible);
                default:
                    // Malformed internal declarations remain programming errors.
                    throw incompatible;
                }
            }
            return prepared.canonical();
        }
        public String healthy() {
            job.checkpoint();
            if (installation == null) {
                try {
                    if(constructionSession==null)constructionSession=prepared.beginConstruction();
                    if(!constructionSession.advance())return null;
                    GenerationRequest.Construction construction = constructionSession.result();
                    candidate = construction.instance;
                    realizationManifest = construction.realizationManifest;
                    constructionSession=null;
                }
                catch (PcbRoutingRejectedException rejection) { throw new GenerationJob.Rejected(rejection.getMessage()); }
                catch (BoundedGeneratedBoardAssembler.AssemblyFailure failure) {
                    if (!failure.isCleanupSucceeded())
                        throw new GenerationJob.Failure(GenerationJob.Outcome.INFRASTRUCTURE_FAILURE,
                            "Generation assembly cleanup failed", failure);
                    if (failure.getCause() instanceof PcbRoutingRejectedException)
                        throw new GenerationJob.Rejected(failure.getCause().getMessage());
                    throw failure;
                }
                job.checkpoint();
                if (candidate == null || candidate.getPcbLayout() == null)
                    throw new IllegalStateException("Native generation returned incomplete physical ownership");
                int proofUnits = GeneratedDiagnosticProofService.requiredWorkUnits(candidate,
                    request.requiresExplicitCompletion());
                expectedProofUnits = proofUnits;
                int otherUnits = candidate.getTemporalBehavior() == null ? 5 :
                    4 + 2 * candidate.getTemporalBehavior().getProfileWorkUnits();
                if (proofUnits > MAX_JOB_STEPS - otherUnits)
                    throw new GenerationJob.Failure(GenerationJob.Outcome.WORK_EXHAUSTED,
                        "Diagnostic program exceeds the current generation work budget");
                if (request.isComposition()) candidate.getPhysicalBoardRuntime().validateSupportedCompositionProviders();
                installation = new FreshGeneratedRuntimeInstallation.Staged(sim, candidate);
                installation.prepare(request.requiresExplicitCompletion());
            } else {
                installation.finishPreparation();
            }
            if (!installation.isPreparationComplete()) return null;
            GeneratedChallengeLifecycleEvidence evidence = sim.getGeneratedChallengeController().getLifecycleEvidence();
            if (!evidence.healthyFamilyValidated || !evidence.selectedFaultValidated)
                throw new IllegalStateException("Generation did not prove healthy and faulty CircuitJS behavior");
            return "CircuitJS:healthy-and-selected-fault:PASS;fresh-materialization-includes-layout;elements=" +
                candidate.getSimulationElements().size();
        }
        public String physical() {
            installation.validatePhysical();
            return dependencies();
        }
        public boolean proveNext() {
            if (proof == null) {
                installation.enterStep();
                proof = GeneratedDiagnosticProofService.begin(sim, candidate,
                    sim.getGeneratedChallengeController(), new GeneratedDiagnosticProofService.Checkpoint() {
                        public void check() { job.checkpoint(); }
                    });
            }
            boolean more = proof.step();
            if (!more) {
                proof.finish();
                if (request.getDifficulty() != null) {
                    difficulty = DifficultyAssessment.assess(candidate, sim.getGeneratedChallengeController().getDiagnosticProofReceipt());
                    if (difficulty.profile != request.getDifficulty())
                        throw new GenerationJob.Rejected("The proved board does not match the requested difficulty");
                }
                if (job.getCandidateStageWorkCount(GenerationJob.Stage.HYPOTHESES) !=
                        GeneratedDiagnosticProofService.requiredWorkUnits(candidate,
                            request.requiresExplicitCompletion()))
                    throw new IllegalStateException("Diagnostic operation count differs from its declared program");
            }
            return more;
        }
        public String symptom() {
            installation.enterStep();
            sim.getGeneratedChallengeController().completeGenerationPresentation();
            return "selected-fault-validated;complete-hypotheses=" + proof.getTotalCount() +
                ";scenario-compatible;answer-private";
        }
        public String dependencies() {
            installation.enterStep();
            return GenerationDependencyContext.capture(sim, candidate,
                request.canonical(), realizationManifest).canonical();
        }
        public void publish(GenerationReceipt receipt) {
            if (receipt == null) throw new IllegalStateException("Missing generation proof receipt");
            if (request.getDifficulty() != null) {
                if (difficulty == null) throw new IllegalStateException("Missing difficulty admission evidence");
                difficulty.require(request.getDifficulty());
            }
            String physicalFingerprint = null;
            if (selection.isCandidateSearch()) {
                physicalFingerprint = PhysicalBoardFingerprint.novelty(candidate);
                recentBoards.requireNovel(physicalFingerprint);
            }
            if (request.isQuickPlay()) {
                QuickPlaySelection selection = new QuickPlaySelection(request.getDescriptor().getDeviceIntent().getId(),
                    request.getDescriptor().getRootSeed());
                sim.quickPlaySession = QuickPlaySession.forCandidate(selection, candidate);
            }
            installation.publish();
            if (physicalFingerprint != null) recentBoards.published(physicalFingerprint);
        }
        public void abort() {
            Throwable failure = null;
            cleanupComplete = false;
            if (proof != null) {
                try { proof.cancel(); }
                catch (Throwable cleanup) { failure = cleanup; }
                finally { lastCleanupAudit = proof.getCleanupAudit(); }
                if (proof.hasPendingPrivateCleanup()) {
                    IllegalStateException incomplete = new IllegalStateException("Diagnostic private cleanup is incomplete");
                    if (failure == null) failure = incomplete;
                    else failure.addSuppressed(incomplete);
                }
            }
            try {
                if (proof != null && proof.hasPendingPrivateCleanup()) {
                    // Keep both exact private and protected owners reachable.
                    // Restoring the outer snapshot here would orphan an inner
                    // graph whose disposal or restoration did not complete.
                    throw new IllegalStateException("Private proof cleanup must finish before outer restoration");
                }
                if (installation != null) installation.abort();
                else if (candidate != null) {
                    candidate.getExternalPowerBindings().setConnected(false);
                    for (CircuitElm element : candidate.getSimulationElements()) {
                        if (sim.elmList.contains(element))
                            throw new IllegalStateException("Generation cleanup found a live element");
                        element.delete();
                    }
                }
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure != null) throw new IllegalStateException("Generation cleanup failed", failure);
            cleanupComplete = true;
            // Restore succeeded before making the next exact candidate eligible.
            // The protected original stays owned until the entire launch terminates.
            installation = null; proof = null; candidate = null; constructionSession = null;
            prepared = null; realizationManifest = null; difficulty = null;
        }
        public boolean isCurrent() {
            if (services != this) return false;
            if (installation == null)
                return sim.getGeneratedBoardInstance() == original &&
                    sim.getGeneratedChallengeController() == originalController && sim.elmList == originalGraph;
            return installation.ownsCandidate() || (proof != null &&
                FreshGeneratedRuntimeInstallation.isInProgress(sim) && proof.ownsWorkingOwner());
        }
        public long nowMillis() {
            long wall = System.currentTimeMillis();
            return foregroundBudget ? foregroundClock.nowMillis(wall) : wall;
        }
        void pause() {
            if (proof != null) proof.pause();
            else if (installation != null) installation.pause();
        }
        void releaseSavedOwners() {
            if (proof != null) lastCleanupAudit = proof.getCleanupAudit();
            original = null; originalController = null; originalGraph = null;
            installation = null; proof = null; prepared = null; constructionSession = null;
            if (job.getOutcome() != GenerationJob.Outcome.PASS) candidate = null;
        }
        boolean hasSavedOwners() {
            return original != null || originalGraph != null || originalController != null ||
                installation != null || proof != null || constructionSession != null;
        }
    }
}
