"""Freeze exact copy, bridges, independent readers and prior evidence before runtime."""
import hashlib,json,stat,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-passive-feasibility-census-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
COPY=Path(read(HERE/'local-state.json')['scratch']).resolve()
SEAL=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(SEAL)=='9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
main=read(SEAL)['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],row['path']
base={r['path']:r['sha256'] for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base)==1467
protocol=read(HERE/'protocol.json')
for n,d in dict(protocol['publishedInputs'],**protocol['classificationOwnerSha256']).items():
    assert sha(ROOT/n)==d,n
prior=read(OLD/'native-inputs.json')
for group,folder in (('gateOwners',OLD),('overrides',OLD/'source'),('additions',OLD/'source')):
    for n,d in prior[group].items():assert sha(folder/n)==d,n
for group in ('preparationFailureArtifacts','protocolRevisionArtifacts','preFreezeReviewArtifacts','nativeFailureArtifacts'):
    for n,d in prior[group].items():assert sha(OLD/n)==d,n
overrides=set(read(HERE/'bootstrap-audit.json')['overrides']);assert len(overrides)==4
additions={'tests/contracts/'+n+'.java' for manifest in ('unit-suites.json','routes-suites.json') for n,marker in read(HERE/manifest)}
assert len(additions)==4 and not additions & set(base)
def inventory(folder):
    assert not folder.is_symlink() and folder.resolve().is_relative_to(COPY)
    assert not folder.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    found={}
    for p in folder.iterdir():
        assert not p.is_symlink() and p.resolve().is_relative_to(COPY)
        assert not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        if p.is_dir():found.update(inventory(p))
        else:found[p.relative_to(COPY).as_posix()]=sha(p)
    return found
actual=inventory(COPY)
assert len(actual)==1471 and set(actual)==set(base)|additions
assert {n for n in base if actual[n]!=base[n]}==overrides
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py')],check=True)
ack=read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256']==sha(HERE/'source-review.diff')
assert ack['protocolSha256']==sha(HERE/'protocol.json')
for name,digest in protocol['suiteManifestSha256'].items():assert sha(HERE/name)==digest,name
canaries=read(HERE/'assessment-canary-results.json')
assert canaries['status']=='PASS' and canaries['canaryCount']>=40
assert canaries['canaryCount']==ack['syntheticReaderCases']
assert canaries['fixtureCounts']==protocol['unitFixtureCounts']
transport=read(HERE/'suite-transport-canaries.json')
assert transport['status']=='PASS' and transport['caseCount']==12
for name,digest in transport['consumedSha256'].items():assert sha(HERE/name)==digest,name
failed=HERE/'preparation-attempt-1';failure=read(failed/'failure.json')
assert failure['status']=='FAIL' and failure['copyChanged'] is False and failure['nativeExecution']=='NOT_RUN'
assert sha(failed/'bootstrap.py')==failure['scriptSha256']
failure_files={p.relative_to(HERE).as_posix():sha(p) for p in failed.rglob('*') if p.is_file()}
assert len(failure_files)==2
revision=HERE/'protocol-revision-1';revision_record=read(revision/'revision.json')
assert revision_record['nativeExecution']=='NOT_RUN'
assert sha(revision/'protocol.json')==revision_record['priorProtocolSha256']
assert sha(revision/'declare_protocol.py')==revision_record['priorDeclarationSha256']
revision_files={p.relative_to(HERE).as_posix():sha(p) for p in revision.rglob('*') if p.is_file()}
assert len(revision_files)==3
reviewed=HERE/'pre-freeze-review-1';reviewed_record=read(reviewed/'review.json')
assert reviewed_record['status']=='CHANGES_REQUIRED_PRE_FREEZE' and reviewed_record['nativeExecution']=='NOT_RUN'
for n,d in reviewed_record['files'].items():assert sha(reviewed/n)==d,n
reviewed_files={p.relative_to(HERE).as_posix():sha(p) for p in reviewed.rglob('*') if p.is_file()}
assert len(reviewed_files)==5
source_reviewed=HERE/'pre-freeze-source-review-1';source_reviewed_record=read(source_reviewed/'review.json')
assert source_reviewed_record['status']=='CHANGES_REQUIRED_PRE_FREEZE' and source_reviewed_record['nativeExecution']=='NOT_RUN'
for n,d in source_reviewed_record['files'].items():assert sha(source_reviewed/n)==d,n
reviewed_files.update({p.relative_to(HERE).as_posix():sha(p) for p in source_reviewed.rglob('*') if p.is_file()})
assert len(reviewed_files)==8
native_failure=HERE/'native-attempt-1';native_failure_record=read(native_failure/'failure.json')
assert native_failure_record['status']=='FAIL' and native_failure_record['nativeExitCode']==2
assert native_failure_record['elapsedSeconds']==46.8271318 and native_failure_record['qualifiedUnitReceiptPresent'] is False
assert len(native_failure_record['files'])==46
for n,d in native_failure_record['files'].items():assert sha(native_failure/n)==d,n
native_failure_files={p.relative_to(HERE).as_posix():sha(p) for p in native_failure.rglob('*') if p.is_file()}
assert len(native_failure_files)==47
runtime_failure=HERE/'native-attempt-2';runtime_failure_record=read(runtime_failure/'failure.json')
assert runtime_failure_record['status']=='FAIL' and runtime_failure_record['nativeExitCode']==2
assert runtime_failure_record['elapsedSeconds']==51.5802251 and runtime_failure_record['qualifiedRoutesReceiptPresent'] is False
assert len(runtime_failure_record['files'])==53
for n,d in runtime_failure_record['files'].items():assert sha(runtime_failure/n)==d,n
runtime_failure_files={p.relative_to(HERE).as_posix():sha(p) for p in runtime_failure.rglob('*') if p.is_file()}
assert len(runtime_failure_files)==54
owners=('scope.md','source-audit.md','reuse-audit.json','preparation-reuse.json','bootstrap-audit.json','host-reuse.json',
 'prepare.py','bootstrap.py','bootstrap_whitespace.py','bootstrap_host.py','declare_protocol.py','protocol.json',
 'unit-suites.json','routes-suites.json','inputs.py','review_source.py','source-review.diff','source-review.json','review-ack.json',
 'run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py',
 'assess.py','check_assessment.py','assessment-canary-results.json','reader-notes.md','worker-notes.md',
 'suite_transport.ps1','check_suite_transport.ps1','suite-transport-canaries.json')
assert len(owners)==31
for n in owners:
    assert (HERE/n).is_file(),n
    assert not (HERE/n).read_bytes().endswith((b'\n\n',b'\r\n\r\n')),n
mode=sys.argv[1];assert mode in ('freeze','audit')
archive=HERE/'source'
if mode=='freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for n in sorted(overrides|additions):
        p=archive/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((COPY/n).read_bytes())
for n in overrides|additions:assert sha(archive/n)==actual[n],n
record={'baseMainSealSha256':sha(SEAL),'baseCount':1467,'copyCount':1471,
 'overrides':{n:actual[n] for n in sorted(overrides)},'additions':{n:actual[n] for n in sorted(additions)},
 'gateOwners':{n:sha(HERE/n) for n in owners},'publishedFeasibilitySealSha256':sha(OLD/'native-inputs.json'),
 'preparationFailureArtifacts':failure_files,'protocolRevisionArtifacts':revision_files,'preFreezeReviewArtifacts':reviewed_files,'nativeFailureArtifacts':native_failure_files,'nativeRuntimeFailureArtifacts':runtime_failure_files}
if mode=='freeze':
    (HERE/'native-inputs.json').write_text(json.dumps(record,indent=2)+'\n')
    print('FROZEN selected slide COPY1471 /4overrides4adds /31gates; main1475 exact')
else:
    assert read(HERE/'native-inputs.json')==record,'Frozen selected slide inputs changed'
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS','main1475Exact':True,
       'copyCount':1471,'sourceDeltas':8,'gateOwners':31,'publishedInputsExact':True,
       'classificationOwnersExact':7,'preparationFailurePreserved':True},indent=2)+'\n')
    print('PASS frozen selected slide source/gates/prior exact; main1475 exact')
