package com.lushprojects.circuitjs1.client;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import java.util.Vector;

/**
 * Debug-only cold/warm qualification of the private Q30 request through the
 * simulator's real staged generation coordinator.
 *
 * <p>The two published owners are probes: each is retired before restoring the
 * exact predecessor snapshot.  The verifier never changes the normal-player family catalog, and the verifier retains only value evidence between the runs.</p>
 */
final class Q30CoordinatorQualificationVerifier {
    static final long MEASUREMENT_JOB_MILLIS = 300000L;
    private static Runner active;

    private Q30CoordinatorQualificationVerifier() { }

    /** Retries only this verifier's retained, exact-owner cleanup transaction. */
    static boolean retryPendingCleanup(CirSim sim) {
        Runner runner = active;
        return runner != null && runner.sim == sim && runner.retryPendingCleanup();
    }

    /** The exact signed-long syntax is checked before this entry mutates state. */
    static void start(CirSim sim, String requestedSeed) {
        if (sim == null)
            throw new IllegalArgumentException("Missing Q30 coordinator simulator");
        final long seed = parseSeed(requestedSeed);
        if (!sim.troubleshootDebug || !sim.troubleshootQ30Verification)
            throw new IllegalStateException("Q30 coordinator qualification requires explicit debug scope");
        if (active != null || sim.developerVerifierRunning ||
                !sim.isGeneratedRuntimeSettled() || sim.activeMeasurementOverlay ||
                sim.generatedRuntimeInstallationInProgress ||
                GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning())
            throw new IllegalStateException("Q30 coordinator qualification requires an idle settled owner");
        if (sim.generationCoordinator == null || sim.generationCoordinator.isRunning() ||
                sim.generationCoordinator.isAdvancing() ||
                sim.generationCoordinator.retainsSavedOwnersForDeveloperVerification())
            throw new IllegalStateException("Q30 coordinator qualification requires the idle simulator coordinator");
        requireRegisteredNormalCatalog();
        if (GenerationCoordinator.MAX_JOB_MILLIS != 90000L ||
                GenerationCoordinator.MAX_DIAGNOSTIC_MEASUREMENT_JOB_MILLIS !=
                    MEASUREMENT_JOB_MILLIS ||
                GenerationCoordinator.MAX_STEP_MILLIS != 5000L ||
                GenerationCoordinator.MAX_JOB_STEPS != 640)
            throw new IllegalStateException("Q30 coordinator verifier requires the unchanged generation budgets");

        Task41SimulationSnapshot snapshot = sim.getGeneratedBoardInstance() == null ?
            Task41SimulationSnapshot.captureForFreshInstallation(sim) :
            Task41SimulationSnapshot.capture(sim);
        GenerationRequest request = GenerationRequest.forQ30Qualification(seed);
        Runner runner = new Runner(sim, seed,
            requestedSeed == null ? "0" : requestedSeed, request, snapshot,
            sim.generationCoordinator);
        active = runner;
        runner.begin();
    }

    private static long parseSeed(String requestedSeed) {
        if (requestedSeed == null) return 0L;
        if (requestedSeed.length() == 0)
            throw new IllegalArgumentException("Q30 coordinator seed cannot be explicitly empty");
        final long seed;
        try {
            seed = Long.parseLong(requestedSeed);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Q30 coordinator seed is not a signed long");
        }
        if (!Long.toString(seed).equals(requestedSeed))
            throw new IllegalArgumentException("Q30 coordinator seed is not canonical");
        return seed;
    }

    private static void requireRegisteredNormalCatalog() {
        if (!PlayerFamilyCatalog.contains(Rb30Plan.FAMILY_ID) ||
                !QuickPlayAdmission.supports(Rb30Plan.FAMILY_ID, DifficultyProfile.MEDIUM))
            throw new IllegalStateException("Q30 coordinator qualification requires the current normal-medium catalog");
    }

    private static final class Runner {
        private final CirSim sim;
        private final long seed;
        private final String requestedSeed;
        private final GenerationRequest request;
        private final String requestCanonical;
        private final Task41SimulationSnapshot snapshot;
        private final GenerationCoordinator coordinator;
        private final GeneratedBoardInstance predecessorOwner;
        private final GeneratedChallengeController predecessorController;
        private final Vector<CircuitElm> predecessorGraph;
        private final boolean predecessorVerifierRunning;
        private final boolean scopeLossCanaryRequested;
        private final Timer timer = new Timer() {
            @Override public void run() { advance(); }
        };

        private String phase = "startup";
        private String runName;
        private GenerationJob job;
        private long runStartedAt;
        private long maxManualAdvanceMillis;
        private int manualAdvanceCalls;
        private int proofHitsAtRunStart;
        private int proofMissesAtRunStart;
        private int planHitsAtRunStart;
        private int planMissesAtRunStart;
        private int measurementCacheInitialSize;
        private int measurementCacheSizeBeforeClear;
        private int measurementCacheSizeAfterClear;
        private int measurementCacheHitsBefore;
        private int measurementCacheMissesBefore;
        private int normalCacheSizeBefore;
        private int normalCacheHitsBefore;
        private int normalCacheMissesBefore;
        private int normalCacheSizeAfter;
        private int normalCacheHitsAfter;
        private int normalCacheMissesAfter;
        private final long startedAt;
        private boolean cleanupPending;
        private boolean cleanupRetryActive;
        private int cleanupRetryCount;
        private GenerationJob cleanupJob;
        private GeneratedBoardInstance cleanupOwner;
        private Vector<CircuitElm> cleanupElements;
        private Vector<CircuitElm> cleanupPublishedGraph;
        private int cleanupCursor;
        private boolean cleanupPowerDisconnected;
        private boolean cleanupSnapshotAttempted;
        private boolean cleanupSnapshotRestored;
        private RunRecord cleanupRecord;
        private String cleanupOwnerKind;
        private String cleanupPendingReason;
        private boolean measurementCacheCleared;
        private boolean scopeLossCanaryInjected;
        private boolean scopeLossCanaryFlagRestored;
        private boolean scopeLossDetected;
        private String scopeLossCanaryStage = "NOT_REACHED";
        private int scopeLossCanaryProofUnitsBeforeLoss = -1;
        private boolean finished;
        private boolean restored;
        private Throwable failure;
        private String failurePhase;
        private String cleanupFailure;
        private RunRecord cold;
        private RunRecord warm;
        private ProofValue coldProof;
        private GeneratedBoardInstance coldPublishedOwner;

        Runner(CirSim sim, long seed, String requestedSeed,
                GenerationRequest request, Task41SimulationSnapshot snapshot,
                GenerationCoordinator coordinator) {
            this.sim = sim;
            this.seed = seed;
            this.requestedSeed = requestedSeed;
            this.request = request;
            this.requestCanonical = request.canonical();
            this.snapshot = snapshot;
            this.coordinator = coordinator;
            predecessorOwner = sim.getGeneratedBoardInstance();
            predecessorController = sim.getGeneratedChallengeController();
            predecessorGraph = sim.elmList;
            predecessorVerifierRunning = sim.developerVerifierRunning;
            scopeLossCanaryRequested = com.google.gwt.core.client.GWT.isClient() &&
                "true".equals(Window.Location.getParameter("tsjQ30CoordinatorScopeLoss"));
            startedAt = System.currentTimeMillis();
        }

