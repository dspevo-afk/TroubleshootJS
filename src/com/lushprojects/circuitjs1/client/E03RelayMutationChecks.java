package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Actual installed five-terminal transactions, fault owners and functional retests. */
final class E03RelayMutationChecks {
    private static int assertions;
    static int verify(CirSim sim, long seed) {
        assertions=0;
        {
            CirSim.console("E03 installed relay: seed="+seed);
            Task41SimulationSnapshot protectedOwner=Task41SimulationSnapshot.capture(sim);
            try {
                final GeneratedBoardInstance owner=new RelayOutputGenerator().generate(seed);
                FreshGeneratedRuntimeInstallation.install(sim,owner,false);
                sim.getGeneratedChallengeController().beginDeveloperVerificationScope();
                settle(sim,owner);
                if(seed==0) {
                    sim.changeBenchSource(owner,"COIL_INPUT",false,.01); settle(sim,owner);
                    E01SourceDeveloperVerifier.verify(sim,false);
                    require(!owner.getExternalPowerBindings().getBinding("COIL_INPUT").isConnected() &&
                        owner.getExternalPowerBindings().getBinding("COIL_INPUT").getLimitedSupply().getLimitAmps()==.01 &&
                        sim.getBoardPowerController().getState()==BoardPowerState.POWERED,
                        "private proof restores partial isolation and current setting");
                    sim.changeBenchSource(owner,"COIL_INPUT",true,.25); settle(sim,owner);
                }
                CircuitPostMeasurementEndpoint load=(CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint("J4.1");
                CircuitPostMeasurementEndpoint controlReturn=(CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint("J1.2");
                require(sim.assessMeasurementReference(load,controlReturn).getDecision()==MeasurementReferencePolicy.Decision.REJECTED &&
                    Double.isNaN(sim.measureDcVoltage(load,controlReturn)),"isolated domains do not manufacture a voltage reference");
                require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"unrepaired retest seed="+seed);
                String id=owner.getFaultBinding().getFault().getTargetComponentId();
                PhysicalPart<?> original=owner.getPhysicalBoardRuntime().getInstalledPart(id);
                final PhysicalSlotMutationProvider controller=owner.getPhysicalBoardRuntime().getMutationProvider(id);
                boolean refused=false;
                try {controller.removeInstalledPart();}catch(BoardModificationRejectedException expected){refused=true;}
                require(refused&&original.isInstalled(),"powered removal rejected");
                sim.setBoardPowerState(BoardPowerState.UNPOWERED); sim.advanceGeneratedTemporalProfile(.025); settle(sim,owner);
                require(RelayOutputBehavior.isDischarged(owner),"actual coil discharge before mutation");
                require(controller.removeInstalledPart(),"remove original"); settle(sim,owner);
                require(!original.isInstalled()&&original.isFaulted(),"removed original owns its fault");
                if("K1".equals(id)) {
                    final ReplaceableRelayCapability capability=(ReplaceableRelayCapability)owner.getPhysicalBoardRuntime().getCapability(ReplaceableRelayCapability.ID);
                    final int beforeElements=owner.getSimulationElements().size();
                    for(int failAt=1;failAt<=5;failAt++) {
                        final int occurrence=failAt; final int[] writes={0};
                        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook(){
                            public void afterStage(PhysicalMutationScope.FailureStage stage) {
                                if(stage==PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT&&++writes[0]==occurrence)
                                    throw new IllegalStateException("e03-attachment-"+occurrence);
                            }
                        });
                        boolean failed=false;
                        try{controller.installNewFromCatalog(ReplaceableRelayCapability.COIL_5V);}
                        catch(IllegalStateException expected){failed=expected.getMessage().contains("e03-attachment-");}
                        finally{PhysicalMutationScope.clearFailureHookForDeveloperVerification();}
                        require(failed&&writes[0]==occurrence,"injected attachment failure observed");
                        require(capability.getMutationSlot().isEmpty()&&capability.getLooseParts().size()==1&&
                            owner.getSimulationElements().size()==beforeElements,"five-terminal rollback restores inventory/graph/mount");
                        GeneratedRuntimeInvariant.verify(sim,owner,sim.getBoardModificationController(),sim.elmList);
                        settle(sim,owner);
                    }
                    require(controller.installNewFromCatalog(ReplaceableRelayCapability.COIL_12V),"wrong relay is mechanically compatible");
                    settle(sim,owner); sim.setBoardPowerState(BoardPowerState.POWERED); settle(sim,owner);
                    require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"12 V replacement fails on 5 V");
                    sim.setBoardPowerState(BoardPowerState.UNPOWERED); sim.advanceGeneratedTemporalProfile(.025); settle(sim,owner);
                    require(controller.removeInstalledPart(),"remove wrong relay"); settle(sim,owner);
                }
                require(controller.installNewFromCatalog(owner.getDiagnosticProvider().getCorrectCatalogId(owner,id)),"install specified replacement");
                settle(sim,owner); sim.setBoardPowerState(BoardPowerState.POWERED); settle(sim,owner);
                require(sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),"repaired HIGH/LOW retest seed="+seed);
                require(original.isFaulted()&&!original.isInstalled(),"successful repair does not heal tray original");
                GeneratedRuntimeInvariant.verify(sim,owner,sim.getBoardModificationController(),sim.elmList);
                // The fixed flyback owner has no player removal path.
                require(owner.getPhysicalBoardRuntime().getMutationProvider("D1")==null,"unsupported flyback removal is not offered");
            } finally {
                PhysicalMutationScope.clearFailureHookForDeveloperVerification();
                protectedOwner.restore(sim); protectedOwner.assertRestored(sim);
            }
        }
        return assertions;
    }
    private static void settle(CirSim sim,GeneratedBoardInstance owner) { GeneratedRuntimeDeveloperSettlement.settle(sim,owner,"E03 relay mutation"); }
    private static void require(boolean condition,String message) { assertions++; if(!condition)throw new AssertionError("E03 mutation: "+message); }
}
