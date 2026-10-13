package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Six predeclared roots; actual routed locality without a packing optimum claim. */
public final class PcbFunctionalGroupingContractTest {
    private static final long[] ROOTS={-1017L,-1004L,-1022L,0L,17L,42L};
    private static final int MEASURED_VERSION=4;
    private static final int[] PART_COUNTS={40,44,60,42,53,54};
    private static final int[] PAD_COUNTS={96,104,143,100,129,131};
    private static final String[] SAVED3_GEOMETRY_SHA256={
        "3cf61944fa5f2002304a32b9061b71691c3e20f1942d7cb1e65a698f221938ef",
        "e682dbd52e8478c1979814502a43713f6be0047517324e54ad0d746b110648a1",
        "eeadd722d90c3f407dd5889caaff11d7fbe0e02d81f3dccf131322bcd6ee4f16",
        "cc397a2f30ca51618bf1dd966bd074ebc34f9025b662d08cd7264b53b13f1bf5",
        "80492fd9cfbf32da38a233f23cf04595f2493c49bde9be1f22448a77cfb80bd8",
        "69a337a3e3dced0cd519b71c6929fef8b11707834bbf580bc0c909351679d493"
    };
    private static int checks;
    public static void main(String[] args) {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,MEASURED_VERSION);
        System.out.println("PASS: PCB functional grouping contracts roots="+ROOTS.length+" assertions="+checks);
    }
    /** Separate existing 60-second JVM bound for the six compatibility cases. */
    static void verifySavedRealismGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,3);
        System.out.println("PASS: saved placement3 geometry contracts roots="+ROOTS.length+" assertions="+checks);
    }
    private static void measure(long seed,int index,int version) {
        Rb56Plan plan=Rb56Plan.resolve(seed,version);
        TroubleshootBoard board=plan.board();
        MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
        if(!result.accepted()) {
            throw new AssertionError("accepted baseline route lost: "+seed+" "+result.toCanonical());
        }
        PcbBoardLayout layout=result.getLayout();
        GeneratedPhysicalAdmission admission=MediumBoardNormalAdmission.fromAcceptedRb56Route(
            board,result,plan.layoutSeed,plan.routingSeed);
        admission.requireConstruction(board,layout);
        check(board.getComponentIds().size()==PART_COUNTS[index],"literal representative part census");
        check(board.getPadIds().size()==PAD_COUNTS[index],"literal representative pad census");
        check(board.getPlacementConstraints().requiresTwoLayerRouting,"RB56 current placement requires P07");
        layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
        new PcbTwoLayerRules(board,layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        check(layout.getComponents().size()==board.getComponentIds().size(),"every component retained");
        check(layout.getPads().size()==board.getPadIds().size(),"every pad retained");
        TreeSet<String> pads=new TreeSet<String>();
        for(PcbPadPlacement pad:layout.getPads()) {
            check(pads.add(pad.getPadId()),"unique physical pad");
        }
        check(pads.equals(new TreeSet<String>(board.getPadIds())),"exact pad census");
        long regionArea=0,regionPairs=0,regionSpan=0,directNetSpan=0;
        TreeMap<String,Rectangle> regions=new TreeMap<String,Rectangle>();
        Vector<PcbComponentPlacement> parts=layout.getComponents();
        for(int i=0;i<parts.size();i++) {
            PcbComponentPlacement a=parts.get(i);
            PcbPlacementConstraints.Part da=board.getPlacementConstraints().get(a.getComponentId());
            Rectangle court=a.getRoutingCourtyard();String key=da.domainId+"/"+da.regionId;
            Rectangle prior=regions.get(key);
            if(prior==null)regions.put(key,court);else {
                int x=Math.min(prior.x,court.x),y=Math.min(prior.y,court.y);
                regions.put(key,new Rectangle(x,y,Math.max(prior.x+prior.width,court.x+court.width)-x,
                    Math.max(prior.y+prior.height,court.y+court.height)-y));
            }
            if(a.getPhysicalPackage().isConnector()) {
                Rectangle outline=layout.getBoardOutline();
                check(a.getX()>=outline.x&&a.getX()+a.getWidth()<=outline.x+outline.width,"connector inside board");
            }
            for(int j=i+1;j<parts.size();j++) {
                PcbComponentPlacement b=parts.get(j);
                PcbPlacementConstraints.Part db=board.getPlacementConstraints().get(b.getComponentId());
                if(da.domainId.equals(db.domainId)&&da.regionId.equals(db.regionId)) {
                    regionPairs++;regionSpan+=Math.abs((a.getX()*2+a.getWidth())-(b.getX()*2+b.getWidth()))+
                        Math.abs((a.getY()*2+a.getHeight())-(b.getY()*2+b.getHeight()));
                }
            }
        }
        for(Rectangle region:regions.values())regionArea+=(long)region.width*region.height;
        int directNets=0;
        for(String id:board.getNetIds()) {
            Vector<String> netPads=board.getNet(id).getPadIds();
            if(netPads.size()!=2)continue;
            PcbPadPlacement a=layout.getPad(netPads.get(0)),b=layout.getPad(netPads.get(1));
            if(board.getPad(a.getPadId()).getComponentId().equals(board.getPad(b.getPadId()).getComponentId()))continue;
            directNets++;directNetSpan+=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY());
        }
        long copperLength=0;int segments=0;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            if(trace.getStartPadId()!=null)
                check(board.getPad(trace.getStartPadId()).getNetId().equals(trace.getNetId()),"start endpoint net binding");
            if(trace.getEndPadId()!=null)
                check(board.getPad(trace.getEndPadId()).getNetId().equals(trace.getNetId()),"end endpoint net binding");
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) {
                copperLength+=Math.abs(x[i]-x[i-1])+Math.abs(y[i]-y[i-1]);segments++;
            }
        }
        if(version==3) check(SAVED3_GEOMETRY_SHA256[index].equals(
            PlayerSessionFingerprint.of(layout.geometryFingerprint())),
            "published saved3 geometry remains byte-exact: "+seed);
        Rectangle outline=layout.getBoardOutline();MediumBoardPhysicalPolicy.Statistics stats=result.getStatistics();
        if(version==4) System.out.println("PCB_GROUPING_ROUTE_CASE seed="+seed+" "+result.toCanonical());
        System.out.println("PCB_GROUPING_CASE {\"seed\":\""+seed+"\",\"version\":"+version+
            ",\"accepted\":true,\"parts\":"+parts.size()+",\"pads\":"+pads.size()+
            ",\"width\":"+outline.width+",\"height\":"+outline.height+",\"area\":"+((long)outline.width*outline.height)+
            ",\"courtyardArea\":"+layout.getRoutingCourtyardArea()+",\"regionBoundingAreaSum\":"+regionArea+
            ",\"sameRegionCenterManhattanMean\":"+(regionPairs==0?0:regionSpan/(2.0*regionPairs))+
            ",\"twoPadNetSpanSum\":"+directNetSpan+",\"twoPadNetCount\":"+directNets+
            ",\"copperManhattanLength\":"+copperLength+",\"traceSegments\":"+segments+
            ",\"vias\":"+layout.getHoles().size()+",\"selectedAttempt\":"+stats.selectedPlacementAttempt+
            ",\"routingExpansions\":"+stats.routingExpansions+
            ",\"geometrySha256\":\""+PlayerSessionFingerprint.of(layout.geometryFingerprint())+"\"}");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
