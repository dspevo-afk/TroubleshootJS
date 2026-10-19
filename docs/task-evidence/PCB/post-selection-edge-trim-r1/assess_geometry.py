"""Retain the previous strict metric oracle and add border-only invariants."""
import hashlib,importlib.util,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'soft-isolation-advice-r1'
PIN='825b828b679a5e87c1e96bab9335083530b12fd2713539ac6af4c7b5cc67cb04'
spec=importlib.util.spec_from_file_location('prior_strict_metrics',OLD/'assess_geometry.py')
prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
def collect(text):
    result={14:{},15:{}};prefix='PCB_EDGE_TRIM_CASE '
    for line in text.splitlines():
        if not line.startswith(prefix):continue
        row=json.loads(line[len(prefix):]);version=row['version'];seed=row['seed']
        if version not in result or seed in result[version]:raise ValueError('Unexpected/duplicate paired case')
        result[version][seed]=row
    return result[14],result[15]
def assess_rows(old,saved,trial):
    result=prior.assess_rows(old,saved,trial);failures=result['failures']
    area_benefit=False
    for seed,before in old.items():
        current=saved[seed];after=trial[seed]
        for field,value in before.items():
            if field!='elapsedMillis' and current.get(field)!=value:failures.append(seed+'/current14 changed '+field)
        for field in ('selectedAttempt','rankedPlacementAttempts','acceptedPlanEvaluations','placementRejections','oneFaceAttempts','twoLayerAttempts','routeAttempts','copperManhattanLength','vias'):
            if after.get(field)!=before[field]:failures.append(seed+'/trial15 border-only invariant changed '+field)
        dw=before['width']-after['width'];dh=before['height']-after['height']
        if dw!=dh or dw<0 or dw%20!=0:failures.append(seed+'/trial15 nonuniform/non-grid border trim')
        if (dw==0)!=(after['geometrySha256']==before['geometrySha256']):failures.append(seed+'/trial15 unexpected geometry identity for crop delta')
        area_benefit=area_benefit or after['area']<before['area']
    if not area_benefit:failures.append('No strict selected-board area benefit')
    result['status']='FAIL' if failures else 'PASS'
    result['limits']='All14 informed regression roots; border-only source-bound native trial. No unseen/all-seed/manufacturing/GWT/player claim. No metric threshold or route/work bound change.'
    return result
def assess():
    raw=(OLD/'published14-baselines.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=PIN:raise ValueError('Frozen baseline pin')
    timing=json.loads((HERE/'focused-timing.json').read_text(encoding='utf-8-sig'))
    audit=json.loads((HERE/'focused-input-audit.json').read_text(encoding='utf-8-sig'))
    if timing['status']!='PASS' or timing['exitCode']!=0 or timing['suiteCount']!=10 or audit['status']!='PASS':raise ValueError('Native/input gate')
    text=(HERE/'focused-native.txt').read_text(encoding='utf-8-sig')
    if 'CLEANUP: current JVM contract classes and task-owned scratch removed.' not in text:raise ValueError('Native cleanup proof')
    if 'CURRENT_CONTRACT_FAILURE' in text:raise ValueError('Native failure log')
    old={r['seed']:r for r in json.loads(raw)['cases']};saved,trial=collect(text)
    return assess_rows(old,saved,trial)
def main():
    try:result=assess()
    except Exception as problem:result={'status':'FAIL','failure':str(problem),'scope':'Incomplete paired/native/input proof; no candidate accepted'}
    (HERE/'focused-assessment.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print(json.dumps(result));raise SystemExit(0 if result['status']=='PASS' else 1)
if __name__=='__main__':main()
