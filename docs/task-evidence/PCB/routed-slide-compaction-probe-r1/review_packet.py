"""Recheck the retired selected-frame probe; keep measured fitness separate."""
import hashlib,json,subprocess,sys
from pathlib import Path
import analyze,assess,check_assessment
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-passive-feasibility-census-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
seal=read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount'])==(1467,1471)
assert len(seal['overrides'])==len(seal['additions'])==4 and len(seal['gateOwners'])==31
for group,folder in (('gateOwners',HERE),('overrides',HERE/'source'),('additions',HERE/'source'),
    ('preparationFailureArtifacts',HERE),('protocolRevisionArtifacts',HERE),('preFreezeReviewArtifacts',HERE),('nativeFailureArtifacts',HERE),('nativeRuntimeFailureArtifacts',HERE)):
    for n,d in seal[group].items():assert sha(folder/n)==d,n
main_seal=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal)==seal['baseMainSealSha256'];main=read(main_seal)['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],row['path']
assert sha(OLD/'native-inputs.json')==seal['publishedFeasibilitySealSha256']
prior=read(OLD/'native-inputs.json')
for group,folder in (('gateOwners',OLD),('overrides',OLD/'source'),('additions',OLD/'source')):
    for n,d in prior[group].items():assert sha(folder/n)==d,n
for group in ('preparationFailureArtifacts','protocolRevisionArtifacts','preFreezeReviewArtifacts','nativeFailureArtifacts'):
    for n,d in prior[group].items():assert sha(OLD/n)==d,n
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py'),'--archive'],check=True)
for n in ('source-review.diff','source-review.json'):assert sha(HERE/n)==seal['gateOwners'][n],n
contract=assess._load_contract();prior_contract=assess._load_prior_contract(contract)
us,ui,um=assess._read_phase_files('unit',contract,prior_contract)
unit=assess._unit_validation(contract,us,ui,um)
assert assess._typed_equal(unit,read(HERE/'unit-validation.json'))
u_sha=assess._validate_unit_prerequisite((HERE/'unit-validation.json').read_bytes(),unit)
rs,ri,rm=assess._read_phase_files('routes',contract,prior_contract)
assessment=assess._routes_assessment(contract,rs,ri,rm,u_sha)
assert assess._typed_equal(assessment,read(HERE/'assessment.json'))
analysis=analyze.result();assert analysis==read(HERE/'compaction-analysis.json')
assert analysis['independentMoves']==31
canaries=read(HERE/'assessment-canary-results.json')
assert check_assessment._run_canaries()==canaries
ack=read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256']==sha(HERE/'source-review.diff')
assert ack['protocolSha256']==sha(HERE/'protocol.json')
assert canaries['canaryCount']==ack['syntheticReaderCases']>=40
assert canaries['fixtureCounts']==contract['protocol']['unitFixtureCounts']
transport=read(HERE/'suite-transport-canaries.json');assert transport['caseCount']==12
for n,d in transport['consumedSha256'].items():assert sha(HERE/n)==d,n
drivers=0
for folder,phase in ((HERE/'native-attempt-1','unit'),(HERE/'native-attempt-2','unit'),
    (HERE/'native-attempt-2','routes'),(HERE,'unit'),(HERE,'routes')):
    cleanup=read(folder/(phase+'-process-cleanup.json'));launch=read(folder/(phase+'-process-launch.json'))
    assert cleanup['status']=='PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers'])==len(cleanup['drivers'])==2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k]==after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome']=='EXITED' and after['sameCreationStillRunning'] is False
        drivers+=1
assert drivers==10
for failure_dir,phase,count,receipt_key in (
    (HERE/'native-attempt-1','unit',46,'qualifiedUnitReceiptPresent'),
    (HERE/'native-attempt-2','routes',53,'qualifiedRoutesReceiptPresent')):
    failure=read(failure_dir/'failure.json')
    assert failure['status']=='FAIL' and failure['nativeExitCode']==2 and len(failure['files'])==count
    assert failure[receipt_key] is False
    for n,d in failure['files'].items():assert sha(failure_dir/n)==d,n
    try:
        assess.check_phase((failure_dir/(phase+'.txt')).read_text(encoding='utf-8-sig'),'',
            read(failure_dir/(phase+'-timing.json')),read(failure_dir/(phase+'-suites.json')),phase)
    except assess.ReaderError:
        pass
    else:
        raise AssertionError('Reader accepted a preserved native failure: '+phase)
assert read(HERE/'copy-restoration.json')=={'status':'PASS','fromCount':1471,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json')=={'status':'PASS','verifiedExactFilesRemoved':1467,
 'privateStateRemoved':True,'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result={'integrityReviewStatus':'PASS','nativeProbeStatus':'PASS',
 'measurementDisposition':analysis['measurementDisposition'],'baseCommit':contract['protocol']['baseCommit'],
 'main1475Exact':True,'sourceDeltas':8,'frozenGates':31,'acceptedAndPriorOwnerErasureExact':True,
 'syntheticReaderCases':canaries['canaryCount'],'actualTransportCases':12,'unitSuites':3,
 'unitFixtureCounts':canaries['fixtureCounts'],'routeSuites':1,'selectedRowsExact42Fields':2,
 'independentMoves':31,'physicallyValidated':analysis['physicallyValidated'],
 'physicallyRejected':analysis['physicallyRejected'],'matchedSourceControls':2,
 'areaBelowSourceAndControl':analysis['areaBelowSourceAndControl'],'strictImprovements':analysis['strictImprovements'],
 'preservedNativeUnitFailures':1,'preservedNativeRouteFailures':1,'nativeFailureReaderRejections':2,
 'recordedDriverBirthsExited':drivers,'copyRestored1467AndRetired':True,
 'productionPolicyFitnessChange':'NONE','winnerSelected':False,'GwtPlayerSolverFullMatrixUnseen':'NOT_RUN'}
p=HERE/'root-review.json'
if p.exists():assert read(p)==result,'Prior review differs; preserve it'
else:p.write_text(json.dumps(result,indent=2)+'\n')
print('PASS31gates/8deltas/31moves/matchedcontrols/ownedcleanup; measured',result['measurementDisposition'])
