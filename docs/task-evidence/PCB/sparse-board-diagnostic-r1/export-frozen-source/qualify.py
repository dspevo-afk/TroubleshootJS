"""Independently check actual selected sparse-board observations."""
import copy,hashlib,json,math,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
EXPECTED={'rc':('RC_DELAY','EASY','-9187226374781659024'),
          'composed':('COMPOSED_CONTROLLED_INDICATOR','MEDIUM','7141843758046216173')}
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def require(ok,message):
    if not ok:raise AssertionError(message)
def rect(r):require(isinstance(r,list) and len(r)==4 and all(type(x) is int for x in r) and r[2]>=0 and r[3]>=0,'rectangle framing')
def validate_row(row,sampler,phase):
    family,profile,seed=EXPECTED[phase]
    require(row['schema']=='sparse-normal-board@1','row schema')
    require((row['family'],row['profile'],row['publicSeed'])==(family,profile,seed),'public identity')
    require(row['replayId']=='tsj-alpha/6/'+profile+'/'+family+'/'+seed,'public replay')
    require(row['executionPolicy']=='SMALL_BOARD_EXECUTION@1' and bool(row['physicalAdmission']),'actual ordinary execution policy')
    identity=row['identity'];layout=row['layout'];counts=row['counts']
    for before,after,flag in (('selectedGeometrySha256Before','selectedGeometrySha256After','selectedGeometryUnchanged'),
                              ('liveGraphSha256Before','liveGraphSha256After','liveGraphUnchanged')):
        require(re.fullmatch('[0-9a-f]{64}',identity[before]) is not None and identity[before]==identity[after] and identity[flag] is True,'identity immutability')
    require(layout['algorithmVersion']==19 and layout['constraints']['placementPackingVersion']==3,'current physical epochs')
    require(layout['constraints']['requiresTwoLayerRouting'] is False,'ordinary one-face target policy')
    components=layout['components'];pads=layout['pads'];nets=layout['nets']
    for items,key,ids in ((components,'componentId',identity['componentIds']),(pads,'padId',identity['padIds']),(nets,'netId',identity['netIds'])):
        require([x[key] for x in items]==ids==sorted(set(ids)),'canonical exact inventories')
    require(counts['logicalParts']==counts['layoutParts']==len(components) and counts['logicalPads']==counts['layoutPads']==len(pads) and counts['logicalNets']==len(nets),'inventory counts')
    padmap={p['padId']:p for p in pads};netmap={n['netId']:n for n in nets};seen=set()
    for p in components:
        for name in ('bounds','bodyBounds','keepOut','routingCourtyard','accessEnvelope','selectionBounds','dragBounds'):rect(p[name])
        require(type(p['accessMargin']) is int and p['accessMargin']>=0,'access margin')
        for i,t in enumerate(p['terminals']):
            require(t['index']==i and t['padId'] not in seen,'terminal identity order')
            seen.add(t['padId']);pad=padmap[t['padId']]
            require(pad['componentId']==p['componentId'] and pad['terminalId']==t['terminalId'] and pad['center']==t['padCenter'],'terminal pad identity')
            require(pad['netId']==t['netId'] and pad['netId'] in netmap,'pad net membership')
    require(seen==set(padmap),'complete terminal inventory')
    for net in nets:
        require(net['padIds']==sorted(p['padId'] for p in pads if p['netId']==net['netId']),'net pad membership')
    copper=0
    for trace in layout['traces']:
        require(trace['netId'] in netmap and len(trace['points'])>=2,'trace framing')
        for endpoint in ('startPadId','endPadId'):
            if trace[endpoint] is not None:require(padmap[trace[endpoint]]['netId']==trace['netId'],'trace endpoint net')
        for a,b in zip(trace['points'],trace['points'][1:]):
            require(len(a)==len(b)==2 and all(type(x) is int for x in a+b),'trace point framing')
            copper+=abs(a[0]-b[0])+abs(a[1]-b[1])
    require(counts['traces']==len(layout['traces']) and counts['holes']==len(layout['holes']),'copper inventory')
    vias=sum(h['kind']=='VIA' for h in layout['holes'])
    for h in layout['holes']:
        if h['netId'] is not None:require(h['netId'] in netmap,'hole net membership')
    outline=layout['outline'];rect(outline);area=outline[2]*outline[3]
    require(sampler['seed']==seed and sampler['version']==1 and sampler['parts']==len(components) and sampler['gridUnits']==10,'independent sampler identity')
    require(sampler['scope'].endswith(';FREE_IS_NOT_PACKABLE_PROOF'),'sampler limit')
    void=sampler['largestProtectedEmptyRectangle']
    require(void['width']*void['height']==void['area'],'sampler rectangle area')
    require(void['x']>=outline[0] and void['y']>=outline[1] and void['x']+void['width']<=outline[0]+outline[2] and void['y']+void['height']<=outline[1]+outline[3],'sampler rectangle containment')
    metrics=row['physicalMetrics']
    independent=row['independentMetrics']
    require(independent['samplerVersion']==1 and independent['samplerGridStep']==10 and independent['area']==area and independent['copper']==copper and independent['vias']==vias and independent['protectedVoid']==void['area'],'independent exported observation')
    if metrics['available']:
        require(metrics['unavailable'] is False and metrics['area']==area,'independent area')
        require(metrics['copper']==copper,'independent copper')
        require(metrics['vias']==vias,'independent vias')
        require(metrics['protectedVoid']==void['area'],'independent protected void')
        require(independent['matchesProduction'] is True,'production comparison state')
    else:
        require(metrics['unavailable'] is True and bool(metrics['reason']) and all(metrics[k] is None for k in ('area','copper','vias','protectedVoid')),'explicit unavailable metrics')
        require(independent['matchesProduction'] is False and independent['comparisonUnavailableReason']==metrics['reason'],'unavailable comparison state')
    validation=row['validation']
    require(all(validation[k]=='PASS' for k in ('normalAdmission','geometry','routingGeometry','routeQuality','traceClearance','access')),'physical validation')
    require(validation['graphImmutability'] is True and validation['selectedGeometryImmutability'] is True and validation['accessReachableCells']>0,'physical invariants')
    probe=row['packingProbe'];evidence=probe['metricEvidence']
    require(probe['canonical'].startswith('normal-packing@3;'),'probe epoch')
    canonical='normal-packing@3'+''.join(';'+key+'='+str(probe[field]) for key,field in (('attempts','attempts'),('orderings','orderings'),('expansions','expansions'),('millis','millis'),('route','routePolicy'),('disposition','disposition'),('baseline','baselineGeometrySha256'),('selected','selectedGeometrySha256')))
    require(probe['canonical']==canonical and probe['routePolicy']=='P05_ONE_FACE','canonical ordinary probe')
    require(probe['selectedGeometrySha256']==identity['selectedGeometrySha256Before'],'probe selected identity')
    allowed={'NO_CHANGE':('NOT_MEASURED',),'NO_HEADROOM':('NOT_MEASURED','BASELINE_UNAVAILABLE'),
             'ROUTE_REJECTED':('BASELINE_ONLY',),'KEPT':('CANDIDATE_UNAVAILABLE','COMPARED'),'SELECTED':('COMPARED',)}
    require(probe['disposition'] in allowed and evidence['state'] in allowed[probe['disposition']],'probe evidence state')
    if evidence['state']=='COMPARED':
        a=evidence['baseline'];b=evidence['candidate']
        selected=b['area']<a['area'] and b['copper']<=a['copper'] and b['vias']<=a['vias'] and b['protectedVoid']<=a['protectedVoid']
        require((probe['disposition']=='SELECTED')==selected,'four strict packing predicates')
        if metrics['available']:require(all(metrics[k]==(b if selected else a)[k] for k in ('area','copper','vias','protectedVoid')),'selected metric tuple')
    if probe['disposition']!='SELECTED':require(probe['baselineGeometrySha256']==probe['selectedGeometrySha256'],'retained incumbent geometry')
    return {'family':family,'profile':profile,'publicSeed':seed,'replayId':row['replayId'],'geometrySha256':identity['selectedGeometrySha256Before'],
            'logicalBoardGraphSha256':identity['liveGraphSha256Before'],'counts':counts,'outline':outline,'physicalMetrics':metrics,
            'packingProbe':probe,'whitespaceSampler':sampler,
            'componentEnvelopes':[{k:p[k] for k in ('componentId','packageId','anchor','accessMargin','bodyBounds','routingCourtyard','accessEnvelope','regionId','domainId')} for p in components]}
