package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Opt-in qualification of the actual generation owner, never a player action. */
final class A10GenerationDeveloperVerifier {
    private static int assertions, cancellations, unitCancellations, failures, lifecycleFailures;
    private static final long BENCHMARK_ATTEMPT_MILLIS = 5000;
    private static long cancellationMaxMs;
    private static String temporalRegression;
    private static final Vector<String> metrics = new Vector<String>();
    private A10GenerationDeveloperVerifier() { }

    static void verify(CirSim sim, boolean forceFailure) {
        assertions = cancellations = unitCancellations = failures = lifecycleFailures = 0; cancellationMaxMs = 0;
        metrics.clear(); clearReport();
        temporalRegression = "null";
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
                    "private operation yielded without an overlay or global proof guard");
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
                require(coordinator.getJob().getOutcome() == GenerationJob.Outcome.STALE &&
                    coordinator.getJob().getReceipt() == null &&
                    coordinator.getJob().getFailure() != null &&
                    "Generation dependency context changed".equals(coordinator.getJob().getFailure().getMessage()),
                    "changed source/load/settings/scenario rejects at dependency comparison " + mutation);
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

            // Pilot seeds precede held-out seeds. Cold/warm are consecutive fresh
            // constructions in one loaded application, as in A01 (not cache flushing).
            String[] families = { QuickPlayFamilyRegistry.LED_INDICATOR,
                QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH, "controlled-indicator" };
            for (int corpus = 0; corpus < 2; corpus++) {
              for (String family : families) {
                for (long seed = corpus * 2; seed < corpus * 2 + 2; seed++) {
                    String firstIdentity = null;
                    for (int repeat = 0; repeat < 2; repeat++) {
                        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
                        GeneratedBoardInstance published = null;
                        int hits = coordinator.getPlanCacheHits();
                        try {
                            coordinator.start("controlled-indicator".equals(family) ?
                                GenerationRequest.controlled(seed) : GenerationRequest.leaf(family, seed, false), null, false);
                            GenerationJob job = coordinator.getJob();
                            if (job.getOutcome() == GenerationJob.Outcome.PASS)
                                published = sim.getGeneratedBoardInstance();
                            metrics.add("{\"family\":\"" + family + "\",\"seed\":\"" + seed +
                                "\",\"corpus\":\"" + (seed < 2 ? "pilot" : "holdout") +
                                "\",\"repeat\":" + repeat + ",\"elapsedMs\":" + job.getElapsedMillis() +
                                ",\"outcome\":\"" + job.getOutcome().name() + "\",\"work\":" + job.getStepCount() +
                                ",\"maxAdvanceMs\":" + coordinator.getMaxAdvanceMillis() +
                                ",\"planCacheHit\":" + (coordinator.getPlanCacheHits() > hits) +
                                ",\"proofCacheHit\":false,\"stages\":" + stages(job) + "}");
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
                            if (repeat == 0) firstIdentity = receipt.canonical();
                            else require(firstIdentity.equals(receipt.canonical()), "cold/warm canonical receipt equality");
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
            ",\"browser\":" + browserEnvironment() + ",\"attempts\":[" + rows + "]}";
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
                    require(dependencies.equals(GenerationDependencyContext.capture(sim, candidate,
                        request.canonical(), request.canonical()).canonical()),
                        "RC first/repeat consumed dependency identity is deterministic");
                    require(candidate.getTemporalBehavior() instanceof RcDelayTemporalBehavior,
                        "RC temporal canary reached the production model");
                    ((RcDelayTemporalBehavior)candidate.getTemporalBehavior())
                        .perturbHealthyReferenceForDeveloperVerification();
                    coordinator.advanceForDeveloperVerification();
                    GenerationJob job = coordinator.getJob();
                    require(job.getOutcome() == GenerationJob.Outcome.STALE && job.getReceipt() == null &&
                        job.getFailure() != null &&
                        "Generation dependency context changed".equals(job.getFailure().getMessage()),
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
    private static void advanceTo(GenerationCoordinator coordinator, GenerationJob.Stage stage) {
        for (int i = 0; i < GenerationCoordinator.MAX_JOB_STEPS && coordinator.isRunning() &&
                coordinator.getJob().getStage() != stage; i++) coordinator.advanceForDeveloperVerification();
        require(coordinator.isRunning() && coordinator.getJob().getStage() == stage,
            "bounded actual stage reached " + stage);
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
