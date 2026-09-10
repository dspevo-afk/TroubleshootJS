package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.PowerOperatingAssessment.*;

/** Small real solver/owner proof; no new playable circuit family or earth scope. */
final class A06PowerDeveloperVerifier {
    private static int assertions;
    private A06PowerDeveloperVerifier() { }
    static String verify(CirSim sim, boolean forced) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning &&
            sim.isGeneratedRuntimeSettled(), "explicit settled A06 route required");
        if (forced) throw new AssertionError("a06-explicit-failure-canary");
        assertions = 0;
        String vectors = A06PowerContractVectors.run();
        int pureCount = A06PowerContractVectors.assertions;
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        Vector<CircuitElm> fixture = new Vector<CircuitElm>();
        GeneratedBoardInstance candidate = null;
        double backfed = Double.NaN;
        boolean restored = false;
        try {
            backfed = verifyBackfeed(sim, snapshot, fixture);
            snapshot.restore(sim); snapshot.assertRestored(sim);
            BoundedGeneratedBoardAssembler.Result built = BoundedGeneratedBoardAssembler.assemble(
                BoundedAssemblyRequest.forControlledIndicator(1));
            candidate = built.getInstance();
            FreshGeneratedRuntimeInstallation.installComposition(sim,candidate,false);
            GeneratedRuntimeDeveloperSettlement.settle(sim,candidate,"a06-domain-install");
            PowerDomainRuntimeCapability power = (PowerDomainRuntimeCapability)
                candidate.getPhysicalBoardRuntime().getCapability(PowerDomainRuntimeCapability.CAPABILITY_ID);
            require(power != null,"domain capability installed");
            PowerOperatingAssessment assessment = power.assessPower();
            require(assessment.getSupplyCondition()==SupplyCondition.CONNECTED,"three actual inputs connected");
            require(assessment.getReadiness()==Readiness.POWER_OFF,"connected source cannot allow active meter");
            require(candidate.getExternalPowerBindings().getSourceStates().size()==3,"per-source inventory");
            CircuitPostMeasurementEndpoint positive = endpoint(candidate,
                candidate.getBoard().getPowerInput(candidate.getBoard().getPowerInputIds().firstElement()).getPositivePadId());
            CircuitPostMeasurementEndpoint negative = endpoint(candidate,
                candidate.getBoard().getPowerInput(candidate.getBoard().getPowerInputIds().firstElement()).getReturnPadId());
            // Reference admission does not create physical earth or change DC loading.
            require(power.assessReference(MeasurementReferencePolicy.Mode.DIFFERENTIAL,positive,negative).admitsReading(),
                "differential reference admitted");
            require(!power.assessReference(MeasurementReferencePolicy.Mode.EARTH_REFERENCED,positive,negative).admitsReading(),
                "numerical ground is not earth");
            for (int reading = 0; reading < 2; reading++) {
                GeneratedRuntimeDeveloperSettlement.settle(sim,candidate,"a06-repeat-dc");
                double voltage = sim.measureDcVoltage(positive,negative);
                require(!Double.isNaN(voltage) && Math.abs(voltage-5)<.01,
                    "successive loaded DC observation uses the current reference");
                require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                    "loaded DC observation restored its owned overlay");
            }
            String input = candidate.getBoard().getPowerInputIds().firstElement();
            ExternalPowerSimulationBinding binding = candidate.getExternalPowerBindings().getBinding(input);
            String before = candidate.getExternalPowerBindings().controlSignature();
            binding.setConnected(false); binding.setConnected(true);
            require(!before.equals(candidate.getExternalPowerBindings().controlSignature()),"roundtrip source change observed");
            // Same timestep cannot authorize an old sample after a roundtrip switch change.
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(!sim.getActiveMeasurementReadiness(positive,negative).isReady(),"immediate source-off not ready");
            GeneratedRuntimeDeveloperSettlement.settle(sim,candidate,"a06-isolated-observation");
            assessment = power.assessPower();
            require(assessment.areAllSourcesIsolated(),"all controls physically isolated");
            require(assessment.getReadiness()==Readiness.READY,"fresh discharged current board ready: "+assessment.getIssues());
            require(sim.getActiveMeasurementReadiness(positive,negative)==ActiveMeasurementReadiness.READY,"readiness consumed by instrument");
            // The new result must still pass through the actual temporary measurement lifecycle.
            double resistance = sim.measureResistance(positive,negative);
            require(!Double.isNaN(resistance) && !sim.activeMeasurementOverlay,"active resistance completed/cleaned");
            for (int reading = 0; reading < 2; reading++) {
                GeneratedRuntimeDeveloperSettlement.settle(sim,candidate,"a06-repeat-resistance");
                require(sim.getActiveMeasurementReadiness(positive,negative).isReady(),
                    "restored meter sample becomes ready on the next real step");
                double again = sim.measureResistance(positive,negative);
                require(!Double.isNaN(again) && !sim.activeMeasurementOverlay &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                    "successive resistance observes and restores the current owner");
            }
            GeneratedRuntimeDeveloperSettlement.settle(sim,candidate,"a06-after-meter");
            power.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            require(power.assessPower().getReadiness()!=Readiness.READY,"pending observation blocks readiness");
            power.observeSimulationTime(Double.NaN);
            require(power.assessPower().getReadiness()!=Readiness.READY,"nonfinite clock cannot refresh readiness");
            sim.requestGeneratedBoardVerification();
            GeneratedRuntimeDeveloperSettlement.settle(sim,candidate,"a06-revalidate-after-invalid-sample");
            require(power.assessPower().getReadiness()==Readiness.READY,"new real step recovers readiness");
            snapshot.restore(sim); snapshot.assertRestored(sim);
            require(power.getActiveMeasurementReadiness(positive,negative,BoardPowerState.UNPOWERED,true)==
                ActiveMeasurementReadiness.UNKNOWN,"retired owner cannot issue ready result");
            require(!power.assessReference(MeasurementReferencePolicy.Mode.DIFFERENTIAL,positive,negative).admitsReading(),
                "retired owner cannot admit reference");
        } finally {
            snapshot.restore(sim);
            if (candidate != null) {
                require(candidate != sim.getGeneratedBoardInstance(),"do not dispose active owner");
                candidate.getExternalPowerBindings().setConnected(false);
                for (CircuitElm element : candidate.getSimulationElements()) element.delete();
            }
            for (CircuitElm element : fixture) {
                require(!sim.elmList.contains(element),"raw fixture not in restored player graph");
                element.delete();
            }
            snapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance()==original,"original owner restored");
            restored = true;
        }
        return "{\"protocol\":\"TSJ-A06-POWER-1\",\"status\":\"PASS\",\"pureAssertions\":"+pureCount+
            ",\"runtimeAssertions\":"+assertions+",\"backfeedVolts\":"+backfed+
            ",\"originalOwnerRestored\":"+(restored ? "true" : "false")+",\"candidateCleanup\":\"PASS\",\"sourceIsolation\":true,"+
            "\"staleOwnerRejected\":true,\"differentialReference\":true,\"earthConnectionNotInvented\":true,"+
            "\"vectors\":\""+vectors+"\"}";
    }
    private static double verifyBackfeed(CirSim sim,Task41SimulationSnapshot snapshot,Vector<CircuitElm> elements) {
        snapshot.beginProof(sim);
        sim.elmList = elements;
        sim.adjustables = new Vector<Adjustable>();
        sim.generatedBoardInstance = null; sim.generatedChallengeController = null;
        DCVoltageElm a = new DCVoltageElm(64,256);a.drag(64,128);a.maxVoltage=5;elements.add(a);
        DCVoltageElm b = new DCVoltageElm(64,512);b.drag(64,384);b.maxVoltage=5;elements.add(b);
        SwitchElm sa = new SwitchElm(64,128);sa.drag(192,128);elements.add(sa);
        SwitchElm sb = new SwitchElm(64,384);sb.drag(192,384);elements.add(sb);
        SwitchExternalPowerControl ac = new SwitchExternalPowerControl(sa), bc = new SwitchExternalPowerControl(sb);
        ac.setConnected(true);bc.setConnected(false);
        ResistorElm link = new ResistorElm(192,128);link.drag(192,384);link.setResistance(1000);elements.add(link);
        ResistorElm load = new ResistorElm(192,384);load.drag(320,384);load.setResistance(1000);elements.add(load);
        WireElm returned = new WireElm(320,384);returned.drag(64,512);elements.add(returned);
        GroundElm ga = new GroundElm(64,256);ga.drag(64,288);elements.add(ga);
        GroundElm gb = new GroundElm(64,512);gb.drag(64,544);elements.add(gb);
        solve(sim);
        double av = sa.getPostVoltage(1), bv = sb.getPostVoltage(1);
        require(Math.abs(av-5)<1e-6 && Math.abs(bv-2.5)<1e-6,"real disabled-source backfeed divider");
        require(Math.abs(Math.abs(link.getCurrent())-.0025)<1e-8,"real backfeed current");
        PowerOperatingAssessment assessment = PowerOperatingAssessment.assess(
            A06PowerContractVectors.fixture(PowerDomainContract.StorageRequirement.NONE,true),
            A06PowerContractVectors.states(ac.isConnected()?SourceState.connected():SourceState.isolated(),
                bc.isConnected()?SourceState.connected():SourceState.isolated()),A06PowerContractVectors.volts(av,bv),true);
        require(assessment.getSupplyCondition()==SupplyCondition.PARTIAL &&
            assessment.getRailConditions().get("B")==RailCondition.BACKFED &&
            assessment.getReadiness()==Readiness.POWER_OFF,"real measured backfeed classified");
        ac.setConnected(false);solve(sim);
        assessment = PowerOperatingAssessment.assess(
            A06PowerContractVectors.fixture(PowerDomainContract.StorageRequirement.NONE,true),
            A06PowerContractVectors.states(SourceState.isolated(),SourceState.isolated()),
            A06PowerContractVectors.volts(sa.getPostVoltage(1),sb.getPostVoltage(1)),true);
        require(assessment.getReadiness()==Readiness.READY,"isolated divider actually discharged");
        return bv;
    }
    private static void solve(CirSim sim) {
        sim.analyzeCircuit();sim.runCircuit(true);sim.runCircuit(true);
        require(sim.stopMessage==null,"raw fixture solver did not converge");
    }
    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance instance,String pad) {
        CircuitMeasurementEndpoint endpoint=instance.getSimulationBindings().getEndpoint(pad);
        require(endpoint instanceof CircuitPostMeasurementEndpoint,"actual endpoint required");
        return (CircuitPostMeasurementEndpoint)endpoint;
    }
    private static void require(boolean value,String message) {
        assertions++;if(!value)throw new AssertionError("A06: "+message);
    }
}
