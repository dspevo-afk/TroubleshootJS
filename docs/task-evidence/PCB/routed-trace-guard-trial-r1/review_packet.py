"""Root's integrated packet audit after native phases and owned COPY retirement."""
import hashlib
import json
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount']) == (1467,1475)
assert len(seal['overrides']) == 7 and len(seal['additions']) == 8
assert len(seal['gateOwners']) == 27
for name,digest in seal['gateOwners'].items():
    assert sha(HERE/name) == digest, name
for name,digest in dict(seal['overrides'],**seal['additions']).items():
    assert sha(HERE/'source'/name) == digest, name
attempt = read(HERE/'attempt1/archive-sha256.json')
assert attempt['status'] == 'PASS' and attempt['nativeStatus'] == 'FAIL'
assert (attempt['artifactCount'],attempt['gateOwners'],attempt['sourceDeltas']) == (49,25,15)
for name,digest in attempt['files'].items():
    assert sha(HERE/'attempt1'/name) == digest,name
old_seal = read(HERE/'attempt1/native-inputs.json')
for name,digest in old_seal['overrides'].items():
    assert seal['overrides'][name] == digest, 'Production/helper/verifier changed after fixture-only failure'
for name,digest in old_seal['additions'].items():
    if name != 'tests/contracts/PcbRoutedTraceGuardContractTest.java':
        assert seal['additions'][name] == digest, name
main_seal = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == seal['baseMainSealSha256']
main = read(main_seal)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'],row['path']
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['caseCount'] == 43
for name,digest in canaries['consumedSha256'].items():
    p = ROOT/name if name.startswith('docs/') else HERE/name
    assert sha(p) == digest,name
transport = read(HERE/'suite-transport-canaries.json')
assert transport['status'] == 'PASS' and transport['caseCount'] == 12
for name,digest in transport['consumedSha256'].items():
    assert sha(HERE/name) == digest,name
_proposal,protocol,baseline_data,_doc,baseline_rows,_origin = assess._load_contract()
unit = assess._unit_validation(protocol,baseline_data)
routes = assess._routes_assessment(protocol,baseline_data)
assert assess._typed_equal(unit,read(HERE/'unit-validation.json'))
assert assess._typed_equal(routes,read(HERE/'assessment.json'))
rows = [json.loads(line[len('PCB_TRACE_GUARD_CASE '):])
    for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith('PCB_TRACE_GUARD_CASE ')]
assert len(rows) == 28
assert [(r['seed'],r['version']) for r in rows] == [(s,v) for s in protocol['roots'] for v in (15,20)]
deltas = []
violations = []
improvement = False
metrics = ('area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias')
for i,old in enumerate(baseline_rows):
    saved,trial = rows[i*2:i*2+2]
    assert {k:v for k,v in saved.items() if k != 'elapsedMillis'} == {
        k:v for k,v in old.items() if k != 'elapsedMillis'}
    delta = {'seed':old['seed']}
    for metric in metrics:
        assert type(trial[metric]) is int and type(old[metric]) is int
        value = trial[metric]-old[metric]
        delta[metric] = value
        if value > 0:
            violations.append({'seed':old['seed'],'metric':metric,'baseline':old[metric],
                'trial':trial[metric],'delta':value})
    improvement |= delta['area'] < 0 or delta['largestProtectedEmptyRectangleArea'] < 0
    deltas.append(delta)
fitness = 'PASS' if improvement and not violations else 'FAIL'
assert deltas == routes['strictFitness']['deltas']
assert violations == routes['strictFitness']['violations']
assert fitness == routes['strictFitness']['status']
parity = read(HERE/'selected-parity.json')
ignored = {'version','density','sourcePlanSha256','elapsedMillis'}
differences = [{'seed':rows[i]['seed'],'changedFieldsExceptVersionDensityPlanTime':
    [k for k in rows[i] if k not in ignored and rows[i][k] != rows[i+1][k]]}
    for i in range(0,len(rows),2)]
assert parity['rows'] == differences and all(not r['changedFieldsExceptVersionDensityPlanTime'] for r in differences)
assert parity['physicalGeometryExactPairs'] == 14 and parity['workAndSelectionExactPairs'] == 14
assert parity['other39NonTimeIdentityFieldsExactPairs'] == 14
drivers = 0
for folder,phase in ((HERE/'attempt1','unit'),(HERE,'unit'),(HERE,'routes')):
    cleanup = read(folder/(phase+'-process-cleanup.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    launch = read(folder/(phase+'-process-launch.json'))
    assert len(launch['drivers']) == 2 and len(cleanup['drivers']) == 2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
restoration = read(HERE/'copy-restoration.json')
assert restoration == {'status':'PASS','fromCount':1475,'restoredCount':1467,'main1475Exact':True}
cleanup = read(HERE/'copy-cleanup.json')
assert cleanup == {'status':'PASS','verifiedExactFilesRemoved':1467,'privateStateRemoved':True,
    'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'status':'PASS','scope':'Integrated source/receipt/strict-fitness/cleanup review; no production promotion',
    'baseCommit':protocol['baseCommit'],'main1475Exact':True,'frozenGates':27,'sourceDeltas':15,
    'syntheticReaderCases':43,'actualTransportCases':12,'unitSuites':3,'routeSuites':7,
    'cases':28,'saved15ExactRows':14,'trial20AdmittedRows':14,'strictFitness':fitness,
    'selectedGeometryAndWorkExactPairs':14,
    'independentMetricDeltas':deltas,'violations':violations,'recordedDriverBirthsExited':drivers,
    'failedAttempt1PreservedArtifacts':49,'failedAttempt1Gates':25,
    'copyRestored1467AndRetired':True,'productionBuildPlayerSolverAllSeed':'NOT_RUN'}
(HERE/'root-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS integrated packet:27gates/15deltas/native28cases; strict fitness '+fitness+'; exactmain1475/ownedcleanup')
