package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Diagnostic observation only. Runs real Q30 physics; does not certify UI or acceptance. */
public final class Q30ActivePowerDiagnostic {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        Q30ServiceFlowContractTest.NativeServiceCirSim sim = new Q30ServiceFlowContractTest.NativeServiceCirSim();
        Q30ServiceFlowContractTest.configureSimulator(sim);
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = new Rb30Generator().generate(seed);
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = owner;
        BoardModificationController modifications = new BoardModificationController(sim, owner);
        sim.boardModificationController = modifications;
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim, owner, modifications, 0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        owner.getFaultBinding().setApplied(false);
        settle(sim, owner, modifications, .000040);
        Rb30Behavior behavior = (Rb30Behavior) owner.getFamilyState();
        behavior.prepareHealthyProfile(sim, owner);
        behavior.verifyHealthy(owner, BoardPowerState.POWERED);
        owner.getFaultBinding().setApplied(true);
        behavior.prepareFaultedProfile(sim, owner);
        behavior.verifyFaultedProfile(sim, owner, modifications, BoardPowerState.POWERED);
        settle(sim, owner, modifications, .000040);
        report("powered-faulted", sim, owner);
        sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
        runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
        report("immediately-isolated", sim, owner);
        double isolatedTime = sim.t;
        for (int tick=1; tick<=50; tick++) {
            settle(sim, owner, modifications, .100);
            if (tick==1 || tick==10 || tick==50)
                report("isolated-"+tick+"00ms", sim, owner);
        }
        if (!sim.boardPowerController.isElectricallyUnpowered() || sim.activeMeasurementOverlay)
            throw new AssertionError("Diagnostic changed isolation or left an overlay");
        System.out.println("Q30_ACTIVE_DIAGNOSTIC_COMPLETE seed="+seed+" actualIsolatedSeconds="+(sim.t-isolatedTime)+" productSourceChanged=false acceptanceCertified=false");
    }
    private static void settle(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, double seconds) {
        sim.advanceGeneratedTemporalProfile(seconds);
        GeneratedRuntimeInvariant.verify(owner, modifications, sim.elmList);
        GeneratedBoardVerifier.verify(owner, sim.boardPowerController.getState(), modifications, sim.elmList, false);
        // Same native UI-loop boundary as the maintained Q30 service fixture,
        // consumed only after actual solver/invariant/verifier execution.
        sim.generatedBoardVerificationPending=false;
        sim.generatedBoardVerificationAnalyzed=false;
        sim.analyzeFlag=false;
        sim.dcAnalysisFlag=false;
    }
    private static void report(String phase, CirSim sim, GeneratedBoardInstance owner) {
        PowerDomainRuntimeCapability power=(PowerDomainRuntimeCapability) owner.getPhysicalBoardRuntime().getCapability(PowerDomainRuntimeCapability.CAPABILITY_ID);
        PowerOperatingAssessment assessment=power.assessPower();
        System.out.println("Q30_ACTIVE_POWER seed="+owner.getSeed()+" phase="+phase+" simulationTime="+sim.t+" settled="+sim.isGeneratedRuntimeSettled()+" source="+assessment.getSupplyCondition()+" readiness="+assessment.getReadiness()+" issues="+assessment.getIssues()+" rails="+assessment.getRailConditions());
        for (PowerDomainContract.Rail rail : power.getContract().getRails().values()) {
            Vector<CircuitMeasurementEndpoint> endpoints=owner.getSimulationBindings().getEndpointsForNet(rail.getId());
            Vector<CircuitMeasurementEndpoint> refs=owner.getSimulationBindings().getEndpointsForNet(rail.getReferenceId());
            CircuitPostMeasurementEndpoint ref=refs.isEmpty()?null:(CircuitPostMeasurementEndpoint)refs.firstElement();
            double reference=ref==null?Double.NaN:ref.getElement().getPostVoltage(ref.getPostIndex());
            String values="";
            for (CircuitMeasurementEndpoint endpoint:endpoints) {
                CircuitPostMeasurementEndpoint post=(CircuitPostMeasurementEndpoint)endpoint;
                values += (values.length()==0?"":",")+(post.getElement().getPostVoltage(post.getPostIndex())-reference)+"/"+sim.containsElement(post.getElement())+"/"+owner.ownsRuntimeSimulationElement(post.getElement());
            }
            System.out.println("Q30_ACTIVE_RAIL seed="+owner.getSeed()+" phase="+phase+" net="+rail.getId()+" reference="+rail.getReferenceId()+" storage="+rail.getStorageRequirement()+" endpointValues="+values);
        }
    }
}
