"""Review completed observation evidence independently of COPY availability."""
import hashlib
import json
import re
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'global-pocket-descent-trial-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount']) == (1467,1470)
assert len(seal['overrides']) == 8 and len(seal['additions']) == 3
assert len(seal['gateOwners']) == 26
for name,digest in seal['gateOwners'].items():
    assert sha(HERE/name) == digest,name
for name,digest in dict(seal['overrides'],**seal['additions']).items():
    assert sha(HERE/'source'/name) == digest,name
main_seal = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == seal['baseMainSealSha256']
main = read(main_seal)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'],row['path']
prior = read(OLD/'native-inputs.json')
assert sha(OLD/'native-inputs.json') == seal['prior22SealSha256']
for name,digest in prior['gateOwners'].items():
    assert sha(OLD/name) == digest,name
generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
inside = False
kept = []
blocks = 0
for line in (HERE/'source'/generator).read_bytes().splitlines(keepends=True):
    if line.strip() == b'// BEGIN COPY-ONLY successful-route-pool observer.':
        assert not inside
        inside = True
        blocks += 1
    elif line.strip() == b'// END COPY-ONLY successful-route-pool observer.':
        assert inside
        inside = False
    elif not inside:
        kept.append(line)
assert not inside and blocks == 2
assert b''.join(kept) == (OLD/'source'/generator).read_bytes()
contract = assess._contract()
old,protocol,_,_,selected,whitespace = contract
unit_summary,unit_inputs,unit_manifest_sha = assess._read_phase_files(
    'unit',protocol,old,selected,whitespace)
unit = assess._unit_validation(contract,unit_summary,unit_inputs,unit_manifest_sha)
assert old._typed_equal(unit,read(HERE/'unit-validation.json'))
unit_sha = assess._validate_unit_prerequisite(
    (HERE/'unit-validation.json').read_bytes(),unit,old)
route_summary,route_inputs,route_manifest_sha = assess._read_phase_files(
    'routes',protocol,old,selected,whitespace)
assessment = assess._routes_assessment(contract,route_summary,route_inputs,
    route_manifest_sha,unit_sha)
assert old._typed_equal(assessment,read(HERE/'assessment.json'))
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['canaryCount'] == 55
assert canaries['fixtureCount'] == 4
assert read(HERE/'suite-transport-canaries.json')['caseCount'] == 12
assert (route_summary['census']['roots'],route_summary['census']['pairs'],
    route_summary['census']['rows']) == (2,2,4)
prefix = protocol['casePrefix']
rows = [json.loads(line[len(prefix):]) for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith(prefix)]
assert [(r['seed'],r['version']) for r in rows] == [(s,v) for s in ('5','2') for v in (15,22)]
for row in rows:
    baseline = selected[(row['seed'],row['version'])]
    assert {k:v for k,v in row.items() if k != 'elapsedMillis'} == {
        k:v for k,v in baseline.items() if k != 'elapsedMillis'}
events = route_summary['census']['cases']
assert len(events) == 4
pool_count = 0
for case in events:
    row = selected[(case['seed'],case['version'])]
    expected = [(m[0],int(m[1])) for m in re.findall(
        r'(P05_ONE_FACE|P07_FULLER_TWO_LAYER)@([0-5])=SUCCESS',row['statisticsCanonical'])]
    pool = case['successfulRoutePool']
    assert [(e['strategy'],e['attempt']) for e in pool] == expected
    for index,event in enumerate(pool):
        assert event['routeIndex'] == index and event['phase'] == 'FINALIZED_ROUTE_CANDIDATE'
        assert event['sourceGraphBeforeSha256'] == event['sourceGraphAfterSha256'] == row['liveBoardGraphSha256']
        assert event['area'] == event['rawOutline']['width']*event['rawOutline']['height']
    pool_count += len(pool)
assert pool_count == 5
analysis = read(HERE/'pool-analysis.json')
assert analysis['status'] == 'PASS' and analysis['assessmentSha256'] == sha(HERE/'assessment.json')
assert analysis['routesLogSha256'] == sha(HERE/'routes.txt')
drivers = 0
for phase in ('unit','routes'):
    cleanup = read(HERE/(phase+'-process-cleanup.json'))
    launch = read(HERE/(phase+'-process-launch.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers']) == len(cleanup['drivers']) == 2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
assert read(HERE/'copy-restoration.json') == {
    'status':'PASS','fromCount':1470,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json') == {'status':'PASS','verifiedExactFilesRemoved':1467,
    'privateStateRemoved':True,'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'status':'PASS','scope':'Completed observation only; published22 remains rejected',
    'baseCommit':protocol['baseCommit'],'main1475Exact':True,'prior22InputsExact':True,
    'frozenGates':26,'sourceDeltas':11,'observerErasureExact22':True,
    'syntheticReaderCases':55,'actualTransportCases':12,'unitSuites':1,'unitFixtures':4,
    'routeSuites':2,'selectedRowsExact42Fields':4,'successfulPoolEvents':5,
    'recordedDriverBirthsExited':drivers,'copyRestored1467AndRetired':True,
    'productionPolicyFitnessChange':'NONE','GwtPlayerSolverFullMatrixAllSeed':'NOT_RUN'}
target = HERE/'root-review.json'
if target.exists():
    assert read(target) == result,'Prior root review differs; preserve it'
else:
    target.write_text(json.dumps(result,indent=2)+'\n')
print('PASS integrated26gates/11deltas/4selected rows/5pool events; unchanged policy/main1475/owned cleanup')
