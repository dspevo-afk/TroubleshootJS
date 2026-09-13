package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;

/** Independent structural expectations; electrical behavior is checked in the actual compiled solver. */
public final class E03RelayContractTest {
    private static int assertions;
    public static void main(String[] args) {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        String[] terminals = { "A1", "A2", "COM", "NC", "NO" };
        int[] posts = {3,4,0,1,2};
        for(int i=0;i<5;i++) {
            check(PhysicalPackages.RELAY_SPDT.getTerminalIds().get(i).equals(terminals[i]));
            check(RelaySpecification.terminalIndex(terminals[i])==i && RelaySpecification.POSTS[i]==posts[i]);
        }
        check(new RelaySpecification(5).coilOhms==125 && new RelaySpecification(12).coilOhms==720);
        PowerDomainContract domains=RelayPowerDomains.create();
        check(MeasurementReferencePolicy.check(domains,MeasurementReferencePolicy.Mode.DIFFERENTIAL,
            "CONTACT_OUT","CONTACT_RETURN",null).admitsReading());
        check(MeasurementReferencePolicy.check(domains,MeasurementReferencePolicy.Mode.DIFFERENTIAL,
            "CONTACT_OUT","CTRL_RETURN",null).getDecision()==MeasurementReferencePolicy.Decision.REJECTED);
        check(MeasurementReferencePolicy.check(domains,MeasurementReferencePolicy.Mode.EARTH_REFERENCED,
            "CONTACT_OUT","CONTACT_RETURN",null).getDecision()==MeasurementReferencePolicy.Decision.UNPROVEN);
        try { new RelaySpecification(24); throw new AssertionError("Unqualified relay coil accepted"); }
        catch(IllegalArgumentException expected) { assertions++; }
        String validRelay = "1 .2 0 .2 1.0E9 " + (.65*5/125) + " 125 false";
        new ServiceRelayElm(0,0,96,0,0,new StringTokenizer(validRelay));
        for(String malformed : new String[] {validRelay.replace("false","unknown"),
                validRelay.replace(" 125 "," 0 "),validRelay.replace(".2 0 ",".2 NaN ")}) {
            try {new ServiceRelayElm(0,0,96,0,0,new StringTokenizer(malformed));throw new AssertionError("Invalid relay artifact accepted");}
            catch(IllegalArgumentException expected) {assertions++;}
        }
        TreeMap<String,String> values = new TreeMap<String,String>(); values.put("sample", ".025"); values.put("threshold", "10.8");
        GeneratedTemporalDependency d = new GeneratedTemporalDependency("RELAY",1,"cold","J4.1","J4.2",values);
        values.put("threshold", "9");
        check(!d.equals(new GeneratedTemporalDependency("RELAY",1,"cold","J4.1","J4.2",values)));
        for(long seed : new long[] {0,1,2,3,4,5}) {
            GeneratedBoardInstance owner = new RelayOutputGenerator().generate(seed);
            try {
                check(owner.getBoard().getComponentIds().size()==9);
                check(owner.getBoard().getPowerInputIds().size()==3);
                check(owner.getFaultCandidates().size()==3);
                check(owner.getDiagnosticSolvabilityContract().getOwnerDiversity()==GeneratedDiagnosticOwnerDiversity.MULTI_OWNER_DIAGNOSTIC);
                check(!owner.getBoard().getPad("J1.2").getNetId().equals(owner.getBoard().getPad("J4.2").getNetId()));
                PhysicalRelayPart relay=(PhysicalRelayPart)owner.getPhysicalBoardRuntime().getSlot("K1").getInstalledPart();
                for(int i=0;i<5;i++) {
                    CircuitPostMeasurementEndpoint ep=(CircuitPostMeasurementEndpoint)relay.getTerminal(i).getEndpoint();
                    check(ep.getElement()==relay.getElement() && ep.getPostIndex()==posts[i]);
                    check(owner.getBoard().getPad("K1."+terminals[i])!=null);
                }
                check(owner.getConnectionBindings().getForComponent("K1").size()==5);
                check(owner.getPhysicalBoardRuntime().getScopedMutationCapability("K1") instanceof ReplaceableRelayCapability);
                PhysicalDiodePart diode=(PhysicalDiodePart)owner.getPhysicalBoardRuntime().getInstalledPart("D1");
                CircuitPostMeasurementEndpoint cathode=(CircuitPostMeasurementEndpoint)diode.getTerminal(1).getEndpoint();
                CircuitPostMeasurementEndpoint coilPositive=(CircuitPostMeasurementEndpoint)owner.getConnectionBindings().get("D1","D1.A").getComponentEndpoint();
                check(cathode.getElement()==coilPositive.getElement() && cathode.getPostIndex()==coilPositive.getPostIndex());
                check(diode.getOrientation()==PhysicalPartOrientation.REVERSED);
                PhysicalMutationSlot first=owner.getPhysicalBoardRuntime().getScopedMutationCapability("K1").getMutationSlot();
                GeneratedBoardInstance foreign=new RelayOutputGenerator().generate(seed+6);
                PhysicalMutationSlot other=foreign.getPhysicalBoardRuntime().getScopedMutationCapability("K1").getMutationSlot();
                try {first.restoreAttachmentState(other.captureAttachmentState()); throw new AssertionError("Foreign relay snapshot accepted");}
                catch(IllegalArgumentException expected) {assertions++;}
                owner.getTemporalBehavior().getDependency(owner);
                owner.getDiagnosticProvider().getObservationProgram();
                GeneratedExternalPowerBindings power=owner.getExternalPowerBindings();
                power.getBinding("COIL_INPUT").setConnected(false);
                power.getBinding("COIL_INPUT").setCurrentLimit(.01);
                GeneratedExternalPowerBindings.SavedControls saved=power.saveControls();
                power.setConnected(true);power.getBinding("COIL_INPUT").setCurrentLimit(.25);
                saved.restore(power);
                check(saved.matches() && !power.areAllDisconnected() && !power.areAllConnected());
                try {saved.restore(foreign.getExternalPowerBindings());throw new AssertionError("Foreign source snapshot accepted");}
                catch(IllegalArgumentException expected) {assertions++;}
            } finally { for(CircuitElm element:owner.getSimulationElements()) element.delete(); }
        }
        for(long value : new long[] {Long.MIN_VALUE,Long.MAX_VALUE,-1,0,1,2,3,4,5,9007199254740993L}) {
            long seed=QuickPlayFamilyRegistry.selectNormalPlayerSeed(QuickPlayFamilyRegistry.RELAY_OUTPUT,value);
            check(seed>=0 && seed<=5);
        }
        System.out.println("PASS: E03 relay contracts " + assertions + " assertions");
    }
    private static void check(boolean condition) { assertions++; if(!condition)throw new AssertionError("E03 structure " + assertions); }
}