        void begin() {
            sim.developerVerifierRunning = true;
            clearReport();
            try {
                measurementCacheInitialSize =
                    coordinator.getDiagnosticMeasurementProofCacheSize();
                measurementCacheHitsBefore =
                    coordinator.getDiagnosticMeasurementProofCacheHits();
                measurementCacheMissesBefore =
                    coordinator.getDiagnosticMeasurementProofCacheMisses();
                normalCacheSizeBefore = coordinator.getDiagnosticProofCacheSize();
                normalCacheHitsBefore = coordinator.getDiagnosticProofCacheHits();
                normalCacheMissesBefore = coordinator.getDiagnosticProofCacheMisses();
                coordinator.clearDiagnosticMeasurementProofCacheForDeveloperVerification();
                if (coordinator.getDiagnosticMeasurementProofCacheSize() != 0)
                    throw new IllegalStateException(
                        "Cold measurement did not start with an empty private proof cache");
                beginRun("cold");
            } catch (Throwable problem) {
                failBeforeOrDuringRun(problem, "cold-start");
            }
        }

        private void beginRun(String name) {
            if (!scopeIsCurrent())
                throw new IllegalStateException("Q30 debug scope changed before coordinator start");
            if (coordinator.isRunning() || coordinator.isAdvancing() ||
                    coordinator.retainsSavedOwnersForDeveloperVerification())
                throw new IllegalStateException("The simulator coordinator is not available for this run");
            runName = name;
            phase = name;
            runStartedAt = System.currentTimeMillis();
            maxManualAdvanceMillis = 0;
            manualAdvanceCalls = 0;
            proofHitsAtRunStart = coordinator.getDiagnosticMeasurementProofCacheHits();
            proofMissesAtRunStart = coordinator.getDiagnosticMeasurementProofCacheMisses();
            planHitsAtRunStart = coordinator.getPlanCacheHits();
            planMissesAtRunStart = coordinator.getPlanCacheMisses();
            GenerationJob priorJob = coordinator.getJob();
            try {
                coordinator.startForDiagnosticCacheVerification(request, MEASUREMENT_JOB_MILLIS);
            } catch (Throwable startFailure) {
                GenerationJob startedJob = coordinator.getJob();
                if (startedJob != priorJob) job = startedJob;
                else job = priorJob;
                throw startFailure;
            }
            job = coordinator.getJob();
            if (job == null)
                throw new IllegalStateException("The simulator coordinator did not create a generation job");
            publish("RUNNING", name);
            if (job.isRunning()) schedule();
            else completeRun(null);
        }

        private void schedule() {
            if (!finished && active == this) timer.schedule(1);
        }

        /** Exactly one coordinator unit is advanced per Timer callback. */
        private void advance() {
            if (finished || active != this) return;
            if (cleanupPending) return;
            if (cleanupRetryActive) {
                advanceCleanupRetry();
                return;
            }
            if (!jobIsStillCurrent()) {
                stopForSuccessor("A successor generation replaced the qualification job");
                return;
            }
            if (injectScopeLossCanaryAtFirstProofUnit()) {
                handleOwnedScopeLoss("Injected Q30 coordinator scope loss");
                return;
            }
            if (!scopeFlagsAreCurrent()) {
                handleOwnedScopeLoss("Q30 coordinator debug scope was lost");
                return;
            }
            if (!job.isRunning()) {
                completeRun(null);
                return;
            }
            long began = System.currentTimeMillis();
            Throwable advanceFailure = null;
            try {
                coordinator.advanceForDeveloperVerification();
            } catch (Throwable problem) {
                advanceFailure = problem;
            } finally {
                long elapsed = Math.max(0, System.currentTimeMillis() - began);
                maxManualAdvanceMillis = Math.max(maxManualAdvanceMillis, elapsed);
                manualAdvanceCalls++;
            }
            if (advanceFailure != null) {
                if (coordinator.getJob() == job && coordinator.isRunning()) {
                    try { coordinator.cancel(); }
                    catch (Throwable cleanup) { retain(advanceFailure, cleanup); }
                }
                completeRun(advanceFailure);
                return;
            }
            if (!jobIsStillCurrent()) {
                stopForSuccessor("A successor generation replaced the qualification job");
                return;
            }
            if (!scopeFlagsAreCurrent()) {
                handleOwnedScopeLoss("Q30 coordinator debug scope was lost during a generation unit");
                return;
            }
            if (job.isRunning()) {
                if (manualAdvanceCalls % 16 == 0) publish("RUNNING", runName);
                schedule();
            } else {
                completeRun(null);
            }
        }

        private boolean scopeIsCurrent() {
            return sim.generationCoordinator == coordinator && scopeFlagsAreCurrent();
        }

        private boolean jobIsStillCurrent() {
            return coordinator.getJob() == job && sim.generationCoordinator == coordinator;
        }

        private boolean scopeFlagsAreCurrent() {
            return sim.troubleshootDebug && sim.troubleshootQ30Verification &&
                sim.developerVerifierRunning;
        }

