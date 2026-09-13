package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.Timer;

/** Real staged admission and legal workbench repair across the frozen small-board cohort. */
final class Q15ControlBoardDeveloperVerifier {
    static final long[] SEEDS={0,1,2,3,17,42,101,-1,9007199254740993L,Long.MIN_VALUE,Long.MAX_VALUE};
    interface Completion { void finished(String report,Throwable failure); }
    static void start(final CirSim sim,final boolean force,final Completion completion) {
        if(!sim.troubleshootDebug || !sim.developerVerifierRunning) throw new IllegalStateException("Q15 requires a developer verification route");
        new Timer() {
            int index,assertions,supportAssertions;
            int phase=-1;
            long cancellationMs,repairMs;
            GeneratedBoardInstance activeOwner;
            String operation="cancellation";
            final GenerationCoordinator coordinator=sim.generationCoordinator;
            final Task41SimulationSnapshot snapshot=Task41SimulationSnapshot.capture(sim);
            final long started=System.currentTimeMillis();
            final StringBuilder cases=new StringBuilder();
            public void run() {
                try {
                    if(force) throw new AssertionError("q15-explicit-failure-canary");
                    if(phase==-1) {
                        GeneratedBoardInstance original=sim.getGeneratedBoardInstance();
                        GeneratedChallengeController controller=sim.getGeneratedChallengeController();
                        Object graph=sim.elmList;
                        coordinator.startForDeveloperVerification(GenerationRequest.leaf(Rb15Plan.FAMILY_ID,3,false));
                        coordinator.advanceForDeveloperVerification(); // Resolve, before routing.
                        coordinator.advanceForDeveloperVerification(); // First bounded placement.
                        require(coordinator.isRunning() && coordinator.getJob().getStage()==GenerationJob.Stage.HEALTHY,
                            "routing yields before electrical allocation");
                        require(sim.getGeneratedBoardInstance()==original && sim.elmList==graph,
                            "unfinished routing retains the exact player graph");
                        coordinator.cancel();cancellationMs=coordinator.getCancellationLatencyMillis();
                        require(coordinator.getJob().getOutcome()==GenerationJob.Outcome.CANCELLED &&
                            coordinator.getJob().getReceipt()==null && !coordinator.retainsSavedOwnersForDeveloperVerification(),
                            "routing cancellation releases work without publication");
                        require(cancellationMs<=500 && sim.getGeneratedBoardInstance()==original &&
                            sim.getGeneratedChallengeController()==controller && sim.elmList==graph,
                            "routing cancellation preserves the exact owner within 500 ms");
                        phase=0;schedule(0);return;
                    }
                    if(phase==0) {
                        operation="admission";
                        CirSim.console("Q15 admission seed="+Long.toString(SEEDS[index]));
                        coordinator.startForDeveloperVerification(GenerationRequest.leaf(Rb15Plan.FAMILY_ID,SEEDS[index],false));
                        publishProgress(report("RUNNING",System.currentTimeMillis()-started,0,false,null));
                        phase=1;schedule(0);return;
                    }
                    if(coordinator.isRunning()) {coordinator.advanceForDeveloperVerification();schedule(0);return;}
                    GenerationJob job=coordinator.getJob();
                    GeneratedBoardInstance owner=sim.getGeneratedBoardInstance();
                    if(phase==1) {
                    require(job.getOutcome()==GenerationJob.Outcome.PASS,"admission seed="+Long.toString(SEEDS[index])+" stage="+job.getStage()+" outcome="+job.getOutcome()+" failure="+job.getFailure());
                    require(job.getReceipt()!=null && job.getReceipt().getStageCount()==6,"all manifest-to-publication stages");
                    require(owner.getSeed()==SEEDS[index] && owner.getBoard().getComponentIds().size()==16,"exact seed and sixteen physical owners");
                    require(owner.getFaultCandidates().size()==3,"complete supported hypothesis population");
                    for(PcbTraceGeometry trace:owner.getPcbLayout().getTraces()) require(trace.getLayer()==PcbCopperLayer.BOTTOM,"one real solder-side copper layer");
                    owner.getPcbLayout().validateGeometry(owner.getBoard());
                    activeOwner=owner;operation="repair";phase=2;
                    publishProgress(report("RUNNING",System.currentTimeMillis()-started,0,false,null));
                    schedule(0);return;
                    }
                    require(owner==activeOwner,"the accepted case still owns the graph");
                    if(phase==2) {
                    long repairStarted=System.currentTimeMillis();
                    repair(owner);
                    repairMs=System.currentTimeMillis()-repairStarted;
                    operation="support";phase=3;
                    publishProgress(report("RUNNING",System.currentTimeMillis()-started,0,false,null));
                    schedule(0);return;
                    }
                    long supportStarted=System.currentTimeMillis();
                    if(index==0 || index==1 || index==3 || index==6) supportAssertions+=Q15SupportChecks.verify(sim,owner.getSeed());
                    if(cases.length()>0)cases.append(',');
                    cases.append("{\"seed\":\"").append(Long.toString(owner.getSeed())).append("\",\"design\":\"").append(Rb15Plan.resolve(owner.getSeed()).topology())
                        .append("\",\"fault\":\"").append(owner.getFaultBinding().getFault().getType()).append("\",\"packages\":16,\"hypotheses\":3,\"stages\":6,\"admissionMs\":")
                        .append(job.getElapsedMillis()).append(",\"maxUnitMs\":").append(coordinator.getMaxAdvanceMillis()).append(",\"repairMs\":").append(repairMs)
                        .append(",\"supportMs\":").append(System.currentTimeMillis()-supportStarted).append(",\"workUnits\":").append(job.getStepCount())
                        .append(",\"routeExpansions\":").append(owner.getPcbLayout().getGenerationRoutingExpansions())
                        .append(",\"routeAttempts\":").append(owner.getPcbLayout().getGenerationRoutingAttempts())
                        .append(",\"routeMs\":").append(owner.getPcbLayout().getGenerationRoutingMillis()).append(",\"stageMs\":[");
                    for(GenerationJob.Stage stage:GenerationJob.Stage.values()) {if(stage.ordinal()>0)cases.append(',');cases.append(job.getStageElapsedMillis(stage));}
                    cases.append("]}");
                    publishProgress(report("RUNNING",System.currentTimeMillis()-started,0,false,null));
                    index++;phase=0;activeOwner=null;
                    if(index<SEEDS.length) {schedule(0);return;}
                    long cleanup=System.currentTimeMillis(); snapshot.restore(sim);snapshot.assertRestored(sim);
                    completion.finished(report("PASS",cleanup-started,System.currentTimeMillis()-cleanup,true,null),null);
                } catch(Throwable failure) {
                    long cleanupStarted=System.currentTimeMillis();boolean restored=false;
                    try {coordinator.cancel();snapshot.restore(sim);snapshot.assertRestored(sim);}
                    catch(Throwable cleanup) {failure=new IllegalStateException("Q15 cleanup failure after "+failure+": "+cleanup,cleanup);}
                    try {snapshot.assertRestored(sim);restored=true;}catch(Throwable ignored) { }
                    completion.finished(report("FAIL",cleanupStarted-started,System.currentTimeMillis()-cleanupStarted,restored,failure),failure);
                }
            }
            String report(String status,long elapsed,long cleanup,boolean restored,Throwable failure) {
                return "{\"protocol\":\"TSJ-Q15-1\",\"status\":"+quote(status)+",\"cases\":["+cases+"],\"assertions\":"+assertions+
                    ",\"supportAssertions\":"+supportAssertions+",\"cancellationMs\":"+cancellationMs+",\"elapsedMs\":"+elapsed+
                    ",\"cleanupMs\":"+cleanup+",\"ownerRestored\":"+restored+",\"operation\":"+quote(operation)+
                    ",\"activeSeed\":"+quote(Long.toString(SEEDS[Math.min(index,SEEDS.length-1)]))+
                    ",\"failure\":"+(failure==null?"null":quote(failure.toString()))+"}";
            }
            void require(boolean value,String message) {assertions++;if(!value)throw new AssertionError("Q15: "+message);}
            void settle(GeneratedBoardInstance owner) {GeneratedRuntimeDeveloperSettlement.settle(sim,owner,"Q15 installed proof");}
            void power(GeneratedBoardInstance owner,BoardPowerState state) {
                sim.setBoardPowerState(state);sim.advanceGeneratedTemporalProfile(.025);settle(owner);
            }
            void repair(GeneratedBoardInstance owner) {
                sim.getGeneratedChallengeController().beginDeveloperVerificationScope();
                require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"unrepaired customer failure");
                String target=owner.getFaultBinding().getFault().getTargetComponentId();
                PhysicalPart<?> original=owner.getPhysicalBoardRuntime().getInstalledPart(target);
                PhysicalSlotMutationProvider mutation=owner.getPhysicalBoardRuntime().getMutationProvider(target);
                power(owner,BoardPowerState.UNPOWERED);
                require(RelayOutputBehavior.isDischarged(owner),"real coil and capacitor discharge");
                require(mutation.removeInstalledPart(),"legal selected-owner removal");settle(owner);
                String wrong=target.equals("K1")?ReplaceableRelayCapability.COIL_5V:"R_CATALOG_1000000";
                require(mutation.installNewFromCatalog(wrong),"mechanically valid wrong replacement");settle(owner);
                power(owner,BoardPowerState.POWERED);
                require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"wrong repair cannot pass customer retest");
                power(owner,BoardPowerState.UNPOWERED);require(mutation.removeInstalledPart(),"remove wrong replacement");settle(owner);
                String correct=owner.getDiagnosticProvider().getCorrectCatalogId(owner,target);
                require(mutation.installNewFromCatalog(correct),"specified physical replacement");settle(owner);power(owner,BoardPowerState.POWERED);
                if(target.equals("RDRIVE")) {
                    power(owner,BoardPowerState.UNPOWERED);require(mutation.removeInstalledPart(),"remove nominal resistor for alternative repair");settle(owner);
                    require(mutation.installNewFromCatalog("R_CATALOG_100000"),"same alternative catalog value on both driver topologies");settle(owner);power(owner,BoardPowerState.POWERED);
                    require(owner.invokeOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,sim).isPassed()==!Rb15Plan.resolve(owner.getSeed()).bjt,
                        "100k drives NMOS but lacks BJT base current; retest follows actual topology");
                    power(owner,BoardPowerState.UNPOWERED);require(mutation.removeInstalledPart(),"remove alternative resistor");settle(owner);
                    require(mutation.installNewFromCatalog(correct),"restore nominal resistor");settle(owner);power(owner,BoardPowerState.POWERED);
                }
                require(sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"restored HIGH/LOW and independent status branch");
                owner.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,sim);
                require(voltage(owner,"J4.1","J4.2")>10 && voltage(owner,"J4.1","J4.2")<12.6,"independent live load HIGH voltage");
                owner.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW,sim);
                require(Math.abs(voltage(owner,"J4.1","J4.2"))<.05,"independent live load LOW voltage");
                owner.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,sim);
                require(original.isFaulted()&&!original.isInstalled(),"tray preserves physical fault identity");
                GeneratedRuntimeInvariant.verify(sim,owner,sim.getBoardModificationController(),sim.elmList);
            }
            double voltage(GeneratedBoardInstance owner,String first,String second) {
                CircuitPostMeasurementEndpoint a=(CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint(first);
                CircuitPostMeasurementEndpoint b=(CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint(second);
                return a.getElement().getPostVoltage(a.getPostIndex())-b.getElement().getPostVoltage(b.getPostIndex());
            }
        }.schedule(0);
    }
    private static native String quote(String value) /*-{ return JSON.stringify(value); }-*/;
    private static native void publishProgress(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-q15-report",report);
    }-*/;
}
