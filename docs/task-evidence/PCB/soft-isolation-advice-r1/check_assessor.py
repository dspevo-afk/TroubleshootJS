"""Synthetic assessor canaries only; no PCB or runtime PASS is implied."""
import copy,importlib.util,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('soft_assessor',HERE/'assess_geometry.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
base={r['seed']:r for r in json.loads((HERE/'published14-baselines.json').read_text(encoding='utf-8-sig'))['cases']}
results=[]
def fixture(benefit=True):
    old=copy.deepcopy(base);current=copy.deepcopy(base);trial=copy.deepcopy(base)
    for row in trial.values():row['version']=15;row['density']=14
    if benefit:
        trial['42']['largestProtectedEmptyRectangleArea']-=1
        trial['42']['geometrySha256']='a'*64
    return old,current,trial
def check(name,expected,change=None,benefit=True):
    old,current,trial=fixture(benefit)
    if change:change(old,current,trial)
    try:actual=module.assess_rows(old,current,trial)['status']
    except ValueError:actual='FAIL'
    if actual!=expected:raise AssertionError(name+': '+actual+' expected '+expected)
    results.append(dict(name=name,expected=expected,observed=actual))
check('one protected-space improvement with no other regressions','PASS')
check('no benefit cannot be accepted','FAIL',benefit=False)
def area_regression(old,current,trial):
    trial['0']['width']+=1;trial['0']['area']=trial['0']['width']*trial['0']['height']
check('area regression is not offset by another seed benefit','FAIL',area_regression)
check('void regression is not offset by another seed benefit','FAIL',lambda o,c,t:t['0'].__setitem__('largestProtectedEmptyRectangleArea',o['0']['largestProtectedEmptyRectangleArea']+1))
check('copper regression is not offset by space benefit','FAIL',lambda o,c,t:t['0'].__setitem__('copperManhattanLength',o['0']['copperManhattanLength']+1))
check('via regression is not offset by space benefit','FAIL',lambda o,c,t:t['0'].__setitem__('vias',o['0']['vias']+1))
check('saved14 geometry remains exact','FAIL',lambda o,c,t:c['0'].__setitem__('geometrySha256','b'*64))
check('live source graph must stay exact','FAIL',lambda o,c,t:t['0'].__setitem__('liveBoardGraphSha256','b'*64))
check('missing predeclared seed rejects instead of filtering','FAIL',lambda o,c,t:t.pop('4'))
check('wrong trial interpretation rejects','FAIL',lambda o,c,t:t['0'].__setitem__('density',13))
check('placement evaluation cap unchanged','FAIL',lambda o,c,t:t['0'].__setitem__('acceptedPlanEvaluations',360001))
check('route attempt cap unchanged','FAIL',lambda o,c,t:t['0'].__setitem__('routeAttempts',7))
row=json.dumps(base['0']);prefix='PCB_SOFT_ISOLATION_CASE '
try:module.collect(prefix+row+'\n'+prefix+row,prefix)
except ValueError:results.append(dict(name='duplicate seed rejects',expected='FAIL',observed='FAIL'))
else:raise AssertionError('Duplicate seed was accepted')
result=dict(status='PASS',canaryCount=len(results),canaries=results,limits='Synthetic scalar/identity guard tests only; no actual placement, routing, native suite or candidate acceptance proof.')
(HERE/'assessor-canaries.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
print('PASS synthetic assessor canaries='+str(len(results))+'; candidate proof NOT RUN')
