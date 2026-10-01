package com.lushprojects.circuitjs1.client;

import com.google.gwt.json.client.*;
import com.google.gwt.user.client.Timer;
import java.util.Vector;

/** Isolated-export entry point only. Never compiled into the shipping module. */
public final class Q30NormalScreenHarness extends circuitjs1 {
    private static final String BASE = "41f02f1b9f6c32cb8cf4ffbf9e8bd2bb3ea2476b";
    private final JSONObject report = new JSONObject();
    private CirSim sim;
    private GenerationCoordinator coordinator;
    private GenerationRequest request;
    private GenerationJob ownJob;
    private IsolatedGenerationQualification authority;
    private Task41SimulationSnapshot snapshot;
    private GeneratedBoardInstance predecessor;
    private GeneratedChallengeController predecessorController;
    private Vector<CircuitElm> predecessorGraph;
    private Timer observer;
    private String mode, seed;
    private boolean done, intervention;
    private long began;
    private Throwable startupFailure;

    @Override public void loadSimulator() {
        super.loadSimulator();
        sim = mysim;
        final long boot = System.currentTimeMillis();
        new Timer() { public void run() {
            if (sim.isGeneratedRuntimeSettled() && !sim.generatedBoardVerificationPending) {
                cancel();
                try { begin(); } catch (Throwable failure) { failBeforeStart(failure); }
            } else if (System.currentTimeMillis() - boot > 15000) {
                cancel(); failBeforeStart(new IllegalStateException("Initial runtime did not settle"));
            }
        }}.scheduleRepeating(50);
    }

    private void begin() {
        QueryParameters query = new QueryParameters();
        mode = query.getValue("tsjNormalMode");
        seed = query.getValue("tsjNormalSeed");
        require(loopbackVisible(), "Isolated screen requires a visible loopback document");
        require(!sim.troubleshootDebug && !sim.developerVerifierRunning,
            "Screen cannot use debug/verifier execution");
        require("cold".equals(mode) || "unauthorized".equals(mode) ||
            "cancel".equals(mode) || "scope-loss".equals(mode), "Unknown isolated screen mode");
        require("7".equals(seed) || "64".equals(seed) || "13".equals(seed), "Unfrozen seed");
        require(!PlayerFamilyCatalog.isNormalPlayerEnabled("RB30_CONTROL"), "Shipping Q30 enabled");
        coordinator = sim.generationCoordinator;
        require(coordinator != null && coordinator.getJob() == null, "Not a fresh simulator");
        require(cachesEmpty(), "Not a cold ordinary/private cache");
        request = PlayerLaunchRequest.random("RB30_CONTROL", seed, "MEDIUM").generation();
        require(!request.isPrivateDiagnosticQualification() && request.isCandidateSearch() &&
            !request.isQuickPlay() && request.candidateCount() == 4 &&
            request.getDifficulty() == DifficultyProfile.MEDIUM && request.requiresExplicitCompletion(),
            "Normal request semantics changed");
        Rb30Plan plan = Rb30Plan.resolve(Long.parseLong(seed));
        int expected = "7".equals(seed) ? 33 : "64".equals(seed) ? 35 : 37;
        require(plan.physicalPackageCount() == expected &&
            plan.board().getComponentIds().size() == expected && plan.canonical().startsWith("rb30-plan@3;"),
            "Frozen plan changed");
        put(report, "schema", 1); put(report, "sourceCommit", BASE);
        put(report, "mode", mode); put(report, "rootSeed", seed);
        put(report, "planVersion", 3); put(report, "planCanonical", plan.canonical());
        put(report, "plannedPackages", expected); put(report, "requestCanonical", request.canonical());
        put(report, "requestPrivate", false); put(report, "candidateSearch", true);
        put(report, "quickPlay", false); put(report, "explicitCompletion", true);
        put(report, "maxJobMillis", 90000); put(report, "maxWorkUnits", 640);
        put(report, "maxActiveOperationMillis", 5000); put(report, "candidateLimit", 4);
        put(report, "executionPolicy", request.getExecutionPolicy().canonical());
        put(report, "cache", "fresh ordinary instance-owned; reuse enabled");
        put(report, "scheduler", "GenerationCoordinator.start(request, completion, true); standard Timer/watchdog");
        put(report, "clock", "normal foreground; debug=false");
        put(report, "initialCachesEmpty", true);
        JSONArray manifests = new JSONArray();
        for (int i=0; i<4; i++) manifests.set(i, new JSONString(request.candidateManifest(i)));
        report.put("plannedManifests", manifests);
        predecessor = sim.generatedBoardInstance;
        predecessorController = sim.generatedChallengeController;
        predecessorGraph = sim.elmList;
        snapshot = Task41SimulationSnapshot.captureForFreshInstallation(sim);
        if ("unauthorized".equals(mode)) {
            boolean rejected = false;
            try { coordinator.start(request, null, true); }
            catch (IllegalArgumentException expectedFailure) { rejected = true; }
            finally { ownJob=coordinator.getJob(); }
            require(rejected && coordinator.getJob() == null && cachesEmpty(),
                "Unauthorized normal Q30 entry was not rejected before mutation");
            snapshot.assertRestored(sim);
            put(report, "classification", "CANARY_PASS"); put(report, "cleanupComplete", true);
            finish(); return;
        }
        authority = IsolatedGenerationQualification.attach(sim, coordinator, request);
        began = System.currentTimeMillis();
        setState("RUNNING", "Normal Q30 " + mode + " seed " + seed);
        try {
            coordinator.start(request, new GenerationCoordinator.Completion() {
                public void complete(GenerationJob job, GeneratedBoardInstance owner) { completed(job, owner); }
            }, true);
        } finally { ownJob = coordinator.getJob(); }
        require(authority.getBoundJob()==ownJob, "Normal start did not bind its exact job");
        require(coordinator.usesForegroundClockForIsolatedQualification(), "Wrong scheduler clock");
        installVisibilityAudit();
        observer = new Timer() { public void run() {
            GenerationJob job = coordinator.getJob();
            if (done || job == null || !job.isRunning()) { cancel(); return; }
            setState("RUNNING", "Normal Q30 " + mode + " seed " + seed + " / " +
                job.getStage().name() + " / work " + job.getStepCount());
            if (!"cold".equals(mode) && !intervention &&
                    job.getCandidateStageWorkCount(GenerationJob.Stage.HYPOTHESES) > 0) {
                intervention = true;
                put(report,"interventionStage",job.getStage().name());
                put(report,"interventionProofUnits",job.getCandidateStageWorkCount(GenerationJob.Stage.HYPOTHESES));
                if ("cancel".equals(mode)) coordinator.cancel();
                else authority.close();
            }
        }};
        observer.scheduleRepeating(25); // Observe/cancel only; never advances generation.
    }

