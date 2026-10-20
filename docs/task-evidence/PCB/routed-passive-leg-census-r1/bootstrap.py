"""Install a proven read-only callback into accepted15, plus native witness seam."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'successful-route-pool-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
COPY = Path(read(HERE/'local-state.json')['scratch'])
generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
previous = (OLD/'source'/generator).read_bytes()
assert sha(OLD/'source'/generator) == read(OLD/'native-inputs.json')['overrides'][generator]
blocks = []
active = None
for line in previous.splitlines(keepends=True):
    if line.strip() == b'// BEGIN COPY-ONLY successful-route-pool observer.':
        assert active is None
        active = [line]
    elif line.strip() == b'// END COPY-ONLY successful-route-pool observer.':
        assert active is not None
        active.append(line)
        blocks.append(b''.join(active))
        active = None
    elif active is not None:
        active.append(line)
assert active is None and len(blocks) == 2
raw = (ROOT/generator).read_bytes()
for needle,block in zip((b'    private final int layoutAlgorithmVersion;',
        b'        boolean preferDensePacking = placementVersion >= 4 && placementVersion < 8;'),blocks):
    assert raw.count(needle) == 1
    end = raw.index(needle)+len(needle)
    newline = b'\r\n' if raw[end:end+2] == b'\r\n' else b'\n'
    assert raw[end:end+len(newline)] == newline
    needle += newline
    block = block.replace(b'\r\n',b'\n').replace(b'\n',newline)
    raw = raw.replace(needle,needle+block)
(COPY/generator).write_bytes(raw)
helper = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
original = (ROOT/helper).read_bytes()
needle = b'    public static void main(String[] args) {'
assert original.count(needle) == 1
addition = '''    // BEGIN COPY-ONLY accepted15 routed-leg witness.
    static String observeRoutedPassiveLegCensus(long seed) {
        check(isFixedRoot(seed), "fixed routed-leg census root");
        Rb56Plan plan = Rb56Plan.resolve(seed, 15);
        TroubleshootBoard board = plan.board();
        check(board.getPlacementConstraints().placementDensityVersion == 14,
            "accepted15 retains density14");
        verifyPlacementBounds(board, seed, "trial15");
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        return routeAndObserve(seed, 15, plan, board,
            PlayerSessionFingerprint.of(savedIntent(plan)),
            PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),
            PcbCanvasBoundaryContractTest.liveGraphSha256(board)).json;
    }
    // END COPY-ONLY accepted15 routed-leg witness.
'''.replace('\n','\r\n' if original.splitlines(keepends=True)[0].endswith(b'\r\n') else '\n').encode()
(COPY/helper).write_bytes(original.replace(needle,addition+needle))
unit = [['PcbRoutedPassiveLegCensusContractTest','PCB routed passive leg census contracts ']]
routes = [['PcbRoutedPassiveLegCensusTrialContractTest','PCB routed passive leg census trial contracts ']]
for name,value in (('unit-suites.json',unit),('routes-suites.json',routes)):
    assert not (HERE/name).exists()
    (HERE/name).write_text(json.dumps(value,indent=2)+'\n')
registry = 'scripts/verify-current-contracts.ps1'
raw = (ROOT/registry).read_bytes()
needle = b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest';"
assert raw.count(needle) == 1
addition = ''.join("        @{ Name = '"+n+"'; Marker = '"+m+"' },\r\n" for n,m in unit+routes).encode()
(COPY/registry).write_bytes(raw.replace(needle,addition+needle))
wrapper = COPY/'tests/contracts/PcbRoutedPassiveLegCensusTrialContractTest.java'
assert not wrapper.exists()
wrapper.write_text('''package com.lushprojects.circuitjs1.client;

/** Fixed informed accepted15 structural census; no geometry mutation. */
public final class PcbRoutedPassiveLegCensusTrialContractTest {
    public static void main(String[] args) {
        for(long seed:new long[]{5L,2L})
            PcbRoutedPassiveLegCensusContractTest.observe(seed,"seed"+seed);
        System.out.println("PASS: PCB routed passive leg census trial contracts "+
            "roots=2 rows=2 evaluator=endpointLegCensus");
    }
}
''')
(HERE/'bootstrap-audit.json').write_text(json.dumps({'status':'PASS',
    'scope':'Exact two proven read-only callback blocks inserted into accepted15; no22 overlay',
    'priorObserverSourceSha256':sha(OLD/'source'/generator),
    'acceptedGeneratorSha256':sha(ROOT/generator),'acceptedPhysicalWitnessSha256':sha(ROOT/helper),
    'overrides':[generator,helper,registry],'addedRootWrapper':wrapper.relative_to(COPY).as_posix()},indent=2)+'\n')
print('Prepared accepted15 callback/witness/registry and root wrapper; no rejected policy copied')
