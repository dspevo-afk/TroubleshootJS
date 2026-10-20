"""Compare one private paired-placement experiment with its fresh immutable baseline."""
import copy,hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
FAMILY='PARALLEL_DUAL_INDICATOR';SEED='-9223372036854775808'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def require(ok,message):
    if not ok:raise AssertionError(message)
def rows(raw,prefix):return [json.loads(s[len(prefix):]) for s in raw.splitlines() if s.startswith(prefix)]
def normalize(row):
    result=copy.deepcopy(row);p=result['packingProbe'];clock=p['millis']
    require(type(clock) is int and clock>=0,'clock framing')
    matches=list(re.finditer(r';millis=([0-9]+);route=',p['canonical']))
    require(len(matches)==1 and int(matches[0][1])==clock,'canonical clock framing')
    p['canonical']=re.sub(r';millis=[0-9]+;route=',';millis=[clock];route=',p['canonical']);p['millis']='[clock]'
    return result
def deterministic(a,b):require(normalize(a)==normalize(b),'nonclock determinism')
def eligible_pairs(row):
    parts={p['componentId']:p for p in row['layout']['components']}
    terminals={t['padId']:(p,t) for p in parts.values() for t in p['terminals']}
    choices={}
    for net in sorted(row['layout']['nets'],key=lambda n:n['netId']):
        if len(net['padIds'])!=2:continue
        (a,ta),(b,tb)=[terminals[pad] for pad in sorted(net['padIds'])]
        if a['componentId']==b['componentId'] or len(a['terminals'])!=2 or len(b['terminals'])!=2:continue
        if a['anchor']!='NONE' or b['anchor']!='NONE':continue
        if a['regionId']!=b['regionId'] or a['domainId']!=b['domainId'] or ta['domainId']!=tb['domainId']:continue
        key=tuple(sorted((a['componentId'],b['componentId'])))
        choices.setdefault(key,net['netId'])
    used=set();selected=[]
    for (a,b),net in sorted(choices.items()):
        if a in used or b in used:continue
        used.update((a,b));selected.append((a,b,net))
    return len(choices),selected
def anchor_margin(part,outline):
    directions={(t['escape']['dx'],t['escape']['dy']) for t in part['terminals']}
    require(len(directions)==1,'one inward anchor direction')
    direction=next(iter(directions));x,y,w,h=part['accessEnvelope'];ox,oy,ow,oh=outline
    margins={(1,0):x-ox,(-1,0):ox+ow-x-w}
    require(direction in margins,'supported horizontal anchor')
    return direction,margins[direction]
