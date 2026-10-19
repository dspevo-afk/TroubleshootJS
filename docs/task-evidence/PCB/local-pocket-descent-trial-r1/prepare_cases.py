"""Add a narrow native20 adapter around the unchanged physical witness core."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
TESTS = COPY/'tests/contracts'
wrapper = TESTS/'PcbPostSelectionEdgeTrimContractTest.java'
original = wrapper.read_bytes().decode('utf-8')
nl = '\r\n' if '\r\n' in original else '\n'
needle = '    private PcbPostSelectionEdgeTrimContractTest() { }'
addition = '''

    /** Native experiment retains every existing per-board physical witness. */
    static String observePocketDescent(long seed, int version) {
        check(isFixedRoot(seed), "fixed local-pocket trial root");
        check(version == 15 || version == 21, "explicit15/21 only");
        Rb56Plan plan = Rb56Plan.resolve(seed, version);
        TroubleshootBoard board = plan.board();
        check(board.getPlacementConstraints().placementDensityVersion ==
            (version == 15 ? 14 : 15), "explicit density mapping");
        check("ROLE_TRACE@1".equals(board.getPlacementConstraints()
            .getTraceWidthProfile().getIdentity()), "unchanged role widths");
        verifyPlacementBounds(board, seed, "pocket" + version);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        return routeAndObserve(seed, version, plan, board,
            PlayerSessionFingerprint.of(savedIntent(plan)),
            PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),
            PcbCanvasBoundaryContractTest.liveGraphSha256(board)).json;
    }
'''
assert original.count(needle) == 1 and 'observePocketDescent' not in original
changed = original.replace(needle, needle+addition.replace('\n', nl))
old = 'String label = version == SAVED_VERSION ? "saved14" : "trial15";'
new = 'String label = version == 21 ? "pocket21" : version == SAVED_VERSION ? "saved14" : "trial15";'
assert changed.count(old) == 1
changed = changed.replace(old, new)
old = 'check(version == SAVED_VERSION || version == TRIAL_VERSION,'
new = 'check(version == SAVED_VERSION || version == TRIAL_VERSION || version == 21,'
assert changed.count(old) == 1
changed = changed.replace(old, new)
wrapper.write_bytes(changed.encode('utf-8'))

roots = [-1017, -1004, -1022, 0, 17, 42, 1, 7, 5, 14, 2, 3, 4, 6]
name = 'PcbLocalPocketDescentTrialContractTest'
source = '''package com.lushprojects.circuitjs1.client;

/** Fixed informed corpus; actual native15/21 generation and physical admission. */
public final class PcbLocalPocketDescentTrialContractTest {
    private static final long[] ROOTS={-1017L,-1004L,-1022L,0L,17L,42L,1L,7L,5L,14L,2L,3L,4L,6L};
    public static void main(String[] args) {
        runSeeds(new long[]{-1017L,-1004L}, "PCB local pocket descent trial contracts ");
    }
    static void runSeeds(long[] seeds,String marker) {
        need(seeds.length==2 && seeds[0]!=seeds[1],"two distinct roots per bounded JVM");
        for(long seed:seeds) {
            boolean known=false;for(long root:ROOTS)if(seed==root)known=true;
            need(known,"fixed informed root");
            Rb56Plan saved=Rb56Plan.resolve(seed,15),trial=Rb56Plan.resolve(seed,21);
            need(saved.layoutSeed==trial.layoutSeed && saved.routingSeed==trial.routingSeed,
                "same exact signed-long placement and routing concerns");
            need(PcbCanvasBoundaryContractTest.liveGraphSha256(saved.board()).equals(
                PcbCanvasBoundaryContractTest.liveGraphSha256(trial.board())),"same source graph");
            for(int version:new int[]{15,21}) {
                String row=PcbPostSelectionEdgeTrimContractTest.observePocketDescent(seed,version);
                String prefix="PCB_EDGE_TRIM_CASE ";
                need(row.startsWith(prefix),"complete existing physical witness receipt");
                System.out.println("PCB_POCKET_DESCENT_CASE "+row.substring(prefix.length()));
            }
        }
        System.out.println("PASS: "+marker+"roots=2 pairs=2 rows=4 evaluator=planMedium");
    }
    private static void need(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }
}
'''
target = TESTS/(name+'.java')
assert not target.exists()
target.write_bytes(source.encode('utf-8'))
suites = [[name, 'PCB local pocket descent trial contracts ']]
for group in range(2, 8):
    group_name = name+'Group'+str(group)
    marker = 'PCB local pocket descent trial Group'+str(group)+' contracts '
    first, second = roots[(group-1)*2:group*2]
    java = 'package com.lushprojects.circuitjs1.client;\npublic final class '+group_name+' { public static void main(String[] args) { '+name+'.runSeeds(new long[]{'+str(first)+'L,'+str(second)+'L}, "'+marker+'"); } }\n'
    target = TESTS/(group_name+'.java')
    assert not target.exists()
    target.write_bytes(java.encode('utf-8'))
    suites.append([group_name, marker])
unit = [['PcbLocalPocketDescentContractTest', 'PCB local pocket descent contracts '],
        ['PcbVacancyTargetsContractTest', 'PCB vacancy targets contracts '],
        ['RelBSessionIdentityContractTest', 'REL-B session identity contracts ']]
registry = COPY/'scripts/verify-current-contracts.ps1'
text = registry.read_bytes().decode('utf-8')
nl = '\r\n' if '\r\n' in text else '\n'
needle = "        @{ Name = 'PcbCompactnessContractTest'; Marker = 'actual RB56 geometry report ' },"
assert text.count(needle) == 1 and 'PcbLocalPocketDescentContractTest' not in text
entries = nl.join("        @{ Name = '"+n+"'; Marker = '"+m+"' }," for n,m in [unit[0]]+suites)
registry.write_bytes(text.replace(needle, entries+nl+needle).encode('utf-8'))
protocol = {
    'schema': 1, 'baseCommit': '91ffe9a3c0e444c28b1ad0ae51005071e7fe0c8a',
    'roots': [str(seed) for seed in roots], 'versions': [15,21], 'densities': [14,15],
    'unitSuites': unit, 'routesSuites': suites,
    'baselinePath': 'docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json',
    'baselineSha256': '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816',
    'baselineSourcePath': 'docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt',
    'baselineSourceSha256': 'db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d',
    'casePrefix': 'PCB_POCKET_DESCENT_CASE ', 'routePassDetail': 'roots=2 pairs=2 rows=4 evaluator=planMedium',
    'metrics': ['area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias'],
    'timingScope': 'Native local pocket descent trial; unchanged per-JVM/search/physical limits',
    'scope': 'COPY-only explicit21; all14 explicit15 replay42 non-time fields exact; strict four-metric no-regression with an area/void improvement required'}
(HERE/'protocol.json').write_text(json.dumps(protocol, indent=2)+'\n')
for phase in ('unit','routes'):
    (HERE/(phase+'-suites.json')).write_text(json.dumps(protocol[phase+'Suites'], indent=2)+'\n')
print('PASS narrow15/21 core adapter, seven paired partitions, manifests; no runtime')
