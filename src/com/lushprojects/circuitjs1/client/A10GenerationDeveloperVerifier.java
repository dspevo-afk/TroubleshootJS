package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Opt-in qualification of the actual generation owner, never a player action. */
final class A10GenerationDeveloperVerifier {
    private static int assertions, cancellations, unitCancellations, failures, lifecycleFailures;
    private static final long BENCHMARK_ATTEMPT_MILLIS = 5000;
    private static final String NORMAL_BUDGET_SCOPE = "NORMAL_FROZEN_ATTEMPT";
    private static final String D01_BUDGET_SCOPE = "D01_JOB_BUDGET";
    private static long cancellationMaxMs;
    private static String temporalRegression, temporalWork, observationCleanup;
    private static final Vector<String> metrics = new Vector<String>();
    private static final Vector<String> d01Metrics = new Vector<String>();
    private A10GenerationDeveloperVerifier() { }

    static void verify(CirSim sim, boolean forceFailure) {
        assertions = cancellations = unitCancellations = failures = lifecycleFailures = 0; cancellationMaxMs = 0;
        metrics.clear(); d01Metrics.clear(); clearReport();
        temporalRegression = "null";
        temporalWork = "null";
        observationCleanup = "null";
        if (forceFailure) throw new IllegalStateException("a10-explicit-failure-canary");
        GenerationCoordinator coordinator = sim.generationCoordinator;
        require(coordinator != null && coordinator.getJob() != null &&
            coordinator.getJob().getOutcome() == GenerationJob.Outcome.PASS, "initial normal generation passed");
        require(coordinator.getJob().getReceipt() != null &&
            coordinator.getJob().getReceipt().getStageCount() == 6, "all initial stage receipts present");
        require(coordinator.getYieldCount() >= 5, "initial production generation yielded browser turns");
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalController = sim.getGeneratedChallengeController();
        Object originalGraph = sim.elmList;
        String copper = original.getPcbLayout().geometryFingerprint();
        int originalAttached = sim.getAttachedPcbWorkbenchCountForDeveloperVerification();
        boolean originalRunning = sim.simIsRunning();
        sim.setSimRunning(false);
        try {
            A10TemporalBatchVerifier.verify(sim);
            require(true, "real fixed/adaptive RC serial and temporal batches agree exactly");
            temporalWork = RcTemporalWorkDeveloperVerifier.verify(sim);
            require(true, "RC profile phases preserve the independent sequence and reject stale/cancelled work");
            observationCleanup = GeneratedCleanupDeveloperVerifier.verify(sim);
            verifyRetiredUiReferences(sim, coordinator, original);
            // Cancellation at every externally resumable boundary uses the actual
            // stage implementation and exact original graph, not a service double.
            for (GenerationJob.Stage stage : GenerationJob.Stage.values()) {
                coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
                advanceTo(coordinator, stage);
                require(coordinator.getJob().isRunning(), "cancel target reached " + stage);
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.CANCELLED,
                    "terminal cancellation " + stage);
                require(coordinator.getJob().getReceipt() == null, "cancel never published a receipt");
                require(coordinator.getCancellationLatencyMillis() <= 500, "cancellation within 500 ms");
                cancellationMaxMs = Math.max(cancellationMaxMs, coordinator.getCancellationLatencyMillis());
                require(sim.getGeneratedBoardInstance() == original &&
                    sim.getGeneratedChallengeController() == originalController && sim.elmList == originalGraph &&
                    sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == originalAttached &&
                    copper.equals(original.getPcbLayout().geometryFingerprint()) && !sim.activeMeasurementOverlay,
                    "cancel restored original ownership and copper");
                cancellations++;
            }
            // A private hypothesis may now span browser turns. Exercise each
            // first-hypothesis operation boundary, including removed/replaced
            // parts, and require the exact protected player graph on cancellation.
            int firstHypothesisUnits = new LedIndicatorDiagnosticProvider(0)
                .getObservationProgram().getSteps().size() + 9;
            for (int boundary = 1; boundary <= firstHypothesisUnits; boundary++) {
                coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
                advanceTo(coordinator, GenerationJob.Stage.HYPOTHESES);
                for (int unit = 0; unit < boundary; unit++)
                    coordinator.advanceForDeveloperVerification();
                require(coordinator.isRunning() && !sim.activeMeasurementOverlay &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning(),
                    "private operation " + boundary + " yielded without an overlay or global proof guard; " +
                    jobDiagnostic(coordinator));
                require(original.getExternalPowerBindings().areAllDisconnected(),
                    "private proof keeps the saved player source isolated");
                if (boundary == 1) requireOverlappingProofRejected(sim);
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.CANCELLED &&
                    coordinator.getJob().getReceipt() == null,
                    "private operation cancellation has no receipt");
                require(coordinator.getCancellationLatencyMillis() <= 500,
                    "private operation cancellation cleanup within 500 ms");
                cancellationMaxMs = Math.max(cancellationMaxMs, coordinator.getCancellationLatencyMillis());
                require(sim.getGeneratedBoardInstance() == original &&
                    sim.getGeneratedChallengeController() == originalController && sim.elmList == originalGraph &&
                    sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == originalAttached &&
                    copper.equals(original.getPcbLayout().geometryFingerprint()) && !sim.activeMeasurementOverlay,
                    "private operation cancellation restored the exact player owner");
                GeneratedDiagnosticProofService.CleanupAudit audit =
                    coordinator.getCleanupAuditForDeveloperVerification();
                require(audit != null && audit.getDisposedHypothesisCount() >= 1 &&
                    audit.getDisconnectedBindingCount() >= 1 && audit.getDisposedElementCount() >= 1 &&
                    audit.getDisposalFailureCount() == 0 && audit.wasLastOwnerGuardPassed() &&
                    audit.wereLastBindingsActuallyDisconnected() && audit.wereLastElementsActuallyDeleted() &&
                    audit.wasLastGraphDetached() && audit.wasLastCleanupComplete(),
                    "cancel disposed the actual private sources and graph at unit " + boundary);
                unitCancellations++;
            }
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            advanceTo(coordinator, GenerationJob.Stage.PHYSICAL);
            GenerationJob superseded = coordinator.getJob();
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 1, false));
            require(superseded.getOutcome() == GenerationJob.Outcome.CANCELLED &&
                !superseded.advance() && superseded.getReceipt() == null,
                "a superseded actual job cannot publish from a late callback");
            require(sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph,
                "supersession restores original before a new candidate begins");
            coordinator.cancel();
            coordinator.start(GenerationRequest.leaf("unsupported-generation-family", 0, false), null, false);
            require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.EXPECTED_REJECTION &&
                coordinator.getJob().getStage() == GenerationJob.Stage.RESOLVE &&
                coordinator.getJob().getStepCount() == 1 && coordinator.getJob().getReceipt() == null,
                "unsupported native request rejects before electrical allocation");
            requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
            // The preparation transaction owns even a failure before a private
            // graph is installed. Every boundary must restore the exact owner.
            for (FreshGeneratedRuntimeInstallation.Staged.PreparationPoint point :
                    FreshGeneratedRuntimeInstallation.Staged.PreparationPoint.values()) {
                FreshGeneratedRuntimeInstallation.Staged.setPreparationFailureForDeveloperVerification(point);
                try {
                    coordinator.start(GenerationRequest.leaf(
                        QuickPlayFamilyRegistry.LED_INDICATOR, 0, false), null, false);
                    require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE &&
                        coordinator.getJob().getReceipt() == null, "preparation failed before publication " + point);
                    requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
                    lifecycleFailures++;
                } finally {
                    FreshGeneratedRuntimeInstallation.Staged.setPreparationFailureForDeveloperVerification(null);
                }
            }
            for (GenerationCoordinator.TurnFailure point : GenerationCoordinator.TurnFailure.values()) {
                coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
                if (point == GenerationCoordinator.TurnFailure.PAUSE)
                    advanceTo(coordinator, GenerationJob.Stage.HEALTHY);
                GenerationCoordinator.setTurnFailureForDeveloperVerification(point);
                try {
                    coordinator.advanceForDeveloperVerification();
                    require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE &&
                        !coordinator.isAdvancing() && coordinator.getJob().getReceipt() == null &&
                        !coordinator.retainsSavedOwnersForDeveloperVerification(),
                        "coordinator failure released advancing guard " + point);
                    requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
                    lifecycleFailures++;
                } finally { GenerationCoordinator.setTurnFailureForDeveloperVerification(null); }
            }
            QuickPlaySession savedSession = sim.quickPlaySession;
            boolean savedQuickPlay = sim.quickPlayActive;
            QuickPlaySession.setCandidateFailureForDeveloperVerification(true);
            try {
                coordinator.start(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 0, true), null, false);
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE &&
                    coordinator.getJob().getReceipt() == null && sim.quickPlaySession == savedSession &&
                    sim.quickPlayActive == savedQuickPlay, "Quick Play validation is inside publication transaction");
                requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
                lifecycleFailures++;
            } finally { QuickPlaySession.setCandidateFailureForDeveloperVerification(false); }
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            advanceTo(coordinator, GenerationJob.Stage.HYPOTHESES);
            coordinator.advanceForDeveloperVerification();
            GeneratedDiagnosticProofService.setCancelFailureForDeveloperVerification(true);
            try {
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE &&
                    coordinator.getJob().getReceipt() == null, "inner cleanup failure cannot issue a receipt");
                requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
                lifecycleFailures++;
            } finally {
                GeneratedDiagnosticProofService.setCancelFailureForDeveloperVerification(false);
                if (coordinator.retainsSavedOwnersForDeveloperVerification())
                    coordinator.retryFailedCleanupForDeveloperVerification();
            }
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            advanceTo(coordinator, GenerationJob.Stage.PHYSICAL);
            FreshGeneratedRuntimeInstallation.Staged.setCleanupFailureForDeveloperVerification(true);
            try {
                coordinator.cancel();
                GenerationJob failed = coordinator.getJob();
                require(failed.getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE &&
                    failed.getReceipt() == null && coordinator.retainsSavedOwnersForDeveloperVerification(),
                    "failed staged cleanup retains its exact owners");
                boolean rejected = false;
                try { coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 1, false)); }
                catch (IllegalStateException expected) {
                    rejected = "Previous generation has incomplete cleanup".equals(expected.getMessage());
                }
                require(rejected && coordinator.getJob() == failed &&
                    coordinator.retainsSavedOwnersForDeveloperVerification(),
                    "new generation cannot overwrite incomplete staged cleanup");
            } finally {
                FreshGeneratedRuntimeInstallation.Staged.setCleanupFailureForDeveloperVerification(false);
                coordinator.retryFailedCleanupForDeveloperVerification();
            }
            requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
            require(!coordinator.retainsSavedOwnersForDeveloperVerification(),
                "guarded staged cleanup retry releases saved owners");
            lifecycleFailures++;
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            advanceTo(coordinator, GenerationJob.Stage.HYPOTHESES);
            coordinator.advanceForDeveloperVerification();
            Object privateGraph = sim.elmList;
            GeneratedBoardInstance privateOwner = sim.getGeneratedBoardInstance();
            GeneratedDiagnosticProofService.setCleanupFailureForDeveloperVerification(true);
            try {
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE &&
                    coordinator.getJob().getReceipt() == null && coordinator.retainsSavedOwnersForDeveloperVerification() &&
                    sim.elmList == privateGraph && sim.getGeneratedBoardInstance() == privateOwner &&
                    privateOwner != original && original.getExternalPowerBindings().areAllDisconnected(),
                    "failed private disposal retains its isolated exact graph before outer restoration");
                boolean rejected = false;
                try { coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 1, false)); }
                catch (IllegalStateException expected) {
                    rejected = "Previous generation has incomplete cleanup".equals(expected.getMessage());
                }
                require(rejected, "new generation cannot orphan an incompletely disposed private graph");
                requireOverlappingProofRejected(sim);
            } finally {
                GeneratedDiagnosticProofService.setCleanupFailureForDeveloperVerification(false);
                coordinator.retryFailedCleanupForDeveloperVerification();
            }
            requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
            GeneratedDiagnosticProofService.CleanupAudit retried = coordinator.getCleanupAuditForDeveloperVerification();
            require(retried != null && retried.getDisposalFailureCount() == 1 &&
                retried.wereLastElementsActuallyDeleted() && retried.wasLastGraphDetached() &&
                retried.wasLastCleanupComplete() && !coordinator.retainsSavedOwnersForDeveloperVerification(),
                "private cleanup retry disposes the graph and releases owners while retaining failed evidence");
            lifecycleFailures++;
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            advanceTo(coordinator, GenerationJob.Stage.HYPOTHESES);
            GeneratedBoardInstance protectedCandidate = sim.getGeneratedBoardInstance();
            Object protectedCandidateGraph = sim.elmList;
            coordinator.advanceForDeveloperVerification();
            GeneratedDiagnosticProofService.setRestoreFailureForDeveloperVerification(true);
            try {
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE &&
                    coordinator.getJob().getReceipt() == null && coordinator.retainsSavedOwnersForDeveloperVerification() &&
                    sim.getGeneratedBoardInstance() == protectedCandidate && sim.elmList == protectedCandidateGraph,
                    "interrupted snapshot restoration retains its exact restored primary owner");
            } finally {
                GeneratedDiagnosticProofService.setRestoreFailureForDeveloperVerification(false);
                coordinator.retryFailedCleanupForDeveloperVerification();
            }
            requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
            require(!coordinator.retainsSavedOwnersForDeveloperVerification() &&
                coordinator.getCleanupAuditForDeveloperVerification().wasLastCleanupComplete(),
                "snapshot retry reuses completed disposal and releases owners");
            lifecycleFailures++;
            // Changes after qualification cannot acquire an earlier proof's authority.
            for (int mutation = 0; mutation < 4; mutation++) {
                coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                    QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
                advanceTo(coordinator, GenerationJob.Stage.PUBLISH);
                boolean changed = false;
                if (mutation == 0) { sim.maxTimeStep *= 2; changed = true; }
                else if (mutation == 3) {
                    sim.getGeneratedChallengeController().perturbScenarioForDeveloperVerification();
                    changed = true;
                }
                else for (CircuitElm element : sim.getGeneratedBoardInstance().getSimulationElements()) {
                    if (mutation == 1 && element instanceof VoltageElm) {
                        ((VoltageElm)element).maxVoltage += 1; changed = true; break;
                    }
                    if (mutation == 2 && element instanceof ResistorElm) {
                        ResistorElm resistor = (ResistorElm)element;
                        resistor.setResistance(resistor.getResistance() * 2); changed = true; break;
                    }
                }
                require(changed, "real consumed dependency mutation executed " + mutation);
                coordinator.advanceForDeveloperVerification();
                require(isDependencyContextChange(coordinator.getJob()),
                    "changed source/load/settings/scenario rejects at dependency comparison " + mutation +
                    "; " + jobDiagnostic(coordinator));
                require(sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph,
                    "changed dependency restores original");
                failures++;
            }

            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(
                FreshGeneratedRuntimeInstallation.Stage.WORKBENCH);
            try {
                coordinator.start(GenerationRequest.leaf(QuickPlayFamilyRegistry.LED_INDICATOR, 0, false), null, false);
                require(coordinator.getJob().getOutcome() != GenerationJob.Outcome.PASS &&
                    coordinator.getJob().getReceipt() == null, "publication failure has no PASS receipt");
                require(sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph &&
                    sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == originalAttached,
                    "publication failure restores attached original");
                failures++;
            } finally { FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null); }

            verifyTemporalRegression(sim, coordinator, original);

            GenerationRequest cacheRequest = GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false);
            coordinator.clearDiagnosticProofCacheForDeveloperVerification();
            Task41SimulationSnapshot warmSnapshot = Task41SimulationSnapshot.capture(sim);
            GeneratedBoardInstance warmedPublished = null;
            int cacheBeforeWarm = coordinator.getDiagnosticProofCacheSize();
            try {
                coordinator.start(cacheRequest, null, false);
                GenerationJob warmJob = coordinator.getJob();
                if (warmJob.getOutcome() == GenerationJob.Outcome.PASS)
                    warmedPublished = sim.getGeneratedBoardInstance();
                require(warmJob.getOutcome() == GenerationJob.Outcome.PASS &&
                    warmJob.getReceipt() != null && warmedPublished != null,
                    "D01 warm-cache setup publishes a real LED owner");
                require(coordinator.getDiagnosticProofCacheSize() == cacheBeforeWarm + 1,
                    "D01 warm-cache setup stores only after publication");
            } finally {
                if (coordinator.isRunning()) coordinator.cancel();
                disposePublishedProbe(sim, warmedPublished, original);
                warmSnapshot.restore(sim); warmSnapshot.assertRestored(sim);
            }

            int warmedCacheSize = coordinator.getDiagnosticProofCacheSize();
            int warmedCacheHits = coordinator.getDiagnosticProofCacheHits();
            Task41SimulationSnapshot warmCancelSnapshot = Task41SimulationSnapshot.capture(sim);
            try {
                coordinator.startForDiagnosticCacheVerification(cacheRequest);
                advanceTo(coordinator, GenerationJob.Stage.HYPOTHESES);
                coordinator.advanceForDeveloperVerification();
                require(coordinator.isRunning() && coordinator.getJob().getStage() == GenerationJob.Stage.SYMPTOM &&
                    coordinator.getDiagnosticProofCacheHits() == warmedCacheHits + 1 &&
                    coordinator.getJob().getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 1,
                    "D01 warm cache hit completes its real proof stage before publication");
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.CANCELLED &&
                    coordinator.getJob().getReceipt() == null &&
                    coordinator.getDiagnosticProofCacheSize() == warmedCacheSize,
                    "D01 warm cache cancellation does not mutate the existing cache");
                requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
            } finally {
                if (coordinator.isRunning()) coordinator.cancel();
                warmCancelSnapshot.restore(sim); warmCancelSnapshot.assertRestored(sim);
            }

            coordinator.clearDiagnosticProofCacheForDeveloperVerification();
            require(coordinator.getDiagnosticProofCacheSize() == 0,
                "D01 cold cancellation starts from an isolated cache");
            int coldCacheMisses = coordinator.getDiagnosticProofCacheMisses();
            Task41SimulationSnapshot coldCancelSnapshot = Task41SimulationSnapshot.capture(sim);
            try {
                coordinator.startForDiagnosticCacheVerification(cacheRequest);
                advanceTo(coordinator, GenerationJob.Stage.SYMPTOM);
                GenerationJob coldJob = coordinator.getJob();
                require(coldJob.isRunning() && coldJob.getReceipt() == null &&
                    coldJob.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) ==
                        GeneratedDiagnosticProofService.requiredWorkUnits(
                            sim.getGeneratedBoardInstance(), false) &&
                    coordinator.getDiagnosticProofCacheMisses() == coldCacheMisses + 1 &&
                    coordinator.getDiagnosticProofCacheSize() == 0,
                    "D01 cold proof reaches post-proof pre-publication with no stored entry");
                coordinator.cancel();
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.CANCELLED &&
                    coordinator.getJob().getReceipt() == null &&
                    coordinator.getDiagnosticProofCacheSize() == 0,
                    "D01 cold cache cancellation discards the prepared entry");
                requireOriginal(sim, original, originalController, originalGraph, originalAttached, copper);
            } finally {
                if (coordinator.isRunning()) coordinator.cancel();
                coldCancelSnapshot.restore(sim); coldCancelSnapshot.assertRestored(sim);
            }

            // Pilot seeds precede held-out seeds. D01 starts this evidence
            // corpus from an empty value-only cache, then proves each immediate
            // repeat is a fresh-owner warm receipt rather than a second serial
            // solver proof.
            coordinator.clearDiagnosticProofCacheForDeveloperVerification();
            String[] families = { QuickPlayFamilyRegistry.LED_INDICATOR,
                QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH, "controlled-indicator" };
            for (int corpus = 0; corpus < 2; corpus++) {
              for (String family : families) {
                for (long seed = corpus * 2; seed < corpus * 2 + 2; seed++) {
                    GeneratedDiagnosticProofReceipt firstDiagnosticReceipt = null;
                    for (int repeat = 0; repeat < 2; repeat++) {
                        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
                        GeneratedBoardInstance published = null;
                        int hits = coordinator.getPlanCacheHits();
                        int proofHits = coordinator.getDiagnosticProofCacheHits();
                        try {
                            coordinator.start("controlled-indicator".equals(family) ?
                                GenerationRequest.controlled(seed) : GenerationRequest.leaf(family, seed, false), null, false);
                            GenerationJob job = coordinator.getJob();
                            if (job.getOutcome() == GenerationJob.Outcome.PASS)
                                published = sim.getGeneratedBoardInstance();
                            boolean proofCacheHit = coordinator.getDiagnosticProofCacheHits() > proofHits;
                            GeneratedDiagnosticProofReceipt diagnosticReceipt =
                                job.getOutcome() == GenerationJob.Outcome.PASS &&
                                sim.getGeneratedChallengeController() != null ?
                                sim.getGeneratedChallengeController().getDiagnosticProofReceipt() : null;
                            metrics.add("{\"family\":\"" + family + "\",\"seed\":\"" + seed +
                                "\",\"corpus\":\"" + (seed < 2 ? "pilot" : "holdout") +
                                "\",\"repeat\":" + repeat + ",\"elapsedMs\":" + job.getElapsedMillis() +
                                ",\"outcome\":\"" + job.getOutcome().name() + "\",\"work\":" + job.getStepCount() +
                                ",\"maxAdvanceMs\":" + coordinator.getMaxAdvanceMillis() +
                                ",\"budgetScope\":\"" + NORMAL_BUDGET_SCOPE + "\"" +
                                ",\"benchmarkAttemptMillis\":" + BENCHMARK_ATTEMPT_MILLIS +
                                ",\"planCacheHit\":" + (coordinator.getPlanCacheHits() > hits) +
                                ",\"proofCacheHit\":" + proofCacheHit +
                                ",\"diagnostic\":" + diagnosticMetrics(diagnosticReceipt,
                                    published, proofCacheHit,
                                    job.getStageElapsedMillis(GenerationJob.Stage.HYPOTHESES)) +
                                ",\"stages\":" + stages(job) + "}");
                            publish(report("RUNNING"));
                            require(job.getOutcome() == GenerationJob.Outcome.PASS && job.getReceipt() != null,
                                "actual staged generation " + family + "/" + seed + ": " +
                                (job.getFailure() == null ? "" : job.getFailure().getMessage()));
                            require(original.getExternalPowerBindings().areAllDisconnected(),
                                "successful publication isolated the retired owner's sources");
                            require(job.getElapsedMillis() <= BENCHMARK_ATTEMPT_MILLIS,
                                "frozen five-second benchmark ceiling");
                            GenerationReceipt receipt = job.getReceipt();
                            require(receipt.getStageCount() == 6 &&
                                sim.getGeneratedChallengeController().getDiagnosticProofReceipt() != null,
                                "complete physical/electrical/diagnostic lineage");
                            require(proofCacheHit == (repeat == 1),
                                "D01 cold/warm cache provenance is exact");
                            int proofWork = job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES);
                            require(proofCacheHit ? proofWork == 1 : proofWork ==
                                    GeneratedDiagnosticProofService.requiredWorkUnits(published, false),
                                "D01 hypothesis work reports exact cold or warm proof provenance");
                            if (repeat == 0) firstDiagnosticReceipt = diagnosticReceipt;
                            else require(sameDiagnosticValue(firstDiagnosticReceipt, diagnosticReceipt),
                                "cold/warm retained diagnostic value equality");
                        } finally {
                            if (coordinator.isRunning()) coordinator.cancel();
                            disposePublishedProbe(sim, published, original);
                            snapshot.restore(sim); snapshot.assertRestored(sim);
                        }
                    }
                }
              }
            }
            require(sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph,
                "generation corpus restored original");
            verifyD01Benchmarks(sim, coordinator, original, originalController, originalGraph,
                originalAttached, copper);
            require(sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph,
                "D01 benchmark cohort restored original");
        } finally {
            if (coordinator.isRunning()) coordinator.cancel();
            sim.setSimRunning(originalRunning);
            sim.setGenerationBusy(false, "");
        }
        publish(report("PASS"));
    }
    private static String report(String status) {
        StringBuilder rows = new StringBuilder();
        for (String row : metrics) { if (rows.length() != 0) rows.append(','); rows.append(row); }
        StringBuilder d01Rows = new StringBuilder();
        for (String row : d01Metrics) {
            if (d01Rows.length() != 0) d01Rows.append(',');
            d01Rows.append(row);
        }
        return "{\"protocol\":\"TSJ-A10-GENERATION-1\",\"status\":\"" + status + "\",\"assertions\":" +
            assertions + ",\"cancellations\":" + cancellations + ",\"rejected\":" + failures +
            ",\"unitCancellations\":" + unitCancellations +
            ",\"lifecycleFailures\":" + lifecycleFailures +
            ",\"cancellationMaxMs\":" + cancellationMaxMs +
            ",\"cleanup\":\"" + ("PASS".equals(status) ? "PASS" : "PENDING") +
            "\",\"maxJobMillis\":" + GenerationCoordinator.MAX_JOB_MILLIS +
            ",\"maxStepMillis\":" + GenerationCoordinator.MAX_STEP_MILLIS +
            ",\"benchmarkAttemptMillis\":" + BENCHMARK_ATTEMPT_MILLIS +
            ",\"maxWork\":" + GenerationCoordinator.MAX_JOB_STEPS +
            ",\"retiredUiReferences\":true" +
            ",\"sessionExclusivity\":true" +
            ",\"temporalBatch\":{\"fixedAndAdaptive\":true,\"exactStateAndEvents\":true,\"rcOracle\":true," +
            "\"measurementCounters\":true,\"stoppedStep\":true}" +
            ",\"temporalRegression\":" + temporalRegression +
            ",\"temporalWork\":" + temporalWork +
            ",\"observationCleanup\":" + observationCleanup +
            ",\"browser\":" + browserEnvironment() + ",\"attempts\":[" + rows + "]" +
            ",\"d01Benchmarks\":[" + d01Rows + "]}";
    }
    /**
     * D01 evidence counts the complete serial admission population and its
     * statically validated adaptive partition for the selected fault. The
     * cache stores only values; no second solver replay or owner is represented
     * by the receipt.
     */
    private static String diagnosticMetrics(GeneratedDiagnosticProofReceipt receipt,
            GeneratedBoardInstance owner, boolean proofCacheHit, long proofStageMillis) {
        if (receipt == null) return "null";
        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
        GeneratedDiagnosticPartitionPlan partition = receipt.getPartitionPlan();
        require(owner != null && partition != null, "D01 receipt has an adaptive plan and owner");
        int observations = 0;
        int repairWitnesses = 0;
        int adaptivePopulationObservations = 0;
        String selectedHypothesis = owner.getFaultBinding().getFault().getHypothesisKey();
        int adaptiveObservations = -1;
        for (GeneratedDiagnosticSolvabilityEvidence value : evidence) {
            observations += value.getSolverSamples().size();
            if (value.isRepairReachable() && value.isCustomerRetestPassed() &&
                    value.isStateIsolated())
                repairWitnesses++;
            int routeObservations = adaptiveRouteObservations(partition, value);
            adaptivePopulationObservations += routeObservations;
            if (selectedHypothesis.equals(value.getHypothesisKey()))
                adaptiveObservations = routeObservations;
        }
        require(adaptiveObservations >= 1,
            "D01 static adaptive plan covers the selected hypothesis");
        int hypotheses = evidence.size();
        int serialSolves = proofCacheHit ? 0 : hypotheses;
        int adaptiveSolves = 0;
        long serialReferenceMillis = proofCacheHit ? 0 : receipt.getElapsedMillis();
        long coldProofMillis = proofCacheHit ? 0 : proofStageMillis;
        long staticPlanOverheadMillis = coldProofMillis - serialReferenceMillis;
        require(staticPlanOverheadMillis >= 0,
            "D01 cold proof timing must include serial reference and static-plan overhead");
        long warmReuseMillis = proofCacheHit ? proofStageMillis : 0;
        return "{\"hypotheses\":" + hypotheses + ",\"observations\":" + observations +
            ",\"serialObservations\":" + observations +
            ",\"adaptiveObservations\":" + adaptiveObservations +
            ",\"adaptivePopulationObservations\":" + adaptivePopulationObservations +
            ",\"serialSolves\":" + serialSolves +
            ",\"adaptiveSolves\":" + adaptiveSolves +
            ",\"solves\":" + serialSolves +
            ",\"hypothesisSolves\":" + serialSolves +
            ",\"repairWitnesses\":" + repairWitnesses +
            ",\"repairs\":" + repairWitnesses +
            ",\"serialReferenceMs\":" + serialReferenceMillis +
            ",\"coldProofMs\":" + coldProofMillis +
            ",\"staticPlanOverheadMs\":" + staticPlanOverheadMillis +
            ",\"warmReuseMs\":" + warmReuseMillis +
            ",\"proofPath\":\"" + (proofCacheHit ? "VALUE_ONLY_REUSE" : "SERIAL_REFERENCE") + "\"" +
            ",\"proofStageScope\":\"SERIAL_REFERENCE_PLUS_STATIC_ADAPTIVE_PLAN\"" +
            ",\"adaptivePlanDepth\":" + partition.getMaximumObservationDepth() +
            ",\"adaptivePlanLeaves\":" + partition.getLeafCount() +
            ",\"adaptivePlanNodes\":" + partition.getNodeCount() + "}";
    }
    private static int adaptiveRouteObservations(GeneratedDiagnosticPartitionPlan partition,
            GeneratedDiagnosticSolvabilityEvidence proof) {
        GeneratedDiagnosticPartitionPlan.Cursor cursor = partition.beginCursor();
        Vector<GeneratedDiagnosticSample> samples = proof.getSolverSamples();
        while (!cursor.isComplete()) {
            String expected = cursor.getNextSampleId();
            GeneratedDiagnosticSample selected = null;
            for (GeneratedDiagnosticSample sample : samples)
                if (sample != null && expected.equals(sample.getSampleId())) {
                    selected = sample; break;
                }
            require(selected != null, "D01 adaptive route sample is in the serial evidence");
            cursor.accept(selected);
        }
        require(cursor.getResolvedHypothesisKeys().contains(proof.getHypothesisKey()),
            "D01 adaptive route resolves to the retained hypothesis");
        return cursor.getObservationCount();
    }
    private static void verifyD01Benchmarks(CirSim sim, GenerationCoordinator coordinator,
            GeneratedBoardInstance original, GeneratedChallengeController originalController,
            Object originalGraph, int originalAttached, String copper) {
        coordinator.clearDiagnosticProofCacheForDeveloperVerification();
        String[] families = { Rb15Plan.FAMILY_ID, "controlled-indicator",
            QuickPlayFamilyRegistry.SENSOR_CONTROL, QuickPlayFamilyRegistry.SENSOR_CONTROL };
        long[] seeds = { 0L, 0L, 0L, 1L };
        for (int fixture = 0; fixture < families.length; fixture++) {
            String family = families[fixture];
            String publishedFamily = publishedFamilyForFixture(family);
            long seed = seeds[fixture];
            GenerationRequest request = Rb15Plan.FAMILY_ID.equals(family) ?
                GenerationRequest.leaf(Rb15Plan.FAMILY_ID, seed, false) :
                "controlled-indicator".equals(family) ? GenerationRequest.controlled(seed) :
                GenerationRequest.leaf(QuickPlayFamilyRegistry.SENSOR_CONTROL, seed, false);
            GeneratedDiagnosticProofReceipt firstDiagnosticReceipt = null;
            for (int repeat = 0; repeat < 2; repeat++) {
                Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
                GeneratedBoardInstance published = null;
                int planHits = coordinator.getPlanCacheHits();
                int proofHits = coordinator.getDiagnosticProofCacheHits();
                int proofMisses = coordinator.getDiagnosticProofCacheMisses();
                int cacheSize = coordinator.getDiagnosticProofCacheSize();
                try {
                    coordinator.start(request, null, false);
                    GenerationJob job = coordinator.getJob();
                    if (job.getOutcome() == GenerationJob.Outcome.PASS)
                        published = sim.getGeneratedBoardInstance();
                    boolean planCacheHit = coordinator.getPlanCacheHits() > planHits;
                    boolean proofCacheHit = coordinator.getDiagnosticProofCacheHits() > proofHits;
                    GeneratedDiagnosticProofReceipt diagnosticReceipt =
                        job.getOutcome() == GenerationJob.Outcome.PASS &&
                        sim.getGeneratedChallengeController() != null ?
                        sim.getGeneratedChallengeController().getDiagnosticProofReceipt() : null;
                    require(job.getOutcome() == GenerationJob.Outcome.PASS &&
                        job.getReceipt() != null && published != null &&
                        publishedFamily.equals(published.getCircuitFamilyId()) &&
                        diagnosticReceipt != null,
                        "D01 benchmark completes " + family + " repeat " + repeat + ": " +
                        (job.getFailure() == null ? "" : job.getFailure().getMessage()));
                    Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
                        diagnosticReceipt.getEvidence();
                    int serialObservations = 0;
                    for (GeneratedDiagnosticSolvabilityEvidence value : evidence)
                        serialObservations += value.getSolverSamples().size();
                    if (Rb15Plan.FAMILY_ID.equals(family)) {
                        require(published.getBoard().getComponentIds().size() == 16 &&
                            evidence.size() == 3 && serialObservations == 27,
                            "D01 RB15 benchmark retains the complete 16-part/3-hypothesis population");
                    } else if (QuickPlayFamilyRegistry.SENSOR_CONTROL.equals(family)) {
                        require(evidence.size() == 3 && serialObservations == 108,
                            "D01 E04 benchmark retains the complete 3-resistor/108-observation population");
                        verifyE04DiagnosticPopulation(published, diagnosticReceipt, seed);
                    } else {
                        require(evidence.size() >= 4 && serialObservations > 100,
                            "D01 larger evolving benchmark retains its complete hypothesis population");
                    }
                    verifyD01StageBudgetAndPublication(sim, coordinator, job, published,
                        original, originalGraph);
                    require(proofCacheHit == (repeat == 1) &&
                        diagnosticReceipt.isWarmReuseForDeveloperVerification() == proofCacheHit,
                        "D01 benchmark cache provenance is exact for " + family);
                    require(coordinator.getDiagnosticProofCacheMisses() == proofMisses +
                            (repeat == 0 ? 1 : 0) &&
                        coordinator.getDiagnosticProofCacheSize() == cacheSize +
                            (repeat == 0 ? 1 : 0),
                        "D01 proof cache publication is exact for " + family + "/" + seed);
                    int proofWork = job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES);
                    require(proofCacheHit ? proofWork == 1 : proofWork ==
                            GeneratedDiagnosticProofService.requiredWorkUnits(published, false),
                        "D01 benchmark reports exact cold/warm hypothesis work for " + family);
                    require(job.getElapsedMillis() <= GenerationCoordinator.MAX_JOB_MILLIS &&
                        coordinator.getMaxAdvanceMillis() <= GenerationCoordinator.MAX_STEP_MILLIS,
                        "D01 benchmark remains inside job and unit budgets for " + family);
                    require(job.getReceipt().getStageCount() == 6,
                        "D01 benchmark retains all six generation stages for " + family);
                    String diagnostic = diagnosticMetrics(diagnosticReceipt, published,
                        proofCacheHit, job.getStageElapsedMillis(GenerationJob.Stage.HYPOTHESES));
                    d01Metrics.add("{\"family\":\"" + family + "\",\"seed\":\"" + seed + "\"" +
                        ",\"corpus\":\"d01\",\"repeat\":" + repeat +
                        ",\"elapsedMs\":" + job.getElapsedMillis() +
                        ",\"outcome\":\"" + job.getOutcome().name() +
                        "\",\"work\":" + job.getStepCount() +
                        ",\"maxAdvanceMs\":" + coordinator.getMaxAdvanceMillis() +
                        ",\"budgetScope\":\"" + D01_BUDGET_SCOPE + "\"" +
                        ",\"benchmarkAttemptMillis\":" + GenerationCoordinator.MAX_JOB_MILLIS +
                        ",\"planCacheHit\":" + planCacheHit +
                        ",\"proofCacheHit\":" + proofCacheHit +
                        ",\"diagnostic\":" + diagnostic +
                        ",\"stages\":" + stages(job) + "}");
                    publish(report("RUNNING"));
                    if (repeat == 0) firstDiagnosticReceipt = diagnosticReceipt;
                    else require(sameDiagnosticValue(firstDiagnosticReceipt, diagnosticReceipt),
                        "D01 cold/warm retained diagnostic value equality for " + family);
                } finally {
                    if (coordinator.isRunning()) coordinator.cancel();
                    disposePublishedProbe(sim, published, original);
                    snapshot.restore(sim); snapshot.assertRestored(sim);
                    requireOriginal(sim, original, originalController, originalGraph,
                        originalAttached, copper);
                }
            }
        }
    }

    /** Benchmark reporting keeps the stable fixture alias while publication uses its canonical ID. */
    private static String publishedFamilyForFixture(String family) {
        return "controlled-indicator".equals(family) ?
            ControlledIndicatorDeviceBehavior.FAMILY_ID : family;
    }
    private static void verifyE04DiagnosticPopulation(GeneratedBoardInstance owner,
            GeneratedDiagnosticProofReceipt receipt, long seed) {
        require(owner != null && receipt != null &&
            QuickPlayFamilyRegistry.SENSOR_CONTROL.equals(owner.getCircuitFamilyId()) &&
            owner.getSeed() == seed &&
            (seed == 0L ? SensorControlGenerator.DIRECT_VARIANT :
                SensorControlGenerator.HYSTERETIC_VARIANT).equals(owner.getTopologyVariantId()),
            "D01 E04 benchmark retains the deterministic direct/hysteretic variant for seed " + seed);
        Vector<GeneratedFaultCandidate> candidates = owner.getFaultCandidates();
        Vector<String> expectedOwners = new Vector<String>();
        expectedOwners.add("RBIAS"); expectedOwners.add("RFB"); expectedOwners.add("RREF");
        Vector<String> expectedFaultIds = new Vector<String>();
        expectedFaultIds.add("SENSOR_RBIAS_OPEN"); expectedFaultIds.add("SENSOR_RFB_OPEN");
        expectedFaultIds.add("SENSOR_RREF_OPEN");
        Vector<String> expectedKeys = GeneratedDiagnosticSolvabilityAdmission.getHypothesisKeys(candidates);
        require(candidates.size() == 3 &&
            GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(candidates) == 3 &&
            owner.getAdmittedFaultPhysicalOwnerCount() == 3 &&
            owner.getAdmittedFaultPhysicalOwnerIds().equals(expectedOwners) &&
            owner.getDiagnosticSolvabilityContract().getFamilyId().equals(
                QuickPlayFamilyRegistry.SENSOR_CONTROL) &&
            owner.getDiagnosticSolvabilityContract().getTopologyVariantId().equals(
                owner.getTopologyVariantId()) && owner.getDiagnosticSolvabilityContract().getSeed() == seed &&
            owner.getDiagnosticSolvabilityContract().getAdmittedCandidateCount() == 3 &&
            owner.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount() == 3 &&
            owner.getDiagnosticSolvabilityContract().getHypothesisKeys().equals(expectedKeys),
            "D01 E04 admission retains exactly RBIAS/RREF/RFB and no supply/U1 candidate");
        Vector<String> candidateOwners = new Vector<String>();
        Vector<String> candidateIds = new Vector<String>();
        for (GeneratedFaultCandidate candidate : candidates) {
            GeneratedFaultServiceability serviceability = candidate.getServiceability();
            require(candidate.isAdmitted() && candidate.getFault().getType() ==
                    GeneratedFaultType.RESISTOR_OPEN &&
                QuickPlayFamilyRegistry.SENSOR_CONTROL.equals(candidate.getFault().getCircuitFamilyId()) &&
                serviceability != null && serviceability.getLocus() != null &&
                serviceability.getLocus().getType() == GeneratedFaultLocusType.COMPONENT_INTERNAL &&
                candidate.getFault().getTargetComponentId().equals(
                    serviceability.getLocus().getComponentId()) &&
                expectedOwners.contains(serviceability.getLocus().getOwnerId()) &&
                !"U1".equals(serviceability.getLocus().getOwnerId()) &&
                !"J1".equals(serviceability.getLocus().getOwnerId()),
                "D01 E04 candidate has a physical resistor owner");
            candidateOwners.add(serviceability.getLocus().getOwnerId());
            candidateIds.add(candidate.getFault().getId());
        }
        require(candidateOwners.size() == 3 && candidateOwners.containsAll(expectedOwners) &&
            candidateIds.size() == 3 && candidateIds.containsAll(expectedFaultIds),
            "D01 E04 candidate owners cover all three physical resistors exactly");
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
        GeneratedDiagnosticPartitionPlan partition = receipt.getPartitionPlan();
        require(provider != null && partition != null &&
            receipt.getContextKey() != null &&
            "sensor-control-diagnostic@1".equals(receipt.getProviderId()) &&
            receipt.getProgramIdentity().equals(provider.getObservationProgram().canonical()) &&
            partition.getHypothesisKeys().equals(expectedKeys) &&
            partition.getProviderId().equals(provider.getProviderId()) &&
            partition.getProgramIdentity().equals(provider.getObservationProgram().canonical()),
            "D01 E04 receipt retains the owner-bound provider and complete partition identity");
        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        partition.validateAgainst(owner.getFaultCandidates(), provider.getObservationProgram(), evidence);
        require(evidence.size() == 3, "D01 E04 receipt covers exactly three hypotheses");
        for (GeneratedDiagnosticSolvabilityEvidence value : evidence)
            require(value.getFamilyId().equals(QuickPlayFamilyRegistry.SENSOR_CONTROL) &&
                value.getSeed() == seed && value.getAdmittedCandidateCount() == 3 &&
                value.getAdmittedPhysicalOwnerCount() == 3 &&
                expectedKeys.contains(value.getHypothesisKey()) &&
                value.getSolverSamples().size() == 36 && value.isRepairReachable() &&
                value.isCustomerRetestPassed() && value.isStateIsolated() &&
                value.hasUnaffectedFunctionRetestObservation() &&
                "PASS".equals(value.getDeterministicResult()) &&
                "NONE".equals(value.getDeterministicRejectionReason()) &&
                value.getRepairSemantics() != null && value.getMeasuredExecutionDepth() > 0 &&
                value.getExecutedRepairActionIds().contains(WorkbenchOperation.CATALOG_INSTALL) &&
                value.getExecutedActionIds().contains(GeneratedBoardOperationIds.CUSTOMER_RETEST),
                "D01 E04 serial proof evidence is complete and repair-reachable");
    }
    private static void verifyD01StageBudgetAndPublication(CirSim sim,
            GenerationCoordinator coordinator, GenerationJob job, GeneratedBoardInstance published,
            GeneratedBoardInstance original, Object originalGraph) {
        int stageWork = 0;
        for (GenerationJob.Stage stage : GenerationJob.Stage.values()) {
            require(job.getStageWorkCount(stage) >= 1 &&
                job.getStageElapsedMillis(stage) >= 0 &&
                job.getStageElapsedMillis(stage) <= GenerationCoordinator.MAX_JOB_MILLIS,
                "D01 generation stage is bounded and recorded: " + stage);
            stageWork += job.getStageWorkCount(stage);
        }
        require(stageWork == job.getStepCount() && job.getStepCount() <= GenerationCoordinator.MAX_JOB_STEPS &&
            job.getElapsedMillis() >= 0 && job.getElapsedMillis() <= GenerationCoordinator.MAX_JOB_MILLIS &&
            coordinator.getMaxAdvanceMillis() <= GenerationCoordinator.MAX_STEP_MILLIS,
            "D01 generation job remains inside its work and wall budgets");
        require(sim.getGeneratedBoardInstance() == published && published != null &&
            sim.elmList != originalGraph && published != original &&
            !coordinator.retainsSavedOwnersForDeveloperVerification() &&
            !coordinator.retainsObservationForDeveloperVerification(),
            "D01 publication owns a fresh board without retaining saved or proof owners");
        require(sim.getGeneratedBoardInstance() != original,
            "D01 publication replaces the original board only after complete proof");
    }
    private static String stages(GenerationJob job) {
        StringBuilder result = new StringBuilder("[");
        for (GenerationJob.Stage stage : GenerationJob.Stage.values()) {
            if (result.length() > 1) result.append(',');
            result.append("{\"stage\":\"").append(stage.name()).append("\",\"elapsedMs\":")
                .append(job.getStageElapsedMillis(stage)).append(",\"work\":")
                .append(job.getStageWorkCount(stage)).append('}');
        }
        return result.append(']').toString();
    }
    private static void verifyTemporalRegression(CirSim sim, GenerationCoordinator coordinator,
            GeneratedBoardInstance original) {
        GenerationRequest request = GenerationRequest.leaf(QuickPlayFamilyRegistry.RC_DELAY, 0, false);
        Object originalGraph = sim.elmList;
        coordinator.startForDeveloperVerification(request);
        advanceTo(coordinator, GenerationJob.Stage.HEALTHY);
        coordinator.advanceForDeveloperVerification();
        require(coordinator.isRunning() && coordinator.getJob().getStage() == GenerationJob.Stage.HEALTHY &&
            coordinator.getJob().getStageWorkCount(GenerationJob.Stage.HEALTHY) == 1,
            "RC yields between the healthy and faulty profiles");
        coordinator.cancel();
        require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.CANCELLED &&
            sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph &&
            !sim.activeMeasurementOverlay && coordinator.getCancellationLatencyMillis() <= 500,
            "RC profile boundary cancellation restores the exact owner within 500 ms");
        cancellationMaxMs = Math.max(cancellationMaxMs, coordinator.getCancellationLatencyMillis());
        String dependencies = null;
        long firstMillis = 0, firstAdvance = 0, repeatMillis = 0, repeatAdvance = 0;
        for (int repeat = 0; repeat < 2; repeat++) {
            Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
            GeneratedBoardInstance published = null;
            try {
                if (repeat == 0) {
                    coordinator.start(request, null, false);
                    GenerationJob job = coordinator.getJob();
                    if (job.getOutcome() == GenerationJob.Outcome.PASS) published = sim.getGeneratedBoardInstance();
                    require(published != null && job.getReceipt() != null,
                        "RC seed 0 completes within the current job budget: " +
                        (job.getFailure() == null ? job.getOutcome().name() : job.getFailure().getMessage()));
                    dependencies = job.getReceipt().getDependencies();
                    firstMillis = job.getElapsedMillis(); firstAdvance = coordinator.getMaxAdvanceMillis();
                } else {
                    coordinator.startForDeveloperVerification(request);
                    advanceTo(coordinator, GenerationJob.Stage.PUBLISH);
                    GeneratedBoardInstance candidate = sim.getGeneratedBoardInstance();
                    String repeatedDependencies = GenerationDependencyContext.capture(sim, candidate,
                        request.canonical(), request.canonical()).canonical();
                    require(dependencies.equals(repeatedDependencies),
                        "RC first/repeat consumed dependency identity is deterministic; " +
                        firstDifference(dependencies, repeatedDependencies));
                    require(candidate.getTemporalBehavior() instanceof RcDelayTemporalBehavior,
                        "RC temporal canary reached the production model");
                    ((RcDelayTemporalBehavior)candidate.getTemporalBehavior())
                        .perturbHealthyReferenceForDeveloperVerification();
                    coordinator.advanceForDeveloperVerification();
                    GenerationJob job = coordinator.getJob();
                    require(isDependencyContextChange(job),
                        "altered retained temporal reference invalidates publication");
                    repeatMillis = job.getElapsedMillis(); repeatAdvance = coordinator.getMaxAdvanceMillis();
                }
                require(coordinator.getMaxAdvanceMillis() <= GenerationCoordinator.MAX_STEP_MILLIS,
                    "RC operations remain inside the active unit budget");
            } finally {
                if (coordinator.isRunning()) coordinator.cancel();
                disposePublishedProbe(sim, published, original);
                snapshot.restore(sim); snapshot.assertRestored(sim);
            }
        }
        temporalRegression = "{\"family\":\"RC_DELAY\",\"seed\":\"0\",\"firstOutcome\":\"PASS\"," +
            "\"firstMs\":" + firstMillis + ",\"firstMaxAdvanceMs\":" + firstAdvance +
            ",\"repeatMs\":" + repeatMillis + ",\"repeatMaxAdvanceMs\":" + repeatAdvance +
            ",\"profileBoundaryCancelled\":true,\"dependenciesEqual\":true,\"changedReferenceRejected\":true}";
    }
    private static void verifyRetiredUiReferences(CirSim sim, GenerationCoordinator coordinator,
            GeneratedBoardInstance original) {
        Task41SimulationSnapshot baseline = Task41SimulationSnapshot.capture(sim);
        GeneratedBoardInstance published = null;
        try {
            Scope[] priorScopes = new Scope[20];
            priorScopes[0] = new Scope(sim);
            CircuitElm priorElement = original.getSimulationElements().firstElement();
            priorScopes[0].setElm(priorElement);
            sim.scopes = priorScopes; sim.scopeCount = 1; sim.scopeColCount = new int[20];
            sim.dragElm = priorElement; sim.menuElm = priorElement;
            for (CircuitElm element : original.getSimulationElements())
                if (element instanceof SwitchElm) { sim.heldSwitchElm = (SwitchElm)element; break; }
            Task41SimulationSnapshot withUi = Task41SimulationSnapshot.capture(sim);
            coordinator.startForDeveloperVerification(GenerationRequest.leaf(
                QuickPlayFamilyRegistry.LED_INDICATOR, 0, false));
            advanceTo(coordinator, GenerationJob.Stage.PHYSICAL);
            require(sim.scopes != priorScopes && sim.scopeCount == 0 && sim.dragElm == null &&
                sim.menuElm == null && sim.heldSwitchElm == null,
                "private generation isolates old scopes and UI element references");
            coordinator.cancel();
            withUi.assertRestored(sim);
            coordinator.start(GenerationRequest.leaf(QuickPlayFamilyRegistry.LED_INDICATOR, 0, false), null, false);
            if (coordinator.getJob().getOutcome() == GenerationJob.Outcome.PASS)
                published = sim.getGeneratedBoardInstance();
            require(published != null && sim.scopes != priorScopes && sim.scopes[0] == null &&
                sim.scopeCount == 0 && sim.dragElm == null && sim.menuElm == null && sim.heldSwitchElm == null,
                "publication retires old scope and UI references after preserving rollback");
        } finally {
            if (coordinator.isRunning()) coordinator.cancel();
            disposePublishedProbe(sim, published, original);
            baseline.restore(sim); baseline.assertRestored(sim);
        }
    }
    private static void disposePublishedProbe(CirSim sim, GeneratedBoardInstance published,
            GeneratedBoardInstance original) {
        if (published == null) return;
        require(sim.getGeneratedBoardInstance() == published && published != original,
            "probe cleanup retains its exact private owner");
        published.getExternalPowerBindings().setConnected(false);
        sim.setSimRunning(false);
        for (CircuitElm element : published.getSimulationElements()) {
            require(!original.getSimulationElements().contains(element),
                "probe graph is disjoint before disposal");
            element.delete();
        }
    }
    private static void requireOverlappingProofRejected(CirSim sim) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController controller = sim.getGeneratedChallengeController();
        Object graph = sim.elmList;
        boolean rejected = false;
        try { GeneratedDiagnosticProofService.begin(sim, owner, controller); }
        catch (IllegalStateException expected) {
            rejected = "Another production proof session is active".equals(expected.getMessage());
        }
        require(rejected && sim.getGeneratedBoardInstance() == owner &&
            sim.getGeneratedChallengeController() == controller && sim.elmList == graph,
            "yielded or cleanup-pending production proof rejects overlap without mutation");
    }
    /**
     * A context mismatch can be observed either while the receipt lineage is
     * revalidated or at the final publication boundary.  Both paths must be
     * stale, receipt-free failures; the boundary suffix is useful provenance,
     * not a different result.
     */
    private static boolean isDependencyContextChange(GenerationJob job) {
        if (job == null || job.getOutcome() != GenerationJob.Outcome.STALE ||
                job.getReceipt() != null || job.getFailure() == null)
            return false;
        String message = job.getFailure().getMessage();
        return message != null && message.startsWith("Generation dependency context changed");
    }

    /**
     * A cold serial proof and a warm value-only reuse intentionally have
     * different GenerationReceipt operation lineage: their HYPOTHESES stage
     * reports full serial work versus one reuse unit. Compare the retained
     * value instead, while the surrounding checks continue to verify those
     * distinct work counts and provenance.
     */
    private static boolean sameDiagnosticValue(GeneratedDiagnosticProofReceipt first,
            GeneratedDiagnosticProofReceipt second) {
        if (first == null || second == null || first.getContextKey() == null ||
                second.getContextKey() == null || first.getPartitionPlan() == null ||
                second.getPartitionPlan() == null ||
                !first.getContextKey().equals(second.getContextKey()) ||
                !first.getProviderId().equals(second.getProviderId()) ||
                !first.getProgramIdentity().equals(second.getProgramIdentity()) ||
                !first.getPartitionPlan().canonical().equals(second.getPartitionPlan().canonical()))
            return false;
        return sameEvidence(first.getEvidence(), second.getEvidence());
    }

    /** Exact immutable proof evidence comparison; live owners are not read. */
    private static boolean sameEvidence(Vector<GeneratedDiagnosticSolvabilityEvidence> first,
            Vector<GeneratedDiagnosticSolvabilityEvidence> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            GeneratedDiagnosticSolvabilityEvidence left = first.get(index);
            GeneratedDiagnosticSolvabilityEvidence right = second.get(index);
            if (left == null || right == null ||
                    !left.getRouteId().equals(right.getRouteId()) ||
                    !left.getFamilyId().equals(right.getFamilyId()) || left.getSeed() != right.getSeed() ||
                    !left.getHypothesisKey().equals(right.getHypothesisKey()) ||
                    left.getAdmittedCandidateCount() != right.getAdmittedCandidateCount() ||
                    left.getAdmittedPhysicalOwnerCount() != right.getAdmittedPhysicalOwnerCount() ||
                    left.getDeclaredPlanDepth() != right.getDeclaredPlanDepth() ||
                    !left.getDeclaredTemplateIds().equals(right.getDeclaredTemplateIds()) ||
                    !left.getDeclaredProbeTargetIds().equals(right.getDeclaredProbeTargetIds()) ||
                    !left.getDeclaredInputPowerTransitions().equals(
                        right.getDeclaredInputPowerTransitions()) ||
                    !left.getDeclaredIsolationActionIds().equals(
                        right.getDeclaredIsolationActionIds()) ||
                    !left.getDeclaredRepairActionIds().equals(right.getDeclaredRepairActionIds()) ||
                    !left.getDeclaredWorkflowActionIds().equals(right.getDeclaredWorkflowActionIds()) ||
                    !left.getDeclaredPlayerOperationIds().equals(
                        right.getDeclaredPlayerOperationIds()) ||
                    !left.getDeclaredMeterModeIds().equals(right.getDeclaredMeterModeIds()) ||
                    !left.getDeclaredTemporalWaitSampleIds().equals(
                        right.getDeclaredTemporalWaitSampleIds()) ||
                    !left.getDeclaredRailDomainIds().equals(right.getDeclaredRailDomainIds()) ||
                    left.hasDeclaredParallelPathAmbiguity() !=
                        right.hasDeclaredParallelPathAmbiguity() ||
                    !left.getExecutedActionIds().equals(right.getExecutedActionIds()) ||
                    !left.getExecutedRepairActionIds().equals(right.getExecutedRepairActionIds()) ||
                    !left.getExecutedMeterModeIds().equals(right.getExecutedMeterModeIds()) ||
                    !left.getExecutedInputPowerTransitions().equals(
                        right.getExecutedInputPowerTransitions()) ||
                    !left.getExecutedIsolationActionIds().equals(
                        right.getExecutedIsolationActionIds()) ||
                    !left.getExecutedTemporalWaitSamples().equals(
                        right.getExecutedTemporalWaitSamples()) ||
                    left.getMeasuredExecutionDepth() != right.getMeasuredExecutionDepth() ||
                    left.getCompletedSemanticActions() != right.getCompletedSemanticActions() ||
                    !left.getRepairSemantics().isEquivalentTo(right.getRepairSemantics()) ||
                    left.hasUnaffectedFunctionRetestObservation() !=
                        right.hasUnaffectedFunctionRetestObservation() ||
                    !left.getEquivalentRepairClass().equals(right.getEquivalentRepairClass()) ||
                    !left.getDeterministicResult().equals(right.getDeterministicResult()) ||
                    !left.getDeterministicRejectionReason().equals(
                        right.getDeterministicRejectionReason()) ||
                    left.isRepairReachable() != right.isRepairReachable() ||
                    left.isCustomerRetestPassed() != right.isCustomerRetestPassed() ||
                    left.isStateIsolated() != right.isStateIsolated() ||
                    !sameSamples(left.getSolverSamples(), right.getSolverSamples()))
                return false;
        }
        return true;
    }

    private static boolean sameSamples(Vector<GeneratedDiagnosticSample> first,
            Vector<GeneratedDiagnosticSample> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            GeneratedDiagnosticSample left = first.get(index);
            GeneratedDiagnosticSample right = second.get(index);
            if (left == null || right == null ||
                    !left.getSampleId().equals(right.getSampleId()) ||
                    left.getOutcome() != right.getOutcome()) return false;
            if (!left.isOverRange() && (Double.doubleToLongBits(left.getValue()) !=
                    Double.doubleToLongBits(right.getValue()) ||
                    Double.doubleToLongBits(left.getComparisonTolerance()) !=
                    Double.doubleToLongBits(right.getComparisonTolerance()))) return false;
        }
        return true;
    }

    /** A bounded failure diagnostic for otherwise opaque canonical contexts. */
    private static String firstDifference(String first, String second) {
        if (first == null || second == null)
            return "missing-context first=" + (first == null) + ";second=" + (second == null);
        int limit = Math.min(first.length(), second.length());
        int index = 0;
        while (index < limit && first.charAt(index) == second.charAt(index)) index++;
        if (index == limit && first.length() == second.length()) return "identical-context";
        int from = Math.max(0, index - 64);
        int firstTo = Math.min(first.length(), index + 128);
        int secondTo = Math.min(second.length(), index + 128);
        return "context-diff-at=" + index + ";first-length=" + first.length() +
            ";second-length=" + second.length() + ";first=" + first.substring(from, firstTo) +
            ";second=" + second.substring(from, secondTo);
    }

    private static void advanceTo(GenerationCoordinator coordinator, GenerationJob.Stage stage) {
        for (int i = 0; i < GenerationCoordinator.MAX_JOB_STEPS && coordinator.isRunning() &&
                coordinator.getJob().getStage() != stage; i++) coordinator.advanceForDeveloperVerification();
        require(coordinator.isRunning() && coordinator.getJob().getStage() == stage,
            "bounded actual stage reached " + stage + "; " + jobDiagnostic(coordinator));
    }
    private static String jobDiagnostic(GenerationCoordinator coordinator) {
        GenerationJob job = coordinator.getJob();
        if (job == null) return "no-job";
        Throwable failure = job.getFailure();
        return "outcome=" + job.getOutcome() + ";stage=" + job.getStage() +
            ";work=" + job.getStepCount() + ";elapsedMs=" + job.getElapsedMillis() +
            ";failure=" + (failure == null ? "none" : failure.getMessage());
    }
    private static void requireOriginal(CirSim sim, GeneratedBoardInstance original,
            GeneratedChallengeController controller, Object graph, int attached, String copper) {
        require(sim.getGeneratedBoardInstance() == original && sim.getGeneratedChallengeController() == controller &&
            sim.elmList == graph && sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == attached &&
            copper.equals(original.getPcbLayout().geometryFingerprint()) && !sim.activeMeasurementOverlay &&
            !sim.generatedRuntimeInstallationInProgress, "failed transaction restored original ownership and copper");
    }
    private static void require(boolean value, String label) {
        assertions++; if (!value) throw new IllegalStateException("A10: " + label);
    }
    private static native void clearReport() /*-{
        $doc.documentElement.removeAttribute("data-tsj-a10-report");
    }-*/;
    private static native String browserEnvironment() /*-{
        var memory = $wnd.performance && $wnd.performance.memory;
        return JSON.stringify({
            viewport: {width: $doc.documentElement.clientWidth, height: $doc.documentElement.clientHeight},
            devicePixelRatio: $wnd.devicePixelRatio,
            userAgent: $wnd.navigator.userAgent,
            memory: memory ? {status: "AVAILABLE", usedJSHeapSize: memory.usedJSHeapSize,
                totalJSHeapSize: memory.totalJSHeapSize, jsHeapSizeLimit: memory.jsHeapSizeLimit} :
                {status: "UNAVAILABLE", reason: "performance.memory is not exposed"}
        });
    }-*/;
    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-a10-report", report);
    }-*/;
}
