"""Describe completed independent alternatives without inferring selected-board shrinkage."""
from collections import Counter
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def result():
    assessment = read(HERE/'assessment.json')
    assert assessment['assessmentStatus'] == 'PASS'
    protocol = read(HERE/'protocol.json')
    prefix = protocol['summaryPrefix']
    cases = [json.loads(line[len(prefix):])
        for line in (HERE/'routes.txt').read_text(encoding='utf-8-sig').splitlines()
        if line.startswith(prefix)]
    assert [r['seed'] for r in cases] == ['5','2']
    assert cases[0]['status'] == 'NO_TARGET' and cases[0]['rows'] == []
    target = cases[1]
    assert target['status'] == 'CENSUS_COMPLETE' and len(target['rows']) == 31
    assert all(r['candidate']['area'] == target['source']['area'] for r in target['rows'])
    assert all(c['newRouteRequests'] == 0 and c['selectedMutation'] is False for c in cases)
    rejected = Counter()
    admitted = []
    outcomes = []
    for row in target['rows']:
        move = row['move']
        assert row['candidate']['copperLength'] - target['source']['copperLength'] == move['analyticCopperDelta']
        if row['disposition'] == 'PHYSICAL_VALIDATED':
            admitted.append(move)
        else:
            rejected[(row['stage'],row['reason'])] += 1
        outcomes.append({'move':move, 'disposition':row['disposition'],
            'stage':row['stage'], 'reason':row['reason'],
            'candidateGeometrySha256':row['candidate']['geometryHash'],
            'totalMillis':row['timing']['totalMillis']})
    return {'analysisStatus':'COMPLETE', 'censusAssessmentStatus':'PASS',
        'scope':'31 independent informed endpoint-only alternatives; no selection or compaction',
        'assessmentSha256':sha(HERE/'assessment.json'), 'routesLogSha256':sha(HERE/'routes.txt'),
        'sourceGeometrySha256':target['source']['geometryBefore'],
        'sourceArea':target['source']['area'], 'sourceCopper':target['source']['copperLength'],
        'constructionCount':31, 'physicallyValidated':len(admitted),
        'physicallyRejected':31-len(admitted), 'validatedMoves':admitted,
        'firstCanonicalValidatedMove':admitted[0] if admitted else None,
        'rejectionCounts':[{'stage':stage,'reason':reason,'count':count}
            for (stage,reason),count in sorted(rejected.items())], 'outcomes':outcomes,
        'sourceReachabilityHash':target['source']['reachabilityHash'],
        'fixedAreaForEveryCandidate':True, 'boardShrinkage':'NOT_DEMONSTRATED',
        'selectedMutation':False, 'newRouteRequests':0, 'productionPolicyFitnessChange':'NONE',
        'limits':protocol['limits']+['Rejection applies to this endpoint-only and fixed-label transform, not every possible routing/label arrangement']}

if __name__ == '__main__':
    value = result()
    target = HERE/'feasibility-analysis.json'
    if target.exists():
        assert read(target) == value, 'Existing analysis differs; preserve it'
    else:
        target.write_text(json.dumps(value,indent=2)+'\n')
    print('COMPLETE physical census:',value['physicallyValidated'],'validated /',
        value['physicallyRejected'],'rejected; fixed area/no selection')
