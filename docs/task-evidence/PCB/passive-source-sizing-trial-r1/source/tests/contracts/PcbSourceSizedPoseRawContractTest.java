package com.lushprojects.circuitjs1.client;
/** Literal old raw baseline and control equality; no routing. */
public final class PcbSourceSizedPoseRawContractTest {
    public static void main(String[] args) { run(42L,"PCB source sized pose raw42 contracts "); }
    static void run(long seed,String marker) {
        if(seed!=42L && seed!=-1022L && seed!=4L)throw new AssertionError("Fixed raw roots only");
        Rb56Plan saved=PcbPassivePoseTrialSourceContractTest.bound(seed,15),control=PcbSourceSizedPoseSourceContractTest.bound(seed);
        TroubleshootBoard oldBoard=saved.board(),newBoard=control.board();
        String oldGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(oldBoard);
        String newGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(newBoard);
        PcbSourceSizedPoseSourceContractTest.check(oldGraph.equals(newGraph),"paired raw source graphs exact");
        for(int attempt:new int[]{0,2,3,4,5}) {
            PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbPlacementPlanner.Plan a=planner.plan(oldBoard,oldBoard.getPlacementConstraints(),saved.layoutSeed,attempt);
            PcbPlacementPlanner.Plan b=planner.plan(newBoard,newBoard.getPlacementConstraints(),control.layoutSeed,attempt);
            String first=PlayerSessionFingerprint.of(a.materialize().geometryFingerprint());
            String second=PlayerSessionFingerprint.of(b.materialize().geometryFingerprint());
            PcbSourceSizedPoseSourceContractTest.check(first.equals(second) && a.evaluations==b.evaluations &&
                a.demandArea==b.demandArea && a.courtyardArea==b.courtyardArea && a.candidate==b.candidate,
                "untouched candidate geometry/work/demand exact");
            System.out.println("PCB_SOURCE_SIZED_POSE_RAW {\"seed\":\""+seed+"\",\"attempt\":"+attempt+
                ",\"geometry15\":\""+first+"\",\"geometry18\":\""+second+"\",\"evaluations15\":"+a.evaluations+
                ",\"evaluations18\":"+b.evaluations+",\"demand15\":"+a.demandArea+",\"demand18\":"+b.demandArea+
                ",\"courtyard15\":"+a.courtyardArea+",\"courtyard18\":"+b.courtyardArea+"}");
        }
        PcbSourceSizedPoseSourceContractTest.check(oldGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(oldBoard)) &&
            newGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(newBoard)),"raw generation leaves source graphs unchanged");
        System.out.println("PASS: "+marker+"rows=5 routes=0");
    }
}
