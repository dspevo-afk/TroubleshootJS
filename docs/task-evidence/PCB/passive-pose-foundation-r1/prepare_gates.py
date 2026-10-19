"""Prepare exact native adapters, literal bindings and unchanged gate machinery."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
OLD = HERE.parent / 'routed-strip-cause-r1'
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(HERE / 'accepted15-baselines.json') == '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
rows = json.loads((HERE / 'accepted15-baselines.json').read_text())['rows']
assert len(rows) == 14 and len({r['seed'] for r in rows}) == 14
adapter = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
source = OLD / 'source' / adapter
assert sha(source) == '215dfed0c329fd261c93981ab3f2d25b56413885f51ac6ca7af4f1579b5fd488'
assert sha(COPY / adapter) == '2de7932b48d479a8df1ffbb39b929ee88e438214224f8a16ccc8d5180b957f36'
(COPY / adapter).write_bytes(source.read_bytes())
fields = ['seed', 'parts', 'pads', 'sourcePlanSha256', 'liveBoardGraphSha256',
          'sourceIntentSha256', 'layoutSeed', 'routingSeed']
literals = ',\n'.join('        new Expected(' + ', '.join(
    (str(r[k]) if k in ('parts', 'pads') else json.dumps(r[k])) for k in fields) + ')' for r in rows)
text = '''package com.lushprojects.circuitjs1.client;

/** Native-only binding of old TOP0 interpretations to sealed accepted literals. */
public final class PcbPassivePoseSourceContractTest {
    private static int assertions;
    private static final Expected[] EXPECTED = {
LITERALS
    };
    private PcbPassivePoseSourceContractTest() { }
    public static void main(String[] args) {
        check(Rb56Placement.CURRENT_VERSION == 15, "current RB56 placement stays15");
        check(SeededPcbLayoutGenerator.CURRENT_VERSION == 16, "general layout algorithm stays16");
        for (Expected expected : EXPECTED) {
            Rb56Plan plan = boundPlan(expected);
            System.out.println("PCB_PASSIVE_POSE_SOURCE_CASE " + expected.json());
        }
        System.out.println("PASS: PCB passive pose source bindings roots=14 assertions=" + assertions);
    }
    static Rb56Plan boundPlan(Expected expected) {
        long seed = Long.parseLong(expected.seed);
        Rb56Plan plan = Rb56Plan.resolve(seed, 15);
        check(plan.seed == seed && plan.placementInterpretationVersion == 15, "explicit15 root");
        TroubleshootBoard board = plan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        check(board.getPlacementConstraints().placementDensityVersion == 14, "density14 retained");
        check(board.getComponentIds().size() == expected.parts && board.getPadIds().size() == expected.pads,
            "literal component/pad inventory");
        check(PcbCanvasBoundaryContractTest.sourcePlanSha256(plan).equals(expected.source), "literal source plan");
        check(PcbCanvasBoundaryContractTest.liveGraphSha256(board).equals(expected.graph), "literal live graph");
        check(PcbPostSelectionEdgeTrimContractTest.savedIntentShaForCurrent15(plan).equals(expected.intent),
            "literal source intent");
        check(Long.toString(plan.layoutSeed).equals(expected.layoutSeed), "exact signed-long layout seed");
        check(Long.toString(plan.routingSeed).equals(expected.routingSeed), "exact signed-long routing seed");
        return plan;
    }
    static Expected expected(long seed) {
        for (Expected row : EXPECTED) if (row.seed.equals(Long.toString(seed))) return row;
        throw new AssertionError("Unbound seed");
    }
    static final class Expected {
        final String seed, source, graph, intent, layoutSeed, routingSeed;
        final int parts, pads;
        Expected(String seed, int parts, int pads, String source, String graph, String intent,
                String layoutSeed, String routingSeed) {
            this.seed=seed;this.parts=parts;this.pads=pads;this.source=source;this.graph=graph;
            this.intent=intent;this.layoutSeed=layoutSeed;this.routingSeed=routingSeed;
        }
        String json() {
            return "{\\"seed\\":\\"" + seed + "\\",\\"parts\\":" + parts + ",\\"pads\\":" + pads +
                ",\\"sourcePlanSha256\\":\\"" + source + "\\",\\"liveBoardGraphSha256\\":\\"" + graph +
                "\\",\\"sourceIntentSha256\\":\\"" + intent + "\\",\\"layoutSeed\\":\\"" + layoutSeed +
                "\\",\\"routingSeed\\":\\"" + routingSeed + "\\"}";
        }
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
'''.replace('LITERALS', literals)
(COPY / 'tests/contracts/PcbPassivePoseSourceContractTest.java').write_text(text)
(COPY / 'tests/contracts/PcbPassivePoseCurrent15ContractTest.java').write_text('''package com.lushprojects.circuitjs1.client;

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
''')
(COPY / 'tests/contracts/PcbPassivePoseCurrent15ContractTestGroup2.java').write_text('''package com.lushprojects.circuitjs1.client;
public final class PcbPassivePoseCurrent15ContractTestGroup2 {
    public static void main(String[] args) {
        PcbPassivePoseCurrent15ContractTest.run(-1022L, "PCB passive pose current15 Group2 ");
    }
}
''')
for name in ('check_process_cleanup.ps1', 'sanitize.py', 'retire_copy.py', 'check_final_rows.py'):
    data = (OLD / name).read_text()
    data = data.replace('tsj-routed-strip-', 'tsj-passive-pose-')
    data = data.replace('Before observer native runtime', 'Before passive foundation native runtime')
    (HERE / name).write_text(data)
data = (OLD / 'run_native.ps1').read_text()
data = data.replace('tsj-routed-strip-', 'tsj-passive-pose-').replace('routed-strip-cause-r1', 'passive-pose-foundation-r1')
data = data.replace('current15 read-only strip cause work', 'passive mechanical capability probe')
data = data.replace("$suites=@('PcbRoutedStripCauseContractTest','PcbRoutedStripCauseContractTestGroup2')",
    "$suites=@('PcbPassivePoseFoundationContractTest','PcbPassivePoseSourceContractTest','PcbPassivePoseCurrent15ContractTest','PcbPassivePoseCurrent15ContractTestGroup2')")
data = data.replace('Native routed-strip cause phase failed', 'Native passive pose foundation phase failed')
data = data.replace('suiteCount=2', 'suiteCount=4').replace('Two informed current15 routes, one per bounded JVM; observation only and independent signature fixtures',
    'Pure mechanical foundation,14 source bindings, two current15 routes; pose selection disabled')
(HERE / 'run_native.ps1').write_text(data)
data = (OLD / 'inputs.py').read_text()
data = data.replace("'src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java'", "'src/com/lushprojects/circuitjs1/client/PhysicalPackages.java'")
data = data.replace("additions = {'tests/contracts/PcbRoutedStripCauseContractTest.java',\n    'tests/contracts/PcbRoutedStripCauseContractTestGroup2.java'}", "additions = {'tests/contracts/PcbPassivePoseFoundationContractTest.java',\n    'tests/contracts/PcbPassivePoseSourceContractTest.java',\n    'tests/contracts/PcbPassivePoseCurrent15ContractTest.java',\n    'tests/contracts/PcbPassivePoseCurrent15ContractTestGroup2.java'}")
data = data.replace('1469', '1471').replace('3overrides/2additions', '3overrides/4additions')
begin = data.index("owners = (")
end = data.index("assert all(", begin)
owners = ('proposal.json', 'prepare.py', 'prepare_gates.py', 'copy-inputs.json', 'accepted15-baselines.json',
    'worker-notes.md', 'source-review.json', 'review_source.py', 'register.py', 'inputs.py', 'run_native.ps1',
    'check_process_cleanup.ps1', 'sanitize.py', 'final_rows.py', 'check_final_rows.py',
    'final-row-canaries.json', 'retire_copy.py')
data = data[:begin] + 'owners = ' + repr(owners) + '\n' + data[end:]
(HERE / 'inputs.py').write_text(data)
print('Prepared literal14 source bindings, two routed wrappers, exact prior adapter and gate machinery')
