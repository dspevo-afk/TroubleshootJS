"""Compare a fixed optional routing stream with its exact accepted relay control."""
import copy,hashlib,json,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
PRIOR=HERE.parent/'fresh-relay-stage-diagnostic-r1'
BASE_GEOMETRY='ce4fd139924615af760040a06df6fc3040667cdbcf2745dcc935e0bf8ed239af'
METRICS={'area':851092,'copper':6600,'vias':0,'protectedVoid':74000}
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def require(ok,message):
    if not ok:raise AssertionError(message)
def load_reader():
    baseline=read(HERE/'baseline-inputs.json')
    require(hashlib.sha256((PRIOR/'qualify.py').read_bytes()).hexdigest()==baseline['controlEvidence']['qualify.py'],'exact accepted relay reader')
    return runpy.run_path(str(PRIOR/'qualify.py'))
def validate_comparison(receipt):
    oracle=load_reader()
    control=(PRIOR/'relay/receipt.txt').read_text(encoding='utf-8-sig')
    rows=lambda s,p:oracle['read_rows'](s,p)
    original=rows(control,oracle['ROW_PREFIX'])[0]
    actual=rows(receipt,oracle['ROW_PREFIX'])
    require(len(actual)==1,'one variant row');actual=actual[0]
    vectors=read(HERE/'derivation-vectors.json')
    old_stages=rows(control,oracle['STAGE_PREFIX'])
    stages=rows(receipt,oracle['STAGE_PREFIX'])
    require(len(old_stages)==len(stages)==4,'exact four cross-run stages')
    require(vectors['actualSourceSeed']==old_stages[0]['routingSeed'],'source stream derives from exact internal routing seed')
    require(oracle['canonical_long'](vectors['actualDerivedSeed']) and vectors['actualDerivedSeed']!=vectors['actualSourceSeed'],'derived signed-long stream')
    for before,after in zip(old_stages,stages):
        expected=dict(before,routingSeed=vectors['actualDerivedSeed'])
        require(after==expected,'exact control placement stages except planned optional routing seed')
    observed=oracle['validate_receipt'](receipt)
    for k in ('family','profile','publicSeed','replayId','executionPolicy','physicalAdmission'):
        require(actual[k]==original[k],'unchanged ordinary identity '+k)
    for k in ('liveGraphSha256Before','liveGraphSha256After','componentIds','padIds','netIds'):
        require(actual['identity'][k]==original['identity'][k],'unchanged electrical graph and identities '+k)
    probe=actual['packingProbe']
    require(probe['baselineGeometrySha256']==BASE_GEOMETRY and probe['metricEvidence']['baseline']==METRICS,'exact accepted geometry and four-metric baseline')
    require(type(probe['attempts']) is int and probe['attempts']==1 and
        type(probe['orderings']) is int and 1<=probe['orderings']<=5 and
        type(probe['expansions']) is int and 0<=probe['expansions']<=1000000,'one bounded optional routing call')
    require(actual['layout']['nets']==original['layout']['nets'],'unchanged exact logical net declarations')
    if probe['disposition']!='SELECTED':
        require(actual['layout']==original['layout'] and actual['physicalMetrics']==original['physicalMetrics'],'retained original full physical layout and metrics')
    events=rows(receipt,oracle['CAUSE_PREFIX'])
    outer=[e for e in events if e['event']=='PACKING_CAUGHT']
    if outer:require(outer[0]['routingSeed']==vectors['actualDerivedSeed'],'outer rejection uses actual derived optional seed')
    selected=probe['disposition']=='SELECTED'
    if selected:
        m=actual['physicalMetrics']
        require(m['area']<METRICS['area'] and all(m[k]<=METRICS[k] for k in ('copper','vias','protectedVoid')),'strict target improvement from accepted control')
    return {'status':'GO_PRIVATE_TARGET_ONLY' if selected else 'NO_GO_PRIVATE_TARGET',
        'sourceRoutingSeed':vectors['actualSourceSeed'],'optionalRoutingSeed':vectors['actualDerivedSeed'],
        'controlReceiptSha256':hashlib.sha256((PRIOR/'relay/receipt.txt').read_bytes()).hexdigest(),
        'variant':observed,'limits':'One fixed private stream, unchanged placement. Target GO requires broader qualification before production; no new production, matrix, GWT or player proof.'}
def negatives(receipt):
    oracle=load_reader();out=[]
    def replace(prefix,mutate):
        lines=receipt.splitlines();indexes=[i for i,l in enumerate(lines) if l.startswith(prefix)]
        values=[json.loads(lines[i][len(prefix):]) for i in indexes]
        mutate(values)
        for i,v in zip(indexes,values):lines[i]=prefix+json.dumps(v,separators=(',',':'))
        return '\n'.join(lines)+'\n'
    old=oracle['read_rows']((PRIOR/'relay/receipt.txt').read_text(encoding='utf-8-sig'),oracle['STAGE_PREFIX'])[0]['routingSeed']
    tests=[('original source routing stream',oracle['STAGE_PREFIX'],lambda vs:[v.update(routingSeed=old) for v in vs],
            'exact control placement stages except planned optional routing seed'),
        ('different shared stage work',oracle['STAGE_PREFIX'],lambda vs:[v.update(evaluations=v['evaluations']+1) for v in vs],
            'exact control placement stages except planned optional routing seed'),
        ('changed source layout seed',oracle['STAGE_PREFIX'],lambda vs:[v.update(layoutSeed=str(int(v['layoutSeed'])+1)) for v in vs],
            'exact control placement stages except planned optional routing seed')]
    for label,prefix,mutate,expected in tests:
        changed=replace(prefix,mutate)
        try:
            validate_comparison(changed)
        except AssertionError as e:
            require(str(e)==expected,'negative exact assertion');out.append(label)
        else:raise AssertionError('accepted comparison negative '+label)
    return out
