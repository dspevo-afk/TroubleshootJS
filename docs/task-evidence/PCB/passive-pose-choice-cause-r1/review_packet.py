"""Post-terminal root packet review; frozen readers own measurement semantics."""
import hashlib
import json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
POLICY=HERE.parent/'passive-pose-policy-r1'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda n:json.loads((HERE/n).read_text(encoding='utf-8-sig'))
seal=read('native-inputs.json')
assert seal['copyCount']==1508 and len(seal['overrides'])==2 and len(seal['additions'])==4
for name,digest in seal['gateOwners'].items():
    assert sha(HERE/name)==digest,'Frozen gate changed: '+name
for name,digest in dict(seal['overrides'],**seal['additions']).items():
    assert sha(HERE/'source'/name)==digest,'Frozen diagnostic source changed: '+name
assert sha(HERE/'copy-inputs.json')==seal['baseSha256']
assert sha(POLICY/'native-inputs.json')==seal['reusedPolicySealSha256']
assert sha(POLICY/'next-cause-source-review.md')==seal['sourceStudySha256']
peer=json.loads((POLICY/'native-inputs.json').read_text())
for name,digest in peer['gateOwners'].items():assert sha(POLICY/name)==digest
for name,digest in dict(peer['overrides'],**peer['additions']).items():
    assert sha(POLICY/'source'/name)==digest
main_seal=HERE.parent/'silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal)==seal['main1475SealSha256']
main=json.loads(main_seal.read_text())['files']
assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],'Main accepted owner changed'
canaries=read('assessment-canary-results.json')
assert canaries['status']=='PASS' and canaries['caseCount']==25 and canaries['assertions']==33
assert canaries['nativeChoiceOutputsConsumed'] is False
for name,digest in canaries['sha256'].items():
    assert sha(HERE/name)==digest,'Reader canary input changed'
for name in ('unit-validation.json','native-input-audit.json','source-review.json',
             'copy-restoration.json','copy-cleanup.json'):
    assert read(name)['status']=='PASS',name
assert not (HERE/'local-state.json').exists()
attempt=HERE/'attempt1'
archive=json.loads((attempt/'archive-sha256.json').read_text())
assert archive['status']=='PASS'
for name,digest in archive['files'].items():assert sha(attempt/name)==digest
assert json.loads((attempt/'assessment.json').read_text())['status']=='FAIL'
reuse=read('native-evidence-reuse.json')
assert reuse['status']=='PASS' and reuse['unchangedNativeInputs']==1508
assert reuse['nativeSourceChanged'] is False
assert reuse['oldSealSha256']==sha(attempt/'native-inputs.json')
assert reuse['newSealSha256']==sha(HERE/'native-inputs.json')
assert len(reuse['phaseArtifactsSha256'])==12
for name,digest in reuse['phaseArtifactsSha256'].items():
    assert sha(HERE/name)==sha(attempt/name)==digest
for phase in ('unit','routes'):
    text=(HERE/(phase+'.txt')).read_text(encoding='utf-8-sig')
    receipt=(HERE/(phase+'-receipt.txt')).read_text(encoding='utf-8-sig')
    timing=read(phase+'-timing.json')
    assess.check_phase(text,receipt,timing,read('unit-suites.json' if phase=='unit' else 'route-suites.json'))
    cleanup=read(phase+'-process-cleanup.json')
    assert cleanup['status']=='PASS' and cleanup['terminationAttempted'] is False
    assert len(cleanup['drivers'])==2 and all(r['sameCreationStillRunning'] is False for r in cleanup['drivers'])
    assert read(phase+'-log-sanitization.json')['status']=='PASS'
unit=(HERE/'unit.txt').read_text(encoding='utf-8-sig')
sample=assess.check_protocol_sample(unit)
bindings=assess.check_sources(unit)
routes=(HERE/'routes.txt').read_text(encoding='utf-8-sig')
rows=assess.extract(routes,assess.ROUTE_PREFIX)
captures=assess.extract(routes,assess.CAPTURE_PREFIX)
route_checks=assess.check_route_rows(rows)
assert len(route_checks)==len(captures)==4
actual=assess.assess_routes(rows,captures,bindings)
assert read('assessment.json')==actual,'Stored assessment differs from frozen recomputation'
result={'status':'PASS','scope':'Root source/evidence review; local causal conclusions remain those of the frozen assessment',
 'assessmentStatus':actual['status'],'localAttribution':actual['localAttribution'],
 'fourExactRoutedReproductions':route_checks,
 'sourceBindings':len(bindings),'syntheticProtocolSample':sample,
 'diagnosticArchivedSourcesExact':6,'reusedPolicySourcesExact':49,
 'frozenGateOwnersExact':len(seal['gateOwners']),'main1475Exact':True,
 'copyRestoredAndRetired':True,'recordedDriversExited':4,
 'initialReaderFailurePreserved':True,'nativePhaseBytesReusedExact':12,
 'newNativeExecutionsAfterReaderCorrection':0,
 'review':'Actual 21 hook diff regions, read-only helper, serializer, pure fixture, source bindings, paired adapters, readers and canaries reviewed',
 'limits':'Leaf implementation/reader work is not independent runtime certification. No new packing policy, owner/replay/repair, GWT/player, full matrix or all-seed proof.'}
(HERE/'root-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS root packet/source/gates/main/restoration review; assessment='+actual['status'])
