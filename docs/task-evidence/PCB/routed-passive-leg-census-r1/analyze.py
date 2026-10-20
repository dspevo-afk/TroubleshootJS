"""Describe validated endpoint advisories without treating them as physical moves."""
from collections import Counter
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def result():
    assessment = read(HERE/'assessment.json')
    assert assessment.get('assessmentStatus', assessment.get('status')) == 'PASS'
    protocol = read(HERE/'protocol.json')
    prefix = protocol['summaryPrefix']
    cases = [json.loads(line[len(prefix):])
        for line in (HERE/'routes.txt').read_text(encoding='utf-8-sig').splitlines()
        if line.startswith(prefix)]
    assert [c['seed'] for c in cases] == ['5', '2']
    rows = []
    for case in cases:
        reasons = Counter()
        packages = Counter()
        opportunities = []
        for callback in case['successfulCallbacks']:
            for part in callback['parts']:
                packages[part['packageId']] += 1
                for direction in part['directions']:
                    reasons[direction['reason']] += 1
                    if direction['eligible']:
                        opportunities.append({'componentId':part['componentId'],
                            'packageId':part['packageId'], 'strategy':callback['strategy'],
                            'attempt':callback['attempt'], 'dx':direction['dx'],
                            'dy':direction['dy'], 'analyticCopperDelta':direction['copperDelta']})
        rows.append({'seed':case['seed'], 'version':case['version'],
            'aggregate':case['aggregate'],
            'packageCounts':dict(sorted(packages.items())),
            'directionReasonCounts':dict(sorted(reasons.items())),
            'structurallyEligibleDirections':opportunities,
            'negativeAnalyticDeltaDirections':sum(o['analyticCopperDelta'] < 0 for o in opportunities),
            'zeroAnalyticDeltaDirections':sum(o['analyticCopperDelta'] == 0 for o in opportunities),
            'positiveAnalyticDeltaDirections':sum(o['analyticCopperDelta'] > 0 for o in opportunities)})
    return {'status':'PASS', 'scope':'Descriptive structural endpoint advisory only',
        'phase':protocol['phase'], 'assessmentSha256':sha(HERE/'assessment.json'),
        'routesLogSha256':sha(HERE/'routes.txt'), 'cases':rows,
        'physicalFeasibility':'NOT_EVALUATED', 'actualMoves':0,
        'newRouteRequests':0, 'policyChange':'NONE',
        'limits':protocol['limits']}

if __name__ == '__main__':
    target = HERE/'census-analysis.json'
    value = result()
    if target.exists():
        assert read(target) == value, 'Existing analysis differs; preserve it'
    else:
        target.write_text(json.dumps(value, indent=2)+'\n')
    for row in value['cases']:
        print('seed', row['seed'], row['aggregate'],
            'analytic negative/zero/positive', row['negativeAnalyticDeltaDirections'],
            row['zeroAnalyticDeltaDirections'], row['positiveAnalyticDeltaDirections'])
