package com.lushprojects.circuitjs1.client;
/** Fixed untouched-candidate binding, no routing or trial winner selection. */
public final class PcbPassivePoseRawContractTest {
    public static void main(String[] args) { run(42L,"PCB passive pose raw42 contracts "); }
    static void run(long seed,String marker) {
        Rb56Plan saved=PcbPassivePoseTrialSourceContractTest.bound(seed,15);
        Rb56Plan trial=PcbPassivePoseTrialSourceContractTest.bound(seed,16);
        TroubleshootBoard savedBoard=saved.board(),trialBoard=trial.board();
        String savedGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
        String trialGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);
        PcbPassivePoseTrialSourceContractTest.check(savedGraph.equals(trialGraph),"paired logical graphs exact");
        for(int attempt:new int[]{0,2,3,4,5}) {
            PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbPlacementPlanner.Plan first=planner.plan(savedBoard,savedBoard.getPlacementConstraints(),saved.layoutSeed,attempt);
            PcbPlacementPlanner.Plan second=planner.plan(trialBoard,trialBoard.getPlacementConstraints(),trial.layoutSeed,attempt);
            String a=PlayerSessionFingerprint.of(first.materialize().geometryFingerprint());
            String b=PlayerSessionFingerprint.of(second.materialize().geometryFingerprint());
            PcbPassivePoseTrialSourceContractTest.check(a.equals(b) && first.evaluations==second.evaluations &&
                first.demandArea==second.demandArea && first.courtyardArea==second.courtyardArea &&
                first.candidate==second.candidate,"untouched candidate geometry/work/demand exact");
            System.out.println("PCB_PASSIVE_POLICY_RAW {\"seed\":\""+seed+"\",\"attempt\":"+attempt+
                ",\"geometry15\":\""+a+"\",\"geometry16\":\""+b+"\",\"evaluations15\":"+first.evaluations+
                ",\"evaluations16\":"+second.evaluations+",\"demand15\":"+first.demandArea+
                ",\"demand16\":"+second.demandArea+",\"courtyard15\":"+first.courtyardArea+
                ",\"courtyard16\":"+second.courtyardArea+"}");
        }
        PcbPassivePoseTrialSourceContractTest.check(savedGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard)) &&
            trialGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)),"raw placement leaves both live graphs unchanged");
        System.out.println("PASS: "+marker+"rows=5 routes=0");
    }
}
