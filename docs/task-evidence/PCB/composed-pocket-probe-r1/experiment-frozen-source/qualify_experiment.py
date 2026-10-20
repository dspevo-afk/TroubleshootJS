"""Decide a finite private-planner experiment against immutable production data."""
import copy,hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;PRIOR=HERE.parent/'sparse-board-diagnostic-r1'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def require(ok,message):
    if not ok:raise AssertionError(message)
def rows(receipt,prefix):return [json.loads(s[len(prefix):]) for s in receipt.splitlines() if s.startswith(prefix)]
def normalize(row):
    result=copy.deepcopy(row);probe=result['packingProbe'];clock=probe['millis']
    require(type(clock) is int and clock>=0,'packing wall clock framing')
    match=list(re.finditer(r';millis=([0-9]+);route=',probe['canonical']))
    require(len(match)==1 and int(match[0][1])==clock,'exact canonical clock framing')
    probe['canonical'],count=re.subn(r';millis=[0-9]+;route=',';millis=[observed-clock];route=',probe['canonical'])
    require(count==1,'single canonical clock');probe['millis']='[observed-clock]'
    return result
def deterministic(a,b):require(normalize(a)==normalize(b),'nonclock determinism')
def sweep_checks(items,part_count):
    require(bool(items),'actual sweep observation required')
    previous=None;groups=0
    for item in items:
        require(item['schema']=='pocket-second-sweep@1' and item['sweep'] in (1,2),'sweep framing')
        start=item['evaluationStart'];end=item['evaluationEnd']
        require(type(start) is int and type(end) is int and 0<=start<=end<=60000,'original shared evaluation cap')
        require(end-start<=5*part_count,'unchanged five-target bound')
        moves=item['acceptedMoves'];require(type(moves) is int and 0<=moves<=part_count,'accepted translations bound')
        for key in ('areaStart','areaEnd','wireStart','wireEnd'):require(type(item[key]) in (int,float) and math.isfinite(item[key]) and item[key]>=0,'finite move proxies')
        require(item['areaEnd']<=item['areaStart'] and item['wireEnd']<=item['wireStart'],'move proxy monotonicity')
        if moves:require(item['areaEnd']<item['areaStart'] or item['wireEnd']<item['wireStart'],'accepted strict move proxy')
        if item['sweep']==1:groups+=1
        else:
            require(previous is not None and previous['sweep']==1 and previous['acceptedMoves']>0 and previous['evaluationEnd']<60000,'second sweep eligibility')
            require(start==previous['evaluationEnd'] and item['areaStart']==previous['areaEnd'] and item['wireStart']==previous['wireEnd'],'shared counter and proxy continuation')
        previous=item
    return groups
def phase_result(phase):
    p=HERE/phase;t=read(p/'timing.json');launch=read(p/'process-launch.json');cleanup=read(p/'process-cleanup.json')
    require(t['phase']==phase and t['status']=='PASS' and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None and t['terminalCleanupIncluded'] is True,'actual operation success')
    require(type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0,'actual elapsed')
    require(cleanup['status']=='PASS' and type(cleanup['actualExecExitCode']) is int and cleanup['actualExecExitCode']==0 and cleanup['launchSha256']==sha(p/'process-launch.json'),'actual tool exit and launch binding')
    require(len(cleanup['checks'])==len(launch['drivers'])==2 and all(x['recordedBirthExited'] is True for x in cleanup['checks']),'driver births exited')
    require([{k:x[k] for k in ('pid','startTicks','executable')} for x in cleanup['checks']]==launch['drivers'],'driver correspondence')
    for side in ('before','after'):require(text(p/('input-audit-'+side+'.txt')).strip()=='PASS private second-sweep experiment unchanged published source1482 machinery24 helpers10 and exact supplemental inputs','exact input audit')
    return t
def anchor_margin(part,outline):
    directions={(t['escape']['dx'],t['escape']['dy']) for t in part['terminals']}
    require(len(directions)==1,'one outward anchor escape direction');direction=next(iter(directions))
    x,y,w,h=part['accessEnvelope'];ox,oy,ow,oh=outline
    margins={(-1,0):x-ox,(1,0):ox+ow-x-w,(0,-1):y-oy,(0,1):oy+oh-y-h}
    require(direction in margins,'axis-aligned anchor escape');return direction,margins[direction]
