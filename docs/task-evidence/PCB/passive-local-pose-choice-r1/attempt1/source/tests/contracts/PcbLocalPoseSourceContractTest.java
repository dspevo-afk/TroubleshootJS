package com.lushprojects.circuitjs1.client;
/** Explicit native-only local-pose source; no routed outcome assumptions. */
public final class PcbLocalPoseSourceContractTest {
    private static int assertions;
    public static void main(String[] args) {
        check(Rb56Placement.CURRENT_VERSION==15 && SeededPcbLayoutGenerator.CURRENT_VERSION==16,
            "default epochs unchanged");
        for(long seed:PcbPassivePoseTrialSourceContractTest.SEEDS)emit(seed);
        boolean rejected=false;
        try { Rb56Plan.resolve(42L,20); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"unsupported interpretation20 rejects");
        System.out.println("PASS: PCB local pose source contracts roots=14 assertions="+assertions);
    }
    static Rb56Plan bound(long seed) {
        PcbPassivePoseSourceContractTest.Expected expected=PcbPassivePoseSourceContractTest.expected(seed);
        Rb56Plan saved=PcbPassivePoseSourceContractTest.boundPlan(expected);
        Rb56Plan control=Rb56Plan.resolve(seed,19);
        String canonical=saved.canonical(),epoch="placement-interpretation=15;";
        check(canonical.indexOf(epoch)>=0 && canonical.indexOf(epoch)==canonical.lastIndexOf(epoch),
            "one declared saved15 epoch");
        check(control.canonical().equals(canonical.replace(epoch,"placement-interpretation=19;")),
            "control source differs only by explicit interpretation");
        TroubleshootBoard board=control.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(control,board);
        check(PcbCanvasBoundaryContractTest.liveGraphSha256(board).equals(expected.graph),"literal graph unchanged");
        check(PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(control).equals(expected.intent),
            "literal source intent unchanged");
        check(Long.toString(control.layoutSeed).equals(expected.layoutSeed) &&
            Long.toString(control.routingSeed).equals(expected.routingSeed),"exact signed-long seeds unchanged");
        check(board.getComponentIds().size()==expected.parts && board.getPadIds().size()==expected.pads,
            "literal population unchanged");
        PcbPlacementConstraints constraints=board.getPlacementConstraints();
        check(constraints.placementDensityVersion==14 && constraints.passivePosePolicyVersion==4 &&
            constraints.requiresTwoLayerRouting,"separate density14 policy4 with P07 routing");
        check(constraints.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS &&
            MediumBoardPhysicalPolicy.ID.equals(constraints.getPhysicalPolicyId()) &&
            constraints.getPhysicalPolicyVersion()==MediumBoardPhysicalPolicy.VERSION,
            "unchanged role widths and MEDIUM_BOARD physical policy");
        check(MediumBoardNormalAdmission.rb56IdentityFor(constraints).equals("RB56_BOARD_NORMAL@5") &&
            MediumBoardNormalAdmission.rb56IdentityForPlacement(19).equals("RB56_BOARD_NORMAL@5"),
            "explicit trial identity5");
        check(PcbPassivePoseTrialSourceContractTest.eligible(board).equals(
            PcbPassivePoseTrialSourceContractTest.eligible(saved.board())),"passive inventory unchanged");
        return control;
    }
    private static void emit(long seed) {
        Rb56Plan saved=PcbPassivePoseTrialSourceContractTest.bound(seed,15),control=bound(seed);
        TroubleshootBoard board=control.board();
        StringBuilder json=new StringBuilder("{");
        PcbPassivePoseTrialSourceContractTest.field(json,"seed",Long.toString(seed));
        PcbPassivePoseTrialSourceContractTest.field(json,"sourceCanonical15",saved.canonical());
        PcbPassivePoseTrialSourceContractTest.field(json,"sourcePlanSha25619",PcbCanvasBoundaryContractTest.sourcePlanSha256(control));
        PcbPassivePoseTrialSourceContractTest.field(json,"graph19",PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        PcbPassivePoseTrialSourceContractTest.field(json,"intent19",PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(control));
        PcbPassivePoseTrialSourceContractTest.field(json,"layoutSeed19",Long.toString(control.layoutSeed));
        PcbPassivePoseTrialSourceContractTest.field(json,"routingSeed19",Long.toString(control.routingSeed));
        json.append(",\"eligibleIds\":").append(PcbPassivePoseTrialSourceContractTest.array(
            PcbPassivePoseTrialSourceContractTest.eligible(board)));
        json.append(",\"densityVersion19\":").append(board.getPlacementConstraints().placementDensityVersion);
        json.append(",\"passivePosePolicy19\":").append(board.getPlacementConstraints().passivePosePolicyVersion);
        PcbPassivePoseTrialSourceContractTest.field(json,"admissionIdentity19",MediumBoardNormalAdmission.rb56IdentityFor(board.getPlacementConstraints()));
        json.append('}');
        System.out.println("PCB_LOCAL_POSE_INPUT "+json);
    }
    static void check(boolean value,String message) { assertions++;if(!value)throw new AssertionError(message); }
}
