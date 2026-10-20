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
wrapper = 'tests/contracts/PcbRoutedPassiveFeasibilityTrialContractTest.java'
copies = {}
for name in (generator, helper):
    copies[name] = (OLD/'source'/name).read_bytes()
assert not (COPY/census).exists() and not (COPY/wrapper).exists()
before = (OLD/'source'/census).read_bytes()
anchor = b'    private static int fixtures;\n'
assert before.count(anchor) == 1
bridge = b'''    // BEGIN COPY-ONLY private feasibility census bridge.
    static final class MoveIntent {
        final String componentId;
        final int dx, dy;
        final long analyticCopperDelta;
        MoveIntent(String componentId, int dx, int dy, long analyticCopperDelta) {
            this.componentId = componentId;
            this.dx = dx; this.dy = dy;
            this.analyticCopperDelta = analyticCopperDelta;
        }
    }
    interface FeasibilityObserver {
        void onCandidates(TroubleshootBoard board, PcbBoardLayout layout,
            Vector<MoveIntent> moves);
    }
    private static FeasibilityObserver feasibilityObserver;
    static FeasibilityObserver setFeasibilityObserver(FeasibilityObserver observer) {
        FeasibilityObserver previous = feasibilityObserver;
        feasibilityObserver = observer;
        return previous;
    }
    static void dispatchFeasibilityObserver(TroubleshootBoard board,
            PcbBoardLayout layout, Vector<PartResult> parts) {
        FeasibilityObserver observer = feasibilityObserver;
        if (observer == null) return;
        Vector<MoveIntent> moves = new Vector<MoveIntent>();
        for (PartResult part : parts)
            for (DirectionResult direction : part.directions)
                if (direction.eligible && direction.copperDelta.longValue() <= 0L)
                    moves.add(new MoveIntent(part.componentId, direction.dx,
                        direction.dy, direction.copperDelta.longValue()));
        observer.onCandidates(board, layout, moves);
    }
    // END COPY-ONLY private feasibility census bridge.
'''

after = before.replace(anchor, anchor+bridge)
anchor = b'            String geometryAfter = PlayerSessionFingerprint.of(\n'
assert after.count(anchor) == 1
dispatch = b'''            // BEGIN COPY-ONLY private feasibility census bridge.
            if (target) dispatchFeasibilityObserver(board, layout, parts);
            // END COPY-ONLY private feasibility census bridge.
'''
after = after.replace(anchor, dispatch+anchor)
copies[census] = after
copies[wrapper] = '''package com.lushprojects.circuitjs1.client;

/** Independent private physical census after an existing success; selected output stays exact. */
public final class PcbRoutedPassiveFeasibilityTrialContractTest {
    public static void main(String[] args) {
        for (long seed : new long[] {5L, 2L})
            PcbRoutedPassiveFeasibilityCensusContractTest.observe(seed, "seed" + seed);
        System.out.println("PASS: PCB routed passive feasibility census trial contracts " +
            "roots=2 rows=2 evaluator=physicalFeasibilityCensus");
    }
}
'''.encode()
unit = [
    ['PcbRoutedPassiveLegCensusContractTest', 'PCB routed passive leg census contracts '],
    ['PcbRoutedPassiveFeasibilityCensusContractTest', 'PCB routed passive feasibility census contracts '],
]
routes = [['PcbRoutedPassiveFeasibilityTrialContractTest',
    'PCB routed passive feasibility census trial contracts ']]
raw = (ROOT/registry).read_bytes()
anchor = b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest';"
assert raw.count(anchor) == 1
rows = b''.join(("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
    for name, marker in unit+routes)
copies[registry] = raw.replace(anchor, rows+anchor)
# Complete all known preflights before the first COPY mutation.
assert not (HERE/'bootstrap-audit.json').exists()
for name in ('unit-suites.json', 'routes-suites.json'):
    assert not (HERE/name).exists()
for name, content in copies.items():
    (COPY/name).write_bytes(content)
for name, value in (('unit-suites.json', unit), ('routes-suites.json', routes)):
    assert not (HERE/name).exists()
    (HERE/name).write_text(json.dumps(value, indent=2)+'\n')
(HERE/'bootstrap-audit.json').write_text(json.dumps({
    'status':'PASS', 'scope':'Accepted15 observation adapters plus test-only all-nonincreasing census bridge',
    'priorCensusSealSha256':sha(OLD/'native-inputs.json'),
    'priorCensusSourceSha256':sha(OLD/'source'/census),
    'overrides':[generator, helper, registry],
    'addedRootSources':[census, wrapper],
    'plannedWorkerSource':'tests/contracts/PcbRoutedPassiveFeasibilityCensusContractTest.java',
    'bridgeSelection':'All canonical target part/directions eligible with analyticCopperDelta<=0, independent original source',
    'constructionLimitPerTargetCallback':31,
    'defaultBridge':'NULL; no inspection',
    'dispatchBoundary':'Inside original census geometry hash bracket; before winner selection',
    'nativeExecution':'NOT_RUN'}, indent=2)+'\n')
print('Prepared COPY1469 root adapters/bridge/wrapper; expected1470 after worker source; no new route')