def main():
    require(runpy.run_path(str(HERE/'experiment_inputs.py'))['record']()==read(HERE/'experiment-inputs.json'),'frozen experimental inputs')
    reused=runpy.run_path(str(PRIOR/'qualify.py'))
    canary_time=phase_result('canary');canary=read(HERE/'canary/canary.json')
    require(canary['status']=='PASS_REJECTED_MISSING_PRIVATE_OWNER_AT_BINDING' and type(canary['actualAdapterExitCode']) is int and canary['actualAdapterExitCode']==1 and canary['privateLogBytes']>0,'actual missing-owner rejection')
    original=rows(text(PRIOR/'composed/receipt.txt'),'PCB_SPARSE_NORMAL_DIAGNOSTIC ')[0]
    results={};actual={};observations={}
    for phase in ('cold','repeat'):
        timing=phase_result(phase);receipt=text(HERE/phase/'receipt.txt');log=text(HERE/phase/'native.txt')
        require('CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log,'no maintained failure')
        require(log.count('PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.')==1,'focused native completion')
        parsed=rows(receipt,'PCB_SPARSE_NORMAL_DIAGNOSTIC ');sampler=rows(receipt,'PCB_WHITESPACE_CASE ')
        require(len(parsed)==len(sampler)==1,'exact final observation counts')
        row=parsed[0];actual[phase]=row
        require(len(re.findall(r'(?m)^PASS: sparse normal board diagnostic family=COMPOSED_CONTROLLED_INDICATOR seed=7141843758046216173 assertions=[1-9][0-9]*\r?$',receipt))==1,'exact positive marker')
        result=reused['validate_row'](row,sampler[0],'composed')
        require(row['identity']['liveGraphSha256Before']==original['identity']['liveGraphSha256Before'],'immutable published logical graph')
        for key in ('componentIds','padIds','netIds'):require(row['identity'][key]==original['identity'][key],'immutable published identities')
        current={p['componentId']:p for p in row['layout']['components']}
        for old in original['layout']['components']:
            if old['anchor']=='NONE':continue
            new=current[old['componentId']]
            require(all(new[k]==old[k] for k in ('anchor','packageId','geometryVariant','geometryTransform','terminalDomains')),'anchor declaration preserved')
            require(new['pose']['rotation']==old['pose']['rotation'] and new['pose']['mountingSide']==old['pose']['mountingSide'],'anchor pose orientation preserved')
            require(anchor_margin(new,row['layout']['outline'])==anchor_margin(old,original['layout']['outline']),'outward anchor edge binding preserved')
        sweeps=rows(receipt,'PCB_POCKET_SWEEP_OBSERVATION ');groups=sweep_checks(sweeps,len(current));observations[phase]=sweeps
        results[phase]={'elapsedSeconds':timing['elapsedSeconds'],'receiptSha256':sha(HERE/phase/'receipt.txt'),'sweepInvocations':groups,'sweeps':sweeps,'selectedGeometrySha256':row['identity']['selectedGeometrySha256Before'],'physicalMetrics':row['physicalMetrics'],'packingProbe':row['packingProbe']}
    deterministic(actual['cold'],actual['repeat']);require(observations['cold']==observations['repeat'],'exact sweep determinism')
    sample=copy.deepcopy(actual['cold']);sample['packingProbe']['millis']+=7
    sample['packingProbe']['canonical']=re.sub(r';millis=[0-9]+;route=',';millis='+str(sample['packingProbe']['millis'])+';route=',sample['packingProbe']['canonical']);deterministic(actual['cold'],sample)
    changed=copy.deepcopy(actual['cold']);changed['layout']['components'][0]['pose']['x']+=10
    try:deterministic(actual['cold'],changed)
    except AssertionError as e:require(str(e)=='nonclock determinism','specific negative determinism rejection')
    else:raise AssertionError('nonclock negative accepted')
    bad=copy.deepcopy(observations['cold']);bad[0]['evaluationEnd']=60001
    try:sweep_checks(bad,original['counts']['logicalParts'])
    except AssertionError as e:require(str(e)=='original shared evaluation cap','specific negative cap rejection')
    else:raise AssertionError('over-cap negative accepted')
    before=original['physicalMetrics'];after=actual['cold']['physicalMetrics'];require(before['available'] is True and after['available'] is True,'actual comparable metrics')
    predicates={'areaStrictlySmaller':after['area']<before['area'],'copperNonincreasing':after['copper']<=before['copper'],'viasNonincreasing':after['vias']<=before['vias'],'protectedVoidNonincreasing':after['protectedVoid']<=before['protectedVoid']}
    go=all(predicates.values());target=HERE/'assessment.json';require(not target.exists(),'preserve prior decision')
    target.write_text(json.dumps({'status':'PASS_BOUNDED_TASK_ONLY_EXPERIMENT','decision':'GO_REQUIRES_SEPARATE_IMPLEMENTATION_SCOPE' if go else 'NO_GO_NO_STRICT_ROUTED_GAIN','interpretation':'TASK_ONLY_SECOND_SWEEP_NOT_CURRENT_PRODUCTION_REPLAY','inputSealSha256':sha(HERE/'experiment-inputs.json'),'canaryElapsedSeconds':canary_time['elapsedSeconds'],'publishedIncumbent':{k:before[k] for k in ('area','copper','vias','protectedVoid')},'actualExperimentalSelected':{k:after[k] for k in ('area','copper','vias','protectedVoid')},'strictPredicates':predicates,'selectedGeometryEqualsPublished':actual['cold']['identity']['selectedGeometrySha256Before']==original['identity']['selectedGeometrySha256Before'],'secondSweepExecuted':any(x['sweep']==2 for x in observations['cold']),'readerCanaries':{'positiveClockOnly':'PASS','negativeNonclockCoordinate':'PASS','negativeOriginalCap':'PASS'},'results':results,'limits':'Two finite fresh-JVM experimental substitutions; no production, epoch, budget, oracle, solver, preview or player change. No rejected proposal reconstructed, optimum/all-seed/manufacturing/electrical/performance or promotion claim.'},indent=2)+'\n')
    print('PASS bounded private second-sweep experiment; decision='+('GO_REQUIRES_SEPARATE_IMPLEMENTATION_SCOPE' if go else 'NO_GO_NO_STRICT_ROUTED_GAIN'))
if __name__=='__main__':main()
