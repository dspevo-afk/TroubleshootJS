package com.lushprojects.circuitjs1.client;

import com.google.gwt.json.client.*;
import com.google.gwt.user.client.Timer;

/** Developer-only driver of normal admission, with one exact protected owner per case. */
final class QuickPlayGateDeveloperVerifier {
    private static final long CONTROLLED_P09_ROOT=-7564325972933069162L;
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
        run(seed,search,cancelAfter,false);
    }
    private void run(String seed,final boolean search,final int cancelAfter,boolean controlledNegative) {
        if(running || sim.developerVerifierRunning || !sim.isGeneratedRuntimeSettled() || cancelAfter < -1)
            throw new IllegalStateException("Gate is busy or the cancellation fixture is invalid");
        if(controlledNegative && (!Long.toString(CONTROLLED_P09_ROOT).equals(seed) || cancelAfter>=0))
            throw new IllegalArgumentException("Controlled P09 negative is predeclared only for its exact root without cancellation");
        final PlayerLaunchRequest launch=search?PlayerLaunchRequest.random(Rb15Plan.FAMILY_ID,seed,"EASY"):
            new PlayerLaunchRequest(Rb15Plan.FAMILY_ID,seed,"EASY");
        final Task41SimulationSnapshot snapshot=Task41SimulationSnapshot.capture(sim);
        final GeneratedBoardInstance original=sim.getGeneratedBoardInstance();
        final Object originalGraph=sim.elmList;
        final long began=System.currentTimeMillis();
        final JSONObject report=new JSONObject();
        final ControlledP09Negative controlled=controlledNegative ?
            new ControlledP09Negative(sim,original,originalGraph,seed) : null;
        put(report,"requestedSeed",seed); put(report,"mode",search?"search":"exact");
        report.put("cancelAfter",new JSONNumber(cancelAfter));
        final GenerationCoordinator coordinator=sim.generationCoordinator;
        running=true; sim.developerVerifierRunning=true;
        report.put("sequence",new JSONNumber(++sequence));
        put(report,"status","RUNNING"); publish(report.toString());
        try {
            if(controlled==null) coordinator.startForDeveloperVerification(launch.generation());
            else coordinator.startForDeveloperVerification(launch.generation(),controlled);
        }
        catch(Throwable failure) { finish(snapshot,report,began,failure,controlled); return; }
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
                    if(controlled!=null) {
                        require(controlled.wasApplied(),"Controlled P09 negative did not reach the physical candidate");
                        require(attempts.size()>0 && attempts.get(0)!=null && attempts.get(0).isObject()!=null,
                            "Controlled P09 attempt is missing");
                        JSONObject firstAttempt=attempts.get(0).isObject();
                        require(firstAttempt.get("ordinal").isNumber().doubleValue()==0 &&
                            "PHYSICAL".equals(firstAttempt.get("stage").isString().stringValue()) &&
                            "EXPECTED_REJECTION".equals(firstAttempt.get("outcome").isString().stringValue()),
                            "Controlled copy did not produce the typed physical rejection at ordinal zero");
                        if(search) {
                            require(job.getOutcome()==GenerationJob.Outcome.PASS && attempts.size()>=2,
                                "Controlled search did not complete a canonical retry");
                            require(job.getAttempts().get(1).ordinal==1 &&
                                job.getAttempts().get(1).outcome==GenerationJob.Outcome.PASS,
                                "Controlled search successor was not the next passing candidate");
                        } else {
                            require(job.getOutcome()==GenerationJob.Outcome.EXPECTED_REJECTION && attempts.size()==1,
                                "Controlled exact mode did not stop on its typed physical rejection");
                        }
                    }
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
                    finish(snapshot,report,began,null,controlled);
                } catch(Throwable failure) { finish(snapshot,report,began,failure,controlled); }
            }
        }.schedule(1);
    }
    private void finish(Task41SimulationSnapshot snapshot,JSONObject report,long began,Throwable failure,
            ControlledP09Negative controlled) {
        long cleanup=System.currentTimeMillis(); boolean restored=false;
        try {
            sim.generationCoordinator.cancel();
            snapshot.restore(sim); snapshot.assertRestored(sim); restored=true;
        } catch(Throwable problem) {
            failure=new IllegalStateException("Gate cleanup failed after "+failure+": "+problem,problem);
        }
        running=false;
        report.put("ownerRestored",JSONBoolean.getInstance(restored));
        if(controlled!=null) {
            JSONObject control=controlled.describe();
            control.put("coordinatorCallbackReleased",JSONBoolean.getInstance(
                !sim.generationCoordinator.retainsControlledP09NegativeForDeveloperVerification()));
            control.put("protectedOwnerRestored",JSONBoolean.getInstance(restored));
            report.put("controlledNegative",control);
        }
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
        put(out,"geometryFingerprint",layout.geometryFingerprint());
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
    /** A disclosed developer-only negative: the real P09 predicate sees a copied oversized outline. */
    private static final class ControlledP09Negative implements
            GenerationCoordinator.ControlledP09NegativeForDeveloperVerification {
        private final CirSim sim;
        private final GeneratedBoardInstance protectedOwner;
        private final Object protectedGraph;
        private final String requestedSeed;
        private GeneratedBoardInstance appliedCandidate;
        private PcbBoardLayout originalLayout,invalidCopy;
        private SupportedEnvelope.Rejected rejection;
        private String fingerprintBefore,fingerprintAfter,invalidFingerprint;
        private int appliedOrdinal=-1,consumedCount;
        private boolean predicateRejected,scopeActiveAtApply,candidatePrivateAtApply;
        private boolean protectedPowerDisconnectedAtApply,originalBoundsPassed;

        ControlledP09Negative(CirSim sim,GeneratedBoardInstance protectedOwner,Object protectedGraph,
                String requestedSeed) {
            this.sim=sim; this.protectedOwner=protectedOwner; this.protectedGraph=protectedGraph;
            this.requestedSeed=requestedSeed;
        }

        public void verifyAndReject(GeneratedBoardInstance candidate,int candidateOrdinal) {
            if(candidateOrdinal!=0) {
                if(!predicateRejected)
                    throw new IllegalStateException("Controlled P09 negative missed canonical root ordinal zero");
                return;
            }
            if(predicateRejected || candidate==null || candidate.getSeed()!=CONTROLLED_P09_ROOT)
                throw new IllegalStateException("Controlled P09 negative was repeated or applied to another candidate");
            originalLayout=candidate.getPcbLayout();
            if(originalLayout==null) throw new IllegalStateException("Controlled P09 root has no physical layout");
            appliedCandidate=candidate; appliedOrdinal=candidateOrdinal;
            fingerprintBefore=originalLayout.geometryFingerprint();
            scopeActiveAtApply=sim.troubleshootDebug && sim.developerVerifierRunning;
            candidatePrivateAtApply=sim.getGeneratedBoardInstance()==candidate && sim.elmList!=protectedGraph &&
                FreshGeneratedRuntimeInstallation.isInProgress(sim) && sim.generationCoordinator.getJob()!=null &&
                sim.generationCoordinator.getJob().getReceipt()==null;
            protectedPowerDisconnectedAtApply=protectedOwner==null ||
                protectedOwner.getExternalPowerBindings().areAllDisconnected();
            if(!scopeActiveAtApply || !candidatePrivateAtApply || !protectedPowerDisconnectedAtApply)
                throw new IllegalStateException("Controlled P09 negative escaped verifier scope or private staging");
            SupportedEnvelope.current().requireBounds(candidate.getBoard(),originalLayout);
            originalBoundsPassed=true;
            invalidCopy=copyWithOversizedOutline(originalLayout);
            invalidFingerprint=invalidCopy.geometryFingerprint();
            consumedCount++;
            try {
                SupportedEnvelope.current().requireBounds(candidate.getBoard(),invalidCopy);
            } catch(SupportedEnvelope.Rejected expected) {
                rejection=expected; predicateRejected=true;
                fingerprintAfter=originalLayout.geometryFingerprint();
                throw expected;
            }
            throw new IllegalStateException("P09 envelope unexpectedly admitted the oversized copied outline");
        }

        public boolean verified(GeneratedBoardInstance candidate,int candidateOrdinal,
                SupportedEnvelope.Rejected actual) {
            return predicateRejected && candidate==appliedCandidate && candidateOrdinal==appliedOrdinal &&
                actual==rejection && actual.reason==SupportedEnvelope.Reason.BOARD_SIZE &&
                candidate.getPcbLayout()==originalLayout && originalBoundsPassed && fingerprintBefore!=null &&
                fingerprintBefore.equals(fingerprintAfter) && !fingerprintBefore.equals(invalidFingerprint) &&
                invalidCopy!=null && invalidCopy!=candidate.getPcbLayout() &&
                invalidCopy.getBoardOutline().width==SupportedEnvelope.MAX_EDGE+1 &&
                sim.getGeneratedBoardInstance()==candidate && sim.elmList!=protectedGraph &&
                FreshGeneratedRuntimeInstallation.isInProgress(sim) &&
                (protectedOwner==null || protectedOwner.getExternalPowerBindings().areAllDisconnected());
        }

        boolean wasApplied() { return predicateRejected && consumedCount==1; }

        JSONObject describe() {
            JSONObject out=new JSONObject();
            put(out,"kind","CONTROLLED_COPY_P09_ENVELOPE_REJECTION");
            put(out,"requestedSeed",requestedSeed);
            out.put("applied",JSONBoolean.getInstance(predicateRejected));
            out.put("appliedOrdinal",new JSONNumber(appliedOrdinal));
            put(out,"reason",rejection==null?"":rejection.reason.name());
            put(out,"originalGeometryFingerprintBefore",fingerprintBefore==null?"":fingerprintBefore);
            put(out,"originalGeometryFingerprintAfter",fingerprintAfter==null?"":fingerprintAfter);
            put(out,"invalidCopyGeometryFingerprint",invalidFingerprint==null?"":invalidFingerprint);
            out.put("invalidCopyWidth",new JSONNumber(invalidCopy==null?0:invalidCopy.getBoardOutline().width));
            out.put("invalidCopyChangedGeometry",JSONBoolean.getInstance(
                fingerprintBefore!=null && invalidFingerprint!=null && !fingerprintBefore.equals(invalidFingerprint)));
            out.put("originalBoundsPassed",JSONBoolean.getInstance(originalBoundsPassed));
            out.put("originalLayoutIdentityUnchanged",JSONBoolean.getInstance(
                appliedCandidate!=null && appliedCandidate.getPcbLayout()==originalLayout));
            out.put("malformedCopyNotInstalled",JSONBoolean.getInstance(
                invalidCopy!=null && appliedCandidate!=null && appliedCandidate.getPcbLayout()!=invalidCopy));
            out.put("candidatePrivateAtApply",JSONBoolean.getInstance(candidatePrivateAtApply));
            out.put("protectedPowerDisconnectedAtApply",JSONBoolean.getInstance(protectedPowerDisconnectedAtApply));
            out.put("scopeActiveAtApply",JSONBoolean.getInstance(scopeActiveAtApply));
            out.put("consumedCount",new JSONNumber(consumedCount));
            out.put("predicateRejected",JSONBoolean.getInstance(predicateRejected));
            return out;
        }

        private static PcbBoardLayout copyWithOversizedOutline(PcbBoardLayout source) {
            Rectangle outline=source.getBoardOutline();
            PcbBoardLayout copy=new PcbBoardLayout(source.getWidth(),source.getHeight(),
                new Rectangle(outline.x,outline.y,SupportedEnvelope.MAX_EDGE+1,outline.height),
                source.getPartsTray(),source.getLayoutAlgorithmVersion());
            for(PcbComponentPlacement value:source.getComponents()) copy.addComponent(value);
            for(PcbPadPlacement value:source.getPads()) copy.addPad(value);
            for(PcbSilkscreenLabel value:source.getSilkscreenLabels()) copy.addSilkscreenLabel(value);
            for(PcbLayoutRegion value:source.getRegions()) copy.addRegion(value);
            for(PcbBoardHole value:source.getHoles()) copy.addHole(value);
            copy.replaceTraces(source.getTraces());
            copy.setRoutingStatistics(source.getRoutingExpansions(),source.getRawRoutingSegments(),
                source.getRoutingCongestionRejections());
            copy.setRoutingRecoveryStatistics(source.getRoutingRecoveryStatistics());
            copy.seal();
            return copy;
        }
    }
    private native void bridge() /*-{
        var owner=this;
        $wnd.tsjQuickPlayGate={run:$entry(function(seed,search,cancelAfter,controlledNegative) {
            owner.@com.lushprojects.circuitjs1.client.QuickPlayGateDeveloperVerifier::run(Ljava/lang/String;ZIZ)(String(seed),!!search,cancelAfter,!!controlledNegative);
        })};
    }-*/;
    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute('data-tsj-quickplay-gate',report);
    }-*/;
}
