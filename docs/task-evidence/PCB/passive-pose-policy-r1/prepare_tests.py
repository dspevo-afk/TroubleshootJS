"""Prepare fixed source/raw/route fixtures and an unchanged shared physical core."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(HERE / 'accepted15-baselines.json') == '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
rows = json.loads((HERE / 'accepted15-baselines.json').read_text())['rows']
assert len(rows) == 14
tests = COPY / 'tests/contracts'
adapter = tests / 'PcbPostSelectionEdgeTrimContractTest.java'
assert sha(adapter) == '215dfed0c329fd261c93981ab3f2d25b56413885f51ac6ca7af4f1579b5fd488'
old = adapter.read_text()
start = old.index('    /** Bounded package adapter for current15 routed-cause observations. */')
end = old.index('    private static Observation routeAndObserve(', start)
intent_start = old.index('    static String savedIntentShaForCurrent15(', start)
intent_end = old.index('    static Current15Observation routeCurrent15(', intent_start)
intent = old[intent_start:intent_end]
bounded = r'''    /** Native-only DTO; retains the actual issued admission identity without reissuing it. */
    static final class PosePolicyObservation {
        final String json, admissionIdentity;
        final PcbBoardLayout layout;
        final MediumBoardPhysicalPolicy.Result route;
        PosePolicyObservation(Observation observation) {
            json=observation.json;layout=observation.layout;route=observation.route;
            admissionIdentity=observation.admissionIdentity;
        }
    }
    static String sourceIntentShaForPosePolicy(Rb56Plan plan) {
        if (plan == null || (plan.placementInterpretationVersion != 15 &&
                plan.placementInterpretationVersion != 16))
            throw new IllegalArgumentException("Only explicit15/16 source intent is supported");
        return PlayerSessionFingerprint.of(savedIntent(plan));
    }
    static PosePolicyObservation routePosePolicy(long seed, int version, Rb56Plan plan,
            TroubleshootBoard board, String intent, String source, String graph) {
        PcbPassivePoseSourceContractTest.expected(seed); // Fixed14 informed cases only.
        if ((version != 15 && version != 16) || plan == null || board == null ||
                plan.seed != seed || plan.placementInterpretationVersion != version ||
                board.getPlacementConstraints().placementDensityVersion != 14 ||
                board.getPlacementConstraints().passivePosePolicyVersion != (version == 16 ? 1 : 0) ||
                !sourceIntentShaForPosePolicy(plan).equals(intent) ||
                !PcbCanvasBoundaryContractTest.sourcePlanSha256(plan).equals(source) ||
                !PcbCanvasBoundaryContractTest.liveGraphSha256(board).equals(graph))
            throw new IllegalArgumentException("Pose observation requires its exact bound15/16 source");
        return new PosePolicyObservation(routeAndObserveCore(seed, version, plan, board,
            intent, source, graph, version == 15 ? "saved15" : "pose16", System.nanoTime()));
    }

'''
data = old[:start] + intent + bounded + old[end:]
needle = '''        check(board.getPlacementConstraints().requiresTwoLayerRouting,
'''
assert data.count(needle) == 1
core_start = data.index(needle)
data = data[:core_start] + '''        return routeAndObserveCore(seed, version, plan, board, sourceIntentSha,
            sourceBefore, graphBefore, label, started);
    }

    private static Observation routeAndObserveCore(long seed, int version, Rb56Plan plan,
            TroubleshootBoard board, String sourceIntentSha, String sourceBefore,
            String graphBefore, String label, long started) {
''' + data[core_start:]
needle = '            area, copperLength, vias, json);'
assert data.count(needle) == 1
data = data.replace(needle, '            area, copperLength, vias, json, admission.identity());')
needle = '        final String json;\n        Observation('
assert data.count(needle) == 1
data = data.replace(needle, '        final String json, admissionIdentity;\n        Observation(')
needle = 'MarginFacts margins, long area, long copperLength, int vias, String json) {'
assert data.count(needle) == 1
data = data.replace(needle, 'MarginFacts margins, long area, long copperLength, int vias, String json,\n                String admissionIdentity) {')
needle = '            this.json = json;'
assert data.count(needle) == 1
data = data.replace(needle, needle + '\n            this.admissionIdentity = admissionIdentity;')
adapter.write_text(data)
seeds = ', '.join(r['seed'] + 'L' for r in rows)
source = r'''package com.lushprojects.circuitjs1.client;
import java.util.TreeSet;
/** Explicit source interpretation derivation, independent of trial route outcomes. */
public final class PcbPassivePoseTrialSourceContractTest {
    static final long[] SEEDS = { SEEDS };
    private static int assertions;
    public static void main(String[] args) {
        check(Rb56Placement.CURRENT_VERSION == 15 && SeededPcbLayoutGenerator.CURRENT_VERSION == 16,
            "default RB56/general epochs remain distinct and unchanged");
        for (long seed : SEEDS) emit(seed);
        boolean rejected = false;
        try { Rb56Plan.resolve(42L, 17); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected, "unknown RB56 interpretation17 rejects");
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
'''.replace('SEEDS };', seeds + ' };')
(tests / 'PcbPassivePoseTrialSourceContractTest.java').write_text(source)
raw = r'''package com.lushprojects.circuitjs1.client;
/** Fixed untouched-candidate binding, no routing or trial winner selection. */
public final class PcbPassivePoseRawContractTest {
    public static void main(String[] args) { run(42L,"PCB passive pose raw42 contracts "); }
    static void run(long seed,String marker) {
        Rb56Plan saved=PcbPassivePoseTrialSourceContractTest.bound(seed,15);
        Rb56Plan trial=PcbPassivePoseTrialSourceContractTest.bound(seed,16);
        for(int attempt:new int[]{0,2,3,4,5}) {
            PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbPlacementPlanner.Plan first=planner.plan(saved.board(),saved.board().getPlacementConstraints(),saved.layoutSeed,attempt);
            PcbPlacementPlanner.Plan second=planner.plan(trial.board(),trial.board().getPlacementConstraints(),trial.layoutSeed,attempt);
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
        System.out.println("PASS: "+marker+"rows=5 routes=0");
    }
}
'''
(tests / 'PcbPassivePoseRawContractTest.java').write_text(raw)
for index, seed in [(2,-1022),(3,4)]:
    name = 'PcbPassivePoseRawContractTestGroup' + str(index)
    (tests / (name+'.java')).write_text('package com.lushprojects.circuitjs1.client;\npublic final class '+name+' {\n public static void main(String[] args) { PcbPassivePoseRawContractTest.run('+str(seed)+'L,"PCB passive pose raw'+str(seed)+' contracts "); }\n}\n')
route = r'''package com.lushprojects.circuitjs1.client;
import java.util.TreeSet;
/** Full actual routed cases through the existing authoritative physical core. */
public final class PcbPassivePoseRouteContractTest {
    public static void main(String[] args) { run(FIRST_SEED,15,"PCB passive pose route Saved1 "); }
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
'''.replace('FIRST_SEED', rows[0]['seed']+'L')
(tests / 'PcbPassivePoseRouteContractTest.java').write_text(route)
route_suites = []
for version, label in [(15,'Saved'),(16,'Trial')]:
    for index,row in enumerate(rows,1):
        name = 'PcbPassivePoseRouteContractTest' if version==15 and index==1 else 'PcbPassivePoseRoute'+label+'Group'+str(index)
        marker = 'PCB passive pose route '+label+str(index)+' '
        route_suites.append([name,marker])
        if version==15 and index==1:
            continue
        (tests / (name+'.java')).write_text('package com.lushprojects.circuitjs1.client;\npublic final class '+name+' {\n public static void main(String[] args) { PcbPassivePoseRouteContractTest.run('+row['seed']+'L,'+str(version)+',"'+marker+'"); }\n}\n')
(HERE / 'route-suites.json').write_text(json.dumps(route_suites,indent=2)+'\n')
print('Prepared source/raw fixtures,28 bounded route suites and shared physical adapter')
