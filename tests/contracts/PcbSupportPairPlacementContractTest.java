package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Small independent nets/pins oracle; no generated-layout golden blessing. */
public final class PcbSupportPairPlacementContractTest {
    private static int checks;
    private static void check(boolean ok,String reason) {checks++;if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args) {
        TroubleshootBoard board=new TroubleshootBoard("support-pair-fixture");
        for(String net:new String[]{"INPUT","OUTPUT","RETURN","ENABLE","OTHER"})board.addNet(new BoardNet(net));
        add(board,"U", "REGULATOR",PhysicalPackages.TO220_REGULATOR_4,
            new String[]{"INPUT","OUTPUT","RETURN","ENABLE"});
        add(board,"R", "RESISTOR",PhysicalPackages.AXIAL_RESISTOR,new String[]{"OUTPUT","RETURN"});
        add(board,"S", "RESISTOR",PhysicalPackages.AXIAL_RESISTOR,new String[]{"OUTPUT","OTHER"});
        add(board,"T", "RESISTOR",PhysicalPackages.AXIAL_RESISTOR,new String[]{"OUTPUT","OUTPUT"});
        add(board,"L", "LED",PhysicalPackages.THROUGH_HOLE_LED,new String[]{"OUTPUT","RETURN"});
        add(board,"A", "RESISTOR",PhysicalPackages.AXIAL_RESISTOR,new String[]{"OUTPUT","RETURN"});
        add(board,"C", "CAPACITOR",PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,new String[]{"OUTPUT","RETURN"});
        add(board,"J", "CONNECTOR",PhysicalPackages.DEV_CANARY_CONNECTOR_4,
            new String[]{"INPUT","OUTPUT","RETURN","ENABLE"});
        add(board,"V", "REGULATOR",PhysicalPackages.TO220_REGULATOR_4,
            new String[]{"INPUT","OUTPUT","RETURN","ENABLE"});
        PcbPlacementConstraints constraints=board.getPlacementConstraints();
        PcbFootprint r=PcbFootprint.fromPhysicalPackage(board.getComponent("R"),300,700);
        PcbFootprint u=PcbFootprint.fromPhysicalPackage(board.getComponent("U"),1000,2000);
        Vector<PcbFootprint> placed=new Vector<PcbFootprint>();placed.add(u);
        String original=r.geometryFingerprint(),owner=u.geometryFingerprint();
        PcbSupportPairPlacement.Pair pair=PcbSupportPairPlacement.find(board,constraints,r,placed,new Point(0,0));
        check(pair!=null && pair.ownerId.equals("U"),"two distinct paired nets discover the placed package");
        check(pair.firstPadId.equals("R.1") && pair.secondPadId.equals("R.2"),"logical pad identities retained");
        check(pair.firstAnchor.getPadId().equals("U.OUTPUT") && pair.secondAnchor.getPadId().equals("U.RETURN"),
            "actual corresponding chip pins, not its rectangle centre");
        check(pair.target.x==1000 && pair.target.y==2120,"independent local-pad offset arithmetic");
        check(pair.endpointSpan(r.translated(1000,2120))==120,"literal endpoint distance retains lead spacing");
        check(PcbSupportPairPlacement.find(board,constraints,
            PcbFootprint.fromPhysicalPackage(board.getComponent("C"),0,0),placed,new Point(0,0))!=null,
            "capacitor and resistor both produce proximity candidates without assigning an electrical role");
        PcbSupportPairPlacement.Pair zero=PcbSupportPairPlacement.find(board,constraints,r.translated(0,0),placed,new Point(0,0));
        check(zero.target.x==pair.target.x && zero.target.y==pair.target.y,"source translation does not change target");
        check(PcbSupportPairPlacement.find(board,constraints,r,new Vector<PcbFootprint>(),new Point(0,0))==null,
            "unplaced package creates no invented target");
        for(String id:new String[]{"S","T","L","U"})
            check(PcbSupportPairPlacement.find(board,constraints,PcbFootprint.fromPhysicalPackage(board.getComponent(id),0,0),
                placed,new Point(0,0))==null,"reject single-net, same-net, status and multi-terminal source: "+id);
        check(original.equals(r.geometryFingerprint()) && owner.equals(u.geometryFingerprint()),"pure relation lookup");
        Vector<PcbFootprint> unsuitable=new Vector<PcbFootprint>();
        unsuitable.add(PcbFootprint.fromPhysicalPackage(board.getComponent("A"),1000,2000));
        unsuitable.add(PcbFootprint.fromPhysicalPackage(board.getComponent("J"),1000,2000));
        check(PcbSupportPairPlacement.find(board,constraints,r,unsuitable,new Point(0,0))==null,
            "paired two-terminal and connector peers are ineligible");
        PcbFootprint v=PcbFootprint.fromPhysicalPackage(board.getComponent("V"),1000,2000);
        placed.add(v);
        check(PcbSupportPairPlacement.find(board,constraints,r,placed,new Point(0,0)).ownerId.equals("U"),
            "equal geometry ties use canonical owner identity");
        Vector<PcbFootprint> reversed=new Vector<PcbFootprint>();reversed.add(v);reversed.add(u);
        check(PcbSupportPairPlacement.find(board,constraints,r,reversed,new Point(0,0)).ownerId.equals("U"),
            "placed-vector ordering cannot change a tie");
        Vector<PcbPlacementConstraints.Part> demands=new Vector<PcbPlacementConstraints.Part>();
        for(String id:board.getComponentIds()) {
            String region=id.equals("U")?"other":"support";
            demands.add(new PcbPlacementConstraints.Part(id,region,region,"board",PcbPlacementConstraints.Anchor.NONE,20));
        }
        PcbPlacementConstraints grouped=new PcbPlacementConstraints(demands,new Vector<PcbPlacementConstraints.Barrier>());
        check(PcbSupportPairPlacement.find(board,grouped,r,reversed,new Point(0,0)).ownerId.equals("V"),
            "functional region precedes geometric tie-breaking");
        Vector<PcbPlacementConstraints.Part> domains=new Vector<PcbPlacementConstraints.Part>();
        for(String id:board.getComponentIds())domains.add(new PcbPlacementConstraints.Part(id,"all","all",
            id.equals("U")?"other-domain":"board",PcbPlacementConstraints.Anchor.NONE,20));
        PcbPlacementConstraints separated=new PcbPlacementConstraints(domains,new Vector<PcbPlacementConstraints.Barrier>());
        Vector<PcbFootprint> foreign=new Vector<PcbFootprint>();foreign.add(u);
        check(PcbSupportPairPlacement.find(board,separated,r,foreign,new Point(0,0))==null,
            "electrical pairing cannot override mismatched terminal physical domains");
        System.out.println("PASS: PCB support pair contracts assertions="+checks);
    }
    private static void add(TroubleshootBoard board,String id,String type,PhysicalPackage physical,String[] nets) {
        board.addComponent(new BoardComponent(id,type,physical));
        Vector<String> terminals=physical.getTerminalIds();
        for(int i=0;i<nets.length;i++)board.addPad(new BoardPad(id+"."+terminals.get(i),id,terminals.get(i),nets[i]));
    }
}
