"""Root-owned source/raw/route fixtures and narrow old native adapter migration."""
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
TEST=COPY/'tests/contracts'

def write(name,text):
    path=TEST/name
    assert not path.exists()
    path.write_bytes(text.replace('\r\n','\n').replace('\n','\r\n').encode())

old_source=TEST/'PcbPassivePoseTrialSourceContractTest.java'
data=old_source.read_bytes()
for old,new in [(b'Rb56Plan.resolve(42L, 17)',b'Rb56Plan.resolve(42L, 18)'),
                (b'unknown RB56 interpretation17 rejects',b'unknown RB56 interpretation18 rejects')]:
    assert data.count(old)==1
    data=data.replace(old,new)
old_source.write_bytes(data)

adapter=TEST/'PcbPostSelectionEdgeTrimContractTest.java'
data=adapter.read_bytes()
changes=[
 (b'plan.placementInterpretationVersion != 16))',b'plan.placementInterpretationVersion != 16 &&\r\n                plan.placementInterpretationVersion != 17))'),
 (b'Only explicit15/16 source intent is supported',b'Only explicit15/16/17 source intent is supported'),
 (b'(version != 15 && version != 16) || plan == null',b'(version != 15 && version != 16 && version != 17) || plan == null'),
 (b'passivePosePolicyVersion != (version == 16 ? 1 : 0)',b'passivePosePolicyVersion != (version == 15 ? 0 : version == 16 ? 1 : 2)'),
 (b'Pose observation requires its exact bound15/16 source',b'Pose observation requires its exact bound15/16/17 source'),
 (b'version == 15 ? "saved15" : "pose16", System.nanoTime()',b'version == 15 ? "saved15" : version == 16 ? "pose16" : "size17", System.nanoTime()')]
for old,new in changes:
    assert data.count(old)==1,old
    data=data.replace(old,new)
adapter.write_bytes(data)

write('PcbPassiveSizingSourceContractTest.java','''package com.lushprojects.circuitjs1.client;
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
        json.append(",\\"eligibleIds\\":").append(PcbPassivePoseTrialSourceContractTest.array(
            PcbPassivePoseTrialSourceContractTest.eligible(board)));
        json.append(",\\"densityVersion17\\":").append(board.getPlacementConstraints().placementDensityVersion);
        json.append(",\\"passivePosePolicy17\\":").append(board.getPlacementConstraints().passivePosePolicyVersion);
        PcbPassivePoseTrialSourceContractTest.field(json,"admissionIdentity17",MediumBoardNormalAdmission.rb56IdentityFor(board.getPlacementConstraints()));
        json.append('}');
        System.out.println("PCB_SIZING_CONTROL_INPUT "+json);
    }
    static void check(boolean value,String message) { assertions++;if(!value)throw new AssertionError(message); }
}
''')
write('PcbPassiveSizingRawContractTest.java','''package com.lushprojects.circuitjs1.client;
/** Literal old raw baseline and control equality; no routing. */
public final class PcbPassiveSizingRawContractTest {
    public static void main(String[] args) { run(42L,"PCB passive sizing raw42 contracts "); }
    static void run(long seed,String marker) {
        if(seed!=42L && seed!=-1022L && seed!=4L)throw new AssertionError("Fixed raw roots only");
        Rb56Plan saved=PcbPassivePoseTrialSourceContractTest.bound(seed,15),control=PcbPassiveSizingSourceContractTest.bound(seed);
        TroubleshootBoard oldBoard=saved.board(),newBoard=control.board();
        String oldGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(oldBoard);
        String newGraph=PcbCanvasBoundaryContractTest.liveGraphSha256(newBoard);
        PcbPassiveSizingSourceContractTest.check(oldGraph.equals(newGraph),"paired raw source graphs exact");
        for(int attempt:new int[]{0,2,3,4,5}) {
            PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbPlacementPlanner.Plan a=planner.plan(oldBoard,oldBoard.getPlacementConstraints(),saved.layoutSeed,attempt);
            PcbPlacementPlanner.Plan b=planner.plan(newBoard,newBoard.getPlacementConstraints(),control.layoutSeed,attempt);
            String first=PlayerSessionFingerprint.of(a.materialize().geometryFingerprint());
            String second=PlayerSessionFingerprint.of(b.materialize().geometryFingerprint());
            PcbPassiveSizingSourceContractTest.check(first.equals(second) && a.evaluations==b.evaluations &&
                a.demandArea==b.demandArea && a.courtyardArea==b.courtyardArea && a.candidate==b.candidate,
                "untouched candidate geometry/work/demand exact");
            System.out.println("PCB_SIZING_CONTROL_RAW {\\"seed\\":\\""+seed+"\\",\\"attempt\\":"+attempt+
                ",\\"geometry15\\":\\""+first+"\\",\\"geometry17\\":\\""+second+"\\",\\"evaluations15\\":"+a.evaluations+
                ",\\"evaluations17\\":"+b.evaluations+",\\"demand15\\":"+a.demandArea+",\\"demand17\\":"+b.demandArea+
                ",\\"courtyard15\\":"+a.courtyardArea+",\\"courtyard17\\":"+b.courtyardArea+"}");
        }
        PcbPassiveSizingSourceContractTest.check(oldGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(oldBoard)) &&
            newGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(newBoard)),"raw generation leaves source graphs unchanged");
        System.out.println("PASS: "+marker+"rows=5 routes=0");
    }
}
''')
for number,seed,marker in [(2,-1022,'neg1022'),(3,4,'4')]:
    write('PcbPassiveSizingRawContractTestGroup%d.java'%number,
        'package com.lushprojects.circuitjs1.client;\npublic final class PcbPassiveSizingRawContractTestGroup%d {\n'%number+
        '    public static void main(String[] args) { PcbPassiveSizingRawContractTest.run(%dL,"PCB passive sizing raw%s contracts "); }\n}\n'%(seed,marker))
write('PcbPassiveSizingRouteContractTest.java','''package com.lushprojects.circuitjs1.client;
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
        json.append(",\\"version\\":").append(version).append(",\\"top0\\":").append(PcbPassivePoseTrialSourceContractTest.array(zero));
        json.append(",\\"top90\\":").append(PcbPassivePoseTrialSourceContractTest.array(turned)).append('}');
        System.out.println("PCB_SIZING_CONTROL_POSES "+json);
        System.out.println("PCB_SIZING_CONTROL_ROUTE "+observed.json.substring(prefix.length()));
    }
}
''')
write('PcbPassiveSizingRouteContractTestGroup2.java','''package com.lushprojects.circuitjs1.client;
public final class PcbPassiveSizingRouteContractTestGroup2 {
    public static void main(String[] args) { PcbPassiveSizingRouteContractTest.run(3L,"PCB passive sizing route3 contracts "); }
}
''')
print('Prepared six root-owned fixtures and narrow17 adapter/unknown18 migration; physical core unchanged')
