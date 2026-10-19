package com.lushprojects.circuitjs1.client;
import java.util.TreeSet;
/** Fixed14 normal generation pairs; actual unchanged physical and whitespace core. */
public final class PcbSourceSizedPoseRouteContractTest {
    public static void main(String[] args) { run(-1017L,"PCB source sized pose routeNeg1017 contracts "); }
    static void run(long seed,String marker) {
        boolean fixed=false;for(long declared:PcbPassivePoseTrialSourceContractTest.SEEDS)if(declared==seed)fixed=true;
        if(!fixed)throw new AssertionError("Fixed informed regression roots only");
        for(int version:new int[]{15,18})observe(seed,version);
        System.out.println("PASS: "+marker+"roots=1 rows=2");
    }
    private static void observe(long seed,int version) {
        Rb56Plan plan=version==18?PcbSourceSizedPoseSourceContractTest.bound(seed):PcbPassivePoseTrialSourceContractTest.bound(seed,15);
        TroubleshootBoard board=plan.board();
        PcbPostSelectionEdgeTrimContractTest.PosePolicyObservation observed=PcbPostSelectionEdgeTrimContractTest.routePosePolicy(seed,version,plan,board,
            PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(plan),PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        String prefix="PCB_EDGE_TRIM_CASE ";
        if(observed==null || observed.layout==null || !observed.json.startsWith(prefix))throw new AssertionError("Missing actual routed observation");
        String expected=version==15?"RB56_BOARD_NORMAL@1":"RB56_BOARD_NORMAL@4";
        if(!observed.admissionIdentity.equals(expected))throw new AssertionError("Wrong actual admission identity");
        TreeSet<String> zero=new TreeSet<String>(),turned=new TreeSet<String>();
        for(PcbComponentPlacement part:observed.layout.getComponents()) {
            if(!PcbPassivePoseTrialSourceContractTest.passive(part.getPhysicalPackage()))continue;
            if(part.getMountingSide()!=PcbBoardSide.TOP)throw new AssertionError("Foreign passive side");
            if(part.getRotation()==PcbRotation.DEG_0)zero.add(part.getComponentId());
            else if(version==18 && part.getRotation()==PcbRotation.DEG_90)turned.add(part.getComponentId());
            else throw new AssertionError("Unsupported passive pose");
        }
        TreeSet<String> all=new TreeSet<String>(zero);all.addAll(turned);
        if(!all.equals(PcbPassivePoseTrialSourceContractTest.eligible(board)))throw new AssertionError("Pose census lost IDs");
        StringBuilder json=new StringBuilder("{");
        PcbPassivePoseTrialSourceContractTest.field(json,"seed",Long.toString(seed));
        PcbPassivePoseTrialSourceContractTest.field(json,"issuedAdmissionIdentity",observed.admissionIdentity);
        json.append(",\"version\":").append(version).append(",\"top0\":").append(PcbPassivePoseTrialSourceContractTest.array(zero));
        json.append(",\"top90\":").append(PcbPassivePoseTrialSourceContractTest.array(turned)).append('}');
        System.out.println("PCB_SOURCE_SIZED_POSE_POSES "+json);
        System.out.println("PCB_SOURCE_SIZED_POSE_ROUTE "+observed.json.substring(prefix.length()));
    }
}
