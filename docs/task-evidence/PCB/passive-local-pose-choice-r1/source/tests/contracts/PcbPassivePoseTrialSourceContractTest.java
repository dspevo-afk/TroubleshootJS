package com.lushprojects.circuitjs1.client;
import java.util.TreeSet;
/** Explicit source interpretation derivation, independent of trial route outcomes. */
public final class PcbPassivePoseTrialSourceContractTest {
    static final long[] SEEDS = { -1017L, -1004L, -1022L, 0L, 17L, 42L, 1L, 7L, 5L, 14L, 2L, 3L, 4L, 6L };
    private static int assertions;
    public static void main(String[] args) {
        check(Rb56Placement.CURRENT_VERSION == 15 && SeededPcbLayoutGenerator.CURRENT_VERSION == 16,
            "default RB56/general epochs remain distinct and unchanged");
        for (long seed : SEEDS) emit(seed);
        boolean rejected = false;
        try { Rb56Plan.resolve(42L, 20); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected, "unknown RB56 interpretation20 rejects");
        System.out.println("PASS: PCB passive pose trial source contracts roots=14 assertions=" + assertions);
    }
    static Rb56Plan bound(long seed, int version) {
        PcbPassivePoseSourceContractTest.Expected expected = PcbPassivePoseSourceContractTest.expected(seed);
        Rb56Plan saved = PcbPassivePoseSourceContractTest.boundPlan(expected);
        if (version == 15) return saved;
        check(version == 16, "only explicit trial16");
        Rb56Plan trial = Rb56Plan.resolve(seed, 16);
        String canonical = saved.canonical();
        String oldEpoch = "placement-interpretation=15;";
        check(canonical.indexOf(oldEpoch) >= 0 && canonical.indexOf(oldEpoch) == canonical.lastIndexOf(oldEpoch),
            "one explicit old interpretation field");
        check(trial.canonical().equals(canonical.replace(oldEpoch, "placement-interpretation=16;")),
            "new source plan differs only by declared interpretation");
        TroubleshootBoard board = trial.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trial, board);
        check(PcbCanvasBoundaryContractTest.liveGraphSha256(board).equals(expected.graph), "unchanged literal graph");
        check(PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(trial).equals(expected.intent),
            "unchanged literal source intent");
        check(Long.toString(trial.layoutSeed).equals(expected.layoutSeed) &&
            Long.toString(trial.routingSeed).equals(expected.routingSeed), "unchanged exact signed-long seeds");
        check(board.getComponentIds().size() == expected.parts && board.getPadIds().size() == expected.pads,
            "unchanged literal population");
        check(board.getPlacementConstraints().placementDensityVersion == 14 &&
            board.getPlacementConstraints().passivePosePolicyVersion == 1, "separate density14/pose1");
        return trial;
    }
    private static void emit(long seed) {
        Rb56Plan saved = bound(seed,15), trial = bound(seed,16);
        check(saved.board().getPlacementConstraints().passivePosePolicyVersion == 0, "saved15 pose0");
        StringBuilder json = new StringBuilder("{");
        field(json,"seed",Long.toString(seed));
        field(json,"sourceCanonical15",saved.canonical());
        field(json,"sourcePlanSha25616",PcbCanvasBoundaryContractTest.sourcePlanSha256(trial));
        field(json,"graph16",PcbCanvasBoundaryContractTest.liveGraphSha256(trial.board()));
        field(json,"intent16",PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(trial));
        field(json,"layoutSeed16",Long.toString(trial.layoutSeed));
        field(json,"routingSeed16",Long.toString(trial.routingSeed));
        json.append(",\"eligibleIds\":").append(array(eligible(trial.board()))).append('}');
        System.out.println("PCB_PASSIVE_POLICY_INPUT " + json);
    }
    static boolean passive(PhysicalPackage physical) {
        return physical == PhysicalPackages.AXIAL_RESISTOR || physical == PhysicalPackages.AXIAL_DIODE ||
            physical == PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR || physical == PhysicalPackages.RADIAL_CERAMIC_CAPACITOR;
    }
    static TreeSet<String> eligible(TroubleshootBoard board) {
        TreeSet<String> result = new TreeSet<String>();
        for (String id : board.getComponentIds()) if (passive(board.getComponent(id).getPhysicalPackage())) result.add(id);
        return result;
    }
    static String array(TreeSet<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (String value : values) { if (json.length()>1) json.append(',');json.append(quote(value)); }
        return json.append(']').toString();
    }
    static void field(StringBuilder json,String key,String value) {
        if (json.length()>1) json.append(',');json.append(quote(key)).append(':').append(quote(value));
    }
    static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index=0;index<value.length();index++) {
            char ch=value.charAt(index);
            if(ch=='"'||ch=='\\')result.append('\\').append(ch);
            else if(ch=='\n')result.append("\\n");
            else if(ch=='\r')result.append("\\r");
            else if(ch=='\t')result.append("\\t");
            else if(ch<0x20)throw new IllegalArgumentException("Unexpected canonical control character");
            else result.append(ch);
        }
        return result.append('"').toString();
    }
    static void check(boolean value,String message) { assertions++;if(!value)throw new AssertionError(message); }
}