        private boolean injectScopeLossCanaryAtFirstProofUnit() {
            if (!scopeLossCanaryRequested || scopeLossCanaryInjected || job == null ||
                    !job.isRunning() || job.getStage() != GenerationJob.Stage.HYPOTHESES ||
                    job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) != 0)
                return false;
            scopeLossCanaryStage = job.getStage().name();
            scopeLossCanaryProofUnitsBeforeLoss =
                job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES);
            sim.troubleshootQ30Verification = false;
            scopeLossCanaryInjected = true;
            return true;
        }

        /** Cancels only the exact still-current job after verifier scope loss. */
        private void handleOwnedScopeLoss(String message) {
            if (finished || active != this) return;
            if (!jobIsStillCurrent()) {
                stopForSuccessor("A successor generation replaced the qualification job");
                return;
            }
            timer.cancel();
            scopeLossDetected = true;
            IllegalStateException scopeFailure = new IllegalStateException(message);
            failure = scopeFailure;
            failurePhase = "scope-loss";
            if (coordinator.isRunning()) {
                try { coordinator.cancel(); }
                catch (Throwable cancelFailure) { retain(scopeFailure, cancelFailure); }
            }
            if (!jobIsStillCurrent()) {
                stopForSuccessor("A successor generation replaced the job during scope-loss cancellation");
                return;
            }
            if (coordinator.isRunning()) {
                finishWithoutTouchingSuccessor(
                    "The exact qualification job remained active after scope-loss cancellation");
                return;
            }
            restoreCanaryScopeFlagInCurrentCallback();
            completeRun(scopeFailure);
        }

        private void restoreCanaryScopeFlagInCurrentCallback() {
            if (!scopeLossCanaryInjected || scopeLossCanaryFlagRestored ||
                    !scopeLossDetected || !jobIsStillCurrent() || coordinator.isRunning() ||
                    coordinator.retainsSavedOwnersForDeveloperVerification() ||
                    sim.getGeneratedBoardInstance() != predecessorOwner ||
                    sim.getGeneratedChallengeController() != predecessorController ||
                    sim.elmList != predecessorGraph || sim.troubleshootQ30Verification)
                return;
            sim.troubleshootQ30Verification = true;
            scopeLossCanaryFlagRestored = true;
        }

        private void completeRun(Throwable priorFailure) {
            if (finished || active != this) return;
            timer.cancel();
            if (coordinator.getJob() != job) {
                stopForSuccessor("A successor generation replaced the qualification job");
                return;
            }
            RunRecord record = RunRecord.capture(runName, job, coordinator,
                runStartedAt, maxManualAdvanceMillis, manualAdvanceCalls,
                proofHitsAtRunStart, proofMissesAtRunStart,
                planHitsAtRunStart, planMissesAtRunStart, MEASUREMENT_JOB_MILLIS);
            GeneratedBoardInstance published = null;
            ProofValue proof = null;
            Throwable runFailure = priorFailure;
            /* Publication happens in the final coordinator unit. Capture its
             * exact live owner before checking receipt/budget evidence so a
             * terminal failure can never skip retirement of a committed board. */
            if (job.getOutcome() == GenerationJob.Outcome.PASS &&
                    sim.getGeneratedBoardInstance() != predecessorOwner)
                published = sim.getGeneratedBoardInstance();
            try {
                require(job.getOutcome() == GenerationJob.Outcome.PASS,
                    "Coordinator outcome was " + job.getOutcome() + failureText(job.getFailure()));
                require(job.getReceipt() != null && job.getReceipt().getStageCount() == 6,
                    "The published job has no complete six-stage receipt");
                require(job.getElapsedMillis() <= MEASUREMENT_JOB_MILLIS &&
                    job.getStepCount() <= GenerationCoordinator.MAX_JOB_STEPS,
                    "The coordinator exceeded its measurement wall or unchanged work budget");
                validatePublishedOwner(published, runName.equals("warm"));
                proof = ProofValue.capture(sim.getGeneratedChallengeController()
                    .getDiagnosticProofReceipt(), published, request.requiresExplicitCompletion());
                require(proof != null, "The normal owner has no real D01 proof receipt");
                record.captureOwner(published, proof, job.getReceipt());
                if (runName.equals("cold")) {
                    require(!proof.warmReuse,
                        "The cold generation unexpectedly reused a diagnostic proof");
                    require(record.proofCacheHitDelta == 0 && record.proofCacheMissDelta == 1 &&
                        record.proofCacheSize == 1,
                        "Cold publication did not perform one cache miss and publish one value entry");
                    require(record.hypothesisWork > 1,
                        "Cold proof did not execute its real serial hypothesis work");
                } else {
                    require(proof.warmReuse,
                        "The warm generation did not issue a cache-reuse receipt");
                    require(record.proofCacheHitDelta == 1 && record.proofCacheMissDelta == 0 &&
                        record.proofCacheSize == 1 && record.hypothesisWork == 1,
                        "Warm publication did not reuse exactly one proof-cache value");
                    require(published != coldPublishedOwner,
                        "Warm publication reused the cold electrical owner");
                    require(coldProof != null && coldProof.sameValue(proof),
                        "Warm proof values differ from the cold published proof");
                }
            } catch (Throwable problem) {
                if (runFailure == null) runFailure = problem;
                else retain(runFailure, problem);
            }

            if (runName.equals("cold")) cold = record;
            else warm = record;
            if (published != null && runFailure == null) {
                if (runName.equals("cold")) {
                    coldPublishedOwner = published;
                    coldProof = proof;
                }
            }

            CleanupResult cleanupResult = cleanup(published, job);
            record.cleanupComplete = cleanupResult.complete;
            record.ownerRestored = cleanupResult.restored;
            record.cleanupElapsedMillis = cleanupResult.elapsedMillis;
            record.cleanupFailure = cleanupResult.failure;
            restored = cleanupResult.restored;
            if (!cleanupResult.complete) {
                String message = "Coordinator owner cleanup failed" +
                    (cleanupResult.failure == null ? "" : ": " + cleanupResult.failure);
                cleanupFailure = cleanupResult.failure;
                if (runFailure == null) runFailure = new IllegalStateException(message);
                else retain(runFailure, new IllegalStateException(message));
                failure = runFailure;
                failurePhase = "cleanup";
                parkCleanupPending(published, job, record, cleanupResult.failure);
                return;
            }
            clearCleanupTransaction();
            restoreCanaryScopeFlagInCurrentCallback();
            if (runFailure != null) {
                failure = runFailure;
                failurePhase = scopeLossDetected ? "scope-loss" : runName;
                finish("FAIL", scopeLossDetected ? "scope-loss" : runName);
                return;
            }

            if (runName.equals("cold")) {
                try {
                    sim.developerVerifierRunning = true;
                    beginRun("warm");
                } catch (Throwable problem) {
                    failBeforeOrDuringRun(problem, "warm-start");
                }
                return;
            }
            try {
                requireRegisteredNormalCatalog();
                require(restored, "The predecessor was not restored after the warm owner");
                finish("PASS", "complete");
            } catch (Throwable problem) {
                failure = problem;
                failurePhase = "complete";
                finish("FAIL", "complete");
            }
        }

        private void validatePublishedOwner(GeneratedBoardInstance owner,
                boolean expectFreshWarmOwner) {
            require(owner != null && owner != predecessorOwner,
                "Coordinator did not publish a fresh normal Q30 owner");
            if (expectFreshWarmOwner)
                require(owner != coldPublishedOwner,
                    "Warm coordinator publication reused the cold owner");
            require(owner.getSeed() == seed && Rb30Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()) &&
                !owner.isDeveloperOnlyFaultRoute(),
                "Published owner identity or normal-route provenance is wrong");
            GeneratedPhysicalAdmission admission = owner.getPhysicalAdmission();
            require(admission != null && MediumBoardNormalAdmission.IDENTITY.equals(admission.identity()),
                "Published owner is missing normal-medium physical admission");
            owner.requireNormalPhysicalAdmission();
            require(sim.getGeneratedChallengeController() != null &&
                sim.getGeneratedChallengeController().isReady(),
                "Published normal owner has no ready challenge controller");
            requireRegisteredNormalCatalog();
        }

        private CleanupResult cleanup(GeneratedBoardInstance published, GenerationJob ownJob) {
            long began = System.currentTimeMillis();
            Throwable cleanupProblem = null;
            try {
                if (coordinator.getJob() != ownJob)
                    throw new IllegalStateException("Refusing cleanup after a successor generation started");
                if (coordinator.isRunning() || coordinator.isAdvancing())
                    throw new IllegalStateException("Refusing cleanup while a generation stage is active");
                if (published != null && cleanupOwner == null) {
                    cleanupJob = ownJob;
                    cleanupOwner = published;
                    cleanupElements = published.getSimulationElements();
                    cleanupPublishedGraph = sim.elmList;
                    cleanupCursor = 0;
                    cleanupPowerDisconnected = false;
                    cleanupSnapshotAttempted = false;
                    cleanupSnapshotRestored = false;
                    cleanupRecord = null;
                    cleanupOwnerKind = "publishedNormalOwner";
                } else if (published == null && cleanupJob == null) {
                    cleanupJob = ownJob;
                    cleanupOwner = null;
                    cleanupElements = null;
                    cleanupPublishedGraph = null;
                    cleanupCursor = 0;
                    cleanupPowerDisconnected = true;
                    cleanupSnapshotAttempted = false;
                    cleanupSnapshotRestored = false;
                    cleanupOwnerKind = "predecessorOwner";
                }
                if (cleanupJob != ownJob || cleanupOwner != published)
                    throw new IllegalStateException("Cleanup retry does not match the retained exact owner");
                if (coordinator.retainsSavedOwnersForDeveloperVerification() && published == null) {
                    try { coordinator.retryFailedCleanupForDeveloperVerification(); }
                    catch (Throwable retryFailure) { cleanupProblem = retain(cleanupProblem, retryFailure); }
                    if (coordinator.retainsSavedOwnersForDeveloperVerification())
                        throw new IllegalStateException("Coordinator still retains failed private cleanup");
                }
                if (published != null) {
                    boolean candidateCurrent = sim.getGeneratedBoardInstance() == published;
                    boolean restoreWasAttempted = cleanupSnapshotAttempted &&
                        predecessorStillCurrent();
                    if (!candidateCurrent && !restoreWasAttempted)
                        throw new IllegalStateException("Refusing to retire a non-current or successor owner");
                    if (candidateCurrent && sim.elmList != cleanupPublishedGraph)
                        throw new IllegalStateException("Published owner lost its exact active CircuitJS graph");
                    Vector<CircuitElm> predecessorElements = predecessorOwner == null ?
                        new Vector<CircuitElm>() : predecessorOwner.getSimulationElements();
                    if (candidateCurrent) {
                        if (!cleanupPowerDisconnected) {
                            published.getExternalPowerBindings().setConnected(false);
                            require(published.getExternalPowerBindings().areAllDisconnected(),
                                "Published owner retained a connected external source");
                            cleanupPowerDisconnected = true;
                        } else {
                            require(published.getExternalPowerBindings().areAllDisconnected(),
                                "Published owner retained a connected external source");
                        }
                        sim.setSimRunning(false);
                        while (cleanupCursor < cleanupElements.size()) {
                            if (coordinator.getJob() != ownJob ||
                                    sim.getGeneratedBoardInstance() != published ||
                                    sim.elmList != cleanupPublishedGraph)
                                throw new IllegalStateException("A successor appeared during owner retirement");
                            CircuitElm element = cleanupElements.get(cleanupCursor);
                            require(!predecessorElements.contains(element),
                                "Published owner overlaps the predecessor CircuitJS graph");
                            element.delete();
                            cleanupCursor++;
                        }
                    }
                    require(cleanupPowerDisconnected && cleanupCursor == cleanupElements.size(),
                        "Published owner retirement stopped before power or elements were disposed");
                } else {
                    if (coordinator.getJob() != ownJob ||
                            sim.getGeneratedBoardInstance() != predecessorOwner ||
                            sim.getGeneratedChallengeController() != predecessorController ||
                            sim.elmList != predecessorGraph)
                        throw new IllegalStateException("Failed generation did not restore its exact predecessor");
                }
                if (coordinator.getJob() != ownJob ||
                        (published != null && sim.getGeneratedBoardInstance() != published &&
                            !(cleanupSnapshotAttempted && predecessorStillCurrent())) ||
                        (published != null && sim.getGeneratedBoardInstance() == published &&
                            sim.elmList != cleanupPublishedGraph) ||
                        (published == null && sim.getGeneratedBoardInstance() != predecessorOwner))
                    throw new IllegalStateException("Refusing snapshot restore across a successor owner");
                cleanupSnapshotAttempted = true;
                snapshot.restore(sim);
                snapshot.assertRestored(sim);
                if (published != null) {
                    require(published.getExternalPowerBindings().areAllDisconnected(),
                        "Retired published owner regained an external source connection");
                    for (CircuitElm element : cleanupElements)
                        require(!sim.elmList.contains(element),
                            "Published owner remained in the restored active CircuitJS graph");
                }
                cleanupSnapshotRestored = true;
                cleanupOwnerKind = "predecessorOwner";
                return new CleanupResult(true, cleanupProblem == null, elapsed(began),
                    cleanupProblem == null ? null : describe(cleanupProblem));
            } catch (Throwable problem) {
                cleanupProblem = retain(cleanupProblem, problem);
                return new CleanupResult(snapshotRestoredForOwnOwner(ownJob), false, elapsed(began),
                    describe(cleanupProblem));
            }
        }

        private boolean snapshotRestoredForOwnOwner(GenerationJob ownJob) {
            if (!cleanupSnapshotRestored || coordinator.getJob() != ownJob) return false;
            return predecessorStillCurrent();
        }

        private void parkCleanupPending(GeneratedBoardInstance published, GenerationJob ownJob,
                RunRecord record, String reason) {
            cleanupPending = true;
            cleanupRetryActive = false;
            cleanupJob = ownJob;
            if (cleanupOwner == null && published != null) {
                cleanupOwner = published;
                cleanupElements = published.getSimulationElements();
                cleanupPublishedGraph = sim.elmList;
                cleanupCursor = 0;
                cleanupPowerDisconnected = false;
                cleanupSnapshotAttempted = false;
                cleanupSnapshotRestored = false;
                cleanupOwnerKind = "publishedNormalOwner";
            }
            if (cleanupRecord == null) cleanupRecord = record;
            if (reason != null) cleanupPendingReason = reason;
            if (cleanupPendingReason == null) cleanupPendingReason = "Cleanup remains incomplete";
            if (failure == null) {
                failure = new IllegalStateException("Q30 coordinator cleanup is pending: " +
                    cleanupPendingReason);
                failurePhase = "cleanup";
            }
            if (cleanupRecord != null) {
                cleanupRecord.cleanupComplete = false;
                cleanupRecord.ownerRestored = snapshotRestoredForOwnOwner(ownJob);
                cleanupRecord.cleanupFailure = cleanupPendingReason;
            }
            restored = snapshotRestoredForOwnOwner(ownJob);
            phase = "cleanupPending";
            if (coordinator.getJob() == ownJob && !coordinator.isRunning() &&
                    !coordinator.isAdvancing())
                sim.developerVerifierRunning = true;
            timer.cancel();
            publish("CLEANUP_PENDING", phase);
        }

        private boolean retryPendingCleanup() {
            if (!cleanupPending || finished || active != this ||
                    coordinator.getJob() != cleanupJob || coordinator.isRunning() ||
                    coordinator.isAdvancing())
                return false;
            cleanupRetryCount++;
            cleanupPending = false;
            cleanupRetryActive = true;
            phase = "cleanupRetry";
            timer.cancel();
            publish("RUNNING", phase);
            schedule();
            return true;
        }

        private void clearCleanupTransaction() {
            cleanupPending = false;
            cleanupRetryActive = false;
            cleanupRetryCount = 0;
            cleanupJob = null;
            cleanupOwner = null;
            cleanupElements = null;
            cleanupPublishedGraph = null;
            cleanupCursor = 0;
            cleanupPowerDisconnected = false;
            cleanupSnapshotAttempted = false;
            cleanupSnapshotRestored = false;
            cleanupRecord = null;
            cleanupOwnerKind = null;
            cleanupPendingReason = null;
        }

        private void advanceCleanupRetry() {
            if (finished || active != this || !cleanupRetryActive) return;
            if (coordinator.getJob() != cleanupJob ||
                    coordinator.isRunning() || coordinator.isAdvancing()) {
                parkCleanupPending(cleanupOwner, cleanupJob, cleanupRecord,
                    "Cleanup retry lost ownership of its completed generation job");
                return;
            }
            CleanupResult result = cleanup(cleanupOwner, cleanupJob);
            if (!result.complete) {
                cleanupFailure = result.failure;
                parkCleanupPending(cleanupOwner, cleanupJob, cleanupRecord, result.failure);
                return;
            }
            cleanupRetryActive = false;
            cleanupPending = false;
            restored = result.restored;
            if (cleanupRecord != null) {
                cleanupRecord.cleanupComplete = true;
                cleanupRecord.ownerRestored = result.restored;
                cleanupRecord.cleanupElapsedMillis += result.elapsedMillis;
                cleanupRecord.cleanupRetryCount = cleanupRetryCount;
            }
            if (failure == null) {
                failure = new IllegalStateException("Q30 coordinator cleanup required a retry");
                failurePhase = "cleanup";
            }
            finish("FAIL", "cleanupRetryComplete");
        }

        private void failBeforeOrDuringRun(Throwable problem, String at) {
            if (finished || active != this) return;
            timer.cancel();
            if (job != null && coordinator.getJob() == job && coordinator.isRunning()) {
                try { coordinator.cancel(); }
                catch (Throwable cancelFailure) { retain(problem, cancelFailure); }
                if (!coordinator.isRunning() && coordinator.getJob() == job) {
                    if (runName == null) runName = "startup";
                    completeRun(problem);
                    return;
                }
            }
            failure = problem;
            failurePhase = at;
            if (coordinator.getJob() == job && job != null && !job.isRunning()) {
                CleanupResult cleanupResult = cleanup(null, job);
                restored = cleanupResult.restored;
                if (!cleanupResult.complete) {
                    retain(failure, new IllegalStateException("Cleanup failed: " + cleanupResult.failure));
                    cleanupFailure = cleanupResult.failure;
                    parkCleanupPending(null, job, null, cleanupResult.failure);
                    return;
                }
                clearCleanupTransaction();
            } else if (job == null && coordinator.getJob() != null && coordinator.isRunning()) {
                /* Never cancel or replace a job that this runner did not start. */
                finishWithoutTouchingSuccessor("Generation coordinator ownership changed during startup");
                return;
            } else if (coordinator.getJob() == null || !coordinator.isRunning()) {
                CleanupResult cleanupResult = cleanup(null, null);
                restored = cleanupResult.restored;
                if (!cleanupResult.complete) {
                    retain(failure, new IllegalStateException("Cleanup failed: " + cleanupResult.failure));
                    cleanupFailure = cleanupResult.failure;
                    parkCleanupPending(null, null, null, cleanupResult.failure);
                    return;
                }
                clearCleanupTransaction();
            }
            finish("FAIL", at);
        }

        private CleanupResult restorePredecessorWithoutCandidate() {
            long began = System.currentTimeMillis();
            try {
                if (sim.getGeneratedBoardInstance() != predecessorOwner ||
                        sim.getGeneratedChallengeController() != predecessorController ||
                        sim.elmList != predecessorGraph)
                    throw new IllegalStateException("Refusing predecessor restore across a successor owner");
                snapshot.restore(sim);
                snapshot.assertRestored(sim);
                return new CleanupResult(true, true, elapsed(began), null);
            } catch (Throwable problem) {
                return new CleanupResult(false, false, elapsed(began), describe(problem));
            }
        }

        private void stopForSuccessor(String message) {
            if (finished || active != this) return;
            timer.cancel();
            failure = new IllegalStateException(message);
            failurePhase = phase;
            /* A different job/owner is outside this verifier's authority. */
            finishWithoutTouchingSuccessor(message);
        }

        private void finishWithoutTouchingSuccessor(String message) {
            if (finished) return;
            finished = true;
            timer.cancel();
            if (failure == null) failure = new IllegalStateException(message);
            publish("FAIL", failurePhase == null ? phase : failurePhase);
            if (active == this) active = null;
        }

        private void finish(String status, String finalPhase) {
            if (finished || active != this) return;
            if (cleanupPending || cleanupRetryActive) return;
            try {
                if (coordinator.isRunning() || coordinator.isAdvancing())
                    throw new IllegalStateException(
                        "Measurement cache cannot be cleared while a coordinator job is active");
                measurementCacheSizeBeforeClear =
                    coordinator.getDiagnosticMeasurementProofCacheSize();
                coordinator.clearDiagnosticMeasurementProofCacheForDeveloperVerification();
                measurementCacheCleared = true;
                measurementCacheSizeAfterClear =
                    coordinator.getDiagnosticMeasurementProofCacheSize();
                if (measurementCacheSizeAfterClear != 0)
                    throw new IllegalStateException("Private measurement cache cleanup was incomplete");
                normalCacheSizeAfter = coordinator.getDiagnosticProofCacheSize();
                normalCacheHitsAfter = coordinator.getDiagnosticProofCacheHits();
                normalCacheMissesAfter = coordinator.getDiagnosticProofCacheMisses();
                if (normalCacheSizeAfter != normalCacheSizeBefore ||
                        normalCacheHitsAfter != normalCacheHitsBefore ||
                        normalCacheMissesAfter != normalCacheMissesBefore)
                    throw new IllegalStateException(
                        "Private measurement changed the ordinary diagnostic proof cache");
            } catch (Throwable cacheFailure) {
                if (failure == null) {
                    failure = cacheFailure;
                    failurePhase = "measurement-cache-cleanup";
                    status = "FAIL";
                } else retain(failure, cacheFailure);
            }
            restoreCanaryScopeFlagInCurrentCallback();
            finished = true;
            timer.cancel();
            phase = finalPhase;
            if (restored && predecessorStillCurrent()) {
                sim.developerVerifierRunning = predecessorVerifierRunning;
            } else if (restored) {
                restored = false;
                if (failure == null) {
                    failure = new IllegalStateException(
                        "The predecessor changed before qualification cleanup completed");
                    failurePhase = finalPhase;
                    status = "FAIL";
                }
            }
            if (failure != null) status = "FAIL";
            publish(status, finalPhase);
            active = null;
            coldPublishedOwner = null;
            coldProof = null;
        }

        private boolean predecessorStillCurrent() {
            return sim.getGeneratedBoardInstance() == predecessorOwner &&
                sim.getGeneratedChallengeController() == predecessorController &&
                sim.elmList == predecessorGraph;
        }

        private void publish(String status, String state) {
            JSONObject result = new JSONObject();
            put(result, "schema", 2);
            put(result, "status", status);
            put(result, "phase", state);
            put(result, "seed", Long.toString(seed));
            put(result, "requestedSeed", requestedSeed);
            put(result, "request", requestCanonical);
            put(result, "normalAdmission", false);
            put(result, "measurementOnly", true);
            put(result, "coordinatorQualificationPassed", cold != null && warm != null && failure == null);
            put(result, "normalCatalogRegistered", PlayerFamilyCatalog.contains(Rb30Plan.FAMILY_ID));
            put(result, "playerPublished", false);
            put(result, "normalPlayerLaunch", false);
            put(result, "predecessorOwnerPresent", predecessorOwner != null);
            put(result, "restored", restored && predecessorStillCurrent());
            put(result, "cleanupComplete", (cold == null || cold.cleanupComplete) &&
                (warm == null || warm.cleanupComplete));
            put(result, "cleanupPending", cleanupPending || cleanupRetryActive);
            put(result, "cleanupRetryCount", cleanupRetryCount);
            if (cleanupOwnerKind != null) put(result, "cleanupOwnerKind", cleanupOwnerKind);
            if (cleanupPendingReason != null) put(result, "cleanupPendingReason", cleanupPendingReason);
            put(result, "totalElapsedMs", elapsed(startedAt));
            put(result, "phaseElapsedMs", Math.max(0, System.currentTimeMillis() -
                (runStartedAt == 0 ? startedAt : runStartedAt)));
            put(result, "maxJobMillis", MEASUREMENT_JOB_MILLIS);
            put(result, "defaultMaxJobMillis", GenerationCoordinator.MAX_JOB_MILLIS);
            put(result, "measurementMaxJobMillis", MEASUREMENT_JOB_MILLIS);
            put(result, "maxUnitMillis", GenerationCoordinator.MAX_STEP_MILLIS);
            put(result, "maxJobSteps", GenerationCoordinator.MAX_JOB_STEPS);
            put(result, "manualAdvancesPerTimer", 1);
            put(result, "measurementCacheCleared", measurementCacheCleared);
            put(result, "measurementCacheInitialSize", measurementCacheInitialSize);
            put(result, "measurementCacheSizeBeforeClear", measurementCacheSizeBeforeClear);
            put(result, "measurementCacheSizeAfterClear", measurementCacheSizeAfterClear);
            put(result, "measurementCacheHitsBefore", measurementCacheHitsBefore);
            put(result, "measurementCacheHitsAfter", coordinator.getDiagnosticMeasurementProofCacheHits());
            put(result, "measurementCacheMissesBefore", measurementCacheMissesBefore);
            put(result, "measurementCacheMissesAfter", coordinator.getDiagnosticMeasurementProofCacheMisses());
            put(result, "normalCacheSizeBefore", normalCacheSizeBefore);
            put(result, "normalCacheSizeAfter", normalCacheSizeAfter);
            put(result, "normalCacheHitsBefore", normalCacheHitsBefore);
            put(result, "normalCacheHitsAfter", normalCacheHitsAfter);
            put(result, "normalCacheMissesBefore", normalCacheMissesBefore);
            put(result, "normalCacheMissesAfter", normalCacheMissesAfter);
            if (scopeLossCanaryRequested) {
                put(result, "scopeLossCanaryQuery", "tsjQ30CoordinatorScopeLoss=true");
                put(result, "scopeLossCanaryRequested", true);
                put(result, "scopeLossCanaryInjected", scopeLossCanaryInjected);
                put(result, "scopeLossCanaryStage", scopeLossCanaryStage);
                put(result, "scopeLossCanaryProofUnitsBeforeLoss",
                    scopeLossCanaryProofUnitsBeforeLoss);
                put(result, "scopeLossCanaryFlagRestored", scopeLossCanaryFlagRestored);
            }
            if (scopeLossDetected) {
                put(result, "scopeLossDetected", true);
                put(result, "coordinatorRunningAfterScopeLoss", coordinator.isRunning());
                put(result, "savedOwnersAfterScopeLoss",
                    coordinator.retainsSavedOwnersForDeveloperVerification());
                put(result, "observationRetainedAfterScopeLoss",
                    coordinator.retainsObservationForDeveloperVerification());
            }
            result.put("cold", cold == null ? notRun() : cold.toJson());
            result.put("warm", warm == null ? notRun() : warm.toJson());
            if (failure != null) {
                put(result, "failurePhase", failurePhase == null ? phase : failurePhase);
                put(result, "failure", describe(failure));
            }
            if (cleanupFailure != null) put(result, "cleanupFailure", cleanupFailure);
            publishNative(result.toString(), status + ":" + state);
        }

        private static JSONObject notRun() {
            JSONObject result = new JSONObject();
            put(result, "outcome", "NOT_RUN");
            return result;
        }
    }

    private static final class RunRecord {
        private final String name;
        private final String outcome;
        private final long elapsedMillis;
        private final long wallElapsedMillis;
        private final long maxAdvanceMillis;
        private final long maxManualUnitMillis;
        private final int manualAdvanceCalls;
        private final int totalWork;
        private final int hypothesisWork;
        private final int proofCacheHitDelta;
        private final int proofCacheMissDelta;
        private final int proofCacheSize;
        private final int planCacheHitDelta;
        private final int planCacheMissDelta;
        private final Vector<StageRecord> stages;
        private final long maxJobMillis;
        private final long routingElapsedMillis;
        private final long proofElapsedMillis;
        private final Vector<WorkTimingRecord> diagnosticWorkTimings;
        private JSONObject ownerEvidence;
        private boolean cleanupComplete;
        private boolean ownerRestored;
        private long cleanupElapsedMillis;
        private String cleanupFailure;
        private int cleanupRetryCount;

        private RunRecord(String name, String outcome, long elapsedMillis,
                long wallElapsedMillis, long maxAdvanceMillis, long maxManualUnitMillis,
                int manualAdvanceCalls, int totalWork, int hypothesisWork,
                int proofCacheHitDelta, int proofCacheMissDelta, int proofCacheSize,
                int planCacheHitDelta, int planCacheMissDelta, Vector<StageRecord> stages,
                long maxJobMillis, long routingElapsedMillis, long proofElapsedMillis,
                Vector<WorkTimingRecord> diagnosticWorkTimings) {
            this.name = name;
            this.outcome = outcome;
            this.elapsedMillis = elapsedMillis;
            this.wallElapsedMillis = wallElapsedMillis;
            this.maxAdvanceMillis = maxAdvanceMillis;
            this.maxManualUnitMillis = maxManualUnitMillis;
            this.manualAdvanceCalls = manualAdvanceCalls;
            this.totalWork = totalWork;
            this.hypothesisWork = hypothesisWork;
            this.proofCacheHitDelta = proofCacheHitDelta;
            this.proofCacheMissDelta = proofCacheMissDelta;
            this.proofCacheSize = proofCacheSize;
            this.planCacheHitDelta = planCacheHitDelta;
            this.planCacheMissDelta = planCacheMissDelta;
            this.stages = stages;
            this.maxJobMillis = maxJobMillis;
            this.routingElapsedMillis = routingElapsedMillis;
            this.proofElapsedMillis = proofElapsedMillis;
            this.diagnosticWorkTimings = diagnosticWorkTimings;
        }

        static RunRecord capture(String name, GenerationJob job,
                GenerationCoordinator coordinator, long startedAt,
                long maxManualUnitMillis, int manualAdvanceCalls,
                int proofHitsAtStart, int proofMissesAtStart,
                int planHitsAtStart, int planMissesAtStart, long maxJobMillis) {
            Vector<StageRecord> stages = new Vector<StageRecord>();
            for (GenerationJob.Stage stage : GenerationJob.Stage.values())
                stages.add(new StageRecord(stage.name(), job.getStageWorkCount(stage),
                    job.getStageElapsedMillis(stage)));
            Vector<WorkTimingRecord> diagnosticWorkTimings = new Vector<WorkTimingRecord>();
            for (java.util.Map.Entry<String, long[]> entry :
                    coordinator.getLastDiagnosticMeasurementWorkTimingsForDeveloperVerification().entrySet())
                diagnosticWorkTimings.add(new WorkTimingRecord(entry.getKey(), entry.getValue()));
            return new RunRecord(name, job.getOutcome().name(), job.getElapsedMillis(),
                Math.max(0, System.currentTimeMillis() - startedAt),
                coordinator.getMaxAdvanceMillis(), maxManualUnitMillis, manualAdvanceCalls,
                job.getStepCount(), job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES),
                coordinator.getDiagnosticMeasurementProofCacheHits() - proofHitsAtStart,
                coordinator.getDiagnosticMeasurementProofCacheMisses() - proofMissesAtStart,
                coordinator.getDiagnosticMeasurementProofCacheSize(),
                coordinator.getPlanCacheHits() - planHitsAtStart,
                coordinator.getPlanCacheMisses() - planMissesAtStart, stages,
                maxJobMillis,
                coordinator.getLastRoutingElapsedMillisForDeveloperVerification(),
                coordinator.getLastProofElapsedMillisForDeveloperVerification(),
                diagnosticWorkTimings);
        }

        void captureOwner(GeneratedBoardInstance owner, ProofValue proof,
                GenerationReceipt receipt) {
            ownerEvidence = new JSONObject();
            put(ownerEvidence, "freshOwner", true);
            put(ownerEvidence, "ownerIdentity", System.identityHashCode(owner));
            put(ownerEvidence, "family", owner.getCircuitFamilyId());
            put(ownerEvidence, "seed", Long.toString(owner.getSeed()));
            put(ownerEvidence, "developerRoute", owner.isDeveloperOnlyFaultRoute());
            put(ownerEvidence, "physicalAdmission", owner.getPhysicalAdmission().identity());
            put(ownerEvidence, "generationReceiptStages", receipt.getStageCount());
            put(ownerEvidence, "generationReceiptWork", receipt.getWorkCount());
            ownerEvidence.put("diagnosticProof", proof.toJson());
        }

        JSONObject toJson() {
            JSONObject result = new JSONObject();
            put(result, "run", name);
            put(result, "outcome", outcome);
            put(result, "maxJobMillis", maxJobMillis);
            put(result, "elapsedMs", elapsedMillis);
            put(result, "wallElapsedMs", wallElapsedMillis);
            put(result, "maxAdvanceMs", maxAdvanceMillis);
            put(result, "maxManualUnitMs", maxManualUnitMillis);
            put(result, "manualAdvanceCalls", manualAdvanceCalls);
            put(result, "totalWork", totalWork);
            put(result, "hypothesisWork", hypothesisWork);
            put(result, "routingElapsedMs", routingElapsedMillis);
            put(result, "proofElapsedMs", proofElapsedMillis);
            put(result, "proofCacheHitDelta", proofCacheHitDelta);
            put(result, "proofCacheMissDelta", proofCacheMissDelta);
            put(result, "proofCacheSize", proofCacheSize);
            put(result, "planCacheHitDelta", planCacheHitDelta);
            put(result, "planCacheMissDelta", planCacheMissDelta);
            JSONArray stageValues = new JSONArray();
            for (StageRecord stage : stages) stageValues.set(stageValues.size(), stage.toJson());
            result.put("stages", stageValues);
            JSONArray workTimingValues = new JSONArray();
            for (WorkTimingRecord timing : diagnosticWorkTimings)
                workTimingValues.set(workTimingValues.size(), timing.toJson());
            result.put("diagnosticWorkTimings", workTimingValues);
            if (ownerEvidence != null) result.put("owner", ownerEvidence);
            put(result, "cleanupComplete", cleanupComplete);
            put(result, "ownerRestored", ownerRestored);
            put(result, "cleanupElapsedMs", cleanupElapsedMillis);
            put(result, "cleanupRetryCount", cleanupRetryCount);
            if (cleanupFailure != null) put(result, "cleanupFailure", cleanupFailure);
            return result;
        }
    }

    private static final class StageRecord {
        final String name;
        final int work;
        final long elapsedMillis;

        StageRecord(String name, int work, long elapsedMillis) {
            this.name = name;
            this.work = work;
            this.elapsedMillis = elapsedMillis;
        }

        JSONObject toJson() {
            JSONObject result = new JSONObject();
            put(result, "name", name);
            put(result, "work", work);
            put(result, "elapsedMs", elapsedMillis);
            return result;
        }
    }

    private static final class WorkTimingRecord {
        final String label;
        final long units;
        final long elapsedMillis;
        final long maxUnitMillis;

        WorkTimingRecord(String label, long[] timing) {
            if (label == null || timing == null || timing.length != 3)
                throw new IllegalArgumentException("Invalid diagnostic work timing snapshot");
            this.label = label;
            this.units = timing[0];
            this.elapsedMillis = timing[1];
            this.maxUnitMillis = timing[2];
        }

        JSONObject toJson() {
            JSONObject result = new JSONObject();
            put(result, "label", label);
            put(result, "units", units);
            put(result, "elapsedMs", elapsedMillis);
            put(result, "maxUnitMs", maxUnitMillis);
            return result;
        }
    }

    /** A value-only copy of proof evidence, with no board or controller owner. */
    private static final class ProofValue {
        final String family;
        final String topology;
        final String contextCanonical;
        final boolean contextTrusted;
        final String providerId;
        final String programIdentity;
        final String partitionCanonical;
        final boolean warmReuse;
        final Vector<GeneratedDiagnosticSolvabilityEvidence> evidence;
        final int sampleCount;
        final int partitionNodeCount;
        final int partitionLeafCount;
        final long elapsedMillis;
        final int workUnits;
        final boolean explicitCompletion;

        private ProofValue(String family, String topology, String contextCanonical,
                boolean contextTrusted, String providerId, String programIdentity,
                String partitionCanonical, boolean warmReuse,
                Vector<GeneratedDiagnosticSolvabilityEvidence> evidence, int sampleCount,
                int partitionNodeCount, int partitionLeafCount, long elapsedMillis,
                int workUnits, boolean explicitCompletion) {
            this.family = family;
            this.topology = topology;
            this.contextCanonical = contextCanonical;
            this.contextTrusted = contextTrusted;
            this.providerId = providerId;
            this.programIdentity = programIdentity;
            this.partitionCanonical = partitionCanonical;
            this.warmReuse = warmReuse;
            this.evidence = evidence;
            this.sampleCount = sampleCount;
            this.partitionNodeCount = partitionNodeCount;
            this.partitionLeafCount = partitionLeafCount;
            this.elapsedMillis = elapsedMillis;
            this.workUnits = workUnits;
            this.explicitCompletion = explicitCompletion;
        }

        static ProofValue capture(GeneratedDiagnosticProofReceipt receipt,
                GeneratedBoardInstance owner, boolean explicitCompletion) {
            if (receipt == null || owner == null || receipt.getContextKey() == null ||
                    receipt.getPartitionPlan() == null)
                return null;
            GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
            if (provider == null || provider.getObservationProgram() == null ||
                    !same(provider.getProviderId(), receipt.getProviderId()) ||
                    !same(provider.getObservationProgram().canonical(), receipt.getProgramIdentity()))
                return null;
            receipt.requireAssessmentOwner(owner);
            GeneratedDiagnosticContextKey context = receipt.getContextKey();
            if (!context.isTrustedCapture()) return null;
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
            int samples = 0;
            for (GeneratedDiagnosticSolvabilityEvidence item : evidence) {
                if (item == null) return null;
                samples += item.getStaticProofSampleCount();
            }
            return new ProofValue(owner.getCircuitFamilyId(), owner.getTopologyVariantId(),
                context.canonical(), context.isTrustedCapture(), receipt.getProviderId(),
                receipt.getProgramIdentity(), receipt.getPartitionPlan().canonical(),
                receipt.isWarmReuseForDeveloperVerification(), evidence, samples,
                receipt.getPartitionPlan().getNodeCount(),
                receipt.getPartitionPlan().getLeafCount(), receipt.getElapsedMillis(),
                GeneratedDiagnosticProofService.requiredWorkUnits(owner, explicitCompletion),
                explicitCompletion);
        }

        boolean sameValue(ProofValue other) {
            if (other == null || !same(family, other.family) ||
                    !same(topology, other.topology) ||
                    !same(contextCanonical, other.contextCanonical) ||
                    contextTrusted != other.contextTrusted ||
                    !same(providerId, other.providerId) ||
                    !same(programIdentity, other.programIdentity) ||
                    !same(partitionCanonical, other.partitionCanonical) ||
                    partitionNodeCount != other.partitionNodeCount ||
                    partitionLeafCount != other.partitionLeafCount ||
                    workUnits != other.workUnits || explicitCompletion != other.explicitCompletion ||
                    evidence.size() != other.evidence.size() || sampleCount != other.sampleCount)
                return false;
            for (int index = 0; index < evidence.size(); index++)
                if (!sameEvidence(evidence.get(index), other.evidence.get(index))) return false;
            return true;
        }

        JSONObject toJson() {
            JSONObject result = new JSONObject();
            put(result, "status", "PASS");
            put(result, "family", family);
            put(result, "topology", topology);
            put(result, "warmReuseReceipt", warmReuse);
            put(result, "providerId", providerId);
            put(result, "programIdentity", programIdentity);
            put(result, "contextCanonical", contextCanonical);
            put(result, "contextTrusted", contextTrusted);
            put(result, "partitionCanonical", partitionCanonical);
            put(result, "partitionNodeCount", partitionNodeCount);
            put(result, "partitionLeafCount", partitionLeafCount);
            put(result, "hypothesisCount", evidence.size());
            put(result, "actualSampleCount", sampleCount);
            put(result, "elapsedMillis", elapsedMillis);
            put(result, "workUnits", workUnits);
            put(result, "explicitCompletion", explicitCompletion);
            JSONArray rows = new JSONArray();
            for (GeneratedDiagnosticSolvabilityEvidence item : evidence) {
                JSONObject row = new JSONObject();
                put(row, "hypothesisKey", item.getHypothesisKey());
                put(row, "routeId", item.getRouteId());
                put(row, "admittedCandidateCount", item.getAdmittedCandidateCount());
                put(row, "repairReachable", item.isRepairReachable());
                put(row, "customerRetestPassed", item.isCustomerRetestPassed());
                put(row, "stateIsolated", item.isStateIsolated());
                put(row, "deterministicResult", item.getDeterministicResult());
                put(row, "deterministicRejectionReason", item.getDeterministicRejectionReason());
                put(row, "equivalentRepairClass", item.getEquivalentRepairClass());
                row.put("executedRepairActionIds", strings(item.getExecutedRepairActionIds()));
                JSONArray sampleRows = new JSONArray();
                Vector<GeneratedDiagnosticSample> samples = item.getSolverSamples();
                for (GeneratedDiagnosticSample sample : samples) {
                    JSONObject sampleRow = new JSONObject();
                    put(sampleRow, "id", sample.getSampleId());
                    put(sampleRow, "outcome", sample.getOutcome().name());
                    if (!sample.isOverRange()) {
                        put(sampleRow, "value", sample.getValue());
                        put(sampleRow, "tolerance", sample.getComparisonTolerance());
                    }
                    sampleRows.set(sampleRows.size(), sampleRow);
                }
                put(row, "sampleCount", samples.size());
                row.put("samples", sampleRows);
                rows.set(rows.size(), row);
            }
            result.put("evidence", rows);
            return result;
        }
    }

    private static boolean sameEvidence(GeneratedDiagnosticSolvabilityEvidence first,
            GeneratedDiagnosticSolvabilityEvidence second) {
        if (first == null || second == null ||
                !same(first.getRouteId(), second.getRouteId()) ||
                !same(first.getFamilyId(), second.getFamilyId()) ||
                first.getSeed() != second.getSeed() ||
                !same(first.getHypothesisKey(), second.getHypothesisKey()) ||
                first.getAdmittedCandidateCount() != second.getAdmittedCandidateCount() ||
                first.getAdmittedPhysicalOwnerCount() != second.getAdmittedPhysicalOwnerCount() ||
                first.getDeclaredPlanDepth() != second.getDeclaredPlanDepth() ||
                !first.getDeclaredTemplateIds().equals(second.getDeclaredTemplateIds()) ||
                !first.getDeclaredProbeTargetIds().equals(second.getDeclaredProbeTargetIds()) ||
                !first.getDeclaredInputPowerTransitions().equals(second.getDeclaredInputPowerTransitions()) ||
                !first.getDeclaredIsolationActionIds().equals(second.getDeclaredIsolationActionIds()) ||
                !first.getDeclaredRepairActionIds().equals(second.getDeclaredRepairActionIds()) ||
                !first.getDeclaredWorkflowActionIds().equals(second.getDeclaredWorkflowActionIds()) ||
                !first.getDeclaredPlayerOperationIds().equals(second.getDeclaredPlayerOperationIds()) ||
                !first.getDeclaredMeterModeIds().equals(second.getDeclaredMeterModeIds()) ||
                !first.getDeclaredTemporalWaitSampleIds().equals(second.getDeclaredTemporalWaitSampleIds()) ||
                !first.getDeclaredRailDomainIds().equals(second.getDeclaredRailDomainIds()) ||
                first.hasDeclaredParallelPathAmbiguity() != second.hasDeclaredParallelPathAmbiguity() ||
                !first.getExecutedActionIds().equals(second.getExecutedActionIds()) ||
                !first.getExecutedRepairActionIds().equals(second.getExecutedRepairActionIds()) ||
                !first.getExecutedMeterModeIds().equals(second.getExecutedMeterModeIds()) ||
                !first.getExecutedInputPowerTransitions().equals(second.getExecutedInputPowerTransitions()) ||
                !first.getExecutedIsolationActionIds().equals(second.getExecutedIsolationActionIds()) ||
                !first.getExecutedTemporalWaitSamples().equals(second.getExecutedTemporalWaitSamples()) ||
                first.getMeasuredExecutionDepth() != second.getMeasuredExecutionDepth() ||
                first.getCompletedSemanticActions() != second.getCompletedSemanticActions() ||
                first.getRepairSemantics() == null || second.getRepairSemantics() == null ||
                !first.getRepairSemantics().isEquivalentTo(second.getRepairSemantics()) ||
                first.hasUnaffectedFunctionRetestObservation() !=
                    second.hasUnaffectedFunctionRetestObservation() ||
                !same(first.getEquivalentRepairClass(), second.getEquivalentRepairClass()) ||
                !same(first.getDeterministicResult(), second.getDeterministicResult()) ||
                !same(first.getDeterministicRejectionReason(), second.getDeterministicRejectionReason()) ||
                first.isRepairReachable() != second.isRepairReachable() ||
                first.isCustomerRetestPassed() != second.isCustomerRetestPassed() ||
                first.isStateIsolated() != second.isStateIsolated() ||
                first.getStaticProofSampleCount() != second.getStaticProofSampleCount())
            return false;
        for (int sampleIndex = 0; sampleIndex < first.getStaticProofSampleCount(); sampleIndex++) {
            GeneratedDiagnosticSample left = first.getStaticProofSample(sampleIndex);
            GeneratedDiagnosticSample right = second.getStaticProofSample(sampleIndex);
            if (left == null || right == null || !same(left.getSampleId(), right.getSampleId()) ||
                    left.getOutcome() != right.getOutcome()) return false;
            if (!left.isOverRange() &&
                    (Double.doubleToLongBits(left.getValue()) != Double.doubleToLongBits(right.getValue()) ||
                     Double.doubleToLongBits(left.getComparisonTolerance()) !=
                        Double.doubleToLongBits(right.getComparisonTolerance()))) return false;
        }
        return true;
    }

    private static final class CleanupResult {
        final boolean restored;
        final boolean complete;
        final long elapsedMillis;
        final String failure;

        CleanupResult(boolean restored, boolean complete, long elapsedMillis, String failure) {
            this.restored = restored;
            this.complete = complete;
            this.elapsedMillis = elapsedMillis;
            this.failure = failure;
        }
    }

    private static boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static long elapsed(long began) {
        return Math.max(0, System.currentTimeMillis() - began);
    }

    private static String failureText(Throwable failure) {
        return failure == null || failure.getMessage() == null ? "" : ": " + failure.getMessage();
    }

    private static String describe(Throwable failure) {
        if (failure == null) return "unknown failure";
        String message = failure.getMessage();
        return failure.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static Throwable retain(Throwable first, Throwable next) {
        if (first == null) return next;
        if (next != null && next != first) first.addSuppressed(next);
        return first;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void put(JSONObject object, String key, String value) {
        if (value != null) object.put(key, new JSONString(value));
    }
    private static void put(JSONObject object, String key, int value) {
        object.put(key, new JSONNumber(value));
    }
    private static void put(JSONObject object, String key, double value) {
        object.put(key, new JSONNumber(value));
    }
    private static void put(JSONObject object, String key, long value) {
        object.put(key, new JSONNumber(value));
    }
    private static void put(JSONObject object, String key, boolean value) {
        object.put(key, JSONBoolean.getInstance(value));
    }
    private static JSONArray strings(Vector<String> values) {
        JSONArray result = new JSONArray();
        for (String value : values) result.set(result.size(), new JSONString(value));
        return result;
    }

    private static native void clearReport() /*-{
        var root = $doc.documentElement;
        root.removeAttribute("data-tsj-q30-coordinator-report");
        root.removeAttribute("data-tsj-q30-coordinator-state");
    }-*/;

    private static native void publishNative(String report, String state) /*-{
        var root = $doc.documentElement;
        root.setAttribute("data-tsj-q30-coordinator-report", report);
        root.setAttribute("data-tsj-q30-coordinator-state", state);
    }-*/;
}
