"""Independent current-gate reader: exact population, physical diversity and real admission."""
from pathlib import Path
import copy, json, re, sys
MASK=(1<<64)-1
STRIDE=0x9e3779b97f4a7c15
FIXED={'J1','J2','J4','Q1','K1','D1','F1','DREV','RIN','RDRIVE','RPD','RBLEED','C1','RLED','LED1'}
CONTROLLED_CANARY_IDS=['p09-controlled-exact','p09-controlled-search-retry','p09-replay-1',
    'p09-replay-2','p09-cancel-healthy','p09-cancel-hypotheses']
def require(value,message):
    if not value: raise ValueError(message)
def signed(value):
    value &= MASK
    return value-(1<<64) if value>>63 else value
def population(holdout):
    salt=2026091602 if holdout else 2026091601
    out=[]
    for i in range(48 if holdout else 24):
        z=(salt+STRIDE*(i+1))&MASK
        z=((z^(z>>30))*0xbf58476d1ce4e5b9)&MASK
        z=((z^(z>>27))*0x94d049bb133111eb)&MASK
        out.append(str(signed(z^(z>>31))))
    return out
def integer(value,low,high):
    require(type(value) is int and low<=value<=high,'Invalid integer or budget')
def near(a,b):
    ids=set(a['parts'])&set(b['parts'])
    moved=0
    for key in ids:
        ax,ay=a['parts'][key]; bx,by=b['parts'][key]
        moved += (abs(ax*b['width']-bx*a['width'])*100>=8*a['width']*b['width'] or
                  abs(ay*b['height']-by*a['height'])*100>=8*a['height']*b['height'])
    return moved<max(2,len(ids)//4)
def board(value):
    integer(value['width'],128,2048); integer(value['height'],128,2048)
    require(value['width']*value['height']<=2250000,'Oversize accepted board')
    require(max(value['width'],value['height'])<=3*min(value['width'],value['height']),'Unqualified aspect')
    require(set(value['parts']) in [FIXED|{'C2'},FIXED|{'RBIAS'}],'Invented or missing physical population')
    for xy in value['parts'].values():
        require(type(xy) is list and len(xy)==2,'Missing physical coordinates')
        for n in xy: integer(n,-16384,16384)
def case(row):
    require(row['status']=='COMPLETE' and row['ownerRestored'] is True,'Incomplete case/cleanup')
    seed=row['requestedSeed']; require(type(seed) is str and str(signed(int(seed)))==seed,'Rounded/noncanonical seed')
    require(row['mode'] in ['exact','search'],'Unknown launch mode')
    integer(row['units'],0,640); integer(row['jobMs'],0,90000); integer(row['maxUnitMs'],0,5000)
    integer(row['cancelAfter'],-1,2**31-1)
    attempts=row['attempts']; limit=4 if row['mode']=='search' else 1
    require(type(attempts) is list and 1<=len(attempts)<=limit,'Missing or unbounded attempts')
    for i,attempt in enumerate(attempts):
        require(attempt['ordinal']==i,'Completion race changed candidate order')
        expected=str(signed(int(seed)+STRIDE*i))
        actual=re.search(r'root-seed=(-?\d+);',attempt['manifest'])
        require(actual is not None and actual.group(1)==expected,'Hidden seed remapping')
        integer(attempt['units'],1,640); integer(attempt['proofUnits'],0,640)
        require('physicalEnvelope=THT_SINGLE_FACE@1' in attempt['manifest'],'Missing physical admission policy')
        require(attempt['stage'] in ['RESOLVE','HEALTHY','PHYSICAL','HYPOTHESES','SYMPTOM','PUBLISH'],'Unknown stage')
        if i<len(attempts)-1: require(attempt['outcome']=='EXPECTED_REJECTION','Retry after non-retryable failure')
    if row['outcome']=='PASS':
        require(attempts[-1]['outcome']=='PASS' and attempts[-1]['stage']=='PUBLISH','Incomplete publication')
        integer(row['units'],6,640); integer(row['jobMs'],1,90000); integer(row['maxUnitMs'],0,5000)
        require(row['hypotheses']==3 and row['proofUnits']>=3,'Diagnostic population shrank')
        require(attempts[-1]['proofUnits']==row['proofUnits'],'Candidate proof accounting mismatch')
        require(row['board']['seed']==str(signed(int(seed)+STRIDE*(len(attempts)-1))),'Accepted seed not in ordinal order')
        require(row['replay']=='tsj-alpha/3/EASY/RB15_CONTROL/'+row['board']['seed'],'Replay is not the exact accepted candidate')
        board(row['board'])
    else:
        require(row['outcome'] in ['EXPECTED_REJECTION','TIMEOUT','WORK_EXHAUSTED','CANCELLED'],'Unexpected runtime failure')
        require('board' not in row and 'replay' not in row,'Rejected seed advertised as playable')
def validate_controlled_canaries(canaries):
    current=[r for r in canaries if r.get('cohort')=='controlled-negative-current' or
        'controlledNegative' in r]
    require([r.get('caseId') for r in current]==CONTROLLED_CANARY_IDS,
        'Missing, duplicated, or reordered current P09 canary cases')
    for row in current:
        require(row['cohort']=='controlled-negative-current','Wrong current P09 canary cohort')
        require('caseId' in row,'Current P09 canary omitted its declared case identity')
        case(row)

    exact,search,replay1,replay2,healthy,hypotheses=current
    require(exact['requestedSeed']=='-7564325972933069162' and
        exact['mode']=='exact' and exact['outcome']=='EXPECTED_REJECTION' and
        exact['cancelAfter']==-1 and len(exact['attempts'])==1,
        'Controlled exact case must reject its first and only physical candidate')
    require(exact['attempts'][0]['ordinal']==0 and exact['attempts'][0]['stage']=='PHYSICAL' and
        exact['attempts'][0]['outcome']=='EXPECTED_REJECTION',
        'Controlled exact case did not preserve typed PHYSICAL rejection')
    require('board' not in exact and 'replay' not in exact,
        'Rejected controlled root was advertised as a playable board')

    control=exact.get('controlledNegative')
    require(type(control) is dict,'Missing controlled P09 provenance')
    require(control.get('kind')=='CONTROLLED_COPY_P09_ENVELOPE_REJECTION' and
        control.get('requestedSeed')==exact['requestedSeed'] and control.get('applied') is True and
        control.get('predicateRejected') is True and control.get('reason')=='BOARD_SIZE',
        'Controlled P09 provenance does not prove the declared real-predicate rejection')
    require(control.get('appliedOrdinal')==0 and control.get('consumedCount')==1 and
        control.get('invalidCopyWidth')==2049 and control.get('invalidCopyChangedGeometry') is True,
        'Controlled P09 mutation was not one-shot at ordinal zero with an over-limit copied outline')
    require(type(control.get('invalidCopyGeometryFingerprint')) is str and
        control['invalidCopyGeometryFingerprint']!=control.get('originalGeometryFingerprintBefore'),
        'Controlled P09 copy fingerprint does not show changed geometry')
    require(control.get('originalBoundsPassed') is True and
        control.get('originalGeometryFingerprintBefore')==control.get('originalGeometryFingerprintAfter') and
        bool(control.get('originalGeometryFingerprintBefore')),
        'Original physical candidate did not pass P09 unchanged before the controlled mutation')
    require(control.get('originalLayoutIdentityUnchanged') is True and
        control.get('malformedCopyNotInstalled') is True and
        control.get('candidatePrivateAtApply') is True and
        control.get('protectedPowerDisconnectedAtApply') is True and
        control.get('scopeActiveAtApply') is True,
        'Controlled copy escaped private staging or mutated the protected source')
    require(control.get('coordinatorCallbackReleased') is True and
        control.get('protectedOwnerRestored') is True and exact['ownerRestored'] is True,
        'Controlled negative did not release its one-shot hook and restore the owner')

    require(search['requestedSeed']==exact['requestedSeed'] and search['mode']=='search' and
        search['outcome']=='PASS' and search['cancelAfter']==-1 and
        len(search['attempts'])==2,'Controlled search did not perform exactly one real canonical retry')
    require(search['attempts'][0]['ordinal']==0 and search['attempts'][0]['stage']=='PHYSICAL' and
        search['attempts'][0]['outcome']=='EXPECTED_REJECTION' and
        search['attempts'][1]['ordinal']==1 and search['attempts'][1]['stage']=='PUBLISH' and
        search['attempts'][1]['outcome']=='PASS',
        'Controlled retry did not preserve typed rejection and ordinary successor publication')
    successor=str(signed(int(search['requestedSeed'])+STRIDE))
    require(successor=='3836388846390129323' and search['board']['seed']==successor,
        'Controlled search did not accept the declared immediate canonical successor')
    require(search.get('controlledNegative')==control,
        'Exact and search controls did not report identical ordinal-zero injection evidence')

    require(replay1['mode']=='exact' and replay2['mode']=='exact' and
        replay1['outcome']=='PASS' and replay2['outcome']=='PASS' and
        replay1['requestedSeed']==successor and replay2['requestedSeed']==successor,
        'Two exact successor replay controls are missing or changed')
    require(replay1.get('replayGroup')==replay2.get('replayGroup')=='p09-controlled-successor-replay' and
        replay1['replay']==replay2['replay']=='tsj-alpha/3/EASY/RB15_CONTROL/'+successor and
        replay1['board']==replay2['board'] and
        replay1['board'].get('geometryFingerprint')==replay2['board'].get('geometryFingerprint'),
        'Two exact replays did not reproduce the same v3 replay and geometry')
    for row in (replay1,replay2,healthy,hypotheses):
        require('controlledNegative' not in row,'Controlled P09 provenance polluted an uninjected canary')

    require(healthy['mode']=='search' and healthy['outcome']=='CANCELLED' and
        healthy['requestedSeed']=='3' and healthy['cancelAfter']==3 and
        healthy['attempts'][-1]['stage']=='HEALTHY',
        'Actual HEALTHY-stage cancellation coverage changed')
    require(hypotheses['mode']=='search' and hypotheses['outcome']=='CANCELLED' and
        hypotheses['requestedSeed']=='-4708384106017907796' and hypotheses['cancelAfter']==15 and
        hypotheses['attempts'][-1]['stage']=='HYPOTHESES',
        'Actual HYPOTHESES-stage cancellation coverage changed')
    for row in (healthy,hypotheses): integer(row['cancellationMs'],0,500)

def validate(native,compiled,canaries):
    for row in native+compiled:
        require(not ({'controlledNegative','caseId','replayGroup'} & set(row)),
            'Controlled canary metadata polluted a natural population row')
    summaries={}
    for cohort,held in [('development',False),('holdout',True)]:
        nr=[r for r in native if r['cohort']==cohort]; cr=[r for r in compiled if r['cohort']==cohort]
        expected=population(held)
        require([r['seed'] for r in nr]==expected and [r['requestedSeed'] for r in cr]==expected,'Dropped/reordered/rounded population cases')
        accepted=[]; structural=[]
        for n,c in zip(nr,cr):
            require(c['mode']=='exact' and c['cancelAfter']==-1,'Population secretly retries or cancels')
            case(c)
            if n['outcome']=='PASS': board(n); structural.append(n)
            if c['outcome']=='PASS':
                require(n['outcome']=='PASS','Electrical admission bypassed the physical bounds')
                for key in ['width','height','design','parts']: require(n[key]==c['board'][key],'JVM/browser discrete geometry mismatch')
                accepted.append(c['board'])
        groups=[]
        for b in accepted:
            group=next((g for g in groups if near(b,g[0])),None)
            if group is None: groups.append([b])
            else: group.append(b)
        bands={(10*b['width']+b['height'])//(2*b['height']) for b in accepted}
        largest=max([len(g) for g in groups] or [0])
        if held:
            require(len(structural)>=24 and len(accepted)>=24,'Less than half the holdout admitted')
            require(len(groups)>=12 and len(bands)>=3,'Insufficient meaningful physical variety')
            require(largest*4<=len(accepted),'Too many near-clone layouts')
        summaries[cohort]={'requested':len(expected),'structuralAccepted':len(structural),'admitted':len(accepted),
            'macroGroups':len(groups),'largestGroup':largest,'aspectBands':sorted(bands),
            'outcomes':{k:sum(r['outcome']==k for r in cr) for k in sorted({r['outcome'] for r in cr})}}
    require(len(native)==72 and len(compiled)==72,'Unexpected hidden population rows')
    validate_controlled_canaries(canaries)
    for row in canaries: case(row)
    retries=[r for r in canaries if r['mode']=='search' and r['outcome']=='PASS']
    require(any(len(r['attempts'])>=2 for r in retries),'Missing actual retry success')
    cancellations=[r for r in canaries if r['outcome']=='CANCELLED']
    require({'HEALTHY','HYPOTHESES'} <= {r['attempts'][-1]['stage'] for r in cancellations},'Missing routing/proof cancellation')
    for row in cancellations: integer(row['cancellationMs'],0,500)
    require(any(r['mode']=='exact' and r['outcome']=='EXPECTED_REJECTION' for r in canaries),'Missing exact physical rejection')
    return summaries

def main(root):
    data=json.loads((root/'corpus.json').read_text(encoding='utf-8-sig'))
    summary=validate(data['native'],data['compiled'],data['canaries'])
    negatives=[]
    accepted=next(i for i,r in enumerate(data['compiled']) if r['outcome']=='PASS')
    for collection in ['native','compiled']:
        bad=copy.deepcopy(data); bad[collection].pop(); negatives.append(bad)
    for field,value in [('requestedSeed','0'),('ownerRestored',False),('status','RUNNING'),
                         ('mode','unknown'),('units',641),('maxUnitMs',5001),('jobMs',90001),
                         ('hypotheses',2),('proofUnits',0),('attempts',[])]:
        bad=copy.deepcopy(data); bad['compiled'][accepted][field]=value; negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['board']['seed']='0'; negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['board']['width']=2049; negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['board']['parts'].pop('K1'); negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['replay']='tsj-alpha/1/EASY/RB15_CONTROL/0'; negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['replay']='tsj-alpha/2/EASY/RB15_CONTROL/'+bad['compiled'][accepted]['board']['seed']; negatives.append(bad)
    for field,value in [('ordinal',1),('outcome','EXPECTED_REJECTION'),('manifest','root-seed=0;')]:
        bad=copy.deepcopy(data); bad['compiled'][accepted]['attempts'][0][field]=value; negatives.append(bad)
    bad=copy.deepcopy(data); bad['canaries']=[]; negatives.append(bad)
    bad=copy.deepcopy(data)
    reference=next(r['board'] for r in bad['compiled'] if r['cohort']=='holdout' and r['outcome']=='PASS')
    shape={k:copy.deepcopy(reference[k]) for k in ['width','height','design','parts']}
    for n,c in zip(bad['native'],bad['compiled']):
        if c['cohort']=='holdout' and c['outcome']=='PASS':
            n.update(copy.deepcopy(shape)); c['board'].update(copy.deepcopy(shape))
    negatives.append(bad)
    bad=copy.deepcopy(data)
    retry=next(r for r in bad['canaries'] if r['mode']=='search' and r['outcome']=='PASS')
    retry['attempts'][0]['outcome']='PROGRAMMING_FAILURE'; negatives.append(bad)
    control_index=next(i for i,r in enumerate(data['canaries']) if r.get('caseId')=='p09-controlled-exact')
    for mutate in [
        lambda r: r.pop('controlledNegative'),
        lambda r: r['controlledNegative'].__setitem__('reason','ROUTE_BUDGET'),
        lambda r: r['controlledNegative'].__setitem__('appliedOrdinal',1),
        lambda r: r['controlledNegative'].__setitem__('applied',False),
        lambda r: r['controlledNegative'].__setitem__('predicateRejected',False),
        lambda r: r['controlledNegative'].__setitem__('invalidCopyWidth',2048),
        lambda r: r['controlledNegative'].__setitem__('invalidCopyChangedGeometry',False),
        lambda r: r['controlledNegative'].__setitem__('originalBoundsPassed',False),
        lambda r: r['controlledNegative'].__setitem__('originalGeometryFingerprintAfter','corrupted'),
        lambda r: r['controlledNegative'].__setitem__('originalLayoutIdentityUnchanged',False),
        lambda r: r['controlledNegative'].__setitem__('malformedCopyNotInstalled',False),
        lambda r: r['controlledNegative'].__setitem__('candidatePrivateAtApply',False),
        lambda r: r['controlledNegative'].__setitem__('protectedPowerDisconnectedAtApply',False),
        lambda r: r['controlledNegative'].__setitem__('scopeActiveAtApply',False),
        lambda r: r['controlledNegative'].__setitem__('consumedCount',2),
        lambda r: r['controlledNegative'].__setitem__('coordinatorCallbackReleased',False),
        lambda r: r['controlledNegative'].__setitem__('protectedOwnerRestored',False),
    ]:
        bad=copy.deepcopy(data); mutate(bad['canaries'][control_index]); negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['controlledNegative']=copy.deepcopy(
        bad['canaries'][control_index]['controlledNegative']); negatives.append(bad)
    bad=copy.deepcopy(data); bad['native'][0]['caseId']='p09-controlled-exact'; negatives.append(bad)
    bad=copy.deepcopy(data); bad['native'][0]['controlledNegative']=copy.deepcopy(
        bad['canaries'][control_index]['controlledNegative']); negatives.append(bad)
    bad=copy.deepcopy(data); bad['compiled'][accepted]['caseId']='p09-controlled-exact'; negatives.append(bad)
    bad=copy.deepcopy(data); bad['canaries'][control_index]['caseId']='renamed-control'; negatives.append(bad)
    bad=copy.deepcopy(data)
    control_index=next(i for i,r in enumerate(bad['canaries']) if r.get('caseId')=='p09-controlled-exact')
    bad['canaries'][control_index],bad['canaries'][control_index+1]=\
        bad['canaries'][control_index+1],bad['canaries'][control_index]
    negatives.append(bad)
    for index,bad in enumerate(negatives):
        rejected=False
        try: validate(bad['native'],bad['compiled'],bad['canaries'])
        except (ValueError,KeyError,TypeError,StopIteration): rejected=True
        require(rejected,'Reader admitted malformed control '+str(index))
    result={'status':'PASS','readerNegatives':len(negatives),'cohorts':summary,
        'controlledNegativeCases':len(CONTROLLED_CANARY_IDS),
        'limits':'Frozen qualification sample plus a separately disclosed controlled-copy P09 negative; no natural rejection distribution claim or universal success guarantee.'}
    (root/'reader-result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('PASS: Quick Play gate population/geometry/repair/retry reader; malformed controls='+str(len(negatives)))

if __name__=='__main__': main(Path(sys.argv[1]))
