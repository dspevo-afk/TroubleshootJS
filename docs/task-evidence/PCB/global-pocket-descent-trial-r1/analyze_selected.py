"""Describe completed paired receipts; no policy or acceptance decision changes."""
import json
import re
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
assert not (HERE/'selection-analysis.json').exists()
_, protocol, baseline_data, _, _, _ = assess._load_contract()
accepted = assess._routes_assessment(protocol, baseline_data)
assert assess._typed_equal(accepted, read(HERE/'assessment.json'))
prefix = protocol['casePrefix']
rows = [json.loads(line[len(prefix):])
    for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith(prefix)]
assert len(rows) == 28
metrics = ('area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias')
work = ('acceptedPlanEvaluations','routeAttempts','routingOrders','routingExpansions')
detail = []
for index, seed in enumerate(protocol['roots']):
    old, new = rows[2*index:2*index+2]
    assert old['seed'] == new['seed'] == seed
    scores = []
    for row in (old,new):
        match = re.search(r'(?:\[|, )2\{total=([0-9]+);', row['statisticsCanonical'])
        assert match
        scores.append(int(match.group(1)))
    detail.append({
        'seed':seed,
        'geometryExact':old['geometrySha256'] == new['geometrySha256'],
        'selectedAttempt15':old['selectedAttempt'],
        'selectedAttempt22':new['selectedAttempt'],
        'candidate2Total15':scores[0],
        'candidate2Total22':scores[1],
        'candidate2TotalDelta':scores[1]-scores[0],
        'candidate2Rank15':old['rankedPlacementAttempts'].index(2)+1,
        'candidate2Rank22':new['rankedPlacementAttempts'].index(2)+1,
        'metricDeltas':{k:new[k]-old[k] for k in metrics},
        'workDeltas':{k:new[k]-old[k] for k in work},
        'routeOutcomes15':old['statisticsCanonical'].split(';routeOutcomes=',1)[1],
        'routeOutcomes22':new['statisticsCanonical'].split(';routeOutcomes=',1)[1],
    })
result = {
    'status':'PASS',
    'scope':'Descriptive complete native receipt comparison; no move-level causation or general performance claim',
    'strictFitness':accepted['strictFitness']['status'],
    'globalScoreMonotonicity':accepted['globalScoreMonotonicity']['status'],
    'selectedGeometriesExact':sum(r['geometryExact'] for r in detail),
    'candidate2CostDecreases':sum(r['candidate2TotalDelta'] < 0 for r in detail),
    'candidate2CostEqual':sum(r['candidate2TotalDelta'] == 0 for r in detail),
    'candidate2CostIncreases':sum(r['candidate2TotalDelta'] > 0 for r in detail),
    'pairs':detail,
}
(HERE/'selection-analysis.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k != 'pairs'}))
