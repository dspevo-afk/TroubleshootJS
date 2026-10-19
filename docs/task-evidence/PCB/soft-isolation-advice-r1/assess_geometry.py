"""Compare the whole predeclared routed cohort without metric waivers."""
import hashlib,json,re,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
PIN='825b828b679a5e87c1e96bab9335083530b12fd2713539ac6af4c7b5cc67cb04'
def read(name):return json.loads((HERE/name).read_text(encoding='utf-8-sig'))
def collect(text,prefix):
    out={}
    for line in text.splitlines():
        if line.startswith(prefix):
            row=json.loads(line[len(prefix):]);seed=row['seed']
            if seed in out:raise ValueError('Duplicate '+prefix+' seed '+seed)
            out[seed]=row
    return out
def assess_rows(old,saved,trial):
    if set(saved)!=set(old) or set(trial)!=set(old):raise ValueError('Missing/extra predeclared seed; do not substitute')
    failures=[];comparisons=[];benefit=False
    metrics=('area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias')
    counters=('parts','pads','area','width','height','largestProtectedEmptyRectangleArea','copperManhattanLength','vias','selectedAttempt','acceptedPlanEvaluations','placementRejections','oneFaceAttempts','twoLayerAttempts','routeAttempts','elapsedMillis')
    for seed,before in old.items():
        current=saved[seed];after=trial[seed]
        for row,version,density,label in ((current,14,13,'current14'),(after,15,14,'trial15')):
            if row['status']!='PASS' or row['version']!=version or row['density']!=density:failures.append(seed+'/'+label+': admission/interpretation')
            if not all(type(row[k]) is int and row[k]>=0 for k in counters):failures.append(seed+'/'+label+': noninteger counters')
            if row['area']!=row['width']*row['height'] or not 0<row['area']<=64000000:failures.append(seed+'/'+label+': outline limit')
            if not (0<row['acceptedPlanEvaluations']<=360000 and row['placementRejections']<=6 and row['oneFaceAttempts']<=3 and 0<row['twoLayerAttempts']<=3 and row['routeAttempts']==row['oneFaceAttempts']+row['twoLayerAttempts']<=6 and row['selectedAttempt']<6):failures.append(seed+'/'+label+': work/route limits')
            for field in ('geometrySha256','sourcePlanSha256','liveBoardGraphSha256'):
                if not isinstance(row[field],str) or not re.fullmatch('[0-9a-f]{64}',row[field]):failures.append(seed+'/'+label+': invalid digest '+field)
            for field in ('parts','pads','layoutSeed','routingSeed','liveBoardGraphSha256'):
                if row[field]!=before[field]:failures.append(seed+'/'+label+': changed inventory/graph/stream '+field)
        for field in metrics+('width','height','geometrySha256','sourcePlanSha256'):
            if current[field]!=before[field]:failures.append(seed+'/current14: accepted baseline changed '+field)
        changes={field:dict(before=before[field],after=after[field],delta=after[field]-before[field]) for field in metrics}
        for field in metrics:
            if after[field]>before[field]:failures.append(seed+'/trial15: regressed '+field)
        smaller=after['area']<before['area'] or after['largestProtectedEmptyRectangleArea']<before['largestProtectedEmptyRectangleArea']
        benefit=benefit or smaller
        comparisons.append(dict(seed=seed,changes=changes,spaceBenefit=smaller,geometryExact=after['geometrySha256']==before['geometrySha256']))
    if not benefit:failures.append('No strict area or protected-empty-space benefit in the complete cohort')
    return dict(status='PASS' if not failures else 'FAIL',failures=failures,cases=comparisons,
        limits='All14 informed regression cases. Representative raw1-5 identity fixtures are separate; no unseen/all-seed/manufacturing claim. Native evidence only; final GWT/player acceptance not implied.')
def assess():
    raw=(HERE/'published14-baselines.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=PIN:raise ValueError('Frozen baseline bytes changed')
    old={r['seed']:r for r in json.loads(raw)['cases']}
    timing=read('focused-timing.json');audit=read('focused-input-audit.json')
    if timing['status']!='PASS' or timing['exitCode']!=0 or audit['status']!='PASS':raise ValueError('Native terminal/input gate not PASS')
    text=(HERE/'focused-native.txt').read_text(encoding='utf-8-sig')
    if 'CLEANUP: current JVM contract classes and task-owned scratch removed.' not in text:raise ValueError('Maintained terminal cleanup not proved')
    saved=collect(text,'PCB_CANVAS_OWNER_CASE ')
    trial=collect(text,'PCB_SOFT_ISOLATION_CASE ')
    return assess_rows(old,saved,trial)

def main():
    try:result=assess()
    except Exception as problem:result=dict(status='FAIL',failure=str(problem),scope='Incomplete native/input/complete-cohort proof; no candidate accepted')
    (HERE/'focused-assessment.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print(json.dumps(result))
    raise SystemExit(0 if result['status']=='PASS' else 1)
if __name__=='__main__':main()
