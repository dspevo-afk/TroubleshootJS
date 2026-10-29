package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import java.util.Vector;

/**
 * Cold RB56 qualification through the actual staged coordinator/D01.
 * The explicit normal mode uses public requests and normal admission; private
 * requests retain their own exact authority independently of catalog enablement.
 * The optional view uses the shared workbench; qualified private PlayerSession adoption is explicit.
 */
final class Rb56QualificationVerifier {
    static final int BENCH_HOLD_MILLIS = 120000;
    static final int SESSION_BENCH_HOLD_MILLIS = 360000;
    static final String CANARY = "rb56-explicit-after-publish-canary";
    private static Runner active;

    private Rb56QualificationVerifier() { }

    static void start(CirSim sim, String seedText, boolean forcedAfterPublish, boolean keepBench) {
        require(sim != null && sim.troubleshootDebug && sim.troubleshootRb56Verification,
            "Explicit RB56 developer route is required");
        require(active == null && !sim.developerVerifierRunning && sim.playerSessionController == null &&
            sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
            !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning(),
            "RB56 qualification requires an idle private workbench owner");
        require(sim.generationCoordinator != null && !sim.generationCoordinator.isRunning() &&
            !sim.generationCoordinator.isAdvancing() &&
            !sim.generationCoordinator.retainsSavedOwnersForDeveloperVerification(),
            "RB56 qualification requires the idle production coordinator");
        require(GenerationCoordinator.MAX_JOB_MILLIS == 90000L &&
            GenerationCoordinator.MAX_JOB_STEPS == 640 && GenerationCoordinator.MAX_STEP_MILLIS == 5000L,
            "RB56 qualification requires unchanged 90s/640/5s generation limits");
        requireQualificationFamily();
        String canonicalSeed = seedText == null ? "0" : seedText;
        final long seed;
        try { seed = Long.parseLong(canonicalSeed); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Invalid RB56 signed-long seed"); }
        require(Long.toString(seed).equals(canonicalSeed), "RB56 seed must be canonical");
        Task41SimulationSnapshot snapshot = sim.getGeneratedBoardInstance() == null ?
            Task41SimulationSnapshot.captureForFreshInstallation(sim) : Task41SimulationSnapshot.capture(sim);
        Runner runner = new Runner(sim, seed, snapshot, forcedAfterPublish, keepBench);
        active = runner;
        runner.begin();
    }

    /** Cleanup authority is this retained transaction, even while its UI has debug=false. */
    static boolean finishRetainedBench(CirSim sim) {
        Runner runner = active;
        if (runner == null || runner.sim != sim || !runner.retained || runner.finished) return false;
        runner.endBench();
        return true;
    }

    /** A private file lease is exact to this retained controller, owner and coordinator. */
    static boolean ownsPersistenceLease(CirSim sim, PlayerSessionController controller) {
        Runner runner = active;
        return runner != null && runner.sim == sim && runner.persistence && runner.retained &&
            !runner.finished && !runner.cleanupPending && runner.retainedSession == controller &&
            sim.playerSessionController == controller && runner.coordinator.getJob() == runner.job &&
            controller.session.owner() == runner.published &&
            (runner.restoringSession || sim.getGeneratedBoardInstance() == runner.published);
    }

    static PlayerSessionSave captureSession(CirSim sim, PlayerSessionController controller, String build) {
        require(ownsPersistenceLease(sim, controller) && !active.restoringSession,
            "Private session file belongs to a closed or busy qualification view");
        boolean debug = sim.troubleshootDebug, verifier = sim.developerVerifierRunning;
        try {
            sim.troubleshootDebug = true; sim.developerVerifierRunning = true;
            return PlayerSessionState.captureForQualification(sim, active.request,
                active.published.getPhysicalBoardRuntime().getSessionHistory().operations(), build);
        } finally { sim.troubleshootDebug = debug; sim.developerVerifierRunning = verifier; }
    }

    static void restoreSession(CirSim sim, PlayerSessionController controller, PlayerSessionSave saved) {
        require(ownsPersistenceLease(sim, controller) && !active.restoringSession,
            "Private session file belongs to a closed or busy qualification view");
        PlayerSessionReplay identity = PlayerSessionReplay.parse(saved.replay);
        if (!identity.isPrivateQualification())
            throw new IllegalArgumentException("Choose a current private file for this exact qualification board. The current board is unchanged.");
        GenerationRequest restoreRequest = active.request.withRb56PlacementInterpretationVersion(
            identity.rb56PlacementInterpretationVersion());
        if (!identity.matches(restoreRequest))
            throw new IllegalArgumentException("Choose a current private file for this exact qualification board. The current board is unchanged.");
        active.beginSessionRestore(saved, restoreRequest);
    }

    static boolean retryPendingCleanup(CirSim sim) {
        Runner runner = active;
        if (runner == null || runner.sim != sim || !runner.cleanupPending) return false;
        runner.finish();
        return true;
    }

    private static void requireQualificationFamily() {
        require(PlayerFamilyCatalog.isRegistered(Rb56Plan.FAMILY_ID) &&
            PlayerFamilyCatalog.stagedCapability(Rb56Plan.FAMILY_ID) instanceof Rb56PlayerFamilyCapability &&
            PlayerFamilyCatalog.stagedCapability(Rb56Plan.FAMILY_ID).supportsPrivateQualification(),
            "RB56 qualification requires its registered private-capable provider");
    }

    /** Rejected proof work remains in the shared receipt, separate from the accepted proof. */
    static void requireProofWork(GenerationJob job, int completedUnits, int requiredUnits) {
        GenerationReceipt receipt = job == null ? null : job.getReceipt();
        java.util.List<GenerationJob.Attempt> attempts = job == null ? null : job.getAttempts();
        require(receipt != null && attempts != null && !attempts.isEmpty() && completedUnits > 0 &&
            completedUnits <= requiredUnits, "Complete accepted proof work is missing");
        job.validateReceipt(receipt);
        int totalProofUnits = 0;
        for (GenerationJob.Attempt attempt : attempts) totalProofUnits += attempt.proofUnits;
        GenerationJob.Attempt accepted = attempts.get(attempts.size() - 1);
        require(accepted.outcome == GenerationJob.Outcome.PASS && accepted.proofUnits == completedUnits &&
            receipt.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == totalProofUnits,
            "Accepted and rejected proof work do not match the shared generation receipt");
    }

    private static final class Runner {
        final CirSim sim;
        final long seed;
        final boolean normalPopulation, normalCatalogAtStart, productionDriven;
        final PlayerLaunchRequest normalLaunch;
        long acceptedSeed;
        final Task41SimulationSnapshot snapshot;
        final GenerationCoordinator coordinator;
        GenerationRequest request;
        GenerationRequest pendingRestoreRequest;
        final GenerationJob previousJob;
        final GeneratedBoardInstance predecessor;
        final GeneratedChallengeController predecessorController;
        final Vector<CircuitElm> predecessorGraph;
        final boolean originalDebug, originalVerifierRunning, forced, keepBench, modernBench, collectDifficulty, collectMediumProfile, persistence;
        final int benchHoldMillis;
        final JSONObject report = new JSONObject();
        final Timer advanceTimer = new Timer() { public void run() { advance(); } };
        final Timer holdTimer = new Timer() { public void run() { endBench(); } };
        GenerationJob job;
        GeneratedBoardInstance published;
        GeneratedChallengeController publishedController;
        Vector<CircuitElm> publishedGraph, retirementElements;
        Button endView;
        PlayerSessionController retainedSession;
        boolean sessionReleased;
        int proofHitsBefore, proofMissesBefore, planHitsBefore, planMissesBefore, retirementCursor;
        long maxManualUnitMillis, startedAt, cleanupStartedAt;
        int manualAdvanceCount;
        boolean admitted, retained, finished, cleanupPending, restored, disposed, snapshotAttempted;
        boolean driverStarted;
        boolean powerDisconnected, customerRetestPassed, restoringSession;
        int restoreToken, supersededCursor;
        GeneratedBoardInstance superseded;
        final JSONArray sessionRestores = new JSONArray();
        Throwable failure;
        String phase = "STARTUP";

        Runner(CirSim sim, long seed, Task41SimulationSnapshot snapshot, boolean forced, boolean keepBench) {
            this.sim = sim; this.seed = seed; this.snapshot = snapshot;
            this.forced = forced; this.keepBench = keepBench;
            modernBench = keepBench && "true".equals(Window.Location.getParameter("tsjRb56PlayerSession"));
            collectDifficulty = "true".equals(Window.Location.getParameter("tsjRb56Difficulty"));
            collectMediumProfile = "true".equals(Window.Location.getParameter("tsjRb56Medium"));
            normalPopulation = "true".equals(Window.Location.getParameter("tsjRb56Normal"));
            productionDriven = "true".equals(Window.Location.getParameter("tsjRb56ProductionDrive"));
            require(!productionDriven || normalPopulation,
                "Owner-driven coordinator diagnostics require normal-population mode");
            normalCatalogAtStart = PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID);
            require(!normalPopulation || (!keepBench && collectDifficulty && collectMediumProfile),
                "Normal population qualification requires cold profile export without a retained private bench");
            require(!collectMediumProfile || collectDifficulty,
                "MEDIUM qualification requires the complete private observation export");
            persistence = modernBench && "true".equals(Window.Location.getParameter("tsjRb56Persistence"));
            // The explicit debug-only route may retain this same owner for a bounded session.
            benchHoldMillis = keepBench && "true".equals(
                Window.Location.getParameter("tsjRb56Session")) ? SESSION_BENCH_HOLD_MILLIS : BENCH_HOLD_MILLIS;
            coordinator = sim.generationCoordinator;
            previousJob = coordinator.getJob();
            predecessor = sim.getGeneratedBoardInstance();
            predecessorController = sim.getGeneratedChallengeController();
            predecessorGraph = sim.elmList;
            originalDebug = sim.troubleshootDebug;
            originalVerifierRunning = sim.developerVerifierRunning;
            normalLaunch = normalPopulation ? PlayerLaunchRequest.random(
                Rb56Plan.FAMILY_ID, Long.toString(seed), "MEDIUM") : null;
            request = normalPopulation ? GenerationRequest.player(normalLaunch) :
                GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, seed);
            acceptedSeed = seed;
        }

