package com.lushprojects.circuitjs1.client;
import java.util.TreeSet;
/** Full actual routed cases through the existing authoritative physical core. */
public final class PcbPassivePoseRouteContractTest {
    public static void main(String[] args) { run(-1017L,15,"PCB passive pose route Saved1 "); }
    static void run(long seed,int version,String marker) {
        Rb56Plan plan=PcbPassivePoseTrialSourceContractTest.bound(seed,version);
        TroubleshootBoard board=plan.board();
        PcbPostSelectionEdgeTrimContractTest.PosePolicyObservation observed=
            PcbPostSelectionEdgeTrimContractTest.routePosePolicy(seed,version,plan,board,
                PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(plan),
                PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        String prefix="PCB_EDGE_TRIM_CASE ";
        if(observed==null||observed.layout==null||!observed.json.startsWith(prefix))throw new AssertionError("Missing route observation");
        TreeSet<String> top0=new TreeSet<String>(),top90=new TreeSet<String>();
        for(PcbComponentPlacement part:observed.layout.getComponents()) {
            if(!PcbPassivePoseTrialSourceContractTest.passive(part.getPhysicalPackage()))continue;
            if(part.getMountingSide()!=PcbBoardSide.TOP)throw new AssertionError("Foreign passive side");
            if(part.getRotation()==PcbRotation.DEG_0)top0.add(part.getComponentId());
            else if(version==16 && part.getRotation()==PcbRotation.DEG_90)top90.add(part.getComponentId());
            else throw new AssertionError("Foreign passive rotation");
        }
        TreeSet<String> both=new TreeSet<String>(top0);both.addAll(top90);
        if(!both.equals(PcbPassivePoseTrialSourceContractTest.eligible(board)))throw new AssertionError("Pose census lost passive IDs");
        StringBuilder json=new StringBuilder("{");
        PcbPassivePoseTrialSourceContractTest.field(json,"seed",Long.toString(seed));
        PcbPassivePoseTrialSourceContractTest.field(json,"issuedAdmissionIdentity",observed.admissionIdentity);
        json.append(",\"version\":").append(version).append(",\"top0\":").append(PcbPassivePoseTrialSourceContractTest.array(top0));
        json.append(",\"top90\":").append(PcbPassivePoseTrialSourceContractTest.array(top90)).append('}');
        System.out.println("PCB_PASSIVE_POLICY_POSES "+json);
        System.out.println("PCB_PASSIVE_POLICY_ROUTE "+observed.json.substring(prefix.length()));
        System.out.println("PASS: "+marker+"roots=1 rows=1");
    }
}
