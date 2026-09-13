package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable small control-board intent. Named choices never consume layout or fault draws. */
final class Rb15Plan {
    static final String FAMILY_ID = "RB15_CONTROL";
    final long seed, layoutSeed, routingSeed;
    final boolean bjt, filtered;
    final GeneratedFaultType fault;
    private final PcbBoardLayout routedLayout;
    private Rb15Plan(long seed) {
        routedLayout=null;
        this.seed=seed;
        NamedRandomStreams streams=new NamedRandomStreams(1,seed,FAMILY_ID,1);
        bjt=streams.openBlock("output-driver",NamedRandomStreams.Concern.TOPOLOGY,1,"implementation").nextInt(2)==0;
        filtered=streams.openBlock("command",NamedRandomStreams.Concern.SUPPORT,1,"conditioning").nextInt(2)==0;
        GeneratedFaultType[] candidates={GeneratedFaultType.BASE_RESISTOR_OPEN,
            GeneratedFaultType.RELAY_COIL_OPEN,GeneratedFaultType.RELAY_CONTACT_OPEN};
        fault=candidates[streams.openDevice(NamedRandomStreams.Concern.FAULT,1,"serviceable").nextInt(3)];
        layoutSeed=streams.deviceSeed(NamedRandomStreams.Concern.PLACEMENT,1,"board");
        routingSeed=streams.deviceSeed(NamedRandomStreams.Concern.ROUTING,1,"copper");
    }
    private Rb15Plan(Rb15Plan plan,PcbBoardLayout layout) {
        seed=plan.seed;layoutSeed=plan.layoutSeed;routingSeed=plan.routingSeed;
        bjt=plan.bjt;filtered=plan.filtered;fault=plan.fault;routedLayout=layout;
    }
    Rb15Plan withRoutedLayout(PcbBoardLayout layout) {
        if(routedLayout!=null || layout==null || !layout.matchesGenerationSeeds(layoutSeed,routingSeed))
            throw new IllegalStateException("Routing resolution does not belong to this procedural plan");
        layout.validateGeometry(board());layout.seal();return new Rb15Plan(this,layout);
    }
    PcbBoardLayout getRoutedLayout() { return routedLayout; }
    static Rb15Plan resolve(long seed) { return new Rb15Plan(seed); }
    RelayDriverProvider driver() { return bjt?new RelayDriverProvider.Bjt():new RelayDriverProvider.Nmos(); }
    String topology() { return "RB15_"+(bjt?"BJT":"NMOS")+"_"+(filtered?"RC_FILTER":"RESISTIVE_BIAS"); }
    String canonical() {
        return "rb15-plan@1;seed="+Long.toString(seed)+";design="+topology()+";layout="+Long.toString(layoutSeed)+";routing="+Long.toString(routingSeed)+
            ";fault="+fault.name()+";packages=16;input=12V;command=5V;load=180ohm;coil=12V;bulk=1uF;bleed=2.2kohm;"+
            "fuse=0.1ohm,0.1875A2s;reverse=1N4148;drive=1kohm;bias=100kohm;input-series=1kohm;"+
            "conditioner="+(filtered?"10nF@DRIVE":"100kohm@FILTERED_CMD")+";indicator=3.3kohm,default-led";
    }
    /** Complete topology manifest: package identities and copper-net membership before allocation. */
    TroubleshootBoard board() {
        TroubleshootBoard board=new TroubleshootBoard("RB15_CONTROL_BOARD");
        String[] supplies={"RAW_INPUT","FUSED_INPUT","CTRL_SUPPLY"};
        for(String net:supplies) board.addNet(new BoardNet(net,BoardNet.RoutingRole.SUPPLY));
        for(String net:new String[]{"CMD","FILTERED_CMD","DRIVE"}) board.addNet(new BoardNet(net,BoardNet.RoutingRole.CONTROL));
        for(String net:new String[]{"COIL_LOW","CONTACT_OUT"}) board.addNet(new BoardNet(net,BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("CTRL_RETURN",BoardNet.RoutingRole.RETURN));
        board.addNet(new BoardNet("NC",BoardNet.RoutingRole.SIGNAL)); board.addNet(new BoardNet("LED_FEED",BoardNet.RoutingRole.SIGNAL));
        declare(board,"J1","CONNECTOR",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,"RAW_INPUT","CTRL_RETURN");
        declare(board,"J2","CONNECTOR",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,"CMD","CTRL_RETURN");
        declare(board,"J4","CONNECTOR",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,"CONTACT_OUT","CTRL_RETURN");
        declare(board,"F1","FUSE",PhysicalPackages.AXIAL_FUSE,"RAW_INPUT","FUSED_INPUT");
        declare(board,"DREV","DIODE",PhysicalPackages.AXIAL_DIODE,"FUSED_INPUT","CTRL_SUPPLY");
        declare(board,"C1","CAPACITOR",PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,"CTRL_SUPPLY","CTRL_RETURN");
        declare(board,"RBLEED","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,"CTRL_SUPPLY","CTRL_RETURN");
        declare(board,"RIN","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,"CMD","FILTERED_CMD");
        declare(board,filtered?"C2":"RBIAS",filtered?"CAPACITOR":"RESISTOR",
            filtered?PhysicalPackages.RADIAL_CERAMIC_CAPACITOR:PhysicalPackages.AXIAL_RESISTOR,filtered?"DRIVE":"FILTERED_CMD","CTRL_RETURN");
        declare(board,"RDRIVE","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,"FILTERED_CMD","DRIVE");
        declare(board,"RPD","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,"DRIVE","CTRL_RETURN");
        declare(board,"Q1",driver().getId(),driver().getPackage(),"DRIVE","COIL_LOW","CTRL_RETURN");
        declare(board,"D1","DIODE",PhysicalPackages.AXIAL_DIODE,"CTRL_SUPPLY","COIL_LOW");
        declare(board,"K1","RELAY",PhysicalPackages.RELAY_SPDT,"CTRL_SUPPLY","COIL_LOW","CTRL_SUPPLY","NC","CONTACT_OUT");
        declare(board,"RLED","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,"CTRL_SUPPLY","LED_FEED");
        declare(board,"LED1","LED",PhysicalPackages.THROUGH_HOLE_LED,"LED_FEED","CTRL_RETURN");
        board.addPowerInput(new ExternalBoardPowerInput("COIL_INPUT","J1.1","J1.2","RAW_INPUT","CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("COMMAND_INPUT","J2.1","J2.2","CMD","CTRL_RETURN"));
        board.setPlacementConstraints(placement(board)); board.validate(); return board;
    }
    private void declare(TroubleshootBoard board,String id,String type,PhysicalPackage physical,String... nets) {
        board.addComponent(new BoardComponent(id,type,physical));
        Vector<String> terminals=physical.getTerminalIds();
        if(terminals.size()!=nets.length) throw new IllegalArgumentException("Manifest terminal count mismatch");
        for(int i=0;i<nets.length;i++) board.addPad(new BoardPad(id+"."+terminals.get(i),id,terminals.get(i),nets[i]));
    }
    PcbPlacementConstraints placement(TroubleshootBoard board) {
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        for(String id:board.getComponentIds()) {
            String region=(id.equals("J1")||id.equals("F1")||id.equals("DREV")||id.equals("C1"))?"entry":
                (id.equals("J2")||id.equals("RIN")||id.equals("C2")||id.equals("RBIAS"))?"input":
                (id.equals("LED1")||id.equals("RLED"))?"status":"output";
            String label=region.equals("entry")?"Power entry":region.equals("input")?"Control input":
                region.equals("status")?"Power indicator":"Switched output";
            PcbPlacementConstraints.Anchor anchor=id.equals("J1")||id.equals("J2")?PcbPlacementConstraints.Anchor.LEFT:
                id.equals("J4")?PcbPlacementConstraints.Anchor.RIGHT:PcbPlacementConstraints.Anchor.NONE;
            parts.add(new PcbPlacementConstraints.Part(id,region,label,"low-voltage",anchor,20));
        }
        return new PcbPlacementConstraints(parts,new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.BOTTOM);
    }
}
