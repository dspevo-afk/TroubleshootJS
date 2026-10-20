"""Recheck the complete private proof after retiring its owned source COPY."""
import hashlib
import json
from pathlib import Path

import analyze
import assess
import check_assessment

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'], seal['copyCount']) == (1467, 1470)
assert len(seal['overrides']) == len(seal['additions']) == 3
assert len(seal['gateOwners']) == 29
for group, folder in (('gateOwners', HERE), ('overrides', HERE/'source'),
                      ('additions', HERE/'source'),
                      ('preparationFailureArtifacts', HERE), ('nativeFailureArtifacts', HERE),
                      ('nativeRuntimeFailureArtifacts', HERE)):
    for name, digest in seal[group].items():
        assert sha(folder/name) == digest, name
main_seal = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == seal['baseMainSealSha256']
main = read(main_seal)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'], row['path']
assert sha(OLD/'native-inputs.json') == seal['publishedCensusSealSha256']
prior = read(OLD/'native-inputs.json')
for group, folder in (('gateOwners', OLD), ('overrides', OLD/'source'),
                      ('additions', OLD/'source')):
    for name, digest in prior[group].items():
        assert sha(folder/name) == digest, name

def stripped(raw, begin, end, count):
    kept = []
    inside = False
    found = 0
    for line in raw.splitlines(keepends=True):
        if line.strip() == begin:
            assert not inside
            inside = True
            found += 1
        elif line.strip() == end:
            assert inside
            inside = False
        elif not inside:
            kept.append(line)
    assert not inside and found == count
    return b''.join(kept)

generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
helper = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
census = 'tests/contracts/PcbRoutedPassiveLegCensusContractTest.java'
for path, begin, end, count in (
    (generator, b'// BEGIN COPY-ONLY successful-route-pool observer.',
     b'// END COPY-ONLY successful-route-pool observer.', 2),
    (helper, b'// BEGIN COPY-ONLY accepted15 routed-leg witness.',
     b'// END COPY-ONLY accepted15 routed-leg witness.', 1)):
    raw = (HERE/'source'/path).read_bytes()
    assert raw == (OLD/'source'/path).read_bytes()
    assert stripped(raw, begin, end, count) == (ROOT/path).read_bytes()
assert stripped((HERE/'source'/census).read_bytes(),
    b'// BEGIN COPY-ONLY private move proof bridge.',
    b'// END COPY-ONLY private move proof bridge.', 2) == (OLD/'source'/census).read_bytes()