def pair_events(raw):
    pending=[];groups=[];moves=[]
    for line in raw.splitlines():
        if line.startswith('PCB_POCKET_PAIR_MOVE_OBSERVATION '):
            item=json.loads(line[len('PCB_POCKET_PAIR_MOVE_OBSERVATION '):]);pending.append(item);moves.append(item)
        elif line.startswith('PCB_POCKET_PAIR_SWEEP_OBSERVATION '):
            item=json.loads(line[len('PCB_POCKET_PAIR_SWEEP_OBSERVATION '):]);groups.append(item)
            require(item['schema']=='pocket-paired-move@1','pair schema')
            a,b=item['evaluationStart'],item['evaluationEnd']
            require(type(a) is int and type(b) is int and 0<=a<=b<=60000,'original shared evaluation cap')
            require(re.fullmatch(r'compactConnectedVacancies/candidate-[0-9]+/evaluation-'+str(a),item['invocationGroup']) is not None,'pair invocation binding')
            eligible,count,targets=item['eligiblePairs'],item['disjointPairs'],item['targetsEvaluated']
            require(all(type(x) is int for x in (eligible,count,targets)) and 0<=count<=eligible and 0<=targets<=5*count and targets==b-a,'bounded pair targets')
            require(item['acceptedMoves']==len(pending)<=count,'accepted pair count')
            for k in ('areaStart','areaEnd','wireStart','wireEnd'):require(type(item[k]) in (int,float) and math.isfinite(item[k]) and item[k]>=0,'finite pair proxies')
            require(item['areaEnd']<=item['areaStart'] and item['wireEnd']<=item['wireStart'],'pair proxy nonincrease')
            if pending:require(item['areaEnd']<item['areaStart'] or item['wireEnd']<item['wireStart'],'strict accepted pair progress')
            used=set();previous=a
            for move in pending:
                require(move['schema']==item['schema'] and move['invocationGroup']==item['invocationGroup'],'move schema')
                ca,cb=move['componentA'],move['componentB']
                require(isinstance(ca,str) and isinstance(cb,str) and ca<cb and ca not in used and cb not in used,'canonical disjoint move IDs')
                used.update((ca,cb));require(isinstance(move['netId'],str) and bool(move['netId']),'move net')
                evaluation=move['evaluation'];require(type(evaluation) is int and previous<evaluation<=b,'accepted move evaluation');previous=evaluation
                points=[move[k] for k in ('fromA','fromB','toA','toB')]
                require(all(isinstance(p,list) and len(p)==2 and all(type(x) is int for x in p) for p in points),'move positions')
                dx,dy=move['dx'],move['dy']
                require(type(dx) is int and type(dy) is int and (dx!=0 or dy!=0) and dx%10==dy%10==0,'common snapped displacement')
                require(all(move[to][i]-move[fr][i]==(dx,dy)[i] for fr,to in (('fromA','toA'),('fromB','toB')) for i in (0,1)),'rigid paired translation')
            pending=[]
    require(not pending,'terminal pair aggregate')
    return groups,moves
def self_test():
    move={'schema':'pocket-paired-move@1','invocationGroup':'compactConnectedVacancies/candidate-0/evaluation-10','componentA':'LED1','componentB':'R1','netId':'N1','fromA':[100,200],'fromB':[300,200],'toA':[110,190],'toB':[310,190],'dx':10,'dy':-10,'evaluation':11}
    group={'schema':move['schema'],'invocationGroup':move['invocationGroup'],'evaluationStart':10,'evaluationEnd':12,'eligiblePairs':1,'disjointPairs':1,'targetsEvaluated':2,'acceptedMoves':1,'areaStart':100000,'areaEnd':99000,'wireStart':1000,'wireEnd':990}
    def body(m,g):return 'PCB_POCKET_PAIR_MOVE_OBSERVATION '+json.dumps(m)+'\nPCB_POCKET_PAIR_SWEEP_OBSERVATION '+json.dumps(g)
    require(pair_events(body(move,group))==([group],[move]),'literal paired positive')
    for expected,m,g in (('rigid paired translation',dict(move,toB=[320,190]),group),('original shared evaluation cap',move,dict(group,evaluationEnd=60001)),('bounded pair targets',move,dict(group,targetsEvaluated=6)),('canonical disjoint move IDs',dict(move,componentB='LED1'),group)):
        try:pair_events(body(m,g))
        except AssertionError as e:require(str(e)==expected,'specific pair negative')
        else:raise AssertionError('pair negative accepted '+expected)
    part={'accessEnvelope':[70,100,100,100],'terminals':[{'escape':{'dx':1,'dy':0}}]}
    require(anchor_margin(part,[40,40,1000,500])==((1,0),30),'literal left anchor margin')
    part['accessEnvelope'][0]+=10
    require(anchor_margin(part,[40,40,1000,500])!=((1,0),30),'literal moved anchor rejection')
    return {'literalRigidMove':'PASS','unequalDisplacement':'PASS','originalCap':'PASS','fiveTargetBound':'PASS','duplicateMember':'PASS','literalAnchorEdge':'PASS'}
