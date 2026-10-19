package com.lushprojects.circuitjs1.client;

/** Fixed informed corpus; actual native15/20 generation and physical admission. */
public final class PcbRoutedTraceGuardTrialContractTest {
    private static final long[] ROOTS={-1017L,-1004L,-1022L,0L,17L,42L,1L,7L,5L,14L,2L,3L,4L,6L};
    public static void main(String[] args) {
        runSeeds(new long[]{-1017L,-1004L}, "PCB routed trace guard trial contracts ");
    }
    static void runSeeds(long[] seeds,String marker) {
        need(seeds.length==2 && seeds[0]!=seeds[1],"two distinct roots per bounded JVM");
        for(long seed:seeds) {
            boolean known=false;for(long root:ROOTS)if(seed==root)known=true;
            need(known,"fixed informed root");
            Rb56Plan saved=Rb56Plan.resolve(seed,15),trial=Rb56Plan.resolve(seed,20);
            need(saved.layoutSeed==trial.layoutSeed && saved.routingSeed==trial.routingSeed,
                "same exact signed-long placement and routing concerns");
            need(PcbCanvasBoundaryContractTest.liveGraphSha256(saved.board()).equals(
                PcbCanvasBoundaryContractTest.liveGraphSha256(trial.board())),"same source graph");
            for(int version:new int[]{15,20}) {
                String row=PcbPostSelectionEdgeTrimContractTest.observeTraceGuard(seed,version);
                String prefix="PCB_EDGE_TRIM_CASE ";
                need(row.startsWith(prefix),"complete existing physical witness receipt");
                System.out.println("PCB_TRACE_GUARD_CASE "+row.substring(prefix.length()));
            }
        }
        System.out.println("PASS: "+marker+"roots=2 pairs=2 rows=4 evaluator=compactRouted");
    }
    private static void need(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }
}
