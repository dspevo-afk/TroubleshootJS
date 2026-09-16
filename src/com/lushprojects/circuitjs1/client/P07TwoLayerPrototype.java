package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Matched unlinked crossing: independently solved 5 V and 7 V loops, with two real vias. */
final class P07TwoLayerPrototype {
    static final String FAMILY = "P07_TWO_LAYER", VARIANT = "RESTRICTED_CROSSING";
    final GeneratedBoardInstance instance;
    final ResistorElm load, underLoad;
    final PcbLayerRoutingPrototype.Result routing;

    P07TwoLayerPrototype() {
        P06FactoryLinkFixtures.Fixture fixture = new P06FactoryLinkFixtures.Fixture(true, 0, 0, true);
        routing=PcbLayerRoutingPrototype.route(fixture.board,fixture.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        if(!routing.accepted() || routing.vias!=2) throw new IllegalStateException("P07 crossing route did not qualify");
        PcbBoardLayout routed=routing.layout; routed.validateGeometry(fixture.board);
        TroubleshootBoard board = fixture.board;
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        WireElm left = wire(300,100,320,100), right = wire(480,100,500,100);
        WireElm under = wire(600,100,600,200), groundWire = wire(500,300,700,300);
        elements.add(left); elements.add(right); elements.add(under); elements.add(groundWire);
        elements.add(wire(320,100,480,100));
        load = resistor(500,100,500,300,100);
        underLoad = resistor(600,200,700,300,1000);
        elements.add(load); elements.add(underLoad);
        GroundElm ground = new GroundElm(500,300);
        ground.x2 = 500; ground.y2 = 332; ground.setPoints(); elements.add(ground);
        elements.add(wire(100,300,500,300));
        GeneratedExternalPowerBindings power = new GeneratedExternalPowerBindings(board);
        supply(elements, power, "LINK_SUPPLY", 100,300,100,100,300,100,5);
        supply(elements, power, "UNDER_SUPPLY", 700,300,700,100,600,100,7);

        BoardSimulationBindings pads = board.getSimulationBindings();
        GeneratedComponentBindings components = new GeneratedComponentBindings(board);
        BoardPhysicalSpecifications specs = new BoardPhysicalSpecifications();
        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        String[] ids = {"L","R","A","B","G"};
        int[][] positions = {{300,100},{500,100},{600,100},{600,200},{700,300}};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i]; int x = positions[i][0], y = positions[i][1];
            WireElm pin = wire(x,y,x + 16,y - 16); elements.add(pin);
            pads.bindPad(id + ".1", endpoint(pin,0)); components.bindComponent(id,pin);
            BasicPhysicalSpecification spec = new BasicPhysicalSpecification("P07_BENCH_TEST_POINT");
            PhysicalNameplate name = new PhysicalNameplate(id,"Bench test point " + id);
            specs.addPhysicalDefinition(id,spec,name,P06FactoryLinkFixtures.TEST_POINT);
            runtime.createSlot(id).install(PhysicalFoundationPartFactory.fromBoardBindings(id,spec,name,
                P06FactoryLinkFixtures.TEST_POINT,pads,pin,
                new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,id)));
        }
        specs.addPowerInputNameplate(new PowerInputNameplate("LINK_SUPPLY",5));
        specs.addPowerInputNameplate(new PowerInputNameplate("UNDER_SUPPLY",7));
        GeneratedChallengeBehaviorContract behavior = new GeneratedChallengeBehaviorContract() {
            public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) {
                verifyReadings(owner, state, true);
            }
            public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes,
                    BoardPowerState state) { verifyReadings(owner, state, changes.isFullyRestored()); }
            public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
                    BoardModificationController changes, BoardPowerState state, boolean overlay) {
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            }
            public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
                    BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
        };
        Vector<GeneratedFaultCandidate> faults = new Vector<GeneratedFaultCandidate>();
        instance = new GeneratedBoardInstance(board,elements,6,FAMILY,VARIANT,
            "P07 developer bench - two copper faces; not a player challenge",components,power,
            new GeneratedComponentConnectionBindings(board),behavior,routed,specs,null,
            new GeneratedComponentOperationalStates(),null,null,runtime,null,true,faults,
            GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(FAMILY,VARIANT,6,faults));
    }

    void verifyReadings(GeneratedBoardInstance owner, BoardPowerState state, boolean connected) {
        boolean on = state == BoardPowerState.POWERED;
        near(Math.abs(load.getCurrent()), on && connected ? 5.0 / 100.0 : 0, 1e-7, "link load current");
        near(Math.abs(underLoad.getCurrent()), on ? .007 : 0, 1e-7, "underpass load current");
        near(voltage(owner,"R.1"), on && connected ? 5 : 0, 1e-5, "link output");
        near(voltage(owner,"A.1"), on ? 7 : 0, 1e-5, "independent underpass");
    }
    static double voltage(GeneratedBoardInstance owner, String pad) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint(pad);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }
    private static void near(double actual,double expected,double tolerance,String name) {
        if (Double.isNaN(actual) || Double.isInfinite(actual) || Math.abs(actual-expected)>tolerance)
            throw new IllegalStateException("P07 " + name + ": expected " + expected + " actual " + actual);
    }
    private static CircuitPostMeasurementEndpoint endpoint(CircuitElm element,int post) {
        return new CircuitPostMeasurementEndpoint(element,post);
    }
    private static WireElm wire(int x,int y,int x2,int y2) {
        WireElm wire = new WireElm(x,y); wire.x2=x2; wire.y2=y2; wire.setPoints(); return wire;
    }
    private static ResistorElm resistor(int x,int y,int x2,int y2,double resistance) {
        ResistorElm resistor = new ResistorElm(x,y); resistor.x2=x2; resistor.y2=y2;
        resistor.resistance=resistance; resistor.setPoints(); return resistor;
    }
    private static void supply(Vector<CircuitElm> elements,GeneratedExternalPowerBindings bindings,
            String id,int x,int y,int x2,int y2,int outX,int outY,double volts) {
        DCVoltageElm supply = new DCVoltageElm(x,y); supply.x2=x2; supply.y2=y2;
        supply.maxVoltage=volts; supply.setPoints();
        SwitchElm isolation = new SwitchElm(x2,y2); isolation.x2=outX; isolation.y2=outY; isolation.setPoints();
        elements.add(supply); elements.add(isolation);
        Vector<CircuitElm> sources = new Vector<CircuitElm>(); sources.add(supply); sources.add(isolation);
        bindings.bindPowerInput(id,new ExternalPowerSimulationBinding(sources,new SwitchExternalPowerControl(isolation)));
    }
}
