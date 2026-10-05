package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Real Q30 source isolation, residual exposure, and active meter transactions. */
public final class Q30PowerReadinessContractTest {
    private static int assertions;
    public static void main(String[] args) {
        for (long seed : new long[] {10387L,10226L,10014L}) verify(seed);
        System.out.println("PASS: Q30 power readiness contracts "+assertions+" assertions seeds=3 realOhm=3 realDiode=3");
    }
    private static void verify(long seed) {
        NativeMeterCirSim sim=new NativeMeterCirSim();
        Q30ServiceFlowContractTest.configureSimulator(sim);
        CircuitElm.sim=sim;
        GeneratedBoardInstance owner=new Rb30Generator().generate(seed);
        sim.elmList=new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables=new Vector<Adjustable>();
        sim.undoStack=new Vector<String>(); sim.redoStack=new Vector<String>();
        sim.generatedBoardInstance=owner;
        BoardModificationController modifications=new BoardModificationController(sim,owner);
        sim.boardModificationController=modifications;
        PhysicalBoardRuntime runtime=owner.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim,owner,modifications,0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        PowerDomainRuntimeCapability power=(PowerDomainRuntimeCapability)runtime.getCapability(PowerDomainRuntimeCapability.CAPABILITY_ID);
        PowerDomainContract contract=power.getContract();
        verifyConservativePolicy(contract);
        CircuitPostMeasurementEndpoint red=endpoint(owner,"JOA.1"),black=endpoint(owner,"JOA.2");
        check(sim.getActiveMeasurementReadiness(red,black)!=ActiveMeasurementReadiness.READY,"connected sources must block active measurements");
        // A newly constructed graph has uncharged capacitors. Isolate before
        // its first real solver step; do not reset stored energy in a live graph.
        sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
        runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
        check(power.assessPower().getReadiness()==PowerOperatingAssessment.Readiness.WAITING,"source change requires a fresh solver receipt");
        settle(sim,owner,modifications,.000040);
        check(sim.getActiveMeasurementReadiness(red,black)==ActiveMeasurementReadiness.READY,"fresh actually discharged graph admits active measurements");
        double ohms=sim.measureResistance(red,black);
        check(finite(ohms)&&Math.abs(ohms-180)<1,"actual JOA external 180-ohm load, not a simulated meter value");
        check(!sim.activeMeasurementOverlay&&sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),"OHM restores its actual temporary solver source");
        settle(sim,owner,modifications,.000040);
        check(sim.getActiveMeasurementReadiness(red,black)==ActiveMeasurementReadiness.READY,"new real sample recovers after OHM cleanup");
        DiodeMeasurementResult diode=sim.measureDiode(red,black);
        check(diode!=null&&finite(diode.voltage)&&finite(diode.current),"real diode-mode transaction returns both electrical samples");
        // Finite-compliance DIODE stimulus is 3 V through 1 kOhm. The 180-ohm
        // external load gives an independent divider/current oracle.
        check(Math.abs(diode.voltage-3.0*180/1180)<.005&&Math.abs(diode.current-3.0/1180)<.00002,"actual DIODE stimulus across known resistive load");
        check(!sim.activeMeasurementOverlay&&sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),"DIODE restores its actual temporary solver source");
        settle(sim,owner,modifications,.000040);
        check(sim.boardPowerController.isElectricallyUnpowered(),"meter cleanup cannot repower isolated sources");
        sim.boardPowerController.setState(BoardPowerState.POWERED);
        runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        owner.getFaultBinding().setApplied(false);
        Rb30Behavior behavior=(Rb30Behavior)owner.getFamilyState();
        check(behavior.getLiveSolverAdvanceSeconds()==.0001,"connected sources retain the original powered cadence");
        String dependency=behavior.getDependency(owner).canonical();
        settle(sim,owner,modifications,.000040);
        behavior.prepareHealthyProfile(sim,owner);
        behavior.verifyHealthy(owner,BoardPowerState.POWERED);
        owner.getFaultBinding().setApplied(true);
        behavior.prepareFaultedProfile(sim,owner);
        behavior.verifyFaultedProfile(sim,owner,modifications,BoardPowerState.POWERED);
        sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
        runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
        for (double seconds : new double[] {.000040,.001,.100,.899}) {
            settle(sim,owner,modifications,seconds);
            PowerOperatingAssessment assessment=power.assessPower();
            check(assessment.areAllSourcesIsolated(),"all actual source switches are disconnected");
            check(assessment.getReadiness()==PowerOperatingAssessment.Readiness.DISCHARGE,"known residual exposure must report DISCHARGE, not UNKNOWN: "+seed+" "+assessment.getIssues());
            check(assessment.getIssues().isEmpty(),"declared real storage exposure is explained: "+assessment.getIssues());
            check(sim.getActiveMeasurementReadiness(red,black)==ActiveMeasurementReadiness.DISCHARGE,"instrument consumes the discharge guard");
            double time=sim.t;
            check(Double.isNaN(sim.measureResistance(red,black))&&sim.measureDiode(red,black)==null,"active instruments cannot inject current while stored energy remains");
            check(sim.t==time&&!sim.activeMeasurementOverlay&&sim.boardPowerController.isElectricallyUnpowered(),"blocked measurements leave time, graph and isolation unchanged");
        }
        check(Math.abs(voltage(owner,"RAW12","CTRL_RETURN"))>.25,"actual input-side residual was not erased or synthesized");
        System.out.println("Q30_POWER_READINESS seed="+seed+" packages="+owner.getBoard().getComponentIds().size()+" status=PASS residual=DISCHARGE thresholdVolts=0.25 activeOhm=PASS activeDiode=PASS");
        check(behavior.getLiveSolverAdvanceSeconds()==.005,"all actual sources isolated select bounded faster transient work");
        check(dependency.equals(behavior.getDependency(owner).canonical()),"live scheduling identity is independent of current power state");
        if(seed==10387L) verifyNaturalRecovery(sim,owner,modifications,behavior,red,black);
        else check(sim.getActiveMeasurementReadiness(red,black)==ActiveMeasurementReadiness.DISCHARGE,"real fault-dependent residual stays blocked");
        sim.boardPowerController.setSourceConnected("SENSOR_A",true);
        check(behavior.getLiveSolverAdvanceSeconds()==.0001,"one remaining connected source excludes off-state acceleration");
        sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
        owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
        check(sim.boardPowerController.isElectricallyUnpowered(),"cadence checks restore real source isolation");
    }
    private static void verifyNaturalRecovery(NativeMeterCirSim sim,GeneratedBoardInstance owner,
            BoardModificationController modifications,Rb30Behavior behavior,
            CircuitPostMeasurementEndpoint red,CircuitPostMeasurementEndpoint black) {
        Vector<CircuitElm> graph=sim.elmList;
        double started=sim.t;int frames=0;
        // Already one second into isolation. Independent network calculation:
        // 22 uF * (1 Mohm || (10 kohm + 1 Mohm)) is about 11.06 s. The observed
        // .761 V tail therefore crosses .25 V after another roughly 12.3 s.
        while(sim.getActiveMeasurementReadiness(red,black)==ActiveMeasurementReadiness.DISCHARGE &&
                sim.t-started<14.0) {
            sim.solverExecutor.advanceFor(behavior.getLiveSolverAdvanceSeconds());
            owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);frames++;
        }
        GeneratedRuntimeInvariant.verify(owner,modifications,sim.elmList);
        GeneratedBoardVerifier.verify(owner,BoardPowerState.UNPOWERED,modifications,sim.elmList,false);
        check(sim.elmList==graph&&sim.boardPowerController.isElectricallyUnpowered(),"natural recovery preserves the same charged graph and source isolation");
        check(frames>2000&&frames<2801&&sim.t-started>10.0,"recovery required real accepted solver time, not wall-clock permission");
        check(sim.getActiveMeasurementReadiness(red,black)==ActiveMeasurementReadiness.READY,"actual storage decay admits measurements after the unchanged guard");
        double residual=Math.abs(voltage(owner,"RAIL12","CTRL_RETURN"));
        check(residual>0&&residual<=.25,"solver-observed capacitor voltage naturally crosses the existing threshold");
        double ohms=sim.measureResistance(red,black);
        check(finite(ohms)&&Math.abs(ohms-180)<1,"charged-to-ready graph yields the real external load resistance");
        settle(sim,owner,modifications,.000040);
        DiodeMeasurementResult diode=sim.measureDiode(red,black);
        check(diode!=null&&Math.abs(diode.voltage-3.0*180/1180)<.005&&Math.abs(diode.current-3.0/1180)<.00002,"charged-to-ready graph yields the real finite-compliance diode reading");
        check(!sim.activeMeasurementOverlay&&sim.isActiveMeasurementSolverRestoredForDeveloperVerification()&&sim.boardPowerController.isElectricallyUnpowered(),"recovered measurements restore the graph without repowering");
        System.out.println("Q30_NATURAL_RECOVERY seed=10387 additionalSolverSeconds="+(sim.t-started)+" liveFrames="+frames+" residualVolts="+residual+" realOhm="+ohms+" realDiode="+diode.voltage);
    }
    private static void verifyConservativePolicy(PowerDomainContract contract) {
        Map<String,PowerOperatingAssessment.SourceState> sources=new TreeMap<String,PowerOperatingAssessment.SourceState>();
        for (String id:contract.getSources().keySet()) sources.put(id,PowerOperatingAssessment.SourceState.isolated());
        Map<String,Double> volts=new TreeMap<String,Double>();
        for (String id:contract.getRails().keySet()) volts.put(id,0.0);
        check(contract.getRails().get("LOAD12").getStorageRequirement()==PowerDomainContract.StorageRequirement.NONE,"isolated load domain is not relabeled as storage");
        volts.put("LOAD12",1.0);
        check(PowerOperatingAssessment.assess(contract,sources,volts,true).getReadiness()==PowerOperatingAssessment.Readiness.UNKNOWN,"unexplained load-domain energy remains blocked as UNKNOWN");
        volts.put("LOAD12",0.0); volts.put("RAW12",Double.NaN);
        check(PowerOperatingAssessment.assess(contract,sources,volts,true).getReadiness()==PowerOperatingAssessment.Readiness.UNKNOWN,"nonfinite storage exposure cannot grant readiness");
        volts.put("RAW12",0.0);
        check(PowerOperatingAssessment.assess(contract,sources,volts,false).getReadiness()==PowerOperatingAssessment.Readiness.WAITING,"stale observations remain waiting");
        sources.put("MAIN12",PowerOperatingAssessment.SourceState.unknown());
        check(PowerOperatingAssessment.assess(contract,sources,volts,true).getReadiness()==PowerOperatingAssessment.Readiness.UNKNOWN,"unknown source remains blocked");
    }
    private static void settle(CirSim sim,GeneratedBoardInstance owner,BoardModificationController modifications,double seconds) {
        sim.advanceGeneratedTemporalProfile(seconds);
        GeneratedRuntimeInvariant.verify(owner,modifications,sim.elmList);
        GeneratedBoardVerifier.verify(owner,sim.boardPowerController.getState(),modifications,sim.elmList,false);
        sim.generatedBoardVerificationPending=false; sim.generatedBoardVerificationAnalyzed=false;
        sim.analyzeFlag=false; sim.dcAnalysisFlag=false;
    }
    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance owner,String pad) {
        return (CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint(pad);
    }
    private static double voltage(GeneratedBoardInstance owner,String net,String reference) {
        CircuitPostMeasurementEndpoint a=(CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpointsForNet(net).firstElement();
        CircuitPostMeasurementEndpoint b=(CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpointsForNet(reference).firstElement();
        return a.getElement().getPostVoltage(a.getPostIndex())-b.getElement().getPostVoltage(b.getPostIndex());
    }
    private static boolean finite(double v) { return !Double.isNaN(v)&&!Double.isInfinite(v); }
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    /** Native fixtures have no speed widget; only scheduling is adapted. */
    private static final class NativeMeterCirSim extends Q30ServiceFlowContractTest.NativeServiceCirSim {
        @Override void runCircuit(boolean didAnalyze) {
            // Keep the production temporary-source/reader/restoration transaction
            // and advance the actual guarded CircuitJS solver, eight real steps.
            solverExecutor.advanceSteps(8);
        }
    }
}