def phase_result(phase):
    p=HERE/phase;t=read(p/'timing.json');launch=read(p/'process-launch.json');cleanup=read(p/'process-cleanup.json')
    require(t['phase']==phase and t['status']=='PASS' and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None and t['terminalCleanupIncluded'] is True,'actual successful operation')
    require(type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0,'actual operation elapsed')
    require(cleanup['status']=='PASS' and type(cleanup['actualExecExitCode']) is int and cleanup['actualExecExitCode']==0 and cleanup['launchSha256']==sha(p/'process-launch.json'),'actual tool exit and launch binding')
    require(len(cleanup['checks'])==len(launch['drivers'])==2 and all(x['recordedBirthExited'] is True for x in cleanup['checks']),'driver births exited')
    require([{k:x[k] for k in ('pid','startTicks','executable')} for x in cleanup['checks']]==launch['drivers'],'driver birth correspondence')
    for side in ('before','after'):require(text(p/('input-audit-'+side+'.txt')).strip()=='PASS sparse export unchanged published source1482 machinery24 frozen helpers12','phase exact input audit')
    return t
def main():
    require(runpy.run_path(str(HERE/'export_inputs.py'))['record']()==read(HERE/'export-inputs.json'),'frozen input seal')
    canary_time=phase_result('canaries');checks=read(HERE/'canaries/canaries.json')['checks']
    require([x['kind'] for x in checks]==['seed','family'],'actual binding cases')
    require(all(x['status']=='PASS_REJECTED_AT_PARAMETER_BINDING' and type(x['actualAdapterExitCode']) is int and x['actualAdapterExitCode']==1 and x['privateLogBytes']>0 and re.fullmatch('[0-9a-f]{64}',x['privateLogSha256']) is not None for x in checks),'actual binding rejection')
    results={};negative=[]
    for phase in EXPECTED:
        timing=phase_result(phase);receipt=text(HERE/phase/'receipt.txt');log=text(HERE/phase/'native.txt')
        require('CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log,'no maintained failure')
        require(log.count('PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.')==1,'maintained focused completion')
        def rows(prefix):return [json.loads(s[len(prefix):]) for s in receipt.splitlines() if s.startswith(prefix)]
        actual=rows('PCB_SPARSE_NORMAL_DIAGNOSTIC ');sample=rows('PCB_WHITESPACE_CASE ')
        require(len(actual)==len(sample)==1,'exact observation row counts')
        row=actual[0];sampler=sample[0];family,_,seed=EXPECTED[phase]
        require(len(re.findall(r'(?m)^PASS: sparse normal board diagnostic family='+family+' seed='+seed+r' assertions=[1-9][0-9]*\r?$',receipt))==1,'exact native final marker')
        results[phase]=validate_row(row,sampler,phase);results[phase]['elapsedSeconds']=timing['elapsedSeconds'];results[phase]['receiptSha256']=sha(HERE/phase/'receipt.txt')
        require(row['physicalMetrics']['available'] is True,'negative canary valid measured control')
        mutations=[('public identity',lambda r:r.update(publicSeed='0')),
                   ('pad net membership',lambda r:r['layout']['pads'][0].update(netId='__WRONG_NET__')),
                   ('independent copper',lambda r:r['physicalMetrics'].update(copper=r['physicalMetrics']['copper']+1)),
                   ('independent protected void',lambda r:r['physicalMetrics'].update(protectedVoid=r['physicalMetrics']['protectedVoid']+1))]
        for expected,mutate in mutations:
            changed=copy.deepcopy(row);mutate(changed)
            try:validate_row(changed,sampler,phase)
            except AssertionError as failure:require(str(failure)==expected,'nonvacuous negative canary');negative.append({'phase':phase,'rejectedCondition':expected})
            else:raise AssertionError('negative reader accepted '+expected)
    out=HERE/'assessment.json';require(not out.exists(),'preserve prior assessment')
    out.write_text(json.dumps({'status':'PASS_EXACT_SELECTED_NORMAL_BOARD_DIAGNOSIS','inputSealSha256':sha(HERE/'export-inputs.json'),'canaryElapsedSeconds':canary_time['elapsedSeconds'],'results':results,'readerCanaries':negative,'limits':'Finite exact accepted public replays; unchanged selected geometry and logical board declarations. Existing grid10 sampler free cells are not packability proof. No production change, new electrical/player/build proof, optimum or all-seed claim.'},indent=2)+'\n',encoding='utf-8')
    print('PASS exact two accepted replays; independent metrics/inventories; eight nonvacuous reader negatives; separate actual tool exits')
if __name__=='__main__':main()
