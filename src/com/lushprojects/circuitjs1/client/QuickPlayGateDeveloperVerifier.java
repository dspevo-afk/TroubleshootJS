package com.lushprojects.circuitjs1.client;

import com.google.gwt.json.client.*;
import com.google.gwt.user.client.Timer;

/** Developer-only driver of normal admission, with one exact protected owner per case. */
final class QuickPlayGateDeveloperVerifier {
    private final CirSim sim;
    private boolean running;
    private int sequence;
    private QuickPlayGateDeveloperVerifier(CirSim sim) { this.sim=sim; }
    static void install(CirSim sim) {
        if(!sim.troubleshootDebug || !sim.isGeneratedRuntimeSettled()) throw new IllegalStateException("Gate requires a settled debug owner");
        new QuickPlayGateDeveloperVerifier(sim).bridge();
        publish("{\"status\":\"READY\"}");
    }
    private void run(String seed,boolean search,final int cancelAfter) {
        if(running || sim.developerVerifierRunning || !sim.isGeneratedRuntimeSettled() || cancelAfter < -1)
            throw new IllegalStateException("Gate is busy or the cancellation fixture is invalid");
        final PlayerLaunchRequest launch=search?PlayerLaunchRequest.random(Rb15Plan.FAMILY_ID,seed,"EASY"):
            new PlayerLaunchRequest(Rb15Plan.FAMILY_ID,seed,"EASY");
        final Task41SimulationSnapshot snapshot=Task41SimulationSnapshot.capture(sim);
        final GeneratedBoardInstance original=sim.getGeneratedBoardInstance();
        final Object originalGraph=sim.elmList;
        final long began=System.currentTimeMillis();
        final JSONObject report=new JSONObject();
        put(report,"requestedSeed",seed); put(report,"mode",search?"search":"exact");
        report.put("cancelAfter",new JSONNumber(cancelAfter));
        final GenerationCoordinator coordinator=sim.generationCoordinator;
        running=true; sim.developerVerifierRunning=true;
        report.put("sequence",new JSONNumber(++sequence));
        put(report,"status","RUNNING"); publish(report.toString());
        try { coordinator.startForDeveloperVerification(launch.generation()); }
        catch(Throwable failure) { finish(snapshot,report,began,failure); return; }
        new Timer() {
            public void run() {
                try {
                    if(coordinator.isRunning()) {
                        if(cancelAfter>=0 && coordinator.getJob().getStepCount()>=cancelAfter) coordinator.cancel();
                        else coordinator.advanceForDeveloperVerification();
                    }
                    GenerationJob job=coordinator.getJob();
                    put(report,"status","RUNNING"); put(report,"stage",job.getStage().name());
                    report.put("units",new JSONNumber(job.getStepCount()));
                    report.put("elapsedMs",new JSONNumber(System.currentTimeMillis()-began));
                    publish(report.toString());
                    if(job.isRunning()) { schedule(1); return; }
                    put(report,"outcome",job.getOutcome().name());
                    report.put("jobMs",new JSONNumber(job.getElapsedMillis()));
                    report.put("maxUnitMs",new JSONNumber(coordinator.getMaxAdvanceMillis()));
                    report.put("cancellationMs",new JSONNumber(coordinator.getCancellationLatencyMillis()));
                    JSONArray attempts=new JSONArray();
                    for(GenerationJob.Attempt attempt:job.getAttempts()) {
                        JSONObject row=new JSONObject();
                        put(row,"manifest",attempt.manifest); put(row,"stage",attempt.stage.name());
                        put(row,"outcome",attempt.outcome.name());
                        row.put("ordinal",new JSONNumber(attempt.ordinal));
                        row.put("units",new JSONNumber(attempt.workUnits));
                        row.put("proofUnits",new JSONNumber(attempt.proofUnits));
                        attempts.set(attempts.size(),row);
                    }
                    report.put("attempts",attempts);
                    require(job.getStepCount()<=640 && attempts.size()<=launch.generation().candidateCount(),"Launch work/candidate limit changed");
                    require(!coordinator.retainsSavedOwnersForDeveloperVerification(),"Terminal job retained protected owners");
                    if(job.getOutcome()==GenerationJob.Outcome.PASS) {
                        require(cancelAfter<0,"Cancelled candidate was published");
                        GeneratedBoardInstance owner=sim.getGeneratedBoardInstance();
                        PlayerLaunchRequest accepted=launch.accepted(owner.getSeed());
                        require(owner!=original && Rb15Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()),"Wrong published owner");
                        require(owner.getFaultCandidates().size()==3,"Diagnostic hypothesis population shrank");
                        require(job.getReceipt()!=null && job.getReceipt().getStageCount()==6,"Incomplete admission receipt");
                        require(PlayerLaunchRequest.parse(accepted.replay()).seed==owner.getSeed() &&
                            accepted.generation().candidateCount()==1,"Accepted replay is not exact");
                        SupportedEnvelope.current().requireNormal(owner);
                        owner.getPcbLayout().validateGeometry(owner.getBoard());
                        PcbConductorProjection.audit(owner,sim.elmList);
                        require(sim.getGeneratedChallengeController().getDiagnosticProofReceipt()!=null,"Missing actual diagnostic/repair proof");
                        require(sim.getGeneratedChallengeController().getLifecycleEvidence().healthyFamilyValidated &&
                            sim.getGeneratedChallengeController().getLifecycleEvidence().selectedFaultValidated,"Missing healthy/fault behavior");
                        report.put("board",describe(owner)); put(report,"replay",accepted.replay());
                        report.put("hypotheses",new JSONNumber(owner.getFaultCandidates().size()));
                        report.put("proofUnits",new JSONNumber(job.getCandidateStageWorkCount(GenerationJob.Stage.HYPOTHESES)));
                    } else {
                        require(job.getReceipt()==null && sim.getGeneratedBoardInstance()==original && sim.elmList==originalGraph,
                            "Rejected launch mutated the protected board");
                        require(job.getOutcome()==GenerationJob.Outcome.EXPECTED_REJECTION ||
                            job.getOutcome()==GenerationJob.Outcome.TIMEOUT || job.getOutcome()==GenerationJob.Outcome.WORK_EXHAUSTED ||
                            cancelAfter>=0 && job.getOutcome()==GenerationJob.Outcome.CANCELLED,
                            "Unexpected admission failure: "+job.getFailure());
                        if(job.getFailure()!=null) put(report,"rejection",job.getFailure().toString());
                    }
                    finish(snapshot,report,began,null);
                } catch(Throwable failure) { finish(snapshot,report,began,failure); }
            }
        }.schedule(1);
    }
    private void finish(Task41SimulationSnapshot snapshot,JSONObject report,long began,Throwable failure) {
        long cleanup=System.currentTimeMillis(); boolean restored=false;
        try {
            sim.generationCoordinator.cancel();
            snapshot.restore(sim); snapshot.assertRestored(sim); restored=true;
        } catch(Throwable problem) {
            failure=new IllegalStateException("Gate cleanup failed after "+failure+": "+problem,problem);
        }
        running=false;
        report.put("ownerRestored",JSONBoolean.getInstance(restored));
        report.put("elapsedMs",new JSONNumber(cleanup-began));
        report.put("cleanupMs",new JSONNumber(System.currentTimeMillis()-cleanup));
        put(report,"status",failure==null?"COMPLETE":"FAIL");
        if(failure!=null) put(report,"failure",failure.toString());
        publish(report.toString());
    }
    private static JSONObject describe(GeneratedBoardInstance owner) {
        PcbBoardLayout layout=owner.getPcbLayout(); Rectangle b=layout.getBoardOutline();
        JSONObject out=new JSONObject(),parts=new JSONObject();
        put(out,"seed",Long.toString(owner.getSeed())); put(out,"design",Rb15Plan.resolve(owner.getSeed()).topology());
        put(out,"fault",owner.getFaultBinding().getFault().getType().name());
        out.put("width",new JSONNumber(b.width)); out.put("height",new JSONNumber(b.height));
        out.put("routes",new JSONNumber(layout.getGenerationRoutingAttempts()));
        out.put("expansions",new JSONNumber(layout.getGenerationRoutingExpansions()));
        out.put("copperLength",new JSONNumber(PcbRouteMetrics.measure(layout.getTraces()).uniqueLength));
        for(PcbComponentPlacement p:layout.getComponents()) {
            JSONArray xy=new JSONArray(); xy.set(0,new JSONNumber(p.getX()-b.x)); xy.set(1,new JSONNumber(p.getY()-b.y));
            parts.put(p.getComponentId(),xy);
        }
        out.put("parts",parts); return out;
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError("Quick Play gate: "+message); }
    private static void put(JSONObject out,String key,String value) { out.put(key,new JSONString(value)); }
    private native void bridge() /*-{
        var owner=this;
        $wnd.tsjQuickPlayGate={run:$entry(function(seed,search,cancelAfter) {
            owner.@com.lushprojects.circuitjs1.client.QuickPlayGateDeveloperVerifier::run(Ljava/lang/String;ZI)(String(seed),!!search,cancelAfter);
        })};
    }-*/;
    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute('data-tsj-quickplay-gate',report);
    }-*/;
}
