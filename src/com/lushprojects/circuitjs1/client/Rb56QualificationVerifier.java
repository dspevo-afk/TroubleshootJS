package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import java.util.Vector;

/**
 * One cold, private RB56 request through the actual staged coordinator/D01.
 * Catalog enablement, public PlayerSession and save admission stay unchanged.
 * The optional view uses the shared classic workbench with normal labels.
 */
final class Rb56QualificationVerifier {
    static final int BENCH_HOLD_MILLIS = 120000;
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
        requireHeldCatalog();
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

    static boolean retryPendingCleanup(CirSim sim) {
        Runner runner = active;
        if (runner == null || runner.sim != sim || !runner.cleanupPending) return false;
        runner.finish();
        return true;
    }

    private static void requireHeldCatalog() {
        require(PlayerFamilyCatalog.isRegistered(Rb56Plan.FAMILY_ID) &&
            !PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID) &&
            PlayerFamilyCatalog.stagedCapability(Rb56Plan.FAMILY_ID) instanceof Rb56PlayerFamilyCapability,
            "RB56 must remain registered and disabled in the normal catalog");
    }

    private static final class Runner {
        final CirSim sim;
        final long seed;
        final Task41SimulationSnapshot snapshot;
        final GenerationCoordinator coordinator;
        final GenerationRequest request;
        final GenerationJob previousJob;
        final GeneratedBoardInstance predecessor;
        final GeneratedChallengeController predecessorController;
        final Vector<CircuitElm> predecessorGraph;
        final boolean originalDebug, originalVerifierRunning, forced, keepBench;
        final JSONObject report = new JSONObject();
        final Timer advanceTimer = new Timer() { public void run() { advance(); } };
        final Timer holdTimer = new Timer() { public void run() { endBench(); } };
        GenerationJob job;
        GeneratedBoardInstance published;
        GeneratedChallengeController publishedController;
        Vector<CircuitElm> publishedGraph, retirementElements;
        Button endView;
        int proofHitsBefore, proofMissesBefore, planHitsBefore, planMissesBefore, retirementCursor;
        long maxManualUnitMillis, startedAt;
        boolean admitted, retained, finished, cleanupPending, restored, disposed, snapshotAttempted;
        boolean powerDisconnected, customerRetestPassed;
        Throwable failure;
        String phase = "STARTUP";

        Runner(CirSim sim, long seed, Task41SimulationSnapshot snapshot, boolean forced, boolean keepBench) {
            this.sim = sim; this.seed = seed; this.snapshot = snapshot;
            this.forced = forced; this.keepBench = keepBench;
            coordinator = sim.generationCoordinator;
            previousJob = coordinator.getJob();
            predecessor = sim.getGeneratedBoardInstance();
            predecessorController = sim.getGeneratedChallengeController();
            predecessorGraph = sim.elmList;
            originalDebug = sim.troubleshootDebug;
            originalVerifierRunning = sim.developerVerifierRunning;
            request = GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, seed);
        }

        void begin() {
            sim.developerVerifierRunning = true;
            startedAt = System.currentTimeMillis();
            put(report, "protocol", "TSJ-RB56-QUALIFICATION-1");
            put(report, "scope", "PRIVATE_SHARED_WORKBENCH");
            put(report, "family", Rb56Plan.FAMILY_ID);
            put(report, "seed", Long.toString(seed));
            put(report, "sourceEpoch", GenerationDependencyContext.INTERPRETATION_EPOCH);
            put(report, "planVersion", Rb56Plan.PLAN_VERSION);
            put(report, "maxJobMillis", 90000L); put(report, "maxJobSteps", 640); put(report, "maxUnitMillis", 5000L);
            put(report, "forcedAfterPublish", forced);
            put(report, "normalCatalogEnabled", PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID));
            publish("RUNNING", "RUNNING:rb56");
            try {
                phase = "COLD_GENERATION";
                coordinator.clearDiagnosticMeasurementProofCacheForDeveloperVerification();
                require(coordinator.getDiagnosticMeasurementProofCacheSize() == 0, "Private D01 cache was not cleared");
                proofHitsBefore = coordinator.getDiagnosticMeasurementProofCacheHits();
                proofMissesBefore = coordinator.getDiagnosticMeasurementProofCacheMisses();
                planHitsBefore = coordinator.getPlanCacheHits();
                planMissesBefore = coordinator.getPlanCacheMisses();
                coordinator.startForDiagnosticCacheVerification(request, 90000L);
                job = coordinator.getJob();
                require(job != null && job != previousJob, "Coordinator did not start a fresh job");
                advanceTimer.schedule(1);
            } catch (Throwable problem) {
                if (coordinator.getJob() != previousJob) job = coordinator.getJob();
                recordFailure(problem); finish();
            }
        }

        void advance() {
            try {
                require(sim.troubleshootDebug && sim.developerVerifierRunning && coordinator.getJob() == job,
                    "RB56 private generation lost its scope or job");
                requireHeldCatalog();
                long unitStart = System.currentTimeMillis();
                coordinator.advanceForDeveloperVerification();
                maxManualUnitMillis = Math.max(maxManualUnitMillis, System.currentTimeMillis() - unitStart);
                if (job.isRunning()) { advanceTimer.schedule(1); return; }
                if (job.getOutcome() != GenerationJob.Outcome.PASS) {
                    if (job.getFailure() != null) throw job.getFailure();
                    throw new IllegalStateException("RB56 coordinator outcome=" + job.getOutcome().name());
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
            require(published != null && published != predecessor && publishedController != null &&
                publishedController != predecessorController && publishedGraph != predecessorGraph &&
                published.getSeed() == seed && Rb56Plan.FAMILY_ID.equals(published.getCircuitFamilyId()) &&
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
            for (GeneratedDiagnosticSolvabilityEvidence row : proof.getEvidence()) {
                require(Rb56Plan.FAMILY_ID.equals(row.getFamilyId()) && row.getSeed() == seed &&
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
            }
            GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(published.getFaultCandidates(), keys);
            require(keys.size() == 5 && receipt.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) ==
                GeneratedDiagnosticProofService.requiredWorkUnits(published, request.requiresExplicitCompletion()),
                "RB56 full five-hypothesis/work census failed");
            require(coordinator.getDiagnosticMeasurementProofCacheHits() == proofHitsBefore &&
                coordinator.getDiagnosticMeasurementProofCacheMisses() == proofMissesBefore + 1,
                "RB56 cold D01 attempt reused proof or missed unexpectedly");
            require(coordinator.getPlanCacheHits() == planHitsBefore && coordinator.getPlanCacheMisses() == planMissesBefore + 1,
                "RB56 cold generation did not resolve its actual plan on a miss");
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
            requireHeldCatalog();
        }

        void retainBench() {
            phase = "HOLD";
            PcbWorkbenchController workbench = sim.pcbWorkbenchController;
            require(workbench != null && sim.playerSessionController == null, "Actual classic workbench is missing");
            // Initial debug bootstrap retains the existing sidebar. No new public session/adoption.
            sim.troubleshootDebug = false;
            workbench.attachToSidebar(sim.verticalPanel);
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
            put(report, "holdMillis", BENCH_HOLD_MILLIS); put(report, "normalLabels", !sim.troubleshootDebug);
            publish("HOLD", "HOLD:rb56");
            holdTimer.schedule(BENCH_HOLD_MILLIS);
        }

        void endBench() {
            if (!retained || finished || cleanupPending) return;
            try {
                require(sim.getGeneratedBoardInstance() == published &&
                    sim.getGeneratedChallengeController() == publishedController &&
                    coordinator.getJob() == job && !sim.troubleshootDebug,
                    "Retained RB56 view lost its exact owner or scoped presentation");
                GeneratedCustomerRetestResult result = publishedController.getCustomerRetestResult();
                customerRetestPassed = result != null && result.isPassed();
                require(customerRetestPassed, "Ordinary customer retest did not pass before view cleanup");
                GeneratedRuntimeInvariant.verify(sim, published, sim.getBoardModificationController(), sim.elmList);
            } catch (Throwable problem) { recordFailure(problem); }
            finish();
        }

        /** Existing neutral counters only; no new solver work, private answers or timing policy. */
        void captureJobCosts() {
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

        void recordFailure(Throwable problem) {
            if (failure == null) { failure = problem; put(report, "failurePhase", phase); captureJobCosts(); }
            else if (failure != problem) failure.addSuppressed(problem);
        }

        void finish() {
            advanceTimer.cancel(); holdTimer.cancel();
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
                // Candidate authority is an actual PASS publication, never an arbitrary failed-stage owner.
                if (published == predecessor) published = null;
                if (published != null) {
                    boolean current = sim.getGeneratedBoardInstance() == published &&
                        sim.getGeneratedChallengeController() == publishedController && sim.elmList == publishedGraph;
                    require(current || (snapshotAttempted && predecessorCurrent()),
                        "Refusing RB56 cleanup across a successor owner");
                    if (current) {
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
                requireHeldCatalog();
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
            put(report, "normalCatalogEnabled", PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID));
            put(report, "wallMillis", System.currentTimeMillis() - startedAt);
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
