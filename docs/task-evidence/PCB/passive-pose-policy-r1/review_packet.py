"""Post-terminal packet review; never substitutes for the frozen strict reader."""
import hashlib
import json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda n:json.loads((HERE/n).read_text(encoding='utf-8-sig'))
seal=read('native-inputs.json')
assert seal['copyCount']==1504 and len(seal['overrides'])==12 and len(seal['additions'])==37
for name,digest in seal['gateOwners'].items():
    assert sha(HERE/name)==digest,'Frozen gate changed: '+name
for name,digest in dict(seal['overrides'],**seal['additions']).items():
    assert sha(HERE/'source'/name)==digest,'Frozen source changed: '+name
assert sha(HERE/'copy-inputs.json')==seal['baseSha256']
assert sha(HERE.parent/'silkscreen-scale-r1/final-inputs.json')==seal['main1475SealSha256']
main=json.loads((HERE.parent/'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(main)==1475
for row in main:
    assert sha(ROOT/row['path'])==row['sha256'],'Main accepted owner changed'
canaries=read('assessment-canary-results.json')
assert canaries['status']=='PASS' and canaries['caseCount']==51 and canaries['assertions']==64
for name,digest in canaries['sha256'].items():
    assert sha(HERE/name)==digest,'Canary receipt input changed'
assert read('unit-validation.json')['status']=='PASS'
assert read('native-input-audit.json')['status']=='PASS'
assert read('source-review.json')['status']=='PASS'
assert read('copy-restoration.json')['status']=='PASS'
assert read('copy-cleanup.json')['status']=='PASS' and not (HERE/'local-state.json').exists()
old=assess.baselines()
unit=(HERE/'unit.txt').read_text(encoding='utf-8-sig')
unit_timing=read('unit-timing.json')
assess.check_phase(unit,(HERE/'unit-receipt.txt').read_text(encoding='utf-8-sig'),unit_timing,read('unit-suites.json'))
bindings=assess.inputs(assess.extract(unit,'PCB_PASSIVE_POLICY_INPUT '),old)
assess.raw(assess.extract(unit,'PCB_PASSIVE_POLICY_RAW '))
route_text=(HERE/'routes.txt').read_text(encoding='utf-8-sig')
route_timing=read('routes-timing.json')
for phase in ('unit','routes'):
    cleanup=read(phase+'-process-cleanup.json')
    assert cleanup['status']=='PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers'])==2 and all(r['sameCreationStillRunning'] is False for r in cleanup['drivers'])
    assert assess.CLEANUP in (HERE/(phase+'.txt')).read_text(encoding='utf-8-sig')
    assert read(phase+'-log-sanitization.json')['status']=='PASS'
rows=assess.extract(route_text,'PCB_PASSIVE_POLICY_ROUTE ')
poses=assess.extract(route_text,'PCB_PASSIVE_POLICY_POSES ')
expected=[(r['seed'],v) for v in (15,16) for r in old]
assert len(rows)==len(poses)<=28
assert [(r['seed'],r['version']) for r in rows]==expected[:len(rows)]
observations=[]
for index,row in enumerate(rows):
    baseline=old[index%14];version=row['version'];binding=bindings[row['seed']]
    assess.route_case(row,baseline,version,binding)
    assess.pose_case(poses[index],baseline,version,binding)
    item={'seed':row['seed'],'version':version,'completedPhysicalCore':True,
      'issuedAdmissionIdentity':poses[index]['issuedAdmissionIdentity'],'selectedAttempt':row['selectedAttempt'],
      'top90':poses[index]['top90'],'elapsedMillis':row['elapsedMillis']}
    if version==16:
        item['metricDelta']={k:row[k]-baseline[k] for k in assess.METRICS}
    else:
        item['exactNonTimingFields']=42
    observations.append(item)
strict=read('assessment.json')
if route_timing['status']=='PASS':
    assess.check_phase(route_text,(HERE/'routes-receipt.txt').read_text(encoding='utf-8-sig'),route_timing,read('route-suites.json'))
    assert strict==assess.assess(rows,poses,old,bindings)
else:
    assert route_timing['status']=='FAIL' and route_timing['exitCode']!=0 and strict['status']=='FAIL'
first_missing=expected[len(rows)] if len(rows)<28 else None
result={'status':'PASS','scope':'Root evidence review; trial acceptance remains exactly the frozen assessment',
 'strictTrialStatus':strict['status'],'nativeUnitStatus':unit_timing['status'],
 'nativeRouteStatus':route_timing['status'],'completedRoutedCases':len(rows),
 'firstUnreportedCase':None if first_missing is None else {'seed':first_missing[0],'version':first_missing[1]},
 'archivedSourcesExact':49,'frozenGateOwnersExact':len(seal['gateOwners']),
 'main1475Exact':True,'copyRestoredAndRetired':True,'recordedDriversExited':4,
 'rootIntegratedSourceReview':'Constraints, actual template visits/sizing, identity/admission consumers, literal/pure fixtures and unchanged physical core reviewed before runtime',
 'reviewLimits':'Leaf implementation/reader work is not an independent runtime or full-matrix review; owner/replay/markings/GWT/player NOT RUN for this native probe',
 'observations':observations}
(HERE/'root-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS packet/source/gates/main/restoration review; strict trial='+strict['status']+' completed='+str(len(rows)))
