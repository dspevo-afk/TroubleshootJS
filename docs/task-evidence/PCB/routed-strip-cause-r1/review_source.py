"""Root static comparison after worker handoff; runtime proof remains separate."""
import hashlib
import json
import re
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
assert (HERE / 'worker-notes.md').is_file(), 'Worker handoff required'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
adapter = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
old = (ROOT / adapter).read_text()
new = (COPY / adapter).read_text()
start = '    /** Bounded package adapter for current15 routed-cause observations. */'
end = '    private static Observation routeAndObserve('
assert new.count(start) == 1 and new[:new.index(start)] + new[new.index(end):] == old, 'Old authoritative fixture changed outside adapter insertion'
compactor = 'src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java'
old = (ROOT / compactor).read_text()
new = (COPY / compactor).read_text()
tail = '    private static int shift('
assert old[old.index(tail):] == new[new.index(tail):], 'Original shift/placement compactAxis body changed'
for token in ('PcbPlacementPlanner.validate(', 'candidate.validateRoutingGeometry(',
              'candidate.validateRouteQuality(', 'result=new Routed(packed,candidate);',
              'trace.withPath(xs,ys)', 'hole.translatedBy('):
    assert old.count(token) == new.count(token), 'Core call count changed: ' + token
test = (COPY / 'tests/contracts/PcbRoutedStripCauseContractTest.java').read_text()
pattern = r'new Baseline\(([0-9]+), ([0-9]+), "([a-f0-9]{64})",\s*"([a-f0-9]{64})",\s*"([a-f0-9]{64})",\s*"(-?[0-9]+)", "(-?[0-9]+)"\)'
actual = re.findall(pattern, test)
bases = json.loads((HERE / 'accepted15-baselines.json').read_text())['rows']
expected = [(str(r['parts']), str(r['pads']), r['sourcePlanSha256'], r['liveBoardGraphSha256'],
    r['sourceIntentSha256'], r['layoutSeed'], r['routingSeed']) for r in bases]
assert actual == expected and len(actual) == 2, 'Baseline literal transport differs'
literal = test.split('EXPECTED_SIGNATURE_CANONICAL =', 1)[1].split(';\n', 1)[0]
canonical = ''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', literal))
tokens = []
cursor = 0
while cursor < len(canonical):
    colon = canonical.index(':', cursor)
    length = int(canonical[cursor:colon])
    cursor = colon + 1
    assert length >= -1
    value = None if length == -1 else canonical[cursor:cursor + length]
    if length != -1:
        assert len(value) == length
        cursor += length
    assert canonical[cursor] == ';', 'Independent literal token length invalid'
    cursor += 1
    tokens.append(value)
assert tokens[:3] == ['routed-physical-layout@1', 'R1', '2']
assert tokens.count('AXIAL_RESISTOR|variant=SPAN_220|transform=IDENTITY|version=3|geometry=220x70') == 2
assert 'observerLifecycleFixtures(seed);' in test and 'stale owner cannot clear or displace its successor' in test
assert 'nonuniform component-origin pose changes physical signature' in test
assert 'PcbRotation.DEG_180' not in test, 'Fixture still requests undeclared axial pose'
owners = [compactor, adapter, 'tests/contracts/PcbRoutedStripCauseContractTest.java',
    'tests/contracts/PcbRoutedStripCauseContractTestGroup2.java']
(HERE / 'source-review.json').write_text(json.dumps({'status': 'PASS',
    'scope': 'Root reviewed observer core diff, immutable capture/signature/lifecycle and both native fixtures; original physical observation body exact after line-ending normalization; original shift/placement compactor body exact; no extra physical validation calls; both baseline literal rows and independent canonical token lengths verified',
    'sourceHashes': {p: sha(COPY / p) for p in owners},
    'preRuntimeCorrection': 'Undeclared axial180 fixture replaced with legal zero-pose nonuniform-origin fixture; no catalog/validator change',
    'limits': 'Static review only; standard passive quarter-turn coverage NOT CLAIMED; native runtime and selected binding remain pending'}, indent=2) + '\n')
print('PASS root static core/adapter/literal/lifecycle review; runtime NOT RUN')
