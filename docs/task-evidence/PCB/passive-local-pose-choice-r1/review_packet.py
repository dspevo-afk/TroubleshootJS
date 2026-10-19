"""Root post-run integrity review; keep native, fitness and promotion distinct."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda n: json.loads((HERE/n).read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read('native-inputs.json')
assert seal['copyCount'] == 1547 and len(seal['overrides']) == 13 and len(seal['additions']) == 19
assert len(seal['gateOwners']) == 32
for name, digest in seal['gateOwners'].items(): assert sha(HERE/name) == digest, name
for name, digest in dict(seal['overrides'], **seal['additions']).items(): assert sha(HERE/'source'/name) == digest, name
assert sha(HERE/'copy-inputs.json') == seal['baseSha256']
assert read('native-input-audit.json')['status'] == 'PASS'
review = read('source-review.json')
assert review['status'] == 'PASS' and sha(HERE/'source-review.diff') == review['diffSha256']
assert sha(HERE/'review-ack.json') == review['rootAcknowledgementSha256']
failed_attempts = []
failed_driver_births = 0
for name in ('attempt1', 'attempt2'):
    folder = HERE/name
    manifest = json.loads((folder/'archive-sha256.json').read_text())
    assert manifest['status'] == 'PASS'
    for path, digest in manifest['files'].items(): assert sha(folder/path) == digest, (name, path)
    previous = json.loads((folder/'native-inputs.json').read_text())
    assert previous['copyCount'] == 1547 and len(previous['gateOwners']) == 32
    assert len(previous['overrides']) == 13 and len(previous['additions']) == 19
    for path, digest in previous['gateOwners'].items(): assert sha(folder/path) == digest
    for path, digest in dict(previous['overrides'], **previous['additions']).items():
        assert sha(folder/'source'/path) == digest
    timing = json.loads((folder/'unit-timing.json').read_text(encoding='utf-8-sig'))
    cleanup = json.loads((folder/'unit-process-cleanup.json').read_text(encoding='utf-8-sig'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers']) == 2 and all(r['outcome'] == 'EXITED' for r in cleanup['drivers'])
    assert json.loads((folder/'unit-log-sanitization.json').read_text())['status'] == 'PASS'
    if name == 'attempt1':
        assert timing['status'] == 'FAIL' and timing['exitCode'] == 2
        assert 'Topology graph requires a connector component' in (folder/'unit.txt').read_text()
        assert not (folder/'unit-receipt.txt').exists()
        outcome = 'Native FAIL before loop; advisory fixture lacked required connector'
    else:
        assert timing['status'] == 'PASS' and timing['exitCode'] == 0
        assert json.loads((folder/'unit-validation.json').read_text())['status'] == 'FAIL'
        assert previous['overrides'] == seal['overrides'] and previous['additions'] == seal['additions'], 'Reader correction changed Java inputs'
        outcome = 'Native PASS; prerequisite reader FAIL on PASS-summary grammar; not reused'
    failed_driver_births += 2
    failed_attempts.append({'name': name, 'nativeStatus': timing['status'],
        'elapsedSeconds': timing['elapsedSeconds'], 'archiveFiles': len(manifest['files']),
        'outcome': outcome, 'gateOwnersExact': True, 'recordedDriverBirthsExited': 2})
canary = read('assessment-canary-results.json')
assert canary['status'] == 'PASS' and canary['nativeRuntimeEvidence'] == 'NOT RUN'
for name, digest in canary['consumedSha256'].items():
    path = HERE.parent/name if name.startswith('passive-pose-policy-r1/') else HERE/name
    assert sha(path) == digest, name
assert read('unit-validation.json')['status'] == 'PASS'
assert len(read('unit-validation.json')['rawOtherCandidateChecks']) == 15
unit_time, routes_time = read('unit-timing.json'), read('routes-timing.json')
assert unit_time['status'] == 'PASS' and unit_time['exitCode'] == 0 and unit_time['suiteCount'] == 9
assert routes_time['status'] in ('PASS', 'FAIL') and routes_time['suiteCount'] == 14
assert routes_time['exitCode'] == (0 if routes_time['status'] == 'PASS' else 2)
drivers = 0
for phase in ('unit', 'routes'):
    cleanup = read(phase+'-process-cleanup.json')
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers']) == 2
    assert all(row['outcome'] == 'EXITED' and row['sameCreationStillRunning'] is False for row in cleanup['drivers'])
    drivers += len(cleanup['drivers'])
    assert read(phase+'-log-sanitization.json')['status'] == 'PASS'
text = (HERE/'routes.txt').read_text(encoding='utf-8-sig')
rows = [json.loads(s[len('PCB_LOCAL_POSE_ROUTE '):]) for s in text.splitlines() if s.startswith('PCB_LOCAL_POSE_ROUTE ')]
old = {row['seed']: row for row in read('accepted15-baselines.json')['rows']}
old_rows = [row for row in rows if row['version'] == 15]
for row in old_rows:
    baseline = old[row['seed']]
    assert len(row) == 43 and set(row) == set(baseline)
    assert {k: v for k, v in row.items() if k != 'elapsedMillis'} == {
        k: v for k, v in baseline.items() if k != 'elapsedMillis'}
assessment = read('assessment.json')
if routes_time['status'] == 'PASS':
    assert len(rows) == 28 and len(old_rows) == 14
    assert assessment['fidelity']['status'] == 'PASS' and assessment['fidelity']['saved15ExactCases'] == 14
    assert assessment['status'] == assessment['strictFitness']['status']
    comparison = 'COMPLETE'
    fitness = assessment['strictFitness']['status']
else:
    assert len(rows) < 28 and assessment['status'] == 'FAIL'
    assert 'CURRENT_CONTRACT_FAILURE:' in text
    comparison, fitness = 'INCOMPLETE', 'NOT ESTABLISHED; native failure'
for name in ('copy-restoration.json', 'copy-cleanup.json'): assert read(name)['status'] == 'PASS'
assert read('copy-restoration.json')['restorationCounts'] == [1528, 1510, 1504, 1467]
assert not (HERE/'local-state.json').exists()
main = json.loads((HERE.parent/'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(main) == 1475
for row in main: assert sha(ROOT/row['path']) == row['sha256']
report = {'status': 'PASS', 'scope': 'Root integrated diff, phase/fitness evidence and resource-integrity audit',
    'nativeInputs': 1547, 'sourceDeltas': 32, 'gateOwners': 32,
    'nativeUnitStatus': unit_time['status'], 'nativeRouteStatus': routes_time['status'],
    'unitElapsedSeconds': unit_time['elapsedSeconds'], 'routesElapsedSeconds': routes_time['elapsedSeconds'],
    'fullRouteRows': len(rows), 'oldRowsExact42NonTimeFields': len(old_rows),
    'comparison': comparison, 'strictFitness': fitness, 'canaryCount': canary['canaryCount'],
    'recordedDriverBirthsExited': drivers+failed_driver_births, 'preservedAttempts': failed_attempts,
    'finalPhaseDriverBirthsExited': drivers, 'copyRetired': True, 'main1475Exact': True,
    'productionPromotion': False, 'GwtAndPlayerValidation': 'NOT RUN; private native diagnostic only',
    'limits': 'Finite informed source/physical regression scope; no independent runtime, all-seed, manufacturing, live replay/repair or player acceptance proof'}
(HERE/'root-review.json').write_text(json.dumps(report, indent=2)+'\n')
print('PASS packet integrity; native routes='+routes_time['status']+'; strictFitness='+fitness+'; production promotion=false')
