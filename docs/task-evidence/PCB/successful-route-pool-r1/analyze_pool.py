"""Compare successful finalized alternatives at the same preselection phase."""
import hashlib
import json
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert not (HERE/'pool-analysis.json').exists()
contract = assess._contract()
old,protocol,_,_,selected,whitespace = contract
summary,inputs,manifest_sha = assess._read_phase_files(
    'routes',protocol,old,selected,whitespace)
unit_summary,unit_inputs,unit_manifest_sha = assess._read_phase_files(
    'unit',protocol,old,selected,whitespace)
unit = assess._unit_validation(contract,unit_summary,unit_inputs,unit_manifest_sha)
assert old._typed_equal(unit,read(HERE/'unit-validation.json'))
unit_sha = assess._validate_unit_prerequisite(
    (HERE/'unit-validation.json').read_bytes(),unit,old)
assessment = assess._routes_assessment(contract,summary,inputs,manifest_sha,unit_sha)
assert old._typed_equal(assessment,read(HERE/'assessment.json'))
cases = {(r['seed'],r['version']):r for r in summary['census']['cases']}
metrics = ('area','copperManhattanLength','vias','routeQualityMilli')

def subset(event):
    return {k:event[k] for k in ('strategy','attempt','phase')+metrics}

def delta(first,second):
    return {k:second[k]-first[k] for k in metrics}

comparisons = []
for seed in protocol['roots']:
    before = cases[(seed,15)]
    after = cases[(seed,22)]
    incumbent = next(e for e in before['successfulRoutePool']
        if e['strategy'] == before['selectedStrategy'] and e['attempt'] == before['selectedAttempt'])
    for event in after['successfulRoutePool']:
        assert incumbent['phase'] == event['phase'] == protocol['phase']
        comparisons.append({'seed':seed,'baselineFinalizedWinner':subset(incumbent),
            'trialFinalizedAlternative':subset(event),
            'samePhaseDeltas':delta(incumbent,event),
            'trialSelected':event['strategy'] == after['selectedStrategy'] and
                event['attempt'] == after['selectedAttempt']})
checks = []
for (seed,version),case in cases.items():
    chosen = next(e for e in case['successfulRoutePool']
        if e['strategy'] == case['selectedStrategy'] and e['attempt'] == case['selectedAttempt'])
    same_strategy = [e for e in case['successfulRoutePool'] if e['strategy'] == chosen['strategy']]
    assert all(e['preferCompact'] is True and e['preferDensePacking'] is False
        for e in same_strategy)
    minimum = min(e['area'] for e in same_strategy)
    assert chosen['area'] == minimum
    checks.append({'seed':seed,'version':version,'selectedAttempt':chosen['attempt'],
        'selectedAreaIsStrategyMinimum':True,
        'minimumAreaTieCount':sum(e['area'] == minimum for e in same_strategy),
        'finalizedArea':chosen['area'],
        'postTrimArea':selected[(seed,version)]['area'],
        'borderAreaRemoved':chosen['area']-selected[(seed,version)]['area']})
    assert checks[-1]['borderAreaRemoved'] >= 0
result = {'status':'PASS','scope':'Descriptive already-routed alternatives only; no new routing or policy acceptance',
    'phase':protocol['phase'],'assessmentSha256':sha(HERE/'assessment.json'),
    'routesLogSha256':sha(HERE/'routes.txt'),'samePhaseComparisons':comparisons,
    'selectionChecks':checks,'limits':[
        'No protected-empty metric for unselected alternatives; this is not four-metric fitness',
        'Candidate area/quality precede selected-only border trim',
        'Rounded quality cannot resolve sub-milli tie differences; only minimum-area checks claimed',
        'Two informed roots only; no individual-move, unseen-root, performance or production claim']}
(HERE/'pool-analysis.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'status':'PASS','samePhaseComparisons':len(comparisons),'selectionChecks':len(checks)}))
