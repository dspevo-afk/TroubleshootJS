package com.lushprojects.circuitjs1.client;
/** Actual old/trial generation with read-only candidate1 observation. */
public final class PcbPassiveChoiceRouteContractTest {
    public static void main(String[] args) { run(42L,"PCB passive choice route42 contracts "); }
    static void run(long seed,String marker) {
        if(seed!=42L && seed!=3L)throw new AssertionError("Fixed two diagnostic roots only");
        PcbPassiveChoiceObserver.Capture saved=observe(seed,15,null);
        PcbPassiveChoiceObserver.Capture trial=observe(seed,16,saved);
        System.out.println("PCB_PASSIVE_CHOICE_CAPTURE "+saved.json());
        System.out.println("PCB_PASSIVE_CHOICE_CAPTURE "+trial.json());
        System.out.println("PASS: "+marker+"roots=1 rows=2");
    }
    private static PcbPassiveChoiceObserver.Capture observe(long seed,int version,
            PcbPassiveChoiceObserver.Capture reference) {
        Rb56Plan plan=PcbPassivePoseTrialSourceContractTest.bound(seed,version);
        TroubleshootBoard board=plan.board();
        PcbPassiveChoiceObserver.Capture capture=PcbPassiveChoiceObserver.start(
            seed,plan.layoutSeed,version,reference);
        PcbPostSelectionEdgeTrimContractTest.PosePolicyObservation observed;
        try {
            observed=PcbPostSelectionEdgeTrimContractTest.routePosePolicy(seed,version,plan,board,
                PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(plan),
                PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),
                PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        } finally { PcbPassiveChoiceObserver.stop(capture); }
        String prefix="PCB_EDGE_TRIM_CASE ";
        if(observed==null || observed.layout==null || !observed.json.startsWith(prefix))
            throw new AssertionError("Missing actual physical route observation");
        if(!observed.admissionIdentity.equals(version==15 ? "RB56_BOARD_NORMAL@1" : "RB56_BOARD_NORMAL@2"))
            throw new AssertionError("Wrong actual issued admission identity");
        System.out.println("PCB_PASSIVE_CHOICE_ROUTE "+observed.json.substring(prefix.length()));
        return capture;
    }
}