    private void completed(GenerationJob job, GeneratedBoardInstance owner) {
        if (done) return;
        if (observer != null) observer.cancel();
        put(report, "applicationOutcome", job.getOutcome().name());
        put(report, "terminalStage", job.getStage().name());
        put(report, "generationElapsedMs", job.getElapsedMillis());
        put(report, "startToCallbackWallMs", System.currentTimeMillis() - began);
        put(report, "workUnits", job.getStepCount());
        put(report, "maxActiveOperationMs", job.getMaxActiveOperationMillis());
        put(report, "maxCoordinatorAdvanceMs", coordinator.getMaxAdvanceMillis());
        put(report, "headroomMs", 90000 - job.getElapsedMillis());
        put(report, "yields", coordinator.getYieldCount());
        put(report, "foregroundClock", coordinator.usesForegroundClockForIsolatedQualification());
        put(report, "hiddenEvents", hiddenEvents());
        put(report, "ordinaryCacheHits", coordinator.getDiagnosticProofCacheHits());
        put(report, "ordinaryCacheMisses", coordinator.getDiagnosticProofCacheMisses());
        put(report, "ordinaryCacheSizeBeforeCleanup", coordinator.getDiagnosticProofCacheSize());
        if(job.getOutcome()==GenerationJob.Outcome.PASS) {
            put(report, "routingElapsedMsNested", coordinator.getLastRoutingElapsedMillisForDeveloperVerification());
            put(report, "proofElapsedMsNested", coordinator.getLastProofElapsedMillisForDeveloperVerification());
        } else {
            // Abort releases these owners; zero would falsely imply no work.
            report.put("routingElapsedMsNested",JSONNull.getInstance());
            report.put("proofElapsedMsNested",JSONNull.getInstance());
        }
        JSONArray stages = new JSONArray();
        for (GenerationJob.Stage stage : GenerationJob.Stage.values()) {
            JSONObject row = new JSONObject(); put(row,"stage",stage.name());
            put(row,"elapsedMs",job.getStageElapsedMillis(stage));
            put(row,"workUnits",job.getStageWorkCount(stage)); stages.set(stages.size(),row);
        }
        report.put("stages", stages);
        JSONArray attempts = new JSONArray();
        for (GenerationJob.Attempt attempt : job.getAttempts()) {
            JSONObject row = new JSONObject(); put(row,"ordinal",attempt.ordinal);
            put(row,"manifest",attempt.manifest); put(row,"stage",attempt.stage.name());
            put(row,"outcome",attempt.outcome.name()); put(row,"workUnits",attempt.workUnits);
            put(row,"proofUnits",attempt.proofUnits); put(row,"failureType",attempt.failureType);
            put(row,"failureMessage",attempt.failureMessage); attempts.set(attempts.size(),row);
        }
        report.put("attempts",attempts);
        JSONArray candidates = new JSONArray();
        for (IsolatedGenerationQualification.CandidateEvidence item : authority.getCandidates()) {
            JSONObject row = new JSONObject(); put(row,"ordinal",item.ordinal); put(row,"seed",item.seed);
            put(row,"actualPackages",item.packages); put(row,"physical",item.physical);
            put(row,"diagnostic",item.diagnostic); put(row,"difficulty",item.difficulty);
            put(row,"published",item.published); put(row,"aborted",item.aborted);
            candidates.set(candidates.size(),row);
        }
        report.put("candidates",candidates);
        put(report,"startScopeChecks",authority.getStartChecks());
        put(report,"publicationScopeChecks",authority.getPublicationChecks());
        if (job.getFailure()!=null) put(report,"failure",describe(job.getFailure()));
        String classification = job.getOutcome()==GenerationJob.Outcome.PASS ? "PASS" :
            job.getOutcome()==GenerationJob.Outcome.TIMEOUT ? "TIMEOUT" :
            job.getOutcome()==GenerationJob.Outcome.EXPECTED_REJECTION ||
            job.getOutcome()==GenerationJob.Outcome.WORK_EXHAUSTED ? "ADMISSION_REJECTED" : "INFRASTRUCTURE_FAILURE";
        try {
            require(startupFailure==null,"Startup invariant failed: "+(startupFailure==null?"":describe(startupFailure)));
            require(coordinator.getJob()==job && ownJob==job &&
                (authority.getBoundJob()==job || ("scope-loss".equals(mode) && authority.isClosed())),
                "Completion belongs to a successor or foreign job");
            require(!coordinator.retainsSavedOwnersForDeveloperVerification() &&
                !coordinator.retainsObservationForDeveloperVerification(), "Coordinator retained temporary proof state");
            require(hiddenEvents()==0 && loopbackVisible(), "Screen lost foreground visibility");
            require(privateCacheUntouched() && coordinator.getDiagnosticProofCacheHits()==0,
                "Screen used private or warm proof cache");
            if (job.getOutcome()==GenerationJob.Outcome.PASS) {
                require(coordinator.getDiagnosticProofCacheMisses()>0,"Cold publication did not perform an ordinary cache lookup");
                require(owner!=null && owner==sim.generatedBoardInstance, "Missing published owner");
                owner.requireNormalPhysicalAdmission();
                GeneratedDiagnosticProofReceipt proof = sim.generatedChallengeController.getDiagnosticProofReceipt();
                require(proof!=null && !proof.isWarmReuseForDeveloperVerification(), "Missing cold proof");
                DifficultyAssessment assessment = DifficultyAssessment.assess(owner,proof);
                assessment.require(DifficultyProfile.MEDIUM);
                put(report,"difficultyAssessment",assessment.canonical());
                report.put("proof",proofJson(proof,owner));
                put(report,"generationReceiptManifest",job.getReceipt().getManifest());
            }
            if (!"cold".equals(mode)) {
                require(intervention && owner==null &&
                    ("cancel".equals(mode) ? job.getOutcome()==GenerationJob.Outcome.CANCELLED :
                        job.getOutcome()==GenerationJob.Outcome.STALE), "Lifecycle canary did not reach expected terminal outcome");
                classification="CANARY_PASS";
            }
        } catch(Throwable failure) {
            classification="INFRASTRUCTURE_FAILURE"; put(report,"harnessFailure",describe(failure));
        }
        long cleanupBegan=System.currentTimeMillis();
        try {
            cleanup(job,owner);
            authority.close();
            coordinator.clearDiagnosticProofCacheForDeveloperVerification();
            require(privateCacheUntouched() && coordinator.getDiagnosticProofCacheSize()==0 && authority.isClosed() &&
                !PlayerFamilyCatalog.isNormalPlayerEnabled("RB30_CONTROL"), "Scope/cache/catalog leaked");
            put(report,"cleanupComplete",true);
        } catch(Throwable failure) {
            classification="INFRASTRUCTURE_FAILURE"; put(report,"cleanupComplete",false);
            put(report,"cleanupFailure",describe(failure));
            authority.close();
        }
        put(report,"cleanupElapsedMs",System.currentTimeMillis()-cleanupBegan);
        put(report,"privateCacheUntouched",privateCacheUntouched());
        put(report,"classification",classification); finish();
    }

