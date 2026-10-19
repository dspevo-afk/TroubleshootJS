"""Offline selected-geometry and existing global placement-score comparisons."""
import hashlib
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
protocol = read(HERE/'protocol.json')
assessment = read(HERE/'assessment.json')
assert assessment['admission'] == {'status':'PASS','cases':28,'roots':14,
    'explicit15ExactRows':14,'explicit21AdmittedRows':14}
prefix = 'PCB_POCKET_DESCENT_CASE '
rows = [json.loads(line[len(prefix):]) for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith(prefix)]
assert [(r['seed'],r['version']) for r in rows] == [(s,v) for s in protocol['roots'] for v in (15,21)]
ignored = {'version','density','sourcePlanSha256','elapsedMillis'}
selected = []
scores = []
fields = ('total','electrical','region','connector','corridor','outline','courtyard','demand')
pattern = re.compile(r'([0-5])\{'+';'.join(n+r'=([0-9]+)' for n in fields)+r'\}')

def placement_scores(row):
    canonical = row['statisticsCanonical']
    assert canonical.count('placementScores=[') == 1 and canonical.count('];selectedScore=') == 1
    text = canonical.split('placementScores=[',1)[1].split('];selectedScore=',1)[0]
    matches = list(pattern.finditer(text))
    assert len(matches) == 6 and ', '.join(m.group(0) for m in matches) == text
    ids = [int(m.group(1)) for m in matches]
    assert set(ids) == set(range(6)) and ids == row['rankedPlacementAttempts']
    return {int(m.group(1)):dict(zip(fields,map(int,m.groups()[1:]))) for m in matches}

for index in range(0,28,2):
    old,trial = rows[index:index+2]
    selected.append({'seed':old['seed'],'geometryExact':old['geometrySha256']==trial['geometrySha256'],
        'changedFields':[k for k in old if k not in ignored and old[k]!=trial[k]],
        'selectedAttempt15':old['selectedAttempt'],'selectedAttempt21':trial['selectedAttempt'],
        'evaluations15':old['acceptedPlanEvaluations'],'evaluations21':trial['acceptedPlanEvaluations']})
    a,b = placement_scores(old),placement_scores(trial)
    assert all(a[n] == b[n] for n in (0,1,3,4,5))
    scores.append({'seed':old['seed'],'candidate':2,
        'rank15':old['rankedPlacementAttempts'].index(2)+1,
        'rank21':trial['rankedPlacementAttempts'].index(2)+1,
        'before':a[2],'after':b[2],'deltas':{n:b[2][n]-a[2][n] for n in fields}})

def save_or_verify(name,value):
    path = HERE/name
    if path.exists():
        assert read(path) == value,name
    else:
        with path.open('x',encoding='utf-8') as out:
            out.write(json.dumps(value,indent=2)+'\n')

consumed = {n:sha(HERE/n) for n in ('routes.txt','assessment.json')}
save_or_verify('selected-comparison.json',{'status':'PASS',
    'scope':'Offline comparison of final15/21 selected receipts; not local-move attribution',
    'consumedSha256':consumed,'ignoredMetadataFields':sorted(ignored),'rows':selected})
root2 = [r for r in rows if r['seed']=='2']
route2 = []
for row in root2:
    text = row['statisticsCanonical'].split(';routeOutcomes=[',1)[1]
    assert text.endswith(']')
    outcomes = text[:-1].split(', ')
    assert len(outcomes) == row['routeAttempts']
    route2.append({'version':row['version'],'candidate2Outcomes':[s for s in outcomes if '@2=' in s]})
save_or_verify('placement-score-comparison.json',{'status':'PASS',
    'scope':'Existing generator global cheap-score receipts, not the local score or individual-move attribution',
    'consumedSha256':consumed,'otherFiveCandidateScoreRecordsExact':True,'rows':scores,
    'root2Candidate2RouteOutcomes':route2})
print('PASS offline14 selected comparisons/14 global-score pairs; other five candidate scores exact')
for row in scores:
    print('GLOBAL_CANDIDATE2 '+json.dumps({'seed':row['seed'],'rank15':row['rank15'],
        'rank21':row['rank21'],'deltas':row['deltas']}))
