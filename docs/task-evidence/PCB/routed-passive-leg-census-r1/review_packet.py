"""Recheck final census evidence after the owned COPY has been retired."""
import hashlib
import json
from pathlib import Path

import analyze
import assess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'], seal['copyCount']) == (1467, 1469)
assert len(seal['overrides']) == 3 and len(seal['additions']) == 2
assert len(seal['gateOwners']) == 28
for name, digest in seal['gateOwners'].items():
    assert sha(HERE/name) == digest, name
for name, digest in dict(seal['overrides'], **seal['additions']).items():
    assert sha(HERE/'source'/name) == digest, name
main_seal = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == seal['baseMainSealSha256']
main = read(main_seal)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'], row['path']
prior_dir = HERE.parent/'successful-route-pool-r1'
assert sha(prior_dir/'native-inputs.json') == seal['publishedPoolSealSha256']
prior = read(prior_dir/'native-inputs.json')
for name, digest in prior['gateOwners'].items():
    assert sha(prior_dir/name) == digest, name
for name, digest in dict(prior['overrides'], **prior['additions']).items():
    assert sha(prior_dir/'source'/name) == digest, name

def erased(path, start, end, count):
    kept = []
    inside = False
    found = 0
    for line in (HERE/'source'/path).read_bytes().splitlines(keepends=True):
        if line.strip() == start:
            assert not inside
            inside = True
            found += 1
        elif line.strip() == end:
            assert inside
            inside = False
        elif not inside:
            kept.append(line)
    assert not inside and found == count
    assert b''.join(kept) == (ROOT/path).read_bytes()

erased('src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
    b'// BEGIN COPY-ONLY successful-route-pool observer.',
    b'// END COPY-ONLY successful-route-pool observer.', 2)
erased('tests/contracts/PcbPostSelectionEdgeTrimContractTest.java',
    b'// BEGIN COPY-ONLY accepted15 routed-leg witness.',
    b'// END COPY-ONLY accepted15 routed-leg witness.', 1)
registry = 'scripts/verify-current-contracts.ps1'
raw = (HERE/'source'/registry).read_bytes()
for manifest in ('unit-suites.json', 'routes-suites.json'):
    for name, marker in read(HERE/manifest):
        line = ("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
        assert raw.count(line) == 1
        raw = raw.replace(line, b'')
assert raw == (ROOT/registry).read_bytes()

contract = assess._load_contract()
unit_summary, unit_inputs, unit_manifest_sha = assess._read_phase_files('unit', contract)
unit = assess._unit_validation(contract, unit_summary, unit_inputs, unit_manifest_sha)
assert assess._typed_equal(unit, read(HERE/'unit-validation.json'))
unit_sha = assess._validate_unit_prerequisite((HERE/'unit-validation.json').read_bytes(), unit)
route_summary, route_inputs, route_manifest_sha = assess._read_phase_files('routes', contract)
assessment = assess._routes_assessment(contract, route_summary, route_inputs,
    route_manifest_sha, unit_sha)
assert assess._typed_equal(assessment, read(HERE/'assessment.json'))
assert analyze.result() == read(HERE/'census-analysis.json')
canaries = read(HERE/'assessment-canary-results.json')
ack = read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True
assert ack['diffSha256'] == sha(HERE/'source-review.diff')
assert canaries['status'] == 'PASS' and canaries['canaryCount'] == ack['syntheticReaderCases']
assert canaries['fixtureCount'] == 6
assert read(HERE/'suite-transport-canaries.json')['caseCount'] == 12
census = route_summary['census']
assert (census['roots'], census['rows'], census['selectedCaseRows'], census['whitespaceRows']) == (2, 2, 2, 2)
drivers = 0
for phase in ('unit', 'routes'):
    cleanup = read(HERE/(phase+'-process-cleanup.json'))
    launch = read(HERE/(phase+'-process-launch.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers']) == len(cleanup['drivers']) == 2
    for before, after in zip(launch['drivers'], cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid', 'startTicks', 'executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
assert read(HERE/'copy-restoration.json') == {
    'status':'PASS', 'fromCount':1469, 'restoredCount':1467, 'main1475Exact':True}
assert read(HERE/'copy-cleanup.json') == {'status':'PASS', 'verifiedExactFilesRemoved':1467,
    'privateStateRemoved':True, 'symlinkOrReparseBoundariesCrossed':False, 'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'status':'PASS', 'scope':'Read-only structural census; no physical move or policy change',
    'baseCommit':contract['protocol']['baseCommit'], 'main1475Exact':True,
    'publishedPoolInputsExact':True, 'frozenGates':28, 'sourceDeltas':5,
    'acceptedGeneratorWitnessRegistryErasureExact':True,
    'syntheticReaderCases':canaries['canaryCount'], 'actualTransportCases':12,
    'unitSuites':1, 'unitFixtures':6, 'routeSuites':1, 'selectedRowsExact42Fields':2,
    'successfulCallbacks':census['successfulCallbacks'],
    'recordedDriverBirthsExited':drivers, 'copyRestored1467AndRetired':True,
    'physicalFeasibility':'NOT_EVALUATED', 'productionPolicyFitnessChange':'NONE',
    'GwtPlayerSolverFullMatrixAllSeed':'NOT_RUN'}
target = HERE/'root-review.json'
if target.exists():
    assert read(target) == result, 'Prior root review differs; preserve it'
else:
    target.write_text(json.dumps(result, indent=2)+'\n')
print('PASS integrated28gates/5deltas/2selected rows; unchanged main1475/owned cleanup')
