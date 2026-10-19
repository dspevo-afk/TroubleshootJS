"""Post-run integrity review; observation is distinct from a compaction change."""
import hashlib
import json
from collections import Counter
from pathlib import Path
import assess

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
seal=read(HERE/'native-inputs.json')
assert seal['baseCount']==1467 and seal['copyCount']==1475
assert len(seal['overrides'])==3 and len(seal['additions'])==8 and len(seal['gateOwners'])==24
for name,digest in seal['gateOwners'].items():assert sha(HERE/name)==digest,name
for name,digest in dict(seal['overrides'],**seal['additions']).items():assert sha(HERE/'source'/name)==digest,name
source=read(HERE/'source-review.json');ack=read(HERE/'review-ack.json')
assert source['status']=='PASS' and ack['rootReviewed'] is True
assert source['diffSha256']==ack['diffSha256']==sha(HERE/'source-review.diff')
canary=read(HERE/'assessment-canary-results.json')
assert canary['status']=='PASS' and canary['caseCount']==49 and canary['nativeRuntimeEvidence']=='NOT_RUN'
assert canary['readerSha256']==sha(HERE/'assess.py')
host=read(HERE/'suite-transport-canaries.json')
assert host['status']=='PASS' and host['caseCount']==12 and host['actualManifests']==2
for name,digest in host['consumedSha256'].items():assert sha(HERE/name)==digest
failed=[];births=0
for folder,count,gate_count in [('attempt1',40,21),('attempt2',43,24)]:
    base=HERE/folder;manifest=read(base/'archive-sha256.json')
    assert manifest['status']=='PASS' and len(manifest['files'])==count
    for name,digest in manifest['files'].items():assert sha(base/name)==digest,(folder,name)
    prior=read(base/'native-inputs.json');assert len(prior['gateOwners'])==gate_count
    for name,digest in prior['gateOwners'].items():assert sha(base/name)==digest
    for name,digest in dict(prior['overrides'],**prior['additions']).items():assert sha(base/'source'/name)==digest
    timing=read(base/'unit-timing.json');cleanup=read(base/'unit-process-cleanup.json')
    assert timing['status']=='FAIL' and timing['exitCode']==2
    assert cleanup['status']=='PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers'])==2 and all(r['outcome']=='EXITED' for r in cleanup['drivers'])
    assert read(base/'unit-log-sanitization.json')['status']=='PASS'
    assert not (base/'unit-receipt.txt').exists()
    failed.append({'name':folder,'archiveFiles':count,'elapsedSeconds':timing['elapsedSeconds'],
        'status':'FAIL','outcome':manifest['outcome'],'recordedBirthsExited':2})
    births+=2
for phase,suites in [('unit',1),('routes',7)]:
    timing=read(HERE/(phase+'-timing.json'));cleanup=read(HERE/(phase+'-process-cleanup.json'))
    assert timing['status']=='PASS' and timing['exitCode']==0 and timing['suiteCount']==suites
    assert cleanup['status']=='PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers'])==2 and all(r['outcome']=='EXITED' for r in cleanup['drivers'])
    assert read(HERE/(phase+'-log-sanitization.json'))['status']=='PASS'
    births+=2
assert 'assertions=91 fixtures=3 evaluator=compactRouted' in (HERE/'unit.txt').read_text()
protocol,baseline_data,_,_=assess._load_contract()
expected=assess._routes_assessment(protocol,baseline_data)
assert assess._typed_equal(read(HERE/'assessment.json'),expected)
census=expected['census'];assert census['caseRows']==14 and census['axisCalls']==62 and census['eventCount']==139
events=[json.loads(line.split(' ',1)[1]) for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith('PCB_COMPACTOR_CUT_EVENT ')]
ends=[row for row in events if row['event']=='END'];cuts=[row for row in events if row['event']=='CUT']
outcomes=Counter(row['outcome'] for row in ends)
assert outcomes=={'NOCUT':52,'ACCEPTED':10} and len(cuts)==15
assert sum(row['amount'] for row in cuts)==census['proposedRemovedTotal']==5400
assert all(not row['reason'] for row in ends)
assert read(HERE/'copy-restoration.json')=={'status':'PASS','fromCount':1475,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json')['status']=='PASS' and not (HERE/'local-state.json').exists()
main=read(ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json')['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256']
result={'status':'PASS','scope':'Exact current15 observation; no changed-policy or smaller-board acceptance',
    'sourceCopyCount':1475,'sourceDeltas':11,'gateOwners':24,'unitAssertions':91,'actualUnitFixtures':3,
    'unitElapsedSeconds':read(HERE/'unit-timing.json')['elapsedSeconds'],
    'routeElapsedSeconds':read(HERE/'routes-timing.json')['elapsedSeconds'],
    'nativeCases':14,'saved15ExactNonTimeFieldsPerCase':42,'axisCalls':62,'eventRecords':139,
    'outcomes':dict(outcomes),'cuts':15,'proposedRemovedAcrossAlternatives':5400,
    'wholeAxisRejectionObserved':False,'syntheticReaderCases':49,'realPowerShellCanaries':12,
    'preservedFailures':failed,'recordedDriverBirthsExited':births,'copyRestoredAndRetired':True,
    'main1475Exact':True,'productionPolicyChanged':False,
    'limits':'No alternative-cut, new shrinkage, GWT/player/solver/repair/full-matrix/all-seed/manufacturing proof'}
(HERE/'root-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS integrated observation:14 exact15 rows;52 NOCUT/10 ACCEPTED/0 rejected;8 births exited;COPY retired/main exact')
