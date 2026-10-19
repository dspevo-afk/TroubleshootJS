package com.lushprojects.circuitjs1.client;
/** Explicit native-only sizing-control source; no routed outcome assumptions. */
public final class PcbPassiveSizingSourceContractTest {
    private static int assertions;
    public static void main(String[] args) {
        check(Rb56Placement.CURRENT_VERSION==15 && SeededPcbLayoutGenerator.CURRENT_VERSION==16,
            "default epochs unchanged");
        for(long seed:PcbPassivePoseTrialSourceContractTest.SEEDS)emit(seed);
        boolean rejected=false;
        try { Rb56Plan.resolve(42L,18); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"unsupported interpretation18 rejects");
        System.out.println("PASS: PCB passive sizing source contracts roots=14 assertions="+assertions);
    }
    static Rb56Plan bound(long seed) {
        PcbPassivePoseSourceContractTest.Expected expected=PcbPassivePoseSourceContractTest.expected(seed);
        Rb56Plan saved=PcbPassivePoseSourceContractTest.boundPlan(expected);
        Rb56Plan control=Rb56Plan.resolve(seed,17);
        String canonical=saved.canonical(),epoch="placement-interpretation=15;";
        check(canonical.indexOf(epoch)>=0 && canonical.indexOf(epoch)==canonical.lastIndexOf(epoch),
            "one declared saved15 epoch");
        check(control.canonical().equals(canonical.replace(epoch,"placement-interpretation=17;")),
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
        check(constraints.placementDensityVersion==14 && constraints.passivePosePolicyVersion==2 &&
            constraints.requiresTwoLayerRouting,"separate density14 policy2 with P07 routing");
        check(constraints.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS &&
            MediumBoardPhysicalPolicy.ID.equals(constraints.getPhysicalPolicyId()) &&
            constraints.getPhysicalPolicyVersion()==MediumBoardPhysicalPolicy.VERSION,
            "unchanged role widths and MEDIUM_BOARD physical policy");
        check(MediumBoardNormalAdmission.rb56IdentityFor(constraints).equals("RB56_BOARD_NORMAL@3") &&
            MediumBoardNormalAdmission.rb56IdentityForPlacement(17).equals("RB56_BOARD_NORMAL@3"),
            "explicit control identity3");
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
        PcbPassivePoseTrialSourceContractTest.field(json,"sourcePlanSha25617",PcbCanvasBoundaryContractTest.sourcePlanSha256(control));
        PcbPassivePoseTrialSourceContractTest.field(json,"graph17",PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        PcbPassivePoseTrialSourceContractTest.field(json,"intent17",PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(control));
        PcbPassivePoseTrialSourceContractTest.field(json,"layoutSeed17",Long.toString(control.layoutSeed));
        PcbPassivePoseTrialSourceContractTest.field(json,"routingSeed17",Long.toString(control.routingSeed));
        json.append(",\"eligibleIds\":").append(PcbPassivePoseTrialSourceContractTest.array(
            PcbPassivePoseTrialSourceContractTest.eligible(board)));
        json.append(",\"densityVersion17\":").append(board.getPlacementConstraints().placementDensityVersion);
        json.append(",\"passivePosePolicy17\":").append(board.getPlacementConstraints().passivePosePolicyVersion);
        PcbPassivePoseTrialSourceContractTest.field(json,"admissionIdentity17",MediumBoardNormalAdmission.rb56IdentityFor(board.getPlacementConstraints()));
        json.append('}');
        System.out.println("PCB_SIZING_CONTROL_INPUT "+json);
    }
    static void check(boolean value,String message) { assertions++;if(!value)throw new AssertionError(message); }
}
