"""Root post-run evidence audit; no extra solver or visual claims."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda name: json.loads((HERE/name).read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read('native-inputs.json')
assert seal['copyCount'] == 1510
assert len(seal['overrides']) == 12 and len(seal['additions']) == 6
for name, digest in seal['gateOwners'].items(): assert sha(HERE/name) == digest, name
for name, digest in dict(seal['overrides'], **seal['additions']).items():
    assert sha(HERE/'source'/name) == digest, name
assert sha(HERE/'copy-inputs.json') == seal['baseSha256']
assert read('native-input-audit.json')['status'] == 'PASS'
static = read('source-review.json')
assert static['status'] == 'PASS' and sha(HERE/'source-review.diff') == static['diffSha256']
canary = read('assessment-canary-results.json')
assert canary['status'] == 'PASS' and canary['failedCases'] == 0
assert canary['syntheticOnly'] is True and canary['newNativeOutputsRead'] is False
for name, digest in canary['consumedSha256'].items():
    if name == 'priorPolicyAssess.py': path = HERE.parent/'passive-pose-policy-r1/assess.py'
    elif name == 'publishedPolicyUnit.txt': path = HERE.parent/'passive-pose-policy-r1/unit.txt'
    else: path = HERE/name
    assert sha(path) == digest, name
assert read('unit-validation.json')['status'] == 'PASS'
assert len(read('unit-validation.json')['rawOtherCandidateChecks']) == 15
assessment = read('assessment.json')
assert assessment == {'status': 'FAIL', 'phase': 'completedArtifactReader', 'errorType': 'FileNotFoundError'}
assert not (HERE/'routes-receipt.txt').exists()
log = (HERE/'routes.txt').read_text(encoding='utf-8-sig')
extract = lambda prefix: [json.loads(s[len(prefix):]) for s in log.splitlines() if s.startswith(prefix)]
rows = extract('PCB_SIZING_CONTROL_ROUTE ')
poses = extract('PCB_SIZING_CONTROL_POSES ')
assert [(r['seed'], r['version']) for r in rows] == [('42', 15), ('42', 16)]
assert [(r['seed'], r['version']) for r in poses] == [('42', 15), ('42', 16)]
for index, (row, pose) in enumerate(zip(rows, poses)):
    baseline = read('route-baselines.json')['rows'][index]
    assert len(row) == 43 and set(row) == set(baseline)
    assert {k: v for k, v in row.items() if k != 'elapsedMillis'} == {
        k: v for k, v in baseline.items() if k != 'elapsedMillis'}
    assert type(row['elapsedMillis']) is int and 0 < row['elapsedMillis'] <= 60000
    assert pose == read('pose-baselines.json')['rows'][index]
spaces = extract('PCB_WHITESPACE_CASE ')
assert [(r['seed'], r['version']) for r in spaces] == [('42', 15), ('42', 16), ('42', 17)]
space = spaces[-1]
assert space['largestProtectedEmptyRectangle'] == {'x': 980, 'y': 0, 'width': 1170, 'height': 660, 'area': 772200}
assert space['largestProtectedEmptyRectangle']['area'] >= 327600
assert 'java.lang.AssertionError: vacancy probes improve the frozen10 protected empty rectangle' in log
assert 'CURRENT_CONTRACT_FAILURE: Current contract did not provide a qualified result: PcbPassiveSizingRouteContractTest, exit 1' in log
assert log.splitlines().count('CLEANUP: current JVM contract classes and task-owned scratch removed.') == 1
failure = {'status': 'FAIL', 'phase': 'root42/control17/maintained protected-whitespace assertion',
           'controlWhitespace': space, 'requiredStrictUpperBound': 327600,
           'old42CasesExactNonTimingFields': 42, 'old42Versions': [15, 16],
           'fullControl17RouteRow': 'NOT PRODUCED: assertion stops before final work/purity/margin/metrics checks',
           'root3AllVersions': 'NOT RUN: maintained verifier stopped at failed first JVM suite',
           'sixCaseComparison': 'INCOMPLETE; no control metric deltas or complete fidelity PASS',
           'readerResult': 'FAIL: completed route receipt absent after native failure',
           'limits': 'Printed whitespace is GRID10 protected-free measurement, not proof of legally packable space'}
(HERE/'failure-analysis.json').write_text(json.dumps(failure, indent=2)+'\n')
failed_attempt = read('attempt1/archive-sha256.json')
assert failed_attempt['status'] == 'PASS'
for name, digest in failed_attempt['files'].items(): assert sha(HERE/'attempt1'/name) == digest, name
assert read('attempt1/unit-validation.json')['status'] == 'FAIL'
assert read('attempt1/unit-timing.json')['status'] == 'PASS'
first_seal = read('attempt1/native-inputs.json')
assert first_seal['overrides'] == seal['overrides'] and first_seal['additions'] == seal['additions']
drivers = 0
times = {}
for phase, count in [('unit', 8), ('routes', 2)]:
    timing = read(phase+'-timing.json')
    status, code = ('PASS', 0) if phase == 'unit' else ('FAIL', 2)
    assert timing['status'] == status and timing['exitCode'] == code and timing['suiteCount'] == count
    times[phase] = timing['elapsedSeconds']
    cleanup = read(phase+'-process-cleanup.json')
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers']) == 2
    assert all(r['outcome'] == 'EXITED' and r['sameCreationStillRunning'] is False for r in cleanup['drivers'])
    drivers += len(cleanup['drivers'])
    assert read(phase+'-log-sanitization.json')['status'] == 'PASS'
for name in ('copy-restoration.json', 'copy-cleanup.json'): assert read(name)['status'] == 'PASS'
assert not (HERE/'local-state.json').exists()
main = json.loads((HERE.parent/'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(main) == 1475
for row in main: assert sha(ROOT/row['path']) == row['sha256'], row['path']
report = {'status': 'PASS', 'scope': 'Root integrated diff, preserved rejection and packet-integrity audit; native control remains FAIL',
          'nativeCopyCount': 1510, 'sourceDeltaCount': 18, 'gateOwnerCount': len(seal['gateOwners']),
          'nativeControlStatus': 'FAIL', 'sixCaseComparison': 'INCOMPLETE',
          'nativePrerequisiteSuitesPassed': 8, 'routeSuitesRequested': 2, 'routeSuitesPassed': 0,
          'routeCasesRequested': 6, 'fullRouteRowsProduced': 2, 'oldCasesExact': 2, 'rawCasesExact': 15,
          'sourceRoots': 14, 'readerCanaries': canary['caseCount'],
          'readerExpectationAssertions': canary['canaryExpectationAssertions'],
          'firstAttemptReaderFailurePreserved': True,
          'failedAttemptArtifactCount': len(failed_attempt['files']),
          'freshNativeExecutionAfterDeclarationCorrection': True,
          'phaseElapsedSeconds': times, 'recordedDriverBirthsExited': drivers,
          'copyRetired': True, 'main1475Exact': True,
          'strict14Fitness': 'Existing FAIL remains; no fitness claim from control metrics',
          'productionPromotion': False, 'GwtAndPlayerValidation': 'NOT APPLICABLE: main production unchanged',
          'reviewLimit': 'Static root review and finite native diagnostics; no independent runtime review, all-seed or manufacturing proof'}
(HERE/'root-review.json').write_text(json.dumps(report, indent=2)+'\n')
print('PASS rejection packet integrity / native control FAIL / 2 old exact / six-case comparison INCOMPLETE / main1475 exact / copy retired')
