package com.lushprojects.circuitjs1.client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Vector;

/** Frozen physical-only matched comparison, runnable against the untouched pre-P05 router. */
public final class P05RoutingCorpus {
    static final SeededPcbLayoutGenerator.AttemptObserver OBSERVER=new SeededPcbLayoutGenerator.AttemptObserver() {
        public void check(int attempt) { }
    };
    static TroubleshootBoard board(String family,long seed) throws Exception {
        return family.equals("rb15")?Rb15Plan.resolve(seed).board():P04RoutingContractTest.board(family);
    }
    static PcbPlacementPlanner.Plan plan(String family,long seed,int attempt,TroubleshootBoard board) {
        long placement=family.equals("rb15")?Rb15Plan.resolve(seed).layoutSeed:seed;
        return new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry()).plan(board,
            board.getPlacementConstraints(),placement,attempt);
    }
    static long routingSeed(String family,long seed) { return family.equals("rb15")?Rb15Plan.resolve(seed).routingSeed:seed; }
    static String identity(TroubleshootBoard board,PcbBoardLayout layout) throws Exception {
        StringBuilder s=new StringBuilder(layout.getBoardOutline().toString());
        Vector<String> nets=board.getNetIds(); Collections.sort(nets);
        for(String id:nets) {Vector<String> pads=board.getNet(id).getPadIds();Collections.sort(pads);s.append(id).append(pads);}
        Vector<String> parts=board.getComponentIds();Collections.sort(parts);
        for(String id:parts)s.append(layout.getComponent(id).geometryFingerprint());
        Vector<String> pads=board.getPadIds();Collections.sort(pads);
        for(String id:pads)s.append(layout.getPad(id).geometryFingerprint());
        return hash(s.toString());
    }
    static String hash(String value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b&255));return out.toString();
    }
    public static void main(String[] args) throws Exception {
        int rows=0,successes=0;
        BufferedReader input=new BufferedReader(new FileReader("tests/benchmarks/p05-routing-corpus.tsv"));
        try {
            String line=input.readLine();
            while((line=input.readLine())!=null) {
                String[] f=line.split("\t");String family=f[0],cohort=f[1];long seed=Long.parseLong(f[2]);int attempt=Integer.parseInt(f[3]);
                String id=family+"/"+seed+"/"+attempt;
                TroubleshootBoard board=board(family,seed);PcbBoardLayout layout=null;
                String outcome="SUCCESS",detail="",identity="";long elapsed=0;double score=0;
                PcbRouteMetrics metrics=null;
                try {
                    PcbPlacementPlanner.Plan plan=plan(family,seed,attempt,board);layout=plan.materialize();identity=identity(board,layout);
                    long start=System.nanoTime();
                    try {PcbNetRouter.route(layout,board,plan.outline,attempt,OBSERVER,true,routingSeed(family,seed),0,null);}
                    finally {elapsed=System.nanoTime()-start;}
                    Method labels=SeededPcbLayoutGenerator.class.getDeclaredMethod("placeSilkscreen",PcbBoardLayout.class,TroubleshootBoard.class,Rectangle.class);
                    labels.setAccessible(true);labels.invoke(new SeededPcbLayoutGenerator(),layout,board,plan.outline);
                    layout.validateGeometry(board);
                    if(!identity.equals(identity(board,layout))) throw new AssertionError("Router changed placement/topology");
                    layout.captureConductorGraph(board).pristine().requirePristineNetConnectivity(board);
                    metrics=PcbRouteMetrics.measure(layout.getTraces());score=layout.getRouteQualityScore(board);successes++;
                } catch(PcbPlacementPlanner.Rejected rejected) {outcome="PLACEMENT";detail=rejected.getMessage();}
                catch(PcbNetRouter.Rejected rejected) {outcome="ROUTING";detail=rejected.getMessage();}
                catch(PcbBoardLayout.RouteQualityRejectedException rejected) {outcome="QUALITY";detail=rejected.getMessage();}
                String work="";
                try {Method method=PcbBoardLayout.class.getDeclaredMethod("getRoutingRecoveryStatistics");method.setAccessible(true);
                    Object stats=layout==null?null:method.invoke(layout);
                    if(stats!=null) {Method canonical=stats.getClass().getDeclaredMethod("toCanonical");canonical.setAccessible(true);work=(String)canonical.invoke(stats);}
                }catch(NoSuchMethodException beforeP05) { }
                Rectangle outline=layout==null?null:layout.getBoardOutline();rows++;
                System.out.println("P05_CORPUS {\"id\":\""+id+"\",\"cohort\":\""+cohort+"\",\"identity\":\""+identity+
                    "\",\"outcome\":\""+outcome+"\",\"detail\":\""+escape(detail)+"\",\"expansions\":"+(layout==null?0:layout.getRoutingExpansions())+
                    ",\"length\":"+(metrics==null?0:metrics.uniqueLength)+",\"bends\":"+(metrics==null?0:metrics.bends)+",\"score\":"+score+
                    ",\"area\":"+(outline==null?0:(long)outline.width*outline.height)+",\"elapsedMs\":"+(elapsed/1000000.0)+
                    ",\"work\":\""+escape(work)+"\"}");
            }
        }finally {input.close();}
        System.out.println("PASS: P05 frozen corpus rows="+rows+" successes="+successes);
    }
    private static String escape(String text) {return text.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
}
