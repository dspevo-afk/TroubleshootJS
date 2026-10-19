"""Root post-run integrity review; keep native, fitness and promotion distinct."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda n: json.loads((HERE/n).read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read('native-inputs.json')
assert seal['copyCount'] == 1528 and len(seal['overrides']) == 12 and len(seal['additions']) == 18
assert len(seal['gateOwners']) == 33
for name, digest in seal['gateOwners'].items(): assert sha(HERE/name) == digest, name
for name, digest in dict(seal['overrides'], **seal['additions']).items(): assert sha(HERE/'source'/name) == digest, name
assert sha(HERE/'copy-inputs.json') == seal['baseSha256']
assert read('native-input-audit.json')['status'] == 'PASS'
review = read('source-review.json')
assert review['status'] == 'PASS' and sha(HERE/'source-review.diff') == review['diffSha256']
assert sha(HERE/'review-ack.json') == review['rootAcknowledgementSha256']
canary = read('assessment-canary-results.json')
assert canary['status'] == 'PASS' and canary['nativeRuntimeEvidence'] == 'NOT RUN'
for name, digest in canary['consumedSha256'].items():
    path = HERE.parent/name if name.startswith('passive-pose-policy-r1/') else HERE/name
    assert sha(path) == digest, name
assert read('unit-validation.json')['status'] == 'PASS'
assert len(read('unit-validation.json')['rawOtherCandidateChecks']) == 15
unit_time, routes_time = read('unit-timing.json'), read('routes-timing.json')
assert unit_time['status'] == 'PASS' and unit_time['exitCode'] == 0 and unit_time['suiteCount'] == 8
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
rows = [json.loads(s[len('PCB_SOURCE_SIZED_POSE_ROUTE '):]) for s in text.splitlines() if s.startswith('PCB_SOURCE_SIZED_POSE_ROUTE ')]
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
assert read('copy-restoration.json')['restorationCounts'] == [1510, 1504, 1467]
assert not (HERE/'local-state.json').exists()
main = json.loads((HERE.parent/'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(main) == 1475
for row in main: assert sha(ROOT/row['path']) == row['sha256']
report = {'status': 'PASS', 'scope': 'Root integrated diff, phase/fitness evidence and resource-integrity audit',
    'nativeInputs': 1528, 'sourceDeltas': 30, 'gateOwners': 33,
    'nativeUnitStatus': unit_time['status'], 'nativeRouteStatus': routes_time['status'],
    'unitElapsedSeconds': unit_time['elapsedSeconds'], 'routesElapsedSeconds': routes_time['elapsedSeconds'],
    'fullRouteRows': len(rows), 'oldRowsExact42NonTimeFields': len(old_rows),
    'comparison': comparison, 'strictFitness': fitness, 'canaryCount': canary['canaryCount'],
    'recordedDriverBirthsExited': drivers, 'copyRetired': True, 'main1475Exact': True,
    'productionPromotion': False, 'GwtAndPlayerValidation': 'NOT RUN; private native diagnostic only',
    'limits': 'Finite informed source/physical regression scope; no independent runtime, all-seed, manufacturing, live replay/repair or player acceptance proof'}
(HERE/'root-review.json').write_text(json.dumps(report, indent=2)+'\n')
print('PASS packet integrity; native routes='+routes_time['status']+'; strictFitness='+fitness+'; production promotion=false')