def phase_result(phase):
    p=HERE/phase;t=read(p/'timing.json');launch=read(p/'process-launch.json');cleanup=read(p/'process-cleanup.json')
    require(t['phase']==phase and t['status']=='PASS' and t['exitCode']==0 and t['failure'] is None,'actual phase success')
    require(math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0 and t['terminalCleanupIncluded'] is True,'phase timing boundary')
    require(cleanup['status']=='PASS' and cleanup['actualExecExitCode']==0 and cleanup['launchSha256']==sha(p/'process-launch.json'),'actual tool completion')
    require(len(cleanup['checks'])==len(launch['drivers'])==2 and all(x['recordedBirthExited'] is True for x in cleanup['checks']),'recorded births exited')
    require([{k:x[k] for k in ('pid','startTicks','executable')} for x in cleanup['checks']]==launch['drivers'],'birth correspondence')
    for side in ('before','after'):require(text(p/('input-audit-'+side+'.txt')).strip()=='PASS paired probe unchanged source1482 machinery24 sealed helpers/private inputs','exact input audit')
    return t
def main(private):
    helper=runpy.run_path(str(HERE/'inputs.py'));require(helper['seal'](private)==read(HERE/'experiment-inputs.json'),'frozen experiment inputs')
    controls=self_test();canary=phase_result('canary');checks=read(HERE/'canary/canaries.json')['checks']
    require([x['kind'] for x in checks]==['seed','owner'] and all(x['status']=='PASS_REJECTED_AT_PARAMETER_BINDING' and x['actualAdapterExitCode']==1 and x['privateLogBytes']>0 for x in checks),'real selected binding negatives')
    prior=runpy.run_path(str(HERE.parent/'npn-sparse-diagnostic-r1/qualify.py'))
    validate=prior['validate_row'];validate.__globals__['EXPECTED']={'dual':(FAMILY,'EASY',SEED)}
    actual={};results={};observations={};samples={}
    for phase in ('baseline','cold','repeat'):
        timing=phase_result(phase);receipt=text(HERE/phase/'receipt.txt');log=text(HERE/phase/'native.txt')
        require('CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log,'no maintained native failure')
        require(log.count('PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.')==1,'focused native completion')
        r=rows(receipt,'PCB_SPARSE_NORMAL_DIAGNOSTIC ');s=rows(receipt,'PCB_WHITESPACE_CASE ')
        require(len(r)==len(s)==1,'exact observation rows')
        require(len(re.findall(r'(?m)^PASS: sparse normal board diagnostic family='+FAMILY+' seed='+SEED+r' assertions=[1-9][0-9]*\r?$',receipt))==1,'exact positive native marker')
        row=r[0];validate(row,s[0],'dual');actual[phase]=row;samples[phase]=s[0]
        groups,moves=pair_events(receipt);observations[phase]=(groups,moves)
        require(bool(groups)==(phase!='baseline'),'private-only pair phase')
        results[phase]={'elapsedSeconds':timing['elapsedSeconds'],'receiptSha256':sha(HERE/phase/'receipt.txt'),'metrics':row['physicalMetrics'],'packingProbe':row['packingProbe'],'pairGroups':groups,'pairMoves':moves}
    original=actual['baseline'];baseline=original['physicalMetrics'];selected=actual['cold']['physicalMetrics']
    for expected,mutate in (('public identity',lambda r:r.update(publicSeed='0')),
                            ('pad net membership',lambda r:r['layout']['pads'][0].update(netId='__WRONG_NET__')),
                            ('independent copper',lambda r:r['physicalMetrics'].update(copper=r['physicalMetrics']['copper']+1)),
                            ('independent protected void',lambda r:r['physicalMetrics'].update(protectedVoid=r['physicalMetrics']['protectedVoid']+1))):
        changed=copy.deepcopy(original);mutate(changed)
        try:validate(changed,samples['baseline'],'dual')
        except AssertionError as e:require(str(e)==expected,'specific reused validator negative');controls[expected]='PASS'
        else:raise AssertionError('reused validator mutation accepted '+expected)
    eligible,disjoint=eligible_pairs(original)
    require(eligible>0 and bool(disjoint),'actual eligible pair witness')
    census=read(HERE.parent/'cross-family-whitespace-review-r1/survey.json')
    reference=[r for r in census['rankedCases'] if r['family']==FAMILY and r['publicSeed']==SEED]
    require(len(reference)==1 and reference[0]['geometrySha256']==original['identity']['selectedGeometrySha256Before'],'immutable published selected geometry')
    require(all(baseline[k]==reference[0][k] for k in ('area','copper','vias','protectedVoid')),'immutable published metrics')
    for phase in ('cold','repeat'):
        row=actual[phase]
        require(row['identity']['liveGraphSha256Before']==original['identity']['liveGraphSha256Before'],'unchanged logical graph')
        for key in ('componentIds','padIds','netIds'):require(row['identity'][key]==original['identity'][key],'unchanged electrical identities')
        groups,moves=observations[phase]
        require(all(g['eligiblePairs']==eligible and g['disjointPairs']==len(disjoint) for g in groups),'independent canonical pair inventory')
        require(all((m['componentA'],m['componentB'],m['netId']) in disjoint for m in moves),'accepted pair electrical membership')
        current={p['componentId']:p for p in row['layout']['components']}
        for old in original['layout']['components']:
            new=current[old['componentId']]
            for key in ('packageId','geometryVariant','geometryTransform','terminalDomains','anchor','regionId','domainId'):require(new[key]==old[key],'unchanged physical declarations')
            require(all(new['pose'][k]==old['pose'][k] for k in ('width','height','rotation','mountingSide')),'rigid package identity')
            if old['anchor']=='NONE':continue
            require(anchor_margin(old,original['layout']['outline'])==anchor_margin(new,row['layout']['outline']),'anchor edge binding')
    deterministic(actual['cold'],actual['repeat']);require(observations['cold']==observations['repeat'],'pair event determinism')
    changed=copy.deepcopy(actual['cold']);changed['layout']['components'][0]['pose']['x']+=10
    try:deterministic(actual['cold'],changed)
    except AssertionError as e:require(str(e)=='nonclock determinism','specific nonclock rejection')
    else:raise AssertionError('nonclock mutation accepted')
    changed=copy.deepcopy(actual['cold']);p=changed['packingProbe'];p['millis']+=7;p['canonical']=re.sub(r';millis=[0-9]+;route=',';millis='+str(p['millis'])+';route=',p['canonical']);deterministic(actual['cold'],changed)
    predicates={'areaStrictlySmaller':selected['area']<baseline['area'],'copperNonincreasing':selected['copper']<=baseline['copper'],'viasNonincreasing':selected['vias']<=baseline['vias'],'protectedVoidNonincreasing':selected['protectedVoid']<=baseline['protectedVoid']}
    decision='GO_REQUIRES_SEPARATE_IMPLEMENTATION_SCOPE' if all(predicates.values()) else 'NO_GO_NO_STRICT_ROUTED_GAIN'
    result={'status':'PASS_FINITE_PRIVATE_PAIRED_EXPERIMENT','decision':decision,'inputSealSha256':sha(HERE/'experiment-inputs.json'),'baselineMetrics':baseline,'selectedMetrics':selected,'strictPredicates':predicates,'baselineGeometryRetained':original['identity']['selectedGeometrySha256Before']==actual['cold']['identity']['selectedGeometrySha256Before'],'independentlyEnumeratedEligiblePairs':eligible,'independentlyEnumeratedDisjointPairs':disjoint,'readerControls':dict(controls,clockOnly='PASS',nonclock='PASS'),'canaryElapsedSeconds':canary['elapsedSeconds'],'results':results,'limits':'Exact small-board request only. Task-only private planner; no production/build/player/all-seed/optimality or medium initial-planning proof. Existing Java validators and independent sampler retained; this reader adapts only the old validator expected request. No isolation geometry is exported; the private source exclusion and existing full physical validators retain that check.'}
    with (HERE/'assessment.json').open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS finite private paired experiment; decision='+decision)
if __name__=='__main__':
    if sys.argv[1:]==['--self-test']:print(json.dumps(self_test(),sort_keys=True))
    else:main(Path(sys.argv[1]))