        void begin() {
            sim.developerVerifierRunning = true;
            startedAt = System.currentTimeMillis();
            put(report, "protocol", normalPopulation ? "TSJ-RB56-NORMAL-QUALIFICATION-1" : "TSJ-RB56-QUALIFICATION-1");
            put(report, "scope", normalPopulation ? "NORMAL_MEDIUM_COLD_POPULATION" :
                modernBench ? "PRIVATE_PLAYER_SESSION" : "PRIVATE_SHARED_WORKBENCH");
            put(report, "family", Rb56Plan.FAMILY_ID);
            put(report, "seed", Long.toString(seed));
            put(report, "rootSeed", Long.toString(seed));
            put(report, "normalRequest", normalPopulation);
            put(report, "requestManifest", request.canonical());
            put(report, "layoutVersion", SeededPcbLayoutGenerator.CURRENT_VERSION);
            put(report, "replayEpoch", PlayerLaunchRequest.EPOCH);
            put(report, "sourceEpoch", GenerationDependencyContext.INTERPRETATION_EPOCH);
            put(report, "modelEpoch", GenerationDependencyContext.CIRCUIT_DUMP_EPOCH);
            put(report, "planVersion", Rb56Plan.PLAN_VERSION);
            if (productionDriven)
                put(report, "generationDriver", "NORMAL_COORDINATOR_ASYNC");
            put(report, "maxJobMillis", 90000L); put(report, "maxJobSteps", 640); put(report, "maxUnitMillis", 5000L);
            put(report, "forcedAfterPublish", forced);
            put(report, "normalCatalogEnabled", PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID));
            publish("RUNNING", "RUNNING:rb56");
            try {
                phase = "COLD_GENERATION";
                if (normalPopulation) coordinator.clearDiagnosticProofCacheForDeveloperVerification();
                else coordinator.clearDiagnosticMeasurementProofCacheForDeveloperVerification();
                require((normalPopulation ? coordinator.getDiagnosticProofCacheSize() :
                    coordinator.getDiagnosticMeasurementProofCacheSize()) == 0, "Selected D01 cache was not cleared");
                proofHitsBefore = proofCacheHits();
                proofMissesBefore = proofCacheMisses();
                planHitsBefore = coordinator.getPlanCacheHits();
                planMissesBefore = coordinator.getPlanCacheMisses();
                if (productionDriven) coordinator.startAsynchronousForDeveloperVerification(request);
                else if (normalPopulation) coordinator.startForDeveloperVerification(request);
                else coordinator.startForDiagnosticCacheVerification(request, 90000L);
                job = coordinator.getJob();
                require(job != null && job != previousJob, "Coordinator did not start a fresh job");
                driverStarted = true;
                if (productionDriven) {
                    require(!coordinator.usesForegroundBudgetForDeveloperVerification() &&
                        !coordinator.isProgressPaused() &&
                        coordinator.isPageVisibleForDeveloperVerification(),
                        "Owner-driven diagnostic cannot exclude foreground-paused time from its 90-second limit");
                    captureOwnerDrivenMetadata();
                }
                advanceTimer.schedule(productionDriven ? 25 : 1);
            } catch (Throwable problem) {
                if (coordinator.getJob() != previousJob) {
                    job = coordinator.getJob();
                    driverStarted = true;
                }
                recordFailure(problem); finish();
            }
        }

        void advance() {
            if (restoringSession) { advanceSessionRestore(); return; }
            try {
                require(sim.troubleshootDebug && sim.developerVerifierRunning && coordinator.getJob() == job,
                    "RB56 private generation lost its scope or job");
                requireQualificationFamily();
                if (productionDriven) {
                    require(!coordinator.usesForegroundBudgetForDeveloperVerification() &&
                        !coordinator.isProgressPaused() &&
                        coordinator.isPageVisibleForDeveloperVerification(),
                        "Owner-driven diagnostic cannot exclude foreground-paused time from its 90-second limit");
                    captureOwnerDrivenMetadata();
                    if (job.isRunning()) { advanceTimer.schedule(25); return; }
                } else {
                    long unitStart = System.currentTimeMillis();
                    manualAdvanceCount++;
                    coordinator.advanceForDeveloperVerification();
                    maxManualUnitMillis = Math.max(maxManualUnitMillis,
                        System.currentTimeMillis() - unitStart);
                    if (job.isRunning()) { advanceTimer.schedule(1); return; }
                }
                if (job.getOutcome() != GenerationJob.Outcome.PASS) {
                    if (job.getFailure() != null) throw job.getFailure();
                    throw new IllegalStateException("RB56 coordinator outcome=" + job.getOutcome().name());
                }
                if (productionDriven) {
                    require(manualAdvanceCount == 0 && maxManualUnitMillis == 0,
                        "Owner-driven diagnostic unexpectedly used manual coordinator advances");
                    require(coordinator.getYieldCount() > 0,
                        "Owner-driven diagnostic completed without a production coordinator yield");
                }
                // Capture the actual published owner BEFORE checking it, so failure can retire it.
                published = sim.getGeneratedBoardInstance();
                publishedController = sim.getGeneratedChallengeController();
                publishedGraph = sim.elmList;
                phase = "PUBLISHED_ADMISSION";
                validatePublished();
                admitted = true;
                if (forced) throw new IllegalStateException(CANARY);
                if (keepBench) retainBench(); else finish();
            } catch (Throwable problem) { recordFailure(problem); finish(); }
        }

        void validatePublished() {
            int acceptedOrdinal = 0;
            if (normalPopulation) {
                require(published != null, "Normal candidate did not publish an owner");
                PlayerLaunchRequest exact = normalLaunch.accepted(published.getSeed());
                acceptedSeed = exact.seed;
                while (acceptedOrdinal < request.candidateCount() &&
                        request.candidate(acceptedOrdinal).getDescriptor().getRootSeed() != acceptedSeed)
                    acceptedOrdinal++;
                require(acceptedOrdinal < request.candidateCount(), "Normal publication is outside its canonical candidates");
                put(report, "seed", Long.toString(acceptedSeed));
                put(report, "acceptedOrdinal", acceptedOrdinal);
                put(report, "publicReplay", exact.replay());
            }
            require(published != null && published != predecessor && publishedController != null &&
                publishedController != predecessorController && publishedGraph != predecessorGraph &&
                published.getSeed() == acceptedSeed && Rb56Plan.FAMILY_ID.equals(published.getCircuitFamilyId()) &&
                !published.isDeveloperOnlyFaultRoute() && publishedController.isReady() &&
                !publishedController.isDeveloperVerificationScopeActive(), "Published RB56 owner is invalid");
            require(published.getPhysicalAdmission() != null &&
                MediumBoardNormalAdmission.RB56_IDENTITY.equals(published.getPhysicalAdmission().identity()),
                "Published RB56 normal physical identity is missing");
            GenerationExecutionPolicy.RB56.requireOwner(published);
            GeneratedRuntimeInvariant.verify(sim, published, sim.getBoardModificationController(), sim.elmList);
            int packages = published.getBoard().getComponentIds().size();
            require(packages >= 40 && packages <= 60, "Actual RB56 package census is outside 40..60");
            for (CircuitElm element : published.getSimulationElements())
                require(!predecessorGraph.contains(element), "Published RB56 graph overlaps predecessor");
            GenerationReceipt receipt = job.getReceipt();
            require(receipt != null, "Actual generation receipt is missing");
            job.validateReceipt(receipt);
            require(receipt.getManifest().equals(normalPopulation ?
                    request.candidateManifest(acceptedOrdinal) : request.canonical()),
                "Publication receipt differs from its exact admitted request");
            put(report, "acceptedManifest", receipt.getManifest());
            require(receipt.isCompleteInternal() && receipt.getStageCount() == 6 &&
                receipt.getMaxJobMillisInternal() == 90000L && receipt.getMaxStepsInternal() == 640 &&
                receipt.getMaxStepMillisInternal() == 5000L && receipt.getWorkCount() <= 640 &&
                receipt.getElapsedMillis() <= 90000L && maxManualUnitMillis <= 5000L,
                "RB56 issuer-bound receipt or actual execution limits failed");
            GeneratedChallengeLifecycleEvidence lifecycle = publishedController.getLifecycleEvidence();
            require(lifecycle.healthyGenerationInstalled && lifecycle.healthyGraphAnalyzedAfterTimeAdvance &&
                lifecycle.healthyFamilyValidated && lifecycle.selectedFaultApplied &&
                lifecycle.faultedGraphAnalyzedAfterTimeAdvance && lifecycle.selectedFaultValidated &&
                lifecycle.scenarioCompatibilityValidated && lifecycle.readyAfterValidation,
                "RB56 healthy/fault/scenario lifecycle is incomplete");
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(published);
            GeneratedDiagnosticProofReceipt proof = publishedController.getDiagnosticProofReceipt();
            require(proof != null && !proof.isWarmReuseForDeveloperVerification() &&
                proof.getContextKey() != null && proof.getContextKey().isTrustedCapture(), "Cold production D01 receipt is missing");
            proof.requireAssessmentOwner(published);
            require(proof.getProviderId().equals(published.getDiagnosticProvider().getProviderId()) &&
                proof.getProgramIdentity().equals(published.getDiagnosticProvider().getObservationProgram().canonical()),
                "Production D01 provider/program does not match the actual owner");
            proof.getPartitionPlan().validateAgainst(published.getFaultCandidates(),
                published.getDiagnosticProvider().getObservationProgram(), proof.getEvidence());
            Vector<String> keys = new Vector<String>();
            Vector<String> expectedSamples = new Vector<String>();
            for (GeneratedDiagnosticProgram.Step step : published.getDiagnosticProvider().getObservationProgram().getSteps()) {
                switch (step.kind) {
                case DC_VOLTAGE: case RESISTANCE: case CONTINUITY: expectedSamples.add(step.id); break;
                case DIODE: expectedSamples.add(step.id + "_VOLTAGE"); expectedSamples.add(step.id + "_CURRENT"); break;
                default: break;
                }
            }
            int samples = 0;
            JSONArray proofRows = new JSONArray();
            for (GeneratedDiagnosticSolvabilityEvidence row : proof.getEvidence()) {
                require(Rb56Plan.FAMILY_ID.equals(row.getFamilyId()) && row.getSeed() == acceptedSeed &&
                    row.getAdmittedCandidateCount() == published.getDiagnosticSolvabilityContract().getAdmittedCandidateCount() &&
                    row.getAdmittedPhysicalOwnerCount() == published.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount() &&
                    row.isRepairReachable() && row.isCustomerRetestPassed() && row.isStateIsolated() &&
                    row.hasUnaffectedFunctionRetestObservation() && !row.getExecutedRepairActionIds().isEmpty() &&
                    "PASS".equals(row.getDeterministicResult()) && "NONE".equals(row.getDeterministicRejectionReason()),
                    "Production D01 hypothesis did not prove repair/retest/isolation");
                keys.add(row.getHypothesisKey());
                Vector<String> actualSamples = new Vector<String>();
                for (GeneratedDiagnosticSample sample : row.getSolverSamples()) {
                    actualSamples.add(sample.getSampleId());
                    if (!sample.isOverRange())
                        require(finite(sample.getValue()) && finite(sample.getComparisonTolerance()), "D01 numeric sample is nonfinite");
                    samples++;
                }
                require(!expectedSamples.isEmpty() && expectedSamples.equals(actualSamples),
                    "D01 hypothesis observations do not match the complete production program");
                JSONObject exported = new JSONObject();
                put(exported, "ordinal", proofRows.size());
                put(exported, "hypothesisDigest", PlayerSessionFingerprint.of(row.getHypothesisKey()));
                put(exported, "repairSemanticsDigest", PlayerSessionFingerprint.of(row.getRepairSemantics().stableDescription()));
                put(exported, "family", row.getFamilyId()); put(exported, "seed", Long.toString(row.getSeed()));
                put(exported, "repairReachable", row.isRepairReachable());
                put(exported, "customerRetestPassed", row.isCustomerRetestPassed());
                put(exported, "stateIsolated", row.isStateIsolated());
                put(exported, "unaffectedFunctionRetested", row.hasUnaffectedFunctionRetestObservation());
                put(exported, "result", row.getDeterministicResult());
                put(exported, "rejection", row.getDeterministicRejectionReason());
                JSONArray actionIds = new JSONArray(), sampleIds = new JSONArray();
                for (String action : row.getExecutedRepairActionIds()) actionIds.set(actionIds.size(), new JSONString(action));
                for (String sample : actualSamples) sampleIds.set(sampleIds.size(), new JSONString(sample));
                exported.put("repairActions", actionIds); exported.put("sampleIds", sampleIds);
                proofRows.set(proofRows.size(), exported);
            }
            report.put("proofRows", proofRows);
            GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(published.getFaultCandidates(), keys);
            int completedProofUnits = coordinator.getLastCompletedProofWorkUnitsForDeveloperVerification();
            int requiredProofUnits = GeneratedDiagnosticProofService.requiredWorkUnits(published,
                request.requiresExplicitCompletion());
            require(keys.size() == 5, "RB56 full five-hypothesis census failed");
            requireProofWork(job, completedProofUnits, requiredProofUnits);
            put(report, "proofWorkUnits", completedProofUnits);
            put(report, "requiredProofWorkUnits", requiredProofUnits);
            require(proofCacheHits() == proofHitsBefore &&
                proofCacheMisses() == proofMissesBefore + (normalPopulation ? 0 : 1),
                "RB56 cold D01 attempt reused proof or missed unexpectedly");
            require(coordinator.getPlanCacheHits() == planHitsBefore &&
                coordinator.getPlanCacheMisses() == planMissesBefore + acceptedOrdinal + 1,
                "RB56 cold generation did not resolve its actual plan on a miss");
            put(report, "proofWarmReuse", proof.isWarmReuseForDeveloperVerification());
            put(report, "proofCachePolicy", normalPopulation ? "BYPASS" : "CLEARED_MISS");
            put(report, "proofCacheHitsDelta", proofCacheHits() - proofHitsBefore);
            put(report, "proofCacheMissesDelta", proofCacheMisses() - proofMissesBefore);
            put(report, "planCacheHitsDelta", coordinator.getPlanCacheHits() - planHitsBefore);
            put(report, "planCacheMissesDelta", coordinator.getPlanCacheMisses() - planMissesBefore);
            require(!coordinator.retainsSavedOwnersForDeveloperVerification() &&
                !coordinator.retainsObservationForDeveloperVerification(), "Coordinator retained private owners/observation");
            GeneratedDiagnosticProofService.CleanupAudit audit = coordinator.getCleanupAuditForDeveloperVerification();
            require(audit != null && audit.getDisposedHypothesisCount() >= keys.size() &&
                audit.getDisposalFailureCount() == 0 && audit.wasLastOwnerGuardPassed() &&
                audit.wereLastBindingsActuallyDisconnected() && audit.wereLastElementsActuallyDeleted() &&
                audit.wasLastGraphDetached() && audit.wasLastCleanupComplete(), "Production D01 private cleanup is incomplete");
            put(report, "packages", packages); put(report, "hypotheses", keys.size()); put(report, "solverSamples", samples);
            put(report, "generationStages", receipt.getStageCount()); put(report, "generationWork", receipt.getWorkCount());
            put(report, "generationMillis", receipt.getElapsedMillis()); put(report, "maxManualUnitMillis", maxManualUnitMillis);
            put(report, "physicalAdmission", published.getPhysicalAdmission().identity());
            put(report, "normalPhysicalAdmission", true); put(report, "fullProductionD01", true); put(report, "coldProof", true);
            put(report, "generationReceiptDigest", PlayerSessionFingerprint.ofGenerationReceipt(receipt));
            put(report, "diagnosticContextDigest", PlayerSessionFingerprint.ofDiagnosticContext(proof.getContextKey()));
            put(report, "diagnosticProgramDigest", PlayerSessionFingerprint.of(proof.getProgramIdentity()));
            requireQualificationFamily();
            // Opt-in only, after the full current cold-owner/population/cleanup checks.
            // This reads immutable proof facts and adds no solver work or admission label.
            if (collectDifficulty) {
                long featureStart = System.currentTimeMillis();
                DifficultyAssessment features = DifficultyAssessment.calibrationFeatures(published, proof);
                require(features.profile == null, "Private calibration cannot assign a release profile");
                put(report, "difficultyFeatures", features.canonical());
                put(report, "difficultyFeatureStatus", "UNCALIBRATED");
                put(report, "difficultyFeatureVersion", DifficultyAssessment.CALIBRATION_FEATURE_VERSION);
                put(report, "difficultyFeatureExtractionMillis", System.currentTimeMillis() - featureStart);
                if (collectMediumProfile) {
                    DifficultyAssessment assessment = DifficultyAssessment.assess(published, proof);
                    assessment.require(DifficultyProfile.MEDIUM);
                    require(assessment.profile == DifficultyProfile.MEDIUM &&
                        !DifficultyProfile.HARD.isAvailable() && !DifficultyProfile.PSYCHOTIC.isAvailable(),
                        "Current owner did not qualify MEDIUM with higher bands unavailable");
                    put(report, "normalProfileAssessment", assessment.canonical());
                    put(report, "normalProfileAssessmentVersion", DifficultyAssessment.VERSION);
                    put(report, "normalProfileValidated", "MEDIUM");
                    put(report, "normalPublicationGranted", normalPopulation);
                }
                // Private, opt-in qualification data only. Export observable
                // outcomes and opaque class ordinals for an independent
                // reduction reader, never selected-fault keys or part values.
                put(report, "calibrationObservationFormat", 1);
                put(report, "diagnosticProgramVersion", GeneratedDiagnosticProgram.VERSION);
                report.put("calibrationObservations", calibrationObservations(proof));
                JSONArray waits = new JSONArray();
                for (GeneratedDiagnosticSolvabilityEvidence row : proof.getEvidence()) {
                    JSONArray executed = new JSONArray();
                    for (String wait : row.getExecutedTemporalWaitSamples())
                        executed.set(executed.size(), new JSONString(wait));
                    waits.set(waits.size(), executed);
                }
                report.put("calibrationTemporalWaits", waits);
            }
        }

        private JSONArray calibrationObservations(GeneratedDiagnosticProofReceipt proof) {
            JSONArray rows = new JSONArray();
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = proof.getEvidence();
            int[] repairClasses = new int[evidence.size()];
            int distinct = 0;
            for (int index = 0; index < evidence.size(); index++) {
                GeneratedDiagnosticSolvabilityEvidence row = evidence.get(index);
                int repairClass = distinct;
                for (int prior = 0; prior < index; prior++)
                    if (row.getRepairSemantics().isEquivalentTo(evidence.get(prior).getRepairSemantics())) {
                        repairClass = repairClasses[prior]; break;
                    }
                if (repairClass == distinct) distinct++;
                repairClasses[index] = repairClass;
                JSONObject result = new JSONObject();
                put(result, "ordinal", index); put(result, "repairClass", repairClass);
                put(result, "hypothesisDigest", PlayerSessionFingerprint.of(row.getHypothesisKey()));
                put(result, "repairSemanticsDigest", PlayerSessionFingerprint.of(row.getRepairSemantics().stableDescription()));
                JSONArray samples = new JSONArray();
                for (GeneratedDiagnosticSample sample : row.getSolverSamples()) {
                    JSONObject observed = new JSONObject();
                    put(observed, "id", sample.getSampleId());
                    put(observed, "outcome", sample.getOutcome().name());
                    if (!sample.isOverRange()) {
                        observed.put("value", new JSONNumber(sample.getValue()));
                        observed.put("tolerance", new JSONNumber(sample.getComparisonTolerance()));
                    }
                    samples.set(samples.size(), observed);
                }
                result.put("samples", samples); rows.set(rows.size(), result);
            }
            return rows;
        }

        void retainBench() {
            phase = "HOLD";
            PcbWorkbenchController workbench = sim.pcbWorkbenchController;
            require(workbench != null && sim.playerSessionController == null, "Actual shared workbench is missing");
            sim.troubleshootDebug = false;
            if (modernBench) {
                retainedSession = new PlayerSessionController(sim);
                sim.playerSessionController = retainedSession;
                retainedSession.openQualifiedBoard(request, published, persistence);
            }
            workbench.attachToSidebar(sim.verticalPanel);
            if (modernBench) workbench.getRenderer().enableTrayDrawer();
            workbench.refresh();
            sim.refreshGeneratedUiForDeveloperVerification();
            workbench.getRenderer().setViewingFace(PcbBoardSide.TOP);
            workbench.getRenderer().fitWorkbench();
            require(workbench.isAttachedToSidebarForDeveloperVerification(), "Shared workbench did not attach");
            require(sim.isGeneratedSemanticInteractionEnabled(), "Shared semantic controls are unavailable");
            endView = new Button("End qualification view");
            endView.addClickHandler(new ClickHandler() { public void onClick(ClickEvent event) {
                finishRetainedBench(sim);
            } });
            sim.verticalPanel.add(endView);
            sim.developerVerifierRunning = originalVerifierRunning;
            retained = true;
            sim.refreshChallengeInteractionState();
            sim.updateCircuit();
            put(report, "holdMillis", benchHoldMillis); put(report, "normalLabels", !sim.troubleshootDebug);
            put(report, "playerSessionInstalled", retainedSession != null);
            put(report, "privatePersistence", persistence);
            publish("HOLD", "HOLD:rb56");
            holdTimer.schedule(benchHoldMillis);
        }

        void beginSessionRestore(PlayerSessionSave saved, GenerationRequest restoreRequest) {
            require(sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
                !coordinator.isRunning() && !coordinator.isAdvancing(), "The current board is busy");
            if (restoreRequest == null || !restoreRequest.isPrivateDiagnosticQualification() ||
                    !PlayerSessionReplay.parse(saved.replay).matches(restoreRequest))
                throw new IllegalArgumentException("Private restore request differs from the saved placement identity");
            restoreToken = retainedSession.beginQualificationRestore(published);
            pendingRestoreRequest = restoreRequest;
            restoringSession = true;
            boolean debug = sim.troubleshootDebug, verifier = sim.developerVerifierRunning;
            RuntimeException startupFailure = null;
            try {
                sim.troubleshootDebug = true; sim.developerVerifierRunning = true;
                coordinator.startSessionRestoreForQualification(pendingRestoreRequest, saved);
                job = coordinator.getJob();
                require(job != null && job.isRunning(), "Private file restore did not start");
                advanceTimer.schedule(1);
            } catch (RuntimeException problem) { startupFailure = problem; }
            finally {
                job = coordinator.getJob();
                sim.troubleshootDebug = debug; sim.developerVerifierRunning = verifier;
            }
            if (startupFailure != null) {
                if (coordinator.isRunning()) coordinator.cancel();
                if (coordinator.retainsSavedOwnersForDeveloperVerification() ||
                        sim.getGeneratedBoardInstance() != published || sim.elmList != publishedGraph) {
                    pendingRestoreRequest = null;
                    recordFailure(startupFailure); finish(); throw startupFailure;
                }
                restoringSession = false;
                pendingRestoreRequest = null;
                retainedSession.finishQualificationRestore(restoreToken, published, null,
                    GenerationJob.Outcome.PROGRAMMING_FAILURE);
                throw startupFailure;
            }
        }

        void advanceSessionRestore() {
            boolean debug = sim.troubleshootDebug, verifier = sim.developerVerifierRunning;
            Throwable problem = null;
            try {
                require(ownsPersistenceLease(sim, retainedSession), "Private file restore lost its lease");
                sim.troubleshootDebug = true; sim.developerVerifierRunning = true;
                manualAdvanceCount++;
                coordinator.advanceForDeveloperVerification();
            } catch (Throwable failure) { problem = failure; }
            finally { sim.troubleshootDebug = debug; sim.developerVerifierRunning = verifier; }
            if (problem != null) { pendingRestoreRequest = null; recordFailure(problem); finish(); return; }
            if (job.isRunning()) { advanceTimer.schedule(1); return; }
            try {
                require(!coordinator.retainsSavedOwnersForDeveloperVerification() &&
                    !coordinator.retainsObservationForDeveloperVerification(), "Private file restore cleanup is incomplete");
                JSONObject result = new JSONObject();
                put(result, "outcome", job.getOutcome().name()); put(result, "stage", job.getStage().name());
                put(result, "elapsedMillis", job.getElapsedMillis()); put(result, "work", job.getStepCount());
                put(result, "maxJobMillis", GenerationCoordinator.MAX_JOB_MILLIS);
                put(result, "maxJobSteps", GenerationCoordinator.MAX_JOB_STEPS);
                put(result, "maxUnitMillis", GenerationCoordinator.MAX_STEP_MILLIS);
                GeneratedBoardInstance previous = published;
                if (job.getOutcome() == GenerationJob.Outcome.PASS) {
                    GeneratedBoardInstance next = sim.getGeneratedBoardInstance();
                    require(pendingRestoreRequest != null && next != previous && next != predecessor &&
                        pendingRestoreRequest.getFamilyId().equals(next.getCircuitFamilyId()) &&
                        next.getSeed() == seed && !next.isDeveloperOnlyFaultRoute(), "Private file restore did not publish its exact fresh owner");
                    // Capture the actual publication before checking or retiring anything.
                    superseded = previous; supersededCursor = 0;
                    published = next; publishedController = sim.getGeneratedChallengeController(); publishedGraph = sim.elmList;
                    next.getPhysicalBoardRuntime().getSessionHistory().start();
                    restoringSession = false;
                    retainedSession.finishQualificationRestore(restoreToken, previous, next, job.getOutcome());
                    next.requireNormalPhysicalAdmission();
                    GeneratedRuntimeInvariant.verify(sim, next, sim.getBoardModificationController(), sim.elmList);
                    retireSuperseded();
                    // Future saves describe the actual published geometry, but only after its restore passed.
                    request = pendingRestoreRequest;
                    pendingRestoreRequest = null;
                    put(result, "freshOwner", true); put(result, "priorOwnerRetired", true);
                } else {
                    require(sim.getGeneratedBoardInstance() == published && sim.getGeneratedChallengeController() == publishedController &&
                        sim.elmList == publishedGraph, "Failed private file restore did not preserve its exact owner");
                    restoringSession = false;
                    pendingRestoreRequest = null;
                    retainedSession.finishQualificationRestore(restoreToken, published, null, job.getOutcome());
                    put(result, "priorOwnerRetained", true);
                }
                sessionRestores.set(sessionRestores.size(), result); report.put("sessionRestores", sessionRestores);
                phase = "HOLD"; publish("HOLD", "HOLD:rb56");
            } catch (Throwable failure) { pendingRestoreRequest = null; recordFailure(failure); finish(); }
        }

        void retireSuperseded() {
            if (superseded == null) return;
            require(superseded != published && superseded != predecessor &&
                superseded.getExternalPowerBindings().areAllDisconnected(), "Superseded private owner retained authority or power");
            Vector<CircuitElm> elements = superseded.getSimulationElements();
            while (supersededCursor < elements.size()) {
                CircuitElm element = elements.get(supersededCursor);
                require(!sim.elmList.contains(element) && !predecessorGraph.contains(element),
                    "Private retirement reached a live or protected element");
                element.delete(); supersededCursor++;
            }
            superseded = null; supersededCursor = 0;
        }

        void endBench() {
            if (!retained || finished || cleanupPending) return;
            try {
                if (restoringSession) {
                    advanceTimer.cancel(); coordinator.cancel(); restoringSession = false;
                    pendingRestoreRequest = null;
                    retainedSession.finishQualificationRestore(restoreToken, published, null, job.getOutcome());
                }
                require(sim.getGeneratedBoardInstance() == published &&
                    sim.getGeneratedChallengeController() == publishedController &&
                    coordinator.getJob() == job && !sim.troubleshootDebug,
                    "Retained RB56 view lost its exact owner or scoped presentation");
                GeneratedCustomerRetestResult result = publishedController.getCustomerRetestResult();
                customerRetestPassed = result != null && result.isPassed();
                require(customerRetestPassed, "Ordinary customer retest did not pass before view cleanup");
                if (modernBench)
                    require(sim.playerSessionController == retainedSession && retainedSession != null &&
                        retainedSession.session.owner() == published &&
                        retainedSession.session.screen() == PlayerSession.Screen.RESULTS && publishedController.isCompleted(),
                        "Qualified player session did not complete its own customer flow");
                GeneratedRuntimeInvariant.verify(sim, published, sim.getBoardModificationController(), sim.elmList);
            } catch (Throwable problem) { recordFailure(problem); }
            finish();
        }

        /** Existing neutral counters only; no new solver work, private answers or timing policy. */
        void captureJobCosts() {
            captureOwnerDrivenMetadata();
            if (job == null) return;
            put(report, "jobOutcome", job.getOutcome().name());
            put(report, "jobStage", job.getStage().name());
            put(report, "jobWork", job.getStepCount());
            put(report, "jobMillis", job.getElapsedMillis());
            put(report, "manualUnitMaxMillis", maxManualUnitMillis);
            put(report, "routingMillis", coordinator.getLastRoutingElapsedMillisForDeveloperVerification());
            put(report, "proofMillis", coordinator.getLastProofElapsedMillisForDeveloperVerification());
            JSONObject stageMillis = new JSONObject(), stageWork = new JSONObject(), diagnostic = new JSONObject();
            for (GenerationJob.Stage stage : GenerationJob.Stage.values()) {
                put(stageMillis, stage.name(), job.getStageElapsedMillis(stage));
                put(stageWork, stage.name(), job.getStageWorkCount(stage));
            }
            for (java.util.Map.Entry<String, long[]> entry :
                    coordinator.getLastDiagnosticMeasurementWorkTimingsForDeveloperVerification().entrySet()) {
                long[] values = entry.getValue();
                JSONObject timing = new JSONObject();
                put(timing, "units", values[0]); put(timing, "millis", values[1]); put(timing, "maxMillis", values[2]);
                diagnostic.put(entry.getKey(), timing);
            }
            report.put("jobStageMillis", stageMillis); report.put("jobStageWork", stageWork);
            report.put("diagnosticWorkTimings", diagnostic);
        }

        void captureOwnerDrivenMetadata() {
            if (!productionDriven) return;
            put(report, "generationDriver", "NORMAL_COORDINATOR_ASYNC");
            put(report, "manualAdvanceCount", manualAdvanceCount);
            if (!driverStarted) return;
            put(report, "coordinatorMaxAdvanceMillis", coordinator.getMaxAdvanceMillis());
            put(report, "coordinatorYieldCount", coordinator.getYieldCount());
            boolean foregroundBudget = coordinator.usesForegroundBudgetForDeveloperVerification();
            put(report, "foregroundBudgetApplied", foregroundBudget);
            put(report, "foregroundBudgetPaused", coordinator.isProgressPaused());
            put(report, "pageVisible", coordinator.isPageVisibleForDeveloperVerification());
            if (!foregroundBudget) put(report, "foregroundPausedMillis", 0L);
        }

        private int proofCacheHits() {
            return normalPopulation ? coordinator.getDiagnosticProofCacheHits() :
                coordinator.getDiagnosticMeasurementProofCacheHits();
        }

        private int proofCacheMisses() {
            return normalPopulation ? coordinator.getDiagnosticProofCacheMisses() :
                coordinator.getDiagnosticMeasurementProofCacheMisses();
        }

        void recordFailure(Throwable problem) {
            if (failure == null) { failure = problem; put(report, "failurePhase", phase); captureJobCosts(); }
            else if (failure != problem) failure.addSuppressed(problem);
        }

        void captureCandidateAttempts() {
            JSONArray attempts = new JSONArray();
            if (job != null) for (GenerationJob.Attempt attempt : job.getAttempts()) {
                require(attempt.ordinal == attempts.size() && attempt.ordinal < request.candidateCount() &&
                    attempt.manifest.equals(request.candidateManifest(attempt.ordinal)),
                    "Qualification candidate attempts differ from canonical request order");
                JSONObject row = new JSONObject();
                put(row, "ordinal", attempt.ordinal);
                put(row, "seed", Long.toString(request.candidate(attempt.ordinal).getDescriptor().getRootSeed()));
                put(row, "manifest", attempt.manifest); put(row, "stage", attempt.stage.name());
                put(row, "outcome", attempt.outcome.name()); put(row, "workUnits", attempt.workUnits);
                put(row, "proofUnits", attempt.proofUnits); attempts.set(attempts.size(), row);
            }
            report.put("candidateAttempts", attempts);
        }

        void finish() {
            if (cleanupStartedAt == 0) cleanupStartedAt = System.currentTimeMillis();
            advanceTimer.cancel(); holdTimer.cancel();
            pendingRestoreRequest = null;
            try {
                phase = "CLEANUP";
                require(coordinator.getJob() == (job == null ? previousJob : job),
                    "Refusing RB56 cleanup after a successor generation");
                if (coordinator.isRunning()) coordinator.cancel();
                require(!coordinator.isAdvancing() && !coordinator.isRunning(), "RB56 generation is still advancing");
                if (coordinator.retainsSavedOwnersForDeveloperVerification())
                    coordinator.retryFailedCleanupForDeveloperVerification();
                require(!coordinator.retainsSavedOwnersForDeveloperVerification() &&
                    !coordinator.retainsObservationForDeveloperVerification(), "RB56 coordinator cleanup is incomplete");
                retireSuperseded();
                // Candidate authority is an actual PASS publication, never an arbitrary failed-stage owner.
                if (published == predecessor) published = null;
                if (published != null) {
                    boolean current = sim.getGeneratedBoardInstance() == published &&
                        sim.getGeneratedChallengeController() == publishedController && sim.elmList == publishedGraph;
                    require(current || (snapshotAttempted && predecessorCurrent()),
                        "Refusing RB56 cleanup across a successor owner");
                    if (current) {
                        if (retainedSession != null && !sessionReleased) {
                            retainedSession.releaseQualification(published);
                            sessionReleased = true;
                        }
                        sim.developerVerifierRunning = true;
                        sim.instrumentController.clearTargets();
                        require(!sim.activeMeasurementOverlay && !publishedController.isOperationInProgress(),
                            "RB56 cleanup retains a meter overlay or operation");
                        published.getExternalPowerBindings().setConnected(false);
                        powerDisconnected = published.getExternalPowerBindings().areAllDisconnected();
                        require(powerDisconnected, "RB56 sources did not disconnect");
                        sim.setSimRunning(false);
                        if (retirementElements == null) retirementElements = published.getSimulationElements();
                        while (retirementCursor < retirementElements.size()) {
                            require(sim.getGeneratedBoardInstance() == published && sim.elmList == publishedGraph &&
                                coordinator.getJob() == job, "RB56 owner changed during retirement");
                            CircuitElm element = retirementElements.get(retirementCursor);
                            require(!predecessorGraph.contains(element), "RB56 cleanup would retire a predecessor element");
                            element.delete(); retirementCursor++;
                        }
                    }
                    require(powerDisconnected && retirementElements != null && retirementCursor == retirementElements.size(),
                        "RB56 published owner retirement is incomplete");
                } else require(predecessorCurrent(), "Failed RB56 generation did not restore predecessor");
                if (endView != null) { endView.removeFromParent(); endView = null; }
                require(retainedSession == null || (sessionReleased && sim.playerSessionController == null),
                    "Qualified player session still owns the restored presentation");
                // Task41 does not capture this presentation flag. Restore BEFORE restoring old UI.
                sim.troubleshootDebug = originalDebug;
                snapshotAttempted = true;
                snapshot.restore(sim); snapshot.assertRestored(sim);
                restored = predecessorCurrent() && sim.troubleshootDebug == originalDebug;
                require(restored, "RB56 snapshot or debug presentation was not restored");
                disposed = published == null;
                if (published != null) {
                    disposed = powerDisconnected && published.getExternalPowerBindings().areAllDisconnected() &&
                        retirementCursor == retirementElements.size();
                    for (CircuitElm element : retirementElements) disposed &= !sim.elmList.contains(element);
                }
                require(disposed && !sim.activeMeasurementOverlay, "RB56 candidate or temporary meter was retained");
                requireQualificationFamily();
                require(PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID) == normalCatalogAtStart,
                    "Qualification changed normal catalog availability");
                captureCandidateAttempts();
                captureJobCosts();
                cleanupPending = false; finished = true; active = null; phase = "RESTORED";
            } catch (Throwable cleanupFailure) {
                recordFailure(cleanupFailure); cleanupPending = true; phase = "CLEANUP_PENDING";
                // Keep the exact transaction reachable for explicit cleanup retry; never report PASS.
            }
            put(report, "qualifiedPublication", admitted);
            put(report, "ownerRestored", restored); put(report, "candidateDisposed", disposed);
            put(report, "temporaryMeterClean", restored && !sim.activeMeasurementOverlay &&
                !coordinator.retainsObservationForDeveloperVerification());
            put(report, "debugRestored", sim.troubleshootDebug == originalDebug);
            put(report, "coordinatorReleased", !coordinator.retainsSavedOwnersForDeveloperVerification());
            put(report, "ordinaryCustomerRetestPassed", customerRetestPassed);
            put(report, "cleanupPending", cleanupPending);
            put(report, "playerSessionReleased", retainedSession == null || sessionReleased);
            put(report, "playerBridgeReleased", retainedSession == null ||
                (sessionReleased && retainedSession.isQualificationReleased()));
            put(report, "normalCatalogEnabled", PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID));
            put(report, "wallMillis", System.currentTimeMillis() - startedAt);
            put(report, "operationMillis", cleanupStartedAt - startedAt);
            put(report, "cleanupMillis", System.currentTimeMillis() - cleanupStartedAt);
            if (failure != null) put(report, "failure", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            String status = failure == null && admitted && restored && disposed && !cleanupPending ? "PASS" : "FAIL";
            publish(status, status + ":rb56" + (failure == null ? "" : ":" + failure.getMessage()));
        }

        boolean predecessorCurrent() {
            return sim.getGeneratedBoardInstance() == predecessor &&
                sim.getGeneratedChallengeController() == predecessorController && sim.elmList == predecessorGraph;
        }

        void publish(String status, String state) {
            put(report, "status", status); put(report, "phase", phase);
            publishNative(report.toString(), state);
        }
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static void put(JSONObject value, String key, String text) { value.put(key, new JSONString(text)); }
    private static void put(JSONObject value, String key, long number) { value.put(key, new JSONNumber(number)); }
    private static void put(JSONObject value, String key, boolean flag) { value.put(key, JSONBoolean.getInstance(flag)); }
    private static native void publishNative(String report, String state) /*-{
        $doc.documentElement.setAttribute("data-tsj-rb56-report", report);
        $doc.documentElement.setAttribute("data-tsj-verification", state);
    }-*/;
}
