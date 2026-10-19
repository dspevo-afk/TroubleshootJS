package com.lushprojects.circuitjs1.client;
import java.util.TreeSet;
/** Six normal full generations; explicit TOP0-only sizing control is not promotion. */
public final class PcbPassiveSizingRouteContractTest {
    public static void main(String[] args) { run(42L,"PCB passive sizing route42 contracts "); }
    static void run(long seed,String marker) {
        if(seed!=42L && seed!=3L)throw new AssertionError("Fixed two diagnostic roots only");
        for(int version:new int[]{15,16,17})observe(seed,version);
        System.out.println("PASS: "+marker+"roots=1 rows=3");
    }
    private static void observe(long seed,int version) {
        Rb56Plan plan=version==17?PcbPassiveSizingSourceContractTest.bound(seed):
            PcbPassivePoseTrialSourceContractTest.bound(seed,version);
        TroubleshootBoard board=plan.board();
        PcbPostSelectionEdgeTrimContractTest.PosePolicyObservation observed=
            PcbPostSelectionEdgeTrimContractTest.routePosePolicy(seed,version,plan,board,
                PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(plan),
                PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        String prefix="PCB_EDGE_TRIM_CASE ";
        if(observed==null || observed.layout==null || !observed.json.startsWith(prefix))
            throw new AssertionError("Missing actual routed control observation");
        String expected=version==15?"RB56_BOARD_NORMAL@1":version==16?"RB56_BOARD_NORMAL@2":"RB56_BOARD_NORMAL@3";
        if(!observed.admissionIdentity.equals(expected))throw new AssertionError("Wrong actual admission identity");
        TreeSet<String> zero=new TreeSet<String>(),turned=new TreeSet<String>();
        for(PcbComponentPlacement part:observed.layout.getComponents()) {
            if(!PcbPassivePoseTrialSourceContractTest.passive(part.getPhysicalPackage()))continue;
            if(part.getMountingSide()!=PcbBoardSide.TOP)throw new AssertionError("Foreign passive side");
            if(part.getRotation()==PcbRotation.DEG_0)zero.add(part.getComponentId());
            else if(version==16 && part.getRotation()==PcbRotation.DEG_90)turned.add(part.getComponentId());
            else throw new AssertionError("Legacy/control passive is turned or pose is unsupported");
        }
        TreeSet<String> all=new TreeSet<String>(zero);all.addAll(turned);
        if(!all.equals(PcbPassivePoseTrialSourceContractTest.eligible(board)))throw new AssertionError("Pose census lost IDs");
        StringBuilder json=new StringBuilder("{");
        PcbPassivePoseTrialSourceContractTest.field(json,"seed",Long.toString(seed));
        PcbPassivePoseTrialSourceContractTest.field(json,"issuedAdmissionIdentity",observed.admissionIdentity);
        json.append(",\"version\":").append(version).append(",\"top0\":").append(PcbPassivePoseTrialSourceContractTest.array(zero));
        json.append(",\"top90\":").append(PcbPassivePoseTrialSourceContractTest.array(turned)).append('}');
        System.out.println("PCB_SIZING_CONTROL_POSES "+json);
        System.out.println("PCB_SIZING_CONTROL_ROUTE "+observed.json.substring(prefix.length()));
    }
}