registry = 'scripts/verify-current-contracts.ps1'
raw = (HERE/'source'/registry).read_bytes()
for manifest in ('unit-suites.json', 'routes-suites.json'):
    for name, marker in read(HERE/manifest):
        line = ("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
        assert raw.count(line) == 1
        raw = raw.replace(line, b'')
assert raw == (ROOT/registry).read_bytes()

contract = assess._load_contract()
prior_contract = assess._load_prior_contract(contract)
unit_summary, unit_inputs, unit_manifest_sha = assess._read_phase_files('unit', contract, prior_contract)
unit = assess._unit_validation(contract, unit_summary, unit_inputs, unit_manifest_sha)
assert assess._typed_equal(unit, read(HERE/'unit-validation.json'))
unit_sha = assess._validate_unit_prerequisite((HERE/'unit-validation.json').read_bytes(), unit)
assert not (HERE/'assessment.json').exists(), 'Failed route proof must never have a PASS assessment'
assert not (HERE/'routes-receipt.txt').exists(), 'Unqualified failed JVM has no suite PASS receipt'
reader_rejection = read(HERE/'routes-reader-rejection.json')
assert reader_rejection['exitCode'] == 2 and reader_rejection['status'] == 'FAIL'
assert reader_rejection['outputSha256'] == sha(HERE/'routes-reader-rejection.txt')
assert reader_rejection['routesLogSha256'] == sha(HERE/'routes.txt')
assert analyze.result() == read(HERE/'move-analysis.json')
canaries = read(HERE/'assessment-canary-results.json')
assert check_assessment._run_canaries() == canaries
ack = read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256'] == sha(HERE/'source-review.diff')
assert canaries['canaryCount'] == ack['syntheticReaderCases'] == 36
assert canaries['fixtureCounts'] == contract['protocol']['unitFixtureCounts']
assert read(HERE/'suite-transport-canaries.json')['caseCount'] == 12
def reports(folder, prefix):
    return [json.loads(line[len(prefix):])
        for line in (folder/'routes.txt').read_text(encoding='utf-8-sig').splitlines()
        if line.startswith(prefix)]

protocol = contract['protocol']
selected = reports(HERE, assess.CASE_PREFIX)
published = reports(OLD, assess.CASE_PREFIX)
assert len(selected) == 1 and selected[0]['seed'] == '5'
before = next(row for row in published if row['seed'] == '5')
assert assess._typed_equal({k:v for k,v in selected[0].items() if k != 'elapsedMillis'},
    {k:v for k,v in before.items() if k != 'elapsedMillis'})
whitespace = reports(HERE, assess.WHITESPACE_PREFIX)
prior_whitespace = reports(OLD, assess.WHITESPACE_PREFIX)
assert len(whitespace) == 1
assert assess._typed_equal(whitespace[0], next(r for r in prior_whitespace if r['seed'] == '5'))
drivers = 0
for phase, folder in (('unit', HERE/'native-attempt-1'),
                       ('unit', HERE/'native-attempt-2'), ('routes', HERE/'native-attempt-2'),
                       ('unit', HERE), ('routes', HERE)):
    cleanup = read(folder/(phase+'-process-cleanup.json'))
    launch = read(folder/(phase+'-process-launch.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers']) == len(cleanup['drivers']) == 2
    for before, after in zip(launch['drivers'], cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid', 'startTicks', 'executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
failure = read(HERE/'native-attempt-1/failure.json')
assert failure['status'] == 'FAIL' and failure['physicalMoveExecution'] == 'NOT_RUN'
runtime_failure = read(HERE/'native-attempt-2/failure.json')
assert runtime_failure['status'] == 'FAIL' and runtime_failure['physicalValidators'] == 'NOT_REACHED'
for folder, phase in ((HERE/'native-attempt-1', 'unit'),
                       (HERE/'native-attempt-2', 'routes'), (HERE, 'routes')):
    receipt_path = folder/(phase+'-receipt.txt')
    receipt = receipt_path.read_text(encoding='utf-8-sig') if receipt_path.exists() else ''
    try:
        assess.check_phase((folder/(phase+'.txt')).read_text(encoding='utf-8-sig'),
            receipt, read(folder/(phase+'-timing.json')), read(folder/(phase+'-suites.json')), phase)
    except assess.ReaderError:
        pass
    else:
        raise AssertionError('Reader accepted preserved native failure: '+str(folder))
assert read(HERE/'copy-restoration.json') == {
    'status':'PASS', 'fromCount':1470, 'restoredCount':1467, 'main1475Exact':True}
assert read(HERE/'copy-cleanup.json') == {'status':'PASS', 'verifiedExactFilesRemoved':1467,
    'privateStateRemoved':True, 'symlinkOrReparseBoundariesCrossed':False, 'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'integrityReviewStatus':'PASS', 'nativePrivateProofStatus':'FAIL',
    'scope':'Reviewed failed first-canonical private construction; no admission or production change',
    'baseCommit':contract['protocol']['baseCommit'], 'main1475Exact':True,
    'publishedCensusInputsExact':True, 'frozenGates':29, 'sourceDeltas':6,
    'acceptedAndPriorOwnerErasureExact':True, 'syntheticReaderCases':36,
    'actualTransportCases':12, 'unitSuites':2, 'unitFixtureCounts':canaries['fixtureCounts'],
    'routeSuitesFailed':1, 'selectedRowsExact42Fields':1,
    'seed2SelectedFidelity':'NOT_COMPLETED_AFTER_PROPAGATED_FAILURE',
    'privateConstructionsReachedGeometryValidator':1, 'physicallyAdmittedPrivateMoves':0,
    'sourceAndCandidatePairProof':'NOT_REACHED',
    'reachabilityOracleSha256':'9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7',
    'preservedNativeCompilationFailures':1, 'preservedNativeConstructionFailures':1,
    'nativeFailureReaderRejections':3,
    'recordedDriverBirthsExited':drivers,
    'copyRestored1467AndRetired':True, 'boardShrinkage':'NOT_DEMONSTRATED',
    'productionPolicyFitnessChange':'NONE', 'GwtPlayerSolverFullMatrixAllSeed':'NOT_RUN'}
target = HERE/'root-review.json'
if target.exists():
    assert read(target) == result, 'Prior root review differs; preserve it'
else:
    target.write_text(json.dumps(result, indent=2)+'\n')
print('PASS integrity29gates/6deltas/owned cleanup; native private proof FAIL/courtyard rejection')
