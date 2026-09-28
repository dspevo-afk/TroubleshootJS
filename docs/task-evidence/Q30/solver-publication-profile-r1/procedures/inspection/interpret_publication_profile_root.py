from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
seq=json.loads((s/'publication-profile-r1-sequence-result.json').read_bytes())
assert seq['status']=='FOCUSED_PUBLICATION_PROFILE_BOTH_COMPARISONS_PASS_PENDING_INTERPRETATION'
assert len(seq['rows'])==3 and len(seq['pairs'])==2 and seq['notRun']==[]
reportpath=next((s/'publication-profile-r1-02-profile-10014/cases').glob('*.report.json'))
report=json.loads(reportpath.read_bytes())
phases={}
for name in ['cold','warm']:
    p=report[name]['publicationProfile'];assert p['valid'] and p['frozen']
    assert all(p[k]==0 for k in ['timerErrors','lateErrors','unknownPathErrors','openSamples'])
    rows=[]
    for x in p['phases']:
        row={k:x[k] for k in ['name','eligible','selected','completed','failed','elapsedMs','zeroDurationCount','rawDurationCount','rawDurationsDropped']}
        row['zeroFraction']=x['zeroDurationCount']/x['selected'] if x['selected'] else None
        row['roughExpandedMs']=x['elapsedMs']*x['eligible']/x['selected'] if x['selected']>=64 else None
        row['expansionLimit']='periodic sample mean times eligible count; coarse inference with timer quantization, not exact total; fewer than64 selected remains unestimated'
        rows.append(row)
    phases[name]={'eligible':p['eligible'],'selected':p['selected'],'sampledElapsedMs':p['sampledElapsedMs'],'phases':rows}
controls=[seq['rows'][i]['cold']['elapsedMs'] for i in [0,2]]
profile=seq['rows'][1]['cold']['elapsedMs']
out=s/'publication-profile-r1-root-interpretation.json';assert not out.exists()
d={'status':'PASS_COARSE_PRIVATE_ATTRIBUTION_ONLY','Q30':'BLOCKED_NOT_ACCEPTED',
 'sequenceSha256':sha((s/'publication-profile-r1-sequence-result.json').read_bytes()),'rawReportSha256':sha(reportpath.read_bytes()),
 'coldControlMs':controls,'coldProfileMs':profile,'withinControlRangeMs':max(controls)-min(controls),
 'profileInsideControlRange':min(controls)<=profile<=max(controls),'profileMinusControlsMs':[profile-x for x in controls],
 'profiles':phases,'quantization':'Retained positive intervals cluster around0.1ms. Most selected calls are zero-duration. Sparse systematic samples are not a randomized independent sample; no confidence interval or exact overhead claim.',
 'scope':'applySolvedRightSide includes validation/current assignment/voltage callbacks. Accepted bookkeeping excludes earlier t/timeStepAccum work and pauses around immediate wire refresh. Delayed-owned and deferred-UI wire refresh separate. Run freezes before verifier retirement cleanup.',
 'unknown':'Ten cold rollback calls unselected; cost unknown, not zero. Immediate wire path not exercised. One cold deferred-UI sample insufficient for expansion.',
 'decision':'Publication and owned-step wire refresh exceed bookkeeping in this coarse sample. Preserve callback semantics; investigate repeated typed lookup costs conditionally. LU remains largest supported contributor from separate CPU/factor evidence. No new optimization validated by this profile.',
 'acceptance':'All three cold rows exceed90000ms. No full final matrix or normal acceptance. No production budgets/proofs/replay/cache/cancellation changes.'}
out.write_bytes((json.dumps(d,indent=2)+'\n').encode())
print(json.dumps({'status':d['status'],'sha256':sha(out.read_bytes()),'controls':controls,'profile':profile,
 'coldRoughExpandedMs':{x['name']:x['roughExpandedMs'] for x in phases['cold']['phases']}}))
