"""Install proven observation adapters and an erasable census-to-proof bridge."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
COPY = Path(read(HERE/'local-state.json')['scratch'])
seal = read(OLD/'native-inputs.json')
assert len(seal['overrides']) == 3 and len(seal['additions']) == 2
for name, digest in dict(seal['overrides'], **seal['additions']).items():
    assert sha(OLD/'source'/name) == digest, name
generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
helper = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
registry = 'scripts/verify-current-contracts.ps1'
census = 'tests/contracts/PcbRoutedPassiveLegCensusContractTest.java'
wrapper = 'tests/contracts/PcbRoutedPassiveMoveProofTrialContractTest.java'
for name in (generator, helper):
    (COPY/name).write_bytes((OLD/'source'/name).read_bytes())
assert not (COPY/census).exists() and not (COPY/wrapper).exists()
before = (OLD/'source'/census).read_bytes()
anchor = b'    private static int fixtures;\n'
assert before.count(anchor) == 1
bridge = b'''    // BEGIN COPY-ONLY private move proof bridge.
    interface MoveProofObserver {
        void onCandidate(TroubleshootBoard board, PcbBoardLayout layout,
            String componentId, int dx, int dy, long analyticCopperDelta);
    }

    private static MoveProofObserver moveProofObserver;

    static MoveProofObserver setMoveProofObserver(MoveProofObserver observer) {
        MoveProofObserver previous = moveProofObserver;
        moveProofObserver = observer;
        return previous;
    }

    static void dispatchMoveProofObserver(TroubleshootBoard board,
            PcbBoardLayout layout, Vector<PartResult> parts) {
        MoveProofObserver observer = moveProofObserver;
        if (observer == null) return;
        for (PartResult part : parts)
            for (DirectionResult direction : part.directions)
                if (direction.eligible && direction.copperDelta.longValue() <= 0L) {
                    observer.onCandidate(board, layout, part.componentId,
                        direction.dx, direction.dy, direction.copperDelta.longValue());
                    return;
                }
        observer.onCandidate(board, layout, null, 0, 0, 0L);
    }
    // END COPY-ONLY private move proof bridge.
'''
after = before.replace(anchor, anchor+bridge)
anchor = b'            String geometryAfter = PlayerSessionFingerprint.of(\n'
assert after.count(anchor) == 1
dispatch = b'''            // BEGIN COPY-ONLY private move proof bridge.
            if (target) dispatchMoveProofObserver(board, layout, parts);
            // END COPY-ONLY private move proof bridge.
'''
after = after.replace(anchor, dispatch+anchor)
(COPY/census).write_bytes(after)
(COPY/wrapper).write_text('''package com.lushprojects.circuitjs1.client;

/** One private candidate after an existing success; original selected output stays exact. */
public final class PcbRoutedPassiveMoveProofTrialContractTest {
    public static void main(String[] args) {
        for (long seed : new long[] {5L, 2L})
            PcbRoutedPassiveMoveProofContractTest.observe(seed, "seed" + seed);
        System.out.println("PASS: PCB routed passive move proof trial contracts " +
            "roots=2 rows=2 evaluator=singlePrivateMove");
    }
}
''')
unit = [
    ['PcbRoutedPassiveLegCensusContractTest', 'PCB routed passive leg census contracts '],
    ['PcbRoutedPassiveMoveProofContractTest', 'PCB routed passive move proof contracts '],
]
routes = [['PcbRoutedPassiveMoveProofTrialContractTest',
    'PCB routed passive move proof trial contracts ']]
raw = (ROOT/registry).read_bytes()
anchor = b'    $suiteDefinitions = @(\r\n'
assert raw.count(anchor) == 1
rows = b''.join(("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
    for name, marker in unit+routes)
(COPY/registry).write_bytes(raw.replace(anchor, anchor+rows))
for name, value in (('unit-suites.json', unit), ('routes-suites.json', routes)):
    assert not (HERE/name).exists()
    (HERE/name).write_text(json.dumps(value, indent=2)+'\n')
(HERE/'bootstrap-audit.json').write_text(json.dumps({
    'status':'PASS', 'scope':'Accepted15 observation adapters plus test-only first-nonincreasing bridge',
    'priorCensusSealSha256':sha(OLD/'native-inputs.json'),
    'priorCensusSourceSha256':sha(OLD/'source'/census),
    'overrides':[generator, helper, registry],
    'addedRootSources':[census, wrapper],
    'plannedWorkerSource':'tests/contracts/PcbRoutedPassiveMoveProofContractTest.java',
    'bridgeSelection':'First canonical target part/direction eligible with analyticCopperDelta<=0',
    'constructionLimitPerTargetCallback':1,
    'defaultBridge':'NULL; no inspection',
    'dispatchBoundary':'Inside original census geometry hash bracket; before winner selection',
    'nativeExecution':'NOT_RUN'}, indent=2)+'\n')
print('Prepared COPY1469 root adapters/bridge/wrapper; expected1470 after worker source; no new route')