    private void cleanup(GenerationJob job, GeneratedBoardInstance owner) {
        require(coordinator.getJob()==job && !coordinator.isRunning() && !coordinator.isAdvancing(),
            "Refusing cleanup across a running/successor job");
        if (owner!=null) {
            Vector<CircuitElm> graph=sim.elmList;
            require(sim.generatedBoardInstance==owner,"Refusing retirement of noncurrent owner");
            owner.getExternalPowerBindings().setConnected(false);
            require(owner.getExternalPowerBindings().areAllDisconnected(),"External power remained connected");
            sim.setSimRunning(false);
            for (CircuitElm element:owner.getSimulationElements()) {
                require(coordinator.getJob()==job && sim.generatedBoardInstance==owner && sim.elmList==graph &&
                    !predecessorGraph.contains(element),"Retirement lost graph ownership");
                element.delete();
            }
            snapshot.restore(sim);
            require(owner.getExternalPowerBindings().areAllDisconnected(),"Retired source reconnected");
            for (CircuitElm element:owner.getSimulationElements())
                require(!sim.elmList.contains(element),"Retired element survived restore");
        } else {
            require(sim.generatedBoardInstance==predecessor && sim.generatedChallengeController==predecessorController &&
                sim.elmList==predecessorGraph,"Abort failed to restore predecessor");
            // The normal renderer can run between our capture and installation's
            // own capture. Restore our exact still-current predecessor snapshot.
            snapshot.restore(sim);
        }
        snapshot.assertRestored(sim);
    }

