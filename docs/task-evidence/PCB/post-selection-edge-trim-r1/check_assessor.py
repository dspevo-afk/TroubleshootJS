"""Synthetic rejection/acceptance guards; no actual candidate proof."""
import copy,json
import assess_geometry as gate
base={r['seed']:r for r in json.loads((gate.OLD/'published14-baselines.json').read_text(encoding='utf-8-sig'))['cases']}
results=[]
def fixture(benefit=True):
    old=copy.deepcopy(base);current=copy.deepcopy(base);trial=copy.deepcopy(base)
    for row in trial.values():row.update(version=15,density=14)
    if benefit:
        row=trial['42'];row['width']-=20;row['height']-=20;row['area']=row['width']*row['height'];row['geometrySha256']='a'*64
    return old,current,trial
def check(name,expected,change=None,benefit=True):
    old,current,trial=fixture(benefit)
    if change:change(old,current,trial)
    try:actual=gate.assess_rows(old,current,trial)['status']
    except (ValueError,KeyError):actual='FAIL'
    if actual!=expected:raise AssertionError(name+' observed='+actual+' expected='+expected)
    results.append({'name':name,'expected':expected,'observed':actual})
check('uniform10-unit border shrink with same copper/vias','PASS')
check('no area benefit rejects','FAIL',benefit=False)
check('area regression elsewhere rejects','FAIL',lambda o,c,t:t['0'].update(width=o['0']['width']+1,area=(o['0']['width']+1)*o['0']['height']))
for metric in ('largestProtectedEmptyRectangleArea','copperManhattanLength','vias'):
    check(metric+' growth rejects','FAIL',lambda o,c,t,m=metric:t['0'].__setitem__(m,o['0'][m]+1))
for field in ('geometrySha256','liveBoardGraphSha256','layoutSeed','routingSeed'):
    check('changed '+field+' rejects','FAIL',lambda o,c,t,f=field:t['0'].__setitem__(f,'b'*64 if f.endswith('Sha256') else '123'))
check('current14 full rank receipt exact','FAIL',lambda o,c,t:c['0'].__setitem__('rankedPlacementAttempts',[]))
check('current14 work receipt exact','FAIL',lambda o,c,t:c['0'].__setitem__('acceptedPlanEvaluations',o['0']['acceptedPlanEvaluations']+1))
check('trial selection unchanged','FAIL',lambda o,c,t:t['0'].__setitem__('selectedAttempt',(o['0']['selectedAttempt']+1)%6))
check('trial work receipt unchanged','FAIL',lambda o,c,t:t['0'].__setitem__('acceptedPlanEvaluations',o['0']['acceptedPlanEvaluations']+1))
check('less copper also violates border-only topology','FAIL',lambda o,c,t:t['0'].__setitem__('copperManhattanLength',o['0']['copperManhattanLength']-1))
check('fewer vias also violates border-only topology','FAIL',lambda o,c,t:t['0'].__setitem__('vias',o['0']['vias']-1))
check('missing root rejects','FAIL',lambda o,c,t:t.pop('4'))
check('wrong density rejects','FAIL',lambda o,c,t:t['0'].__setitem__('density',13))
check('placement bound unchanged','FAIL',lambda o,c,t:t['0'].__setitem__('acceptedPlanEvaluations',360001))
check('route bound unchanged','FAIL',lambda o,c,t:t['0'].__setitem__('routeAttempts',7))
check('no-op geometry must be exact','FAIL',lambda o,c,t:t['0'].__setitem__('geometrySha256','a'*64))
check('shrunken geometry must differ','FAIL',lambda o,c,t:t['42'].__setitem__('geometrySha256',o['42']['geometrySha256']))
def nongrid(o,c,t):
    r=t['42'];r['width']=o['42']['width']-2;r['height']=o['42']['height']-2;r['area']=r['width']*r['height']
check('subgrid crop rejects','FAIL',nongrid)
row=json.dumps(base['0']);prefix='PCB_EDGE_TRIM_CASE '
try:gate.collect(prefix+row+'\n'+prefix+row)
except ValueError:results.append({'name':'duplicate paired case rejects','expected':'FAIL','observed':'FAIL'})
else:raise AssertionError('Duplicate accepted')
result={'status':'PASS','canaryCount':len(results),'canaries':results,'scope':'Synthetic scalar/receipt guards only; actual native/physical/candidate proof separate'}
(gate.HERE/'assessor-canaries.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print('PASS synthetic edge-trim assessor canaries='+str(len(results)))
