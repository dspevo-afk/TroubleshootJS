package com.lushprojects.circuitjs1.client;
/** Literal old raw baseline and control equality; no routing. */
public final class PcbPassiveSizingRawContractTest {
    public static void main(String[] args) { run(42L,"PCB passive sizing raw42 contracts "); }
    static void run(long seed,String marker) {
        if(seed!=42L && seed!=-1022L && seed!=4L)throw new AssertionError("Fixed raw roots only");
        Rb56Plan saved=PcbPassivePoseTrialSourceContractTest.bound(seed,15),control=PcbPassiveSizingSourceContractTest.bound(seed);
        TroubleshootBoard oldBoard=saved.board(),newBoard=control.board();
        String oldGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(oldBoard);
        String newGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(newBoard);
        PcbPassiveSizingSourceContractTest.check(oldGraph.equals(newGraph),"paired raw source graphs exact");
        for(int attempt:new int[]{0,2,3,4,5}) {
            PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbPlacementPlanner.Plan a=planner.plan(oldBoard,oldBoard.getPlacementConstraints(),saved.layoutSeed,attempt);
            PcbPlacementPlanner.Plan b=planner.plan(newBoard,newBoard.getPlacementConstraints(),control.layoutSeed,attempt);
            String first=PlayerSessionFingerprint.of(a.materialize().geometryFingerprint());
            String second=PlayerSessionFingerprint.of(b.materialize().geometryFingerprint());
            PcbPassiveSizingSourceContractTest.check(first.equals(second) && a.evaluations==b.evaluations &&
                a.demandArea==b.demandArea && a.courtyardArea==b.courtyardArea && a.candidate==b.candidate,
                "untouched candidate geometry/work/demand exact");
            System.out.println("PCB_SIZING_CONTROL_RAW {\"seed\":\""+seed+"\",\"attempt\":"+attempt+
                ",\"geometry15\":\""+first+"\",\"geometry17\":\""+second+"\",\"evaluations15\":"+a.evaluations+
                ",\"evaluations17\":"+b.evaluations+",\"demand15\":"+a.demandArea+",\"demand17\":"+b.demandArea+
                ",\"courtyard15\":"+a.courtyardArea+",\"courtyard17\":"+b.courtyardArea+"}");
        }
        PcbPassiveSizingSourceContractTest.check(oldGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(oldBoard)) &&
            newGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(newBoard)),"raw generation leaves source graphs unchanged");
        System.out.println("PASS: "+marker+"rows=5 routes=0");
    }
}
