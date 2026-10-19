"""Require exact archived deterministic observations before diagnosing outcomes."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'soft-isolation-advice-r1'
ROOTS={'-1022','7','14','4','6'}
def records(path,prefix):
    return [json.loads(line[len(prefix):]) for line in path.read_text(encoding='utf-8-sig').splitlines() if line.startswith(prefix)]
def assess(rows,baseline,trial):
    expected={(14,r['seed']):r for r in baseline if r['seed'] in ROOTS}
    expected.update({(15,r['seed']):r for r in trial if r['seed'] in ROOTS})
    actual={}
    for r in rows:
        key=(r['version'],r['seed'])
        if key in actual: raise ValueError('Duplicate case')
        actual[key]=r
    if set(actual)!=set(expected) or len(expected)!=10: raise ValueError('Case census')
    comparisons=[]
    for key,before in expected.items():
        now=actual[key]
        for field,value in before.items():
            if field=='elapsedMillis': continue
            if now.get(field)!=value: raise ValueError('Reproduction mismatch '+str(key)+' '+field)
        stats=now['statisticsCanonical']
        if ';routeOutcomes=[' not in stats or ';placementScores=[' not in stats or ';selectedQualityMilli=' not in stats: raise ValueError('Missing diagnostic statistics')
        comparisons.append({'version':key[0],'seed':key[1],'selectedAttempt':now['selectedAttempt'],'statisticsCanonical':stats})
    return comparisons
def main():
    p=OLD/'published14-baselines.json'
    if hashlib.sha256(p.read_bytes()).hexdigest()!='825b828b679a5e87c1e96bab9335083530b12fd2713539ac6af4c7b5cc67cb04': raise ValueError('Baseline pin')
    baseline=json.loads(p.read_text(encoding='utf-8-sig'))['cases']
    trial=records(OLD/'focused-native.txt','PCB_SOFT_ISOLATION_CASE ')
    rows=records(HERE/'focused-native.txt','PCB_ROUTE_RANKING_CASE ')
    result={'status':'PASS_EXACT_REPRODUCTION','cases':assess(rows,baseline,trial),'scope':'Ten selected layouts/metrics/identity receipts exact; existing route outcomes newly observed. Not a new generation acceptance.'}
    (HERE/'assessment.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
    print('PASS exact reproduction cases=10; chronological route outcomes available')
if __name__=='__main__':main()
