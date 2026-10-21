"""Qualify a private P05 strip proposal before the unchanged pair phase."""
import copy,hashlib,json,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
PRIOR=HERE.parent/'fresh-relay-stage-diagnostic-r1'
BASE_GEOMETRY='ce4fd139924615af760040a06df6fc3040667cdbcf2745dcc935e0bf8ed239af'
METRICS={'area':851092,'copper':6600,'vias':0,'protectedVoid':74000}
BUDGET_PREFIX='PCB_OPTIONAL_STRIP_BUDGET_CHECKPOINT '
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def require(ok,message):
    if not ok:raise AssertionError(message)
def load_reader():
    baseline=read(HERE/'baseline-inputs.json')
    require(hashlib.sha256((PRIOR/'qualify.py').read_bytes()).hexdigest()==baseline['controlEvidence']['qualify.py'],'exact accepted relay reader')
    return runpy.run_path(str(PRIOR/'qualify.py'))
def validate_budgets(records):
    keys={'inputEvaluations','resultEvaluations','samePlan','inputUnchanged','parts','pads'}
    require(len(records)==3 and all(isinstance(r,dict) and set(r)==keys for r in records),'exact three native budget canary schemas')
    require(sorted(r['inputEvaluations'] for r in records)==[0,59999,60000],'exact budget boundary inputs')
    for r in records:
        require(all(type(r[k]) is int for k in ('inputEvaluations','resultEvaluations','parts','pads')) and
                0<=r['inputEvaluations']<=r['resultEvaluations']<=60000,'native budget canary typed cumulative bound')
        require(type(r['samePlan']) is bool and r['inputUnchanged'] is True and r['parts']==9 and r['pads']==22,'native budget canary input and identity preservation')
        if r['inputEvaluations']:
            require(r['samePlan'] is True and r['resultEvaluations']==r['inputEvaluations'],'exhausted optional budget returns exact untouched input')
    return records
def validate_comparison(receipt):
    oracle=load_reader();control=(PRIOR/'relay/receipt.txt').read_text(encoding='utf-8-sig')
    rows=lambda s,p:oracle['read_rows'](s,p)
    budgets=validate_budgets(rows(receipt,BUDGET_PREFIX))
    original=rows(control,oracle['ROW_PREFIX'])[0]
    actual=rows(receipt,oracle['ROW_PREFIX']);require(len(actual)==1,'one variant row');actual=actual[0]
    old_stages=rows(control,oracle['STAGE_PREFIX']);stages=rows(receipt,oracle['STAGE_PREFIX'])
    require(len(old_stages)==len(stages)==4,'exact four cross-run stages')
    require(stages[0]==old_stages[0],'unchanged routed source checkpoint')
    for stage in stages:
        require(all(stage[k]==old_stages[0][k] for k in ('routingSeed','layoutSeed','planCandidate')),'original routing stream and placement identity')
    observed=oracle['validate_receipt'](receipt)
    require({k:v for k,v in stages[1].items() if k!='phase'}==
        {k:v for k,v in stages[2].items() if k!='phase'},'zero eligible pairs preserve strip result and counted work')
    for k in ('family','profile','publicSeed','replayId','executionPolicy','physicalAdmission'):
        require(actual[k]==original[k],'unchanged ordinary identity '+k)
    for k in ('liveGraphSha256Before','liveGraphSha256After','componentIds','padIds','netIds'):
        require(actual['identity'][k]==original['identity'][k],'unchanged electrical graph and identities '+k)
    probe=actual['packingProbe']
    require(probe['baselineGeometrySha256']==BASE_GEOMETRY,'exact accepted baseline geometry')
    if probe['attempts']:
        require(probe['metricEvidence']['baseline']==METRICS,'exact accepted four-metric baseline')
        require(type(probe['attempts']) is int and probe['attempts']==1 and
            type(probe['orderings']) is int and 1<=probe['orderings']<=5 and
            type(probe['expansions']) is int and 0<=probe['expansions']<=1000000,'one bounded optional routing call')
    require(actual['layout']['nets']==original['layout']['nets'],'unchanged exact logical net declarations')
    selected=probe['disposition']=='SELECTED'
    if not selected:
        require(actual['layout']==original['layout'] and actual['physicalMetrics']==original['physicalMetrics'],'retained original full physical layout and metrics')
    else:
        m=actual['physicalMetrics']
        require(m['area']<METRICS['area'] and all(m[k]<=METRICS[k] for k in ('copper','vias','protectedVoid')),'strict target improvement from accepted control')
    return {'status':'GO_PRIVATE_TARGET_ONLY' if selected else 'NO_GO_PRIVATE_TARGET',
        'sourceRoutingSeed':old_stages[0]['routingSeed'],'optionalRoutingSeed':stages[3]['routingSeed'],
        'controlReceiptSha256':hashlib.sha256((PRIOR/'relay/receipt.txt').read_bytes()).hexdigest(),
        'budgetCanaries':budgets,'variant':observed,
        'limits':'One private strip stage before unchanged pair pass. Target GO requires broader qualification; no production, matrix, GWT, browser, all-seed or optimality proof.'}
def negatives(receipt):
    oracle=load_reader();out=[]
    def replace(prefix,mutate):
        lines=receipt.splitlines();indexes=[i for i,l in enumerate(lines) if l.startswith(prefix)]
        values=[json.loads(lines[i][len(prefix):]) for i in indexes];mutate(values)
        for i,v in zip(indexes,values):lines[i]=prefix+json.dumps(v,separators=(',',':'))
        return '\n'.join(lines)+'\n'
    def capped_false(vs):
        next(r for r in vs if r['inputEvaluations']==60000)['samePlan']=False
    def headroom_over(vs):
        next(r for r in vs if r['inputEvaluations']==0)['resultEvaluations']=60001
    tests=[('source cumulative work changed',oracle['STAGE_PREFIX'],lambda vs:vs[0].update(evaluations=vs[0]['evaluations']+1),'unchanged routed source checkpoint'),
        ('routing stream changed',oracle['STAGE_PREFIX'],lambda vs:vs[3].update(routingSeed=str(int(vs[3]['routingSeed'])+1)),'original routing stream and placement identity'),
        ('exhausted budget returns different plan',BUDGET_PREFIX,capped_false,'exhausted optional budget returns exact untouched input'),
        ('headroom fixture exceeds cap',BUDGET_PREFIX,headroom_over,'native budget canary typed cumulative bound')]
    for label,prefix,mutate,expected in tests:
        try:validate_comparison(replace(prefix,mutate))
        except AssertionError as e:
            require(str(e)==expected,'negative exact assertion');out.append(label)
        else:raise AssertionError('accepted comparison negative '+label)
    missing='\n'.join(l for i,l in enumerate(receipt.splitlines()) if not (l.startswith(BUDGET_PREFIX) and '"inputEvaluations":60000' in l.replace(' ','')))+'\n'
    require(missing!=receipt,'missing canary negative framing')
    try:validate_comparison(missing)
    except AssertionError as e:
        require(str(e)=='exact three native budget canary schemas','missing native canary assertion');out.append('missing native budget canary')
    else:raise AssertionError('accepted missing native canary')
    return out
