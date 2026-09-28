from pathlib import Path
import hashlib,json,sys
s=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
load=lambda p:json.loads((s/p).read_bytes())
plan=load('live-lu-support-r1-measurement-plan.json')
binding=load('live-lu-support-r1-execution-binding.json')
sequence=load('live-lu-support-r1-sequence-result.json')
assert sequence['status']=='STOPPED_INCONCLUSIVE_REPEAT_RULE'
assert len(sequence['rows'])==4 and len(sequence['pairs'])==2 and len(sequence['notRun'])==4
assert sha((s/'run_live_lu_support_r1_sequence_root.py').read_bytes())==binding['driverSha256']
for e in binding['files']:assert sha((s/e['path']).read_bytes())==e['sha256'],e['path']
rows=[]
for i,row in enumerate(sequence['rows']):
    label=row['label'];assert label==plan['sequence'][i]['label']
    host=load(label+'/result.json');assert host['outcome']=='PASS'
    assert host['cleanup']['status']=='PASS' and not host['cleanup']['errors'] and not host['cleanup']['ownedSurvivors']
    assert host['inputAudit']['status']=='PASS'
    arm=plan['sequence'][i]['arm'];runtime=plan['runtime'][arm]
    assert host['inputAudit']['beforeSha256']==host['inputAudit']['afterSha256']==runtime['sha256']
    case=host['cases'][0];raw=(s/label/case['reportFile']).read_bytes()
    assert sha(raw)==case['reportSha256']
    report=json.loads(raw)
    assert row['cold']['totalWork']==490 and row['cold']['hypothesisWork']==390
    assert row['warm']['totalWork']==101 and row['warm']['hypothesisWork']==1
    assert row['cold']['cleanupComplete'] and row['warm']['cleanupComplete']
    assert not row['profile'] and not row['cold']['profileKeys'] and not row['warm']['profileKeys']
    assert abs(row['wallMinusMonotonicSeconds'])<.001
    rows.append({'label':label,'arm':arm,'seed':'10014','packages':40,'reportSha256':sha(raw),
      'cold':row['cold'],'warm':row['warm'],'hostMonotonicSeconds':row['hostMonotonicSeconds'],
      'cleanupSeconds':host['cleanup']['seconds'],'inputAudit':'PASS','cleanup':'PASS'})
for pair in sequence['pairs']:
    parity=json.loads((s/pair['log']).read_text(encoding='utf-8'))
    assert parity['status']=='PASS' and parity['requestExact'] and parity['reportExactOutsideDeclaredTimings']
    assert all(x['strictReader']['selfTestCanaryCounts']['corruptionCanaries']==43 for x in parity['runs'])
assert sequence['pairs'][0]['coldGainMs']==5710 and sequence['pairs'][1]['coldGainMs']==-2460
assert sequence['repeatColdRangeMs']==10682
result={'schema':1,'status':'INCONCLUSIVE_NOT_INTEGRATED','q30':'BLOCKED_NOT_ACCEPTED',
 'headAtTrial':'f94d523232677760c2af24cde886f4a92a09833d','acceptedProductionSource':'ffe9132',
 'planSha256':sha((s/'live-lu-support-r1-measurement-plan.json').read_bytes()),
 'bindingSha256':sha((s/'live-lu-support-r1-execution-binding.json').read_bytes()),
 'sequenceSha256':sha((s/'live-lu-support-r1-sequence-result.json').read_bytes()),
 'sourceAuditSha256':sha((s/'live-lu-support-r1-preparation/source-audit.json').read_bytes()),
 'rows':rows,'pairs':sequence['pairs'],'notRun':sequence['notRun'],
 'repeatColdRangeMs':10682,'candidateColdRangeMs':2512,
 'interpretation':'The first pair improves cold/proof by 5710/5864 ms but the reverse pair regresses by 2460/2197 ms. Control spread 10682 ms exceeds the first gain and the repeat rule fails. No reliable total/proof improvement is attributable to this prototype. Its tracking cost versus saved scans remains unresolved. No smaller-scale claims or acceptance follows.',
 'coldIsolation':'Every row uses a fresh owned browser and the complete cold proof: 490 work/390 hypothesis units. Warm private-cache rows remain separate at 101 work/1 hypothesis unit. This excludes warm-proof reuse as an explanation for the cold comparison; it does not control OS caches, scheduling, thermal/power state or external load.',
 'clockBoundary':'Application cold/proof/routing fields retain legacy wall-clock semantics. Whole-host operation is measured with time.monotonic and cleanup separately. Host wall-minus-monotonic differences are under 0.001 s; no clock-jump evidence, but no OS/thermal/noise-cause diagnosis. The separately committed stage profile supplies the monotonic partition.',
 'reviewBoundary':'Focused native9, actual JDK8/GWT5, compiled A07/disabled-normal, strict negatives and two full proof/replay parity pairs PASS. Current full-kernel factor/pivot/solve bits remain exact in new numerical fixtures. Historical dense zero-sign difference independently witnessed in unchanged accepted source; only that new-test comparison was corrected. No existing oracle changed. n512 boundary not directly tested; no universal parity claim.',
 'preservedRequirements':['90000 cumulative normal ms','640 shared work','5000 active operation ms','private 90-300 second measurement isolation','family owners and generic request/coordinator','signed-long replay/canonical order','full settling/hypotheses/physical admission/service/repair/retest','cache/cancellation/cleanup'],
 'resources':'All four timing hosts and compiled canary host cleaned up owned browser/server resources. No heavy task process active. Isolated source/build/evidence fixtures retained. No source integration or push.',
 'next':'Retain the source-valid but timing-inconclusive prototype as an experiment; locate its actual incremental tracking/scan costs before any further implementation. Do not change budgets, shorten proofs or run final acceptance while timing/scale hard gates fail.'}
out=s/'live-lu-support-r1-root-interpretation.json';assert not out.exists();out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':result['status'],'sha256':sha(out.read_bytes()),'rows':len(rows),'pairs':len(sequence['pairs']),'notRun':len(sequence['notRun'])}))
