"""Independent synthetic canaries for strict acceptance, without generator work."""
import copy
import json
from pathlib import Path
from assess_metrics import assess

HERE = Path(__file__).resolve().parent
baseline = json.loads((HERE/'published13-baselines.json').read_text(encoding='utf-8'))
rows = [dict(seed=c['seed'],version=14,density=13,parts=c['parts'],area=c['area'],
             copperManhattanLength=c['copperLength'],vias=c['vias'],
             largestProtectedEmptyRectangleArea=c['largestProtectedEmptyRectangle']['area']) for c in baseline['cases']]
results = []


def check(name, cases, expected):
    try:
        actual = assess(baseline, cases)['status']
    except ValueError:
        actual = 'REJECT'
    if actual != expected:
        raise AssertionError((name,actual,expected))
    results.append(dict(case=name,status='PASS',actual=actual))


check('unchanged cannot pass', rows, 'FAIL')
better = copy.deepcopy(rows)
better[5]['area'] -= 1
check('one strict improvement without regression', better, 'PASS')
for field in ('area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias'):
    worse = copy.deepcopy(better)
    worse[0][field] += 1
    check('regression rejected: '+field, worse, 'FAIL')
check('missing root', better[:-1], 'REJECT')
check('duplicate root', better+[better[0]], 'REJECT')
extra = copy.deepcopy(better[0])
extra['seed'] = '999'
check('unexpected root', better+[extra], 'REJECT')
for field, value in (('version',13),('density',12),('parts',999),('area',1.5),('vias',-1)):
    wrong = copy.deepcopy(better)
    wrong[0][field] = value
    check('wrong metric/identity: '+field, wrong, 'REJECT')
(HERE/'assessor-canaries.json').write_text(json.dumps(dict(status='PASS',
    scope='Synthetic metric boundary only; no physical/generation claim',cases=results),indent=2)+'\n',encoding='utf-8')
print('PASS: assessor canaries='+str(len(results)))
