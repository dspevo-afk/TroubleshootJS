"""Recheck the retired bounded descent and separate integrity from measured gain."""
import hashlib,json,subprocess,sys
from pathlib import Path
import analyze,assess,check_assessment
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-slide-compaction-probe-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
seal=read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount'])==(1467,1473)
assert len(seal['overrides'])==4 and len(seal['additions'])==6 and len(seal['gateOwners'])==28
for group,folder in (('gateOwners',HERE),('overrides',HERE/'source'),('additions',HERE/'source')):
    for n,d in seal[group].items():assert sha(folder/n)==d,n
main_seal=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal)==seal['baseMainSealSha256'];main=read(main_seal)['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],row['path']
assert sha(OLD/'native-inputs.json')==seal['publishedProbeSealSha256']
prior_seal=read(OLD/'native-inputs.json')
for group,folder in (('gateOwners',OLD),('overrides',OLD/'source'),('additions',OLD/'source')):
    for n,d in prior_seal[group].items():assert sha(folder/n)==d,n
for group in ('preparationFailureArtifacts','protocolRevisionArtifacts','preFreezeReviewArtifacts','nativeFailureArtifacts','nativeRuntimeFailureArtifacts'):
    for n,d in prior_seal[group].items():assert sha(OLD/n)==d,n
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py'),'--archive'],check=True)
for n in ('source-review.diff','source-review.json'):assert sha(HERE/n)==seal['gateOwners'][n],n
contract=assess._load_contract();prior=assess._load_prior_contract(contract)
us,ui,um=assess._read_phase_files('unit',contract,prior)
unit=assess._unit_validation(contract,us,ui,um)
assert assess._typed_equal(unit,read(HERE/'unit-validation.json'))
unit_sha=assess._validate_unit_prerequisite((HERE/'unit-validation.json').read_bytes(),unit)
rs,ri,rm=assess._read_phase_files('routes',contract,prior)
assessment=assess._routes_assessment(contract,rs,ri,rm,unit_sha)
assert assess._typed_equal(assessment,read(HERE/'assessment.json'))
analysis=analyze.result();assert analysis==read(HERE/'descent-analysis.json')
canaries=read(HERE/'assessment-canary-results.json');assert check_assessment._run_canaries()==canaries
ack=read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256']==sha(HERE/'source-review.diff')
assert ack['protocolSha256']==sha(HERE/'protocol.json')
assert canaries['canaryCount']==ack['syntheticReaderCases']>=60
assert canaries['fixtureCounts']==contract['protocol']['unitFixtureCounts']
transport=read(HERE/'suite-transport-canaries.json');assert transport['caseCount']==12
for n,d in transport['consumedSha256'].items():assert sha(HERE/n)==d,n
drivers=0
for phase in ('unit','routes'):
    launch=read(HERE/(phase+'-process-launch.json'));cleanup=read(HERE/(phase+'-process-cleanup.json'))
    assert cleanup['status']=='PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers'])==len(cleanup['drivers'])==2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k]==after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome']=='EXITED' and after['sameCreationStillRunning'] is False
        drivers+=1
assert drivers==4
assert read(HERE/'copy-restoration.json')=={'status':'PASS','fromCount':1473,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json')=={'status':'PASS','verifiedExactFilesRemoved':1467,'privateStateRemoved':True,
    'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
roots=analysis['roots']
result={'integrityReviewStatus':'PASS','nativeDescentStatus':'PASS','measurementDisposition':analysis['measurementDisposition'],
    'baseCommit':contract['protocol']['baseCommit'],'main1475Exact':True,'sourceDeltas':10,'frozenGates':28,
    'acceptedAndPublishedOwnerErasureExact':True,'syntheticReaderCases':canaries['canaryCount'],'actualTransportCases':12,
    'unitSuites':4,'unitFixtureCounts':canaries['fixtureCounts'],'routeSuites':1,'selectedRowsExact42Fields':2,
    'roots':[{'seed':r['seed'],'acceptedSteps':r['aggregate']['acceptedSteps'],'evaluatedMoves':r['aggregate']['evaluatedMoves'],
        'areaReduction':r['areaReduction'],'termination':r['termination']} for r in roots],
    'recordedDriverBirthsExited':drivers,'copyRestored1467AndRetired':True,'productionPolicyChange':'NONE',
    'GwtPlayerSolverFullMatrixUnseen':'NOT_RUN'}
p=HERE/'root-review.json'
if p.exists():assert read(p)==result,'Prior review differs; preserve it'
else:p.write_text(json.dumps(result,indent=2)+'\n')
print('PASS28gates/10deltas/boundedchain/originalselected/ownedcleanup;',result['measurementDisposition'])