    private static JSONObject proofJson(GeneratedDiagnosticProofReceipt proof, GeneratedBoardInstance owner) {
        proof.requireAssessmentOwner(owner);
        require(proof.getContextKey()!=null && proof.getContextKey().isTrustedCapture(),"Untrusted proof context");
        JSONObject result=new JSONObject(); put(result,"warmReuse",proof.isWarmReuseForDeveloperVerification());
        put(result,"context",proof.getContextKey().canonical()); put(result,"program",proof.getProgramIdentity());
        put(result,"partition",proof.getPartitionPlan().canonical()); put(result,"explicitCompletion",true);
        JSONArray rows=new JSONArray();
        for (GeneratedDiagnosticSolvabilityEvidence item:proof.getEvidence()) {
            require(item.isRepairReachable() && item.isCustomerRetestPassed() && item.isStateIsolated(),"Incomplete repair/retest proof");
            JSONObject row=new JSONObject(); put(row,"hypothesis",item.getHypothesisKey());
            put(row,"repairReachable",item.isRepairReachable()); put(row,"customerRetestPassed",item.isCustomerRetestPassed());
            put(row,"stateIsolated",item.isStateIsolated()); put(row,"deterministicResult",item.getDeterministicResult());
            JSONArray samples=new JSONArray();
            for (GeneratedDiagnosticSample sample:item.getSolverSamples()) {
                JSONObject value=new JSONObject(); put(value,"id",sample.getSampleId()); put(value,"outcome",sample.getOutcome().name());
                if (!sample.isOverRange()) { put(value,"value",sample.getValue()); put(value,"tolerance",sample.getComparisonTolerance()); }
                samples.set(samples.size(),value);
            }
            row.put("samples",samples); rows.set(rows.size(),row);
        }
        result.put("evidence",rows); return result;
    }
    private boolean privateCacheUntouched() {
        return coordinator.getDiagnosticMeasurementProofCacheSize()==0 && coordinator.getDiagnosticMeasurementProofCacheHits()==0 &&
            coordinator.getDiagnosticMeasurementProofCacheMisses()==0;
    }
    private boolean cachesEmpty() {
        return privateCacheUntouched() && coordinator.getDiagnosticProofCacheSize()==0 &&
            coordinator.getDiagnosticProofCacheHits()==0 && coordinator.getDiagnosticProofCacheMisses()==0;
    }
    private void failBeforeStart(Throwable failure) {
        if (done) return;
        startupFailure=failure;
        put(report,"classification","INFRASTRUCTURE_FAILURE"); put(report,"harnessFailure",describe(failure));
        put(report,"cleanupComplete",false);
        try {
            if (ownJob!=null) {
                require(coordinator!=null && coordinator.getJob()==ownJob && !coordinator.isAdvancing(),
                    "Startup failure cannot cancel a successor or active job");
                if (ownJob.isRunning()) coordinator.cancel();
                if (done) return; // The exact normal completion callback already checked cleanup.
                GeneratedBoardInstance published=ownJob.getOutcome()==GenerationJob.Outcome.PASS ?
                    sim.generatedBoardInstance : null;
                if(published!=null) require(published!=predecessor && ownJob.getReceipt()!=null &&
                    request.candidateManifest(ownJob.getCandidateIndex()).equals(ownJob.getReceipt().getManifest()) &&
                    published.getSeed()==request.candidate(ownJob.getCandidateIndex()).getDescriptor().getRootSeed(),
                    "Unexpected publication does not belong to this exact request/job");
                cleanup(ownJob,published);
            } else if(snapshot!=null) {
                require(coordinator==null || coordinator.getJob()==null,"Unknown generation owner after startup failure");
                snapshot.assertRestored(sim);
            }
            if(snapshot!=null) put(report,"cleanupComplete",true);
        } catch(Throwable cleanupFailure) { put(report,"cleanupFailure",describe(cleanupFailure)); }
        if (authority!=null) authority.close();
        finish();
    }
    private void finish() {
        done=true; if(observer!=null) observer.cancel();
        publish(report.toString());
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    private static String describe(Throwable failure) { return failure.getClass().getName()+": "+failure.getMessage(); }
    private static void put(JSONObject o,String k,String v) { o.put(k,v==null?JSONNull.getInstance():new JSONString(v)); }
    private static void put(JSONObject o,String k,double v) { o.put(k,new JSONNumber(v)); }
    private static void put(JSONObject o,String k,boolean v) { o.put(k,JSONBoolean.getInstance(v)); }
    private static native boolean loopbackVisible() /*-{
        return ($wnd.location.hostname==='127.0.0.1'||$wnd.location.hostname==='localhost') && !$doc.hidden;
    }-*/;
    private static native void installVisibilityAudit() /*-{
        $wnd.__q30HiddenEvents=0;
        $doc.addEventListener('visibilitychange',function(){if($doc.hidden)$wnd.__q30HiddenEvents++;});
    }-*/;
    private static native int hiddenEvents() /*-{ return $wnd.__q30HiddenEvents||0; }-*/;
    private static native void setState(String state,String message) /*-{
        $doc.documentElement.setAttribute('data-tsj-q30-normal-state',state);
        var node=$doc.getElementById('q30-normal-status');
        if(!node){node=$doc.createElement('pre');node.id='q30-normal-status';
            node.style.cssText='position:fixed;left:10px;top:10px;z-index:99999;background:white;color:black;padding:14px;border:2px solid #345';
            $doc.body.appendChild(node);}
        node.textContent=message;
    }-*/;
    private static native void publish(String json) /*-{
        $doc.documentElement.setAttribute('data-tsj-q30-normal-report',json);
        $doc.documentElement.setAttribute('data-tsj-q30-normal-state','SCREEN_DONE');
        var node=$doc.getElementById('q30-normal-status');
        if(node)node.textContent='Q30 normal screen: '+JSON.parse(json).classification;
    }-*/;
}
