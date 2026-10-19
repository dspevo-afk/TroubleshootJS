"""Require frozen selected geometry before interpreting developer crop exports."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOTS={'-1022','7','14','4','6','42'}
def assess(rows,baseline):
    expected={r['seed']:r for r in baseline if r['seed'] in ROOTS};actual={}
    for row in rows:
        if row['seed'] in actual:raise ValueError('Duplicate root')
        actual[row['seed']]=row
    if set(expected)!=ROOTS or set(actual)!=ROOTS:raise ValueError('Case census')
    result=[]
    for seed in sorted(ROOTS,key=int):
        row=actual[seed]
        for key,value in expected[seed].items():
            if key!='elapsedMillis' and row.get(key)!=value:raise ValueError('Archived mismatch '+seed+' '+key)
        margin=row['actualMargin'];requested=row['requestedMargin']
        if type(margin)!=int or type(requested)!=int or requested<26 or margin<requested:raise ValueError('Margin range/type')
        if type(row['fixedMarginFloor'])!=int or type(row['accessEnvelopeMargin'])!=int or row['fixedMarginFloor']<26 or row['accessEnvelopeMargin']<0:raise ValueError('Requested components')
        if requested!=max(row['fixedMarginFloor'],row['accessEnvelopeMargin']):raise ValueError('Requested composition')
        if row['width']-row['occupiedWidth']!=2*margin or row['height']-row['occupiedHeight']!=2*margin:raise ValueError('Uniform crop extent')
        if row['occupiedX']-row['outlineX']!=margin or row['occupiedY']-row['outlineY']!=margin:raise ValueError('Uniform crop origin')
        if type(row['inferredFallbackUsed'])!=bool or row['inferredFallbackUsed']!=(margin>requested):raise ValueError('Inference classification')
        result.append({k:row[k] for k in ('seed','width','height','occupiedWidth','occupiedHeight','actualMargin','requestedMargin','fixedMarginFloor','accessEnvelopeMargin','inferredFallbackUsed')})
    return result
def main():
    path=HERE.parent/'soft-isolation-advice-r1/published14-baselines.json'
    if hashlib.sha256(path.read_bytes()).hexdigest()!='825b828b679a5e87c1e96bab9335083530b12fd2713539ac6af4c7b5cc67cb04':raise ValueError('Baseline pin')
    baseline=json.loads(path.read_text(encoding='utf-8-sig'))['cases'];prefix='PCB_CROP_MARGIN_CASE '
    rows=[json.loads(s[len(prefix):]) for s in (HERE/'focused-native.txt').read_text(encoding='utf-8-sig').splitlines() if s.startswith(prefix)]
    cases=assess(rows,baseline)
    result={'status':'PASS_EXACT_REPRODUCTION_AND_CROP_OBSERVATION','cases':cases,'fallbackCount':sum(r['inferredFallbackUsed'] for r in cases),
            'scope':'Six informed accepted14 layouts exact; requested margin reconstructed from final physical envelopes; fallback is source-supported inference, not captured exception telemetry'}
    (HERE/'assessment.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print(json.dumps(result))
if __name__=='__main__':main()
