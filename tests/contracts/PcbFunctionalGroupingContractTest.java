package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Six predeclared roots; actual routed locality without a packing optimum claim. */
public final class PcbFunctionalGroupingContractTest {
    private static final long[] ROOTS={-1017L,-1004L,-1022L,0L,17L,42L};
    private static final int MEASURED_VERSION=11;
    private static final int[] PART_COUNTS={40,44,60,42,53,54};
    private static final int[] PAD_COUNTS={96,104,143,100,129,131};
    // Independent accepted6 receipts, frozen before guarded route selection.
    private static final long[] SAVED6_AREA={5122184,4622400,8443994,4679776,7708262,6371585};
    private static final long[] SAVED7_AREA={5122184,4622400,7395541,4679776,7708262,6371585};
    private static final long[] SAVED8_AREA={4529785,4466664,6943752,4679776,5814368,6211431};
    private static final long[] SAVED9_AREA={4529785,4466664,6943752,4358809,5814368,6211431};
    private static final String[] SAVED8_GEOMETRY_SHA256={
        "0f557eee2670d3ea4d963dc4cd4797d908b0af33e405db1f86e4528dfb0c1cba",
        "d349957427274ae74726d07034409619d3a43e8e571b756622100dd2d7d2438d",
        "1216897c60853a35008c3d2fd62965d6489ad997c2e3a62cb578b081dedd5be9",
        "58dbf1b3362e9921b821ec3084119e76b4c3a3e84b181dd618ba5dabce8417dd",
        "2e5a36d464418c7411b68055d408d17867012eaec011c8e21e5d5f4da7caee32",
        "3be6941e60168182fdd377ed938fe298b32d56037e3c3f30d4a22ee39fb79324"
    };
    private static final String[] SAVED6_GEOMETRY_SHA256={
        "bb9e6ec7d38d1453288c3d1c69ba58711b54e89f6cf3d34c3948fbd8a2c6c60b",
        "4f6d4aaaa43eb26ebc75e869294d7402718c69900f1566b8a714c8e2512c9d28",
        "27d29b461e8fa664fc73d0b0022d6bad41b11eb5d9191abd5d4bf1f40f49827e",
        "58dbf1b3362e9921b821ec3084119e76b4c3a3e84b181dd618ba5dabce8417dd",
        "d3bf299dab5253da1674c2e628ba68a1116f659951cf909fe077f4a568dd9772",
        "5fb11869fc9eeb56461666e7dbfb53b97ae8a2cff868daebaaf7c1edb36dcd16"
    };
    private static final String[] SAVED3_GEOMETRY_SHA256={
        "3cf61944fa5f2002304a32b9061b71691c3e20f1942d7cb1e65a698f221938ef",
        "e682dbd52e8478c1979814502a43713f6be0047517324e54ad0d746b110648a1",
        "eeadd722d90c3f407dd5889caaff11d7fbe0e02d81f3dccf131322bcd6ee4f16",
        "cc397a2f30ca51618bf1dd966bd074ebc34f9025b662d08cd7264b53b13f1bf5",
        "80492fd9cfbf32da38a233f23cf04595f2493c49bde9be1f22448a77cfb80bd8",
        "69a337a3e3dced0cd519b71c6929fef8b11707834bbf580bc0c909351679d493"
    };
    private static final String[] SAVED4_GEOMETRY_SHA256={
        "811c741b4d669b573757ede79a36d6fb2d552106bb04f3c3000926295fd89fdf",
        "fc91af896d33ec54d747f49a65c5b280760fc17c58069e58cede05316df4f573",
        "eeadd722d90c3f407dd5889caaff11d7fbe0e02d81f3dccf131322bcd6ee4f16",
        "5580137419342f8f512876bce1a43b8eeef10666f2f437d58a3c41a1e4bd11e2",
        "d3bf299dab5253da1674c2e628ba68a1116f659951cf909fe077f4a568dd9772",
        "99ced1b9f1efd568aa178f99f7b35c703dda8c890b91cf83112ab0d4c0d65044"
    };
    private static final String[] SAVED5_GEOMETRY_SHA256={
        "811c741b4d669b573757ede79a36d6fb2d552106bb04f3c3000926295fd89fdf",
        "fc91af896d33ec54d747f49a65c5b280760fc17c58069e58cede05316df4f573",
        "27d29b461e8fa664fc73d0b0022d6bad41b11eb5d9191abd5d4bf1f40f49827e",
        "5580137419342f8f512876bce1a43b8eeef10666f2f437d58a3c41a1e4bd11e2",
        "d3bf299dab5253da1674c2e628ba68a1116f659951cf909fe077f4a568dd9772",
        "5fb11869fc9eeb56461666e7dbfb53b97ae8a2cff868daebaaf7c1edb36dcd16"
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
    static void verifySavedDensityGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,4);
        System.out.println("PASS: saved placement4 geometry contracts roots="+ROOTS.length+" assertions="+checks);
    }
    static void verifyFocusedSingleGroup() {
        for(int index:new int[]{1,3}) measure(ROOTS[index],index,MEASURED_VERSION);
        System.out.println("PASS: focused single-group density contracts roots=2 assertions="+checks);
    }
    static void verifySavedLargeGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,5);
        System.out.println("PASS: saved placement5 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedSingleGroupGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,6);
        System.out.println("PASS: saved placement6 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedGuardedGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,7);
        System.out.println("PASS: saved placement7 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedNarrowGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,8);
        System.out.println("PASS: saved placement8 geometry contracts roots=6 assertions="+checks);
    }
    static void verifySavedConnectedGeometry() {
        for(int index=0;index<ROOTS.length;index++) measure(ROOTS[index],index,9);
        System.out.println("PASS: saved placement9 geometry contracts roots=6 assertions="+checks);
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
        if(version>=4) reportWhitespace(board,layout,seed,version);
        Rectangle outline=layout.getBoardOutline();MediumBoardPhysicalPolicy.Statistics stats=result.getStatistics();
        if(version>=4) System.out.println("PCB_GROUPING_ROUTE_CASE seed="+seed+" "+result.toCanonical());
        if(version==6) check(SAVED6_GEOMETRY_SHA256[index].equals(
            PlayerSessionFingerprint.of(layout.geometryFingerprint())),"saved6 geometry stays byte-exact: "+seed);
        if(version==7) {
            String expected=index==2?"f4ea1023da1da851f16846ba6535d05742d4d7e6d30ff9ee878028b9cbc9158e":
                SAVED6_GEOMETRY_SHA256[index];
            check(expected.equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
                "saved7 geometry stays byte-exact: "+seed);
            check((long)outline.width*outline.height<=SAVED6_AREA[index],"no representative outline regression: "+seed);
            check(stats.placementCandidates==6 && stats.oneFaceAttempts<=3 && stats.twoLayerAttempts<=3,
                "guarded selection keeps original placement and per-policy route caps");
        }
        if(version==8) check(SAVED8_GEOMETRY_SHA256[index].equals(
            PlayerSessionFingerprint.of(layout.geometryFingerprint())),"saved8 geometry stays byte-exact: "+seed);
        if(version==9) {
            long area=(long)outline.width*outline.height;
            check(area<=SAVED8_AREA[index],"no representative regression against accepted8: "+seed);
            if(index==3) check(area<SAVED8_AREA[index],"connected packing reduces the retained42 fixture");
            else check(SAVED8_GEOMETRY_SHA256[index].equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
                "other representative layouts remain exact accepted8: "+seed);
        }
        if(version==10) {
            long area=(long)outline.width*outline.height;
            check(area<=SAVED9_AREA[index],"no representative regression against accepted9: "+seed);
            if(index==5)check(area<SAVED9_AREA[index],"local-support candidate reduces the declared54 fixture");
            else check((index==3 ? "5d93ab0115d8721826d93f2220ec10707dfe30836b6e31d56f7b068d249b7836" :
                SAVED8_GEOMETRY_SHA256[index]).equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
                "other representative layouts remain exact accepted9: "+seed);
        }
        if(version==11) {
            long area=(long)outline.width*outline.height;
            long baseline=index==5?6103992:SAVED9_AREA[index];
            check(area<=baseline,"no representative regression against accepted10: "+seed);
            if(index==5)check(area<baseline,"vacancy candidate reduces the declared54 fixture");
            else check((index==3 ? "5d93ab0115d8721826d93f2220ec10707dfe30836b6e31d56f7b068d249b7836" :
                SAVED8_GEOMETRY_SHA256[index]).equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
                "other representative layouts remain exact accepted10: "+seed);
        }
        if(version>=8) {
            long area=(long)outline.width*outline.height;
            check(area<=SAVED7_AREA[index],"no representative outline regression against accepted7: "+seed);
            if(index==0 || index==1 || index==2 || index==4 || index==5)
                check(area<SAVED7_AREA[index],"narrow alternatives improve the declared density cases: "+seed);
            check(stats.placementCandidates==6 && stats.oneFaceAttempts<=3 && stats.twoLayerAttempts<=3,
                "narrow exploration retains original placement and per-policy route caps");
        }
        if(version==5 && (index==0 || index==1 || index==3))
            check(SAVED4_GEOMETRY_SHA256[index].equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
                "published single-group40/44/42 geometry must not regress: "+seed);
        if(version==5) check(SAVED5_GEOMETRY_SHA256[index].equals(
            PlayerSessionFingerprint.of(layout.geometryFingerprint())),
            "published saved5 geometry remains byte-exact: "+seed);
        if(version==4) check(SAVED4_GEOMETRY_SHA256[index].equals(
            PlayerSessionFingerprint.of(layout.geometryFingerprint())),
            "published saved4 geometry remains byte-exact: "+seed);
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
    /** Drawing-space sampling, not a statement that free cells are packable. */
    static void reportWhitespace(TroubleshootBoard board,PcbBoardLayout layout,long seed,int version) {
        Rectangle outline=layout.getBoardOutline();int step=10,cols=outline.width/step,rows=outline.height/step;
        check(cols>0&&rows>0&&(long)cols*rows<=250000,"bounded whitespace sampling");
        boolean[] blocked=new boolean[cols*rows];
        for(PcbComponentPlacement part:layout.getComponents()) {
            Vector<PcbPadPlacement> pads=new Vector<PcbPadPlacement>();
            for(String id:board.getComponent(part.getComponentId()).getPadIds())pads.add(layout.getPad(id));
            PcbFootprint footprint=new PcbFootprint(part,pads);
            Rectangle envelope=PcbPlacementPlanner.envelope(footprint,
                board.getPlacementConstraints().get(part.getComponentId()).accessMargin);
            mark(blocked,cols,rows,outline,envelope,step);
        }
        int packageOccupied=0;for(boolean cell:blocked)if(cell)packageOccupied++;
        int guard=PcbTraceRules.MIN_CENTERLINE_CLEARANCE;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++)mark(blocked,cols,rows,outline,new Rectangle(
                Math.min(x[i-1],x[i])-guard,Math.min(y[i-1],y[i])-guard,
                Math.abs(x[i]-x[i-1])+guard*2,Math.abs(y[i]-y[i-1])+guard*2),step);
        }
        for(PcbBoardHole hole:layout.getHoles()) {
            Rectangle r=hole.getBounds();mark(blocked,cols,rows,outline,
                new Rectangle(r.x-guard,r.y-guard,r.width+guard*2,r.height+guard*2),step);
        }
        for(PcbSilkscreenLabel label:layout.getSilkscreenLabels())
            mark(blocked,cols,rows,outline,label.getBounds(),step);
        // Count the actual declared domain isolation strip as reserved, even
        // where the normal front view paints it as bare green substrate.
        TreeMap<String,Rectangle> domains=new TreeMap<String,Rectangle>();
        for(PcbComponentPlacement part:layout.getComponents())
            for(java.util.Map.Entry<String,Rectangle> entry:board.getPlacementConstraints().domainCourtyards(part).entrySet()) {
                Rectangle prior=domains.get(entry.getKey()),r=entry.getValue();
                if(prior==null)domains.put(entry.getKey(),new Rectangle(r.x,r.y,r.width,r.height));
                else {int left=Math.min(prior.x,r.x),top=Math.min(prior.y,r.y);
                    domains.put(entry.getKey(),new Rectangle(left,top,
                        Math.max(prior.x+prior.width,r.x+r.width)-left,
                        Math.max(prior.y+prior.height,r.y+r.height)-top));}
            }
        for(PcbPlacementConstraints.Barrier barrier:board.getPlacementConstraints().getBarriers()) {
            Rectangle a=domains.get(barrier.firstDomain),b=domains.get(barrier.secondDomain);int gap=barrier.clearance;
            Rectangle reserved;
            if(a.x+a.width+gap<=b.x || b.x+b.width+gap<=a.x) {
                int lo=a.x<b.x?a.x+a.width:b.x+b.width,hi=a.x<b.x?b.x:a.x;
                int mid=lo+(hi-lo)/2;reserved=new Rectangle(mid-(gap+1)/2,outline.y,gap,outline.height);
            } else {
                int lo=a.y<b.y?a.y+a.height:b.y+b.height,hi=a.y<b.y?b.y:a.y;
                int mid=lo+(hi-lo)/2;reserved=new Rectangle(outline.x,mid-(gap+1)/2,outline.width,gap);
            }
            mark(blocked,cols,rows,outline,reserved,step);
        }
        int free=0,bestArea=0,bestX=0,bestY=0,bestW=0,bestH=0;
        int[] height=new int[cols],stack=new int[cols+1];
        for(int y=0;y<rows;y++) {
            for(int x=0;x<cols;x++) {boolean occupied=blocked[y*cols+x];if(!occupied)free++;
                height[x]=occupied?0:height[x]+1;}
            int top=-1;
            for(int x=0;x<=cols;x++) {
                int current=x==cols?0:height[x];
                while(top>=0&&height[stack[top]]>current) {
                    int h=height[stack[top--]],left=top<0?0:stack[top]+1,w=x-left;
                    if(h*w>bestArea) {bestArea=h*w;bestX=left;bestY=y-h+1;bestW=w;bestH=h;}
                }
                if(x<cols)stack[++top]=x;
            }
        }
        System.out.println("PCB_WHITESPACE_CASE {\"seed\":\""+seed+"\",\"version\":"+version+
            ",\"parts\":"+layout.getComponents().size()+",\"gridUnits\":10,\"sampledCells\":"+(cols*rows)+
            ",\"packageAccessOccupiedCells\":"+packageOccupied+",\"protectedFreeCells\":"+free+
            ",\"protectedFreeFraction\":"+(free/(double)(cols*rows))+",\"largestProtectedEmptyRectangle\":{\"x\":"+
            (outline.x+bestX*step)+",\"y\":"+(outline.y+bestY*step)+",\"width\":"+(bestW*step)+
            ",\"height\":"+(bestH*step)+",\"area\":"+(bestArea*step*step)+"},\"scope\":\"GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;FREE_IS_NOT_PACKABLE_PROOF\"}");
        // Independent pre-primary trial receipt: 1130x1050 at (60,776).
        // This bounds the dominant empty patch, not a manufacturing packing optimum.
        if(version>=8 && seed==17L)check(bestArea*step*step<=1186500/2,
            "the targeted primary-side void shrinks by at least half");
        if(version>=11 && seed==42L)check(bestArea*step*step<327600,
            "vacancy probes improve the frozen10 protected empty rectangle");
    }
    private static void mark(boolean[] blocked,int cols,int rows,Rectangle outline,Rectangle r,int step) {
        int x0=Math.max(0,(r.x-outline.x)/step),y0=Math.max(0,(r.y-outline.y)/step);
        int x1=Math.min(cols-1,(r.x+r.width-outline.x+step-1)/step),y1=Math.min(rows-1,(r.y+r.height-outline.y+step-1)/step);
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)blocked[y*cols+x]=true;
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
