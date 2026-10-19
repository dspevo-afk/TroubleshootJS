"""Require exact accepted13 preservation and newly admitted root4, with no dropped case."""
import hashlib
import argparse
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = {'-1017','-1004','-1022','0','17','42','1','7','5','14'}
FRESH = {'2','3','4','6'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def rows(text, prefix):
    result = {}
    for line in text.splitlines():
        if line.startswith(prefix):
            row = json.loads(line[len(prefix):])
            require(row['seed'] not in result, 'Duplicate seed in '+prefix)
            result[row['seed']] = row
    return result


def assess(old, fresh, proposed):
    require(set(old)==OLD and set(fresh)==FRESH and set(proposed)==OLD|FRESH,
            'Missing or changed predeclared cohort')
    require(fresh['4']['status']=='FAIL' and fresh['4']['error']==
            'java.lang.IllegalStateException: PCB board outline leaves workbench canvas',
            'Original root4 rejection changed')
    fields = ('version','density','parts','pads','area','width','height',
              'largestProtectedEmptyRectangleArea','copperManhattanLength','vias',
              'selectedAttempt','acceptedPlanEvaluations','placementRejections',
              'oneFaceAttempts','twoLayerAttempts','routeAttempts','elapsedMillis')
    comparisons = []
    for seed, new in proposed.items():
        require(new.get('status')=='PASS' and new['version']==14 and new['density']==13,
                'Non-admitted or wrong interpretation for '+seed)
        require(all(type(new[k]) is int and new[k]>=0 for k in fields), 'Invalid integer for '+seed)
        require(new['width']>0 and new['height']>0 and new['area']==new['width']*new['height']<=64000000,
                'Invalid outline for '+seed)
        require(all(isinstance(new[k],str) and re.fullmatch('[0-9a-f]{64}',new[k])
                    for k in ('geometrySha256','sourcePlanSha256','liveBoardGraphSha256')),
                'Invalid identity digest for '+seed)
        for key in ('layoutSeed','routingSeed'):
            value = new[key]
            require(isinstance(value,str) and re.fullmatch('-?(0|[1-9][0-9]*)',value) and
                    str(int(value))==value and -(1<<63)<=int(value)<(1<<63), 'Invalid stream seed for '+seed)
        require(0<new['acceptedPlanEvaluations']<=360000 and new['placementRejections']<=6 and
                new['oneFaceAttempts']<=3 and 0<new['twoLayerAttempts']<=3 and
                new['routeAttempts']==new['oneFaceAttempts']+new['twoLayerAttempts']<=6,
                'Policy counter boundary mismatch for '+seed)
        if seed in OLD:
            before = old[seed]
            require(new['geometrySha256']==before['geometrySha256'], 'Old geometry changed for '+seed)
            for key, baseline in (('parts',before['parts']),('area',before['area']),
                    ('copperManhattanLength',before['copperLength']),('vias',before['vias']),
                    ('largestProtectedEmptyRectangleArea',before['largestProtectedEmptyRectangle']['area'])):
                require(new[key]==baseline, 'Old metric changed: '+seed+'/'+key)
            outcome = 'EXACT_ACCEPTED13_GEOMETRY_AND_METRICS'
        elif seed!='4':
            require(fresh[seed]['status']=='PASS' and
                    new['geometrySha256']==fresh[seed]['geometrySha256'], 'Fresh geometry changed for '+seed)
            for key in ('parts','pads','area','width','height','largestProtectedEmptyRectangleArea',
                        'copperManhattanLength','vias','layoutSeed','routingSeed'):
                require(new[key]==fresh[seed][key], 'Fresh metric or stream changed: '+seed+'/'+key)
            outcome = 'EXACT_ACCEPTED13_GEOMETRY_AND_METRICS'
        else:
            outcome = 'NEWLY_ADMITTED_PREVIOUSLY_REJECTED'
        comparisons.append(dict(seed=seed,status='PASS',outcome=outcome))
    return dict(status='PASS',cases=comparisons,
                scope='Finite admission fix; no shrinkage claim. All14 roots are informed regression data.',
                limits='Native positive/negative and source review only; final native/GWT/player gates remain separate')


if __name__=='__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--phase', choices=('focused', 'final'), default='focused')
    args = parser.parse_args()
    phase = args.phase
    prior = ROOT/'docs/task-evidence/PCB/pad-cost-vacancy-r1/published13-baselines.json'
    raw = prior.read_bytes()
    require(hashlib.sha256(raw).hexdigest()=='0495c3da32783a52372a6afc2875dcc6cf8d6ae1c8c2e745b4daaf5b74dd65ac',
            'Frozen ten-root baseline bytes changed')
    old = {row['seed']:row for row in json.loads(raw)['cases']}
    fresh_packet = ROOT/'docs/task-evidence/PCB/fresh-qualification-r1'
    raw_fresh = (fresh_packet/'native.txt').read_bytes()
    audit = json.loads((fresh_packet/'failure-audit.json').read_text(encoding='utf-8-sig'))
    require(hashlib.sha256(raw_fresh).hexdigest()==audit['nativeLogSha256'], 'Fresh observation bytes changed')
    fresh = rows(raw_fresh.decode('utf-8-sig'), 'PCB_FRESH_BASELINE_CASE ')
    timing_name = 'focused-timing.json' if phase=='focused' else 'final-native-timing.json'
    audit_name = 'focused-input-audit.json' if phase=='focused' else 'post-native-input-audit.json'
    timing = json.loads((HERE/timing_name).read_text(encoding='utf-8-sig'))
    inputs = json.loads((HERE/audit_name).read_text(encoding='utf-8-sig'))
    require(timing['status']=='PASS' and inputs['status']=='PASS', 'Native terminal/input gate not PASS')
    text = (HERE/(phase+'-native.txt')).read_text(encoding='utf-8-sig')
    require(all('PASS: '+marker in text for marker in ('PCB canvas owner fit contracts ',
                'PCB canvas owner regression contracts ', 'PCB canvas owner fresh contracts ',
                'PCB canvas owner recovery contracts ', 'PCB canvas boundary contracts ',
                'PCB canvas owner bounds contracts ')) and
            'CLEANUP: current JVM contract classes and task-owned scratch removed.' in text,
            'Missing positive/cleanup receipt')
    proposed = rows(text,'PCB_CANVAS_OWNER_CASE ')
    result = assess(old,fresh,proposed)
    if phase=='final':
        result['limits'] = 'Selected56 native contracts passed; full matrix and independent oracles NOT RUN. GWT/player gates remain separate.'
    output_name = 'geometry-assessment.json' if phase=='focused' else 'final-geometry-assessment.json'
    result['phase'] = phase
    (HERE/output_name).write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print(json.dumps(result))
