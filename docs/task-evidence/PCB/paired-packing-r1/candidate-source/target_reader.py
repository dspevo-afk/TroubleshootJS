"""Retain the published sparse-board oracle, adapting only request and packing epoch."""
import copy,hashlib,json,runpy
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
PRIOR=HERE.parent/'npn-sparse-diagnostic-r1/qualify.py'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def require(ok,message):
    if not ok:raise AssertionError(message)
def assess():
    seal=read(HERE/'candidate-inputs.json');require(sha(PRIOR)==seal['publishedTargetReader'][PRIOR.relative_to(ROOT).as_posix()],'pinned published target reader')
    source=PRIOR.read_text(encoding='utf-8-sig');old="layout['constraints']['placementPackingVersion']==3";new="layout['constraints']['placementPackingVersion']==4"
    require(source.count(old)==1,'one explicit current packing adaptation')
    ns={'__file__':str(PRIOR),'__name__':'_paired_current_target_oracle'}
    exec(compile(source.replace(old,new),str(PRIOR),'exec'),ns)
    ns['EXPECTED']={'dual':('PARALLEL_DUAL_INDICATOR','EASY','-9223372036854775808')}
    raw=(HERE/'target-receipt.txt').read_text(encoding='utf-8-sig')
    def rows(prefix):return [json.loads(s[len(prefix):]) for s in raw.splitlines() if s.startswith(prefix)]
    actual=rows('PCB_SPARSE_NORMAL_DIAGNOSTIC ');samples=rows('PCB_WHITESPACE_CASE ')
    require(len(actual)==len(samples)==1,'exact target rows');row=actual[0];sample=samples[0]
    ns['validate_row'](row,sample,'dual')
    private=read(HERE.parent/'paired-pocket-probe-r1/assessment.json')
    require(row['physicalMetrics']==private['selectedMetrics'],'exact qualified target metric gain')
    require(row['identity']['selectedGeometrySha256Before']=='a1794723e9156c443402549ab9534fa8e846a611a86825017fb3ef52ad06ac2d','exact qualified target geometry')
    require(row['packingProbe']['disposition']=='SELECTED','actual optional route selected')
    controls=[]
    for label,mutate in (('public identity',lambda r:r.update(publicSeed='0')),
                         ('current physical epochs',lambda r:r['layout']['constraints'].update(placementPackingVersion=3)),
                         ('pad net membership',lambda r:r['layout']['pads'][0].update(netId='__WRONG_NET__')),
                         ('independent copper',lambda r:r['physicalMetrics'].update(copper=r['physicalMetrics']['copper']+1)),
                         ('independent protected void',lambda r:r['physicalMetrics'].update(protectedVoid=r['physicalMetrics']['protectedVoid']+1))):
        bad=copy.deepcopy(row);mutate(bad)
        try:ns['validate_row'](bad,sample,'dual')
        except AssertionError as e:require(str(e)==label,'specific target mutation rejection');controls.append(label)
        else:raise AssertionError('target reader accepted '+label)
    return {'status':'PASS_EXACT_CURRENT_TARGET','request':row['replayId'],'receiptSha256':sha(HERE/'target-receipt.txt'),
            'publishedReaderSha256':sha(PRIOR),'readerAdaptation':'Only expected request and literal packing3->4; original schema/geometry/mapping/independent metric/strict probe assertions retained',
            'metrics':row['physicalMetrics'],'geometrySha256':row['identity']['selectedGeometrySha256Before'],
            'packingProbe':row['packingProbe'],'readerNegativeControls':controls,
            'limits':'Exact finite native target; no player/electrical/build/allseed proof'}
if __name__=='__main__':
    result=assess()
    with (HERE/'target-comparison.json').open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS exact packing4 target and five reader negatives')
