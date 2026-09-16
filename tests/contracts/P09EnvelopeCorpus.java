package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.TreeSet;

/** Predeclared calibration, then held-out physical-only qualification. */
public final class P09EnvelopeCorpus {
    static final long[] REGRESSION={0,1,2,3,17,42,101,-1,9007199254740993L,Long.MIN_VALUE,Long.MAX_VALUE};
    static final long[] HELD_OUT={11,23,37,59,83,127,251,509,-11,-23,-127,-509};
    public static void main(String[] args) {
        CirSim sim=new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        for(String family:PlayerFamilyCatalog.families()) {
            long start=System.nanoTime();
            GeneratedBoardInstance owner=new PlayerLaunchRequest(family,"0",
                PlayerFamilyCatalog.candidateProfile(family).name()).generation()
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
            row("calibration",family,0,owner.getBoard(),owner.getPcbLayout(),start);
        }
        for(long seed:REGRESSION) {
            long start=System.nanoTime(); Rb15Plan plan=Rb15Plan.resolve(seed);
            TroubleshootBoard board=plan.board();
            PcbBoardLayout layout=new SeededPcbLayoutGenerator().generate(board,plan.layoutSeed,plan.routingSeed);
            row("regression",Rb15Plan.FAMILY_ID,seed,board,layout,start);
        }
        int heldPass=0,heldReject=0;
        for(long seed:HELD_OUT) {
            long start=System.nanoTime(); Rb15Plan plan=Rb15Plan.resolve(seed);
            TroubleshootBoard board=plan.board();
            PcbBoardLayout attempted=null;
            try {
                PcbBoardLayout layout=new SeededPcbLayoutGenerator().generate(board,plan.layoutSeed,plan.routingSeed);
                attempted=layout;
                SupportedEnvelope.current().requireBounds(board,layout);
                layout.validateGeometry(board);
                PcbBoardLayout repeat=new SeededPcbLayoutGenerator().generate(board,plan.layoutSeed,plan.routingSeed);
                if(!layout.geometryFingerprint().equals(repeat.geometryFingerprint())) throw new AssertionError("Held-out replay changed");
                row("held-out",Rb15Plan.FAMILY_ID,seed,board,layout,start); heldPass++;
            } catch(PcbRoutingRejectedException rejected) {
                rejected(seed,"ROUTING_"+rejected.getKind(),start); heldReject++;
            } catch(SupportedEnvelope.Rejected rejected) {
                rejected(seed,"ENVELOPE_"+rejected.reason,start); heldReject++;
                Rectangle r=attempted.getBoardOutline(); PcbRouteMetrics m=PcbRouteMetrics.measure(attempted.getTraces());
                System.out.println("P09_REJECT_METRICS {\"seed\":\""+seed+"\",\"width\":"+r.width+",\"height\":"+r.height+
                    ",\"uniqueLength\":"+m.uniqueLength+",\"score\":"+attempted.getRouteQualityScore(board)+"}");
            }
        }
        if(heldPass+heldReject!=HELD_OUT.length) throw new AssertionError("Missing held-out rows");
        System.out.println("PASS: P09 physical envelope corpus rows=32 heldPass="+heldPass+" heldReject="+heldReject);
    }
    private static void rejected(long seed,String reason,long start) {
        System.out.println("P09_ROW {\"cohort\":\"held-out\",\"seed\":\""+seed+"\",\"outcome\":\""+reason+
            "\",\"elapsedMs\":"+((System.nanoTime()-start)/1000000.0)+"}");
    }
    static void row(String cohort,String family,long seed,TroubleshootBoard board,PcbBoardLayout layout,long start) {
        SupportedEnvelope.current().requireBounds(board,layout);
        long checkStart=System.nanoTime(); layout.validateGeometry(board); long validationNs=System.nanoTime()-checkStart;
        Rectangle r=layout.getBoardOutline();
        int degree=0,padFloor=Integer.MAX_VALUE,probeFloor=Integer.MAX_VALUE;
        TreeSet<String> domains=new TreeSet<String>(); TreeMap<String,Integer> packages=new TreeMap<String,Integer>();
        for(String net:board.getNetIds()) degree=Math.max(degree,board.getNet(net).getPadIds().size());
        for(PcbPlacementConstraints.Part p:board.getPlacementConstraints().getParts()) domains.add(p.domainId);
        for(PcbComponentPlacement p:layout.getComponents()) {
            String key=p.getPhysicalPackage().getId()+"/"+p.getMountingSide()+"/"+p.getRotation();
            Integer old=packages.get(key); packages.put(key,old==null?1:old+1);
        }
        for(PcbPadPlacement p:layout.getPads()) {
            padFloor=Math.min(padFloor,Math.min(p.getPadBounds().width,p.getPadBounds().height));
            probeFloor=Math.min(probeFloor,Math.min(p.getProbeBounds().width,p.getProbeBounds().height));
            if(!PcbCopperAccess.canProbe(p,PcbBoardSide.BOTTOM)) throw new AssertionError("Inaccessible solder pad "+p.getPadId());
        }
        PcbRouteMetrics m=PcbRouteMetrics.measure(layout.getTraces());
        System.out.println("P09_ROW {\"cohort\":\""+cohort+"\",\"family\":\""+family+"\",\"seed\":\""+seed+
            "\",\"outcome\":\"BOUNDS_GEOMETRY_PASS\",\"validationNs\":"+validationNs+",\"heapUsedBytes\":"+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+",\"parts\":"+board.getComponentIds().size()+",\"pads\":"+board.getPadIds().size()+",\"nets\":"+board.getNetIds().size()+
            ",\"layer\":\""+board.getPlacementConstraints().routingLayer+"\",\"width\":"+r.width+",\"height\":"+r.height+",\"area\":"+((long)r.width*r.height)+
            ",\"degree\":"+degree+",\"domains\":"+domains.size()+",\"barriers\":"+board.getPlacementConstraints().getBarriers().size()+
            ",\"padFloor\":"+padFloor+",\"probeFloor\":"+probeFloor+",\"segments\":"+m.segments+",\"uniqueLength\":"+m.uniqueLength+
            ",\"score\":"+layout.getRouteQualityScore(board)+",\"placements\":"+layout.getGenerationPlacementAttempts()+
            ",\"routes\":"+layout.getGenerationRoutingAttempts()+",\"expansions\":"+layout.getGenerationRoutingExpansions()+
            ",\"elapsedMs\":"+((System.nanoTime()-start)/1000000.0)+",\"packages\":\""+packages+"\"}");
    }
}
