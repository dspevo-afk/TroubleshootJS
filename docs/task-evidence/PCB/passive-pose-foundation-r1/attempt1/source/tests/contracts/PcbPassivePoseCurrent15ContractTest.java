package com.lushprojects.circuitjs1.client;

/** Reuses the unchanged authoritative current15 routing/physical fixture body. */
public final class PcbPassivePoseCurrent15ContractTest {
    private PcbPassivePoseCurrent15ContractTest() { }
    public static void main(String[] args) { run(42L, "PCB passive pose current15 contracts "); }
    static void run(long seed, String marker) {
        if (seed != 42L && seed != -1022L) throw new AssertionError("Fixed roots only");
        PcbPassivePoseSourceContractTest.Expected expected = PcbPassivePoseSourceContractTest.expected(seed);
        Rb56Plan plan = PcbPassivePoseSourceContractTest.boundPlan(expected);
        TroubleshootBoard board = plan.board();
        PcbPostSelectionEdgeTrimContractTest.Current15Observation observed =
            PcbPostSelectionEdgeTrimContractTest.routeCurrent15(seed, plan, board,
                expected.intent, expected.source, expected.graph);
        String prefix = "PCB_EDGE_TRIM_CASE ";
        if (observed == null || observed.layout == null || !observed.json.startsWith(prefix))
            throw new AssertionError("Missing original current15 observation");
        System.out.println("PCB_PASSIVE_POSE_BASELINE_CASE " + observed.json.substring(prefix.length()));
        System.out.println("PASS: " + marker + "roots=1 rows=1 poseSelection=DISABLED");
    }
}
