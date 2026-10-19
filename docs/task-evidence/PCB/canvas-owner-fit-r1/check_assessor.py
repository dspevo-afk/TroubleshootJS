"""Synthetic cohort/hash assessor checks; no actual measurement input."""
import copy
import contextlib
import io
import json
from pathlib import Path
from assess_geometry import OLD, FRESH, assess, rows

old = {seed:dict(seed=seed,parts=40,area=10000,copperLength=1000,vias=2,
                geometrySha256='a'*64,largestProtectedEmptyRectangle=dict(area=400)) for seed in OLD}
fresh = {seed:dict(seed=seed,status='PASS',geometrySha256='a'*64,parts=40,pads=80,area=10000,
    width=100,height=100,largestProtectedEmptyRectangleArea=400,copperManhattanLength=1000,vias=2,
    layoutSeed='9007199254740993',routingSeed='-9223372036854775808') for seed in FRESH}
fresh['4'] = dict(seed='4',status='FAIL',error=
    'java.lang.IllegalStateException: PCB board outline leaves workbench canvas')
proposed = {seed:dict(seed=seed,status='PASS',version=14,density=13,parts=40,pads=80,
    area=10000,width=100,height=100,largestProtectedEmptyRectangleArea=400,
    copperManhattanLength=1000,vias=2,geometrySha256='a'*64,sourcePlanSha256='b'*64,
    liveBoardGraphSha256='c'*64,layoutSeed='9007199254740993',routingSeed='-9223372036854775808',
    selectedAttempt=0,rankedPlacementAttempts=[0,1,2,3,4,5],acceptedPlanEvaluations=100,
    placementRejections=0,oneFaceAttempts=3,twoLayerAttempts=3,routeAttempts=6,elapsedMillis=10)
    for seed in OLD|FRESH}
results = []


def check(name, operation, reject=True):
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            operation()
        actual = 'PASS'
    except ValueError:
        actual = 'REJECT'
    assert actual == ('REJECT' if reject else 'PASS'), (name,actual)
    results.append(dict(case=name,status='PASS',actual=actual))


check('complete admitted cohort and exact hashes',lambda:assess(old,fresh,proposed),False)
for seed,key,value in (('4','status','FAIL'),('2','geometrySha256','d'*64),
                       ('42','area',9999),('42','copperManhattanLength',1001),
                       ('42','version',13),('2','layoutSeed',9007199254740993),
                       ('2','routingSeed',str(1<<63)),('4','area',10000.0),
                       ('4','twoLayerAttempts',4)):
    changed = copy.deepcopy(proposed); changed[seed][key]=value
    check('reject '+seed+'/'+key,lambda changed=changed:assess(old,fresh,changed))
changed = copy.deepcopy(proposed); del changed['4']
check('missing originally rejected case',lambda:assess(old,fresh,changed))
text = 'ROW '+json.dumps(proposed['4'])+'\n'
check('duplicate parsed seed',lambda:rows(text+text,'ROW '))
result = dict(status='PASS',scope='Synthetic assessor boundary only; no native/physical proof or real cohort values read',cases=results)
(Path(__file__).resolve().parent/'assessor-canaries.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
print('PASS: synthetic canvas owner assessor canaries='+str(len(results)))
