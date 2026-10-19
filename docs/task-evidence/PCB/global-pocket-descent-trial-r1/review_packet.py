"""Post-runtime independent metric/global-cost/source and owned-cleanup review."""
import hashlib
import json
import re
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount']) == (1467,1476)
assert len(seal['overrides']) == 8 and len(seal['additions']) == 9
assert len(seal['gateOwners']) == 27
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
for name,digest in read(HERE/'source-audit.json')['files'].items():
    assert sha(ROOT/name) == digest,name
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['caseCount'] == 51
for name,digest in canaries['consumedSha256'].items():
    path = ROOT/name if name.startswith('docs/') else HERE/name
    assert sha(path) == digest,name
transport = read(HERE/'suite-transport-canaries.json')
assert transport['status'] == 'PASS' and transport['caseCount'] == 12
for name,digest in transport['consumedSha256'].items():
    assert sha(HERE/name) == digest,name
_proposal,protocol,baseline_data,_doc,baseline_rows,_origin = assess._load_contract()
unit = assess._unit_validation(protocol,baseline_data)
routes = assess._routes_assessment(protocol,baseline_data)
assert assess._typed_equal(unit,read(HERE/'unit-validation.json'))
assert assess._typed_equal(routes,read(HERE/'assessment.json'))
prefix = 'PCB_GLOBAL_POCKET_CASE '
rows = [json.loads(line[len(prefix):]) for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith(prefix)]
assert len(rows) == 28
assert [(r['seed'],r['version']) for r in rows] == [(s,v) for s in protocol['roots'] for v in (15,22)]
metrics = ('area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias')
score_fields = ('total','electrical','region','connector','corridor','outline','courtyard','demand')
pattern = re.compile(r'([0-5])\{'+';'.join(n+r'=([0-9]+)' for n in score_fields)+r'\}')

def scores(row):
    text = row['statisticsCanonical'].split('placementScores=[',1)[1].split('];selectedScore=',1)[0]
    matches = list(pattern.finditer(text))
    assert len(matches) == 6 and ', '.join(m.group(0) for m in matches) == text
    ids = [int(m.group(1)) for m in matches]
    assert set(ids) == set(range(6)) and ids == row['rankedPlacementAttempts']
    result = {int(m.group(1)):dict(zip(score_fields,map(int,m.groups()[1:]))) for m in matches}
    for record in result.values():
        assert record['total'] == 8*record['electrical']+4*record['region']+6*record['connector']+12*record['corridor']+record['outline']//1000+record['courtyard']//1000+record['demand']//1000
    return result

deltas = []
violations = []
global_costs = []
improvement = False
for i,old in enumerate(baseline_rows):
    saved,trial = rows[i*2:i*2+2]
    assert {k:v for k,v in saved.items() if k != 'elapsedMillis'} == {
        k:v for k,v in old.items() if k != 'elapsedMillis'}
    a,b = scores(saved),scores(trial)
    assert all(a[n] == b[n] for n in (0,1,3,4,5))
    assert b[2]['total'] <= a[2]['total']
    global_costs.append({'seed':old['seed'],'candidate2Total15':a[2]['total'],
        'candidate2Total22':b[2]['total'],'delta':b[2]['total']-a[2]['total']})
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
drivers = 0
for phase in ('unit','routes'):
    cleanup = read(HERE/(phase+'-process-cleanup.json'))
    launch = read(HERE/(phase+'-process-launch.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers']) == 2 and len(cleanup['drivers']) == 2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
assert read(HERE/'copy-restoration.json') == {
    'status':'PASS','fromCount':1476,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json') == {'status':'PASS','verifiedExactFilesRemoved':1467,
    'privateStateRemoved':True,'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'status':'PASS','scope':'Integrated native/fitness/global-cost/source/cleanup review; no production promotion',
    'baseCommit':protocol['baseCommit'],'main1475Exact':True,'frozenGates':27,'sourceDeltas':17,
    'syntheticReaderCases':51,'actualTransportCases':12,'unitSuites':4,'routeSuites':7,
    'cases':28,'saved15ExactRows':14,'trial22AdmittedRows':14,'strictFitness':fitness,
    'independentMetricDeltas':deltas,'violations':violations,'globalCosts':global_costs,
    'otherFiveCandidateScoreRecordsExact':True,'recordedDriverBirthsExited':drivers,
    'copyRestored1467AndRetired':True,'productionBuildPlayerSolverAllSeed':'NOT_RUN'}
(HERE/'root-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS integrated global-pocket27gates/17deltas/native28cases; strict fitness '+fitness+'; monotoneglobalcost/exactmain1475/ownedcleanup')
