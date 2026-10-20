"""Freeze/audit the complete private descent source and independent gate inputs."""
import hashlib,json,stat,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-slide-compaction-probe-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
COPY=Path(read(HERE/'local-state.json')['scratch']).resolve()
main_seal=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal)=='9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
main=read(main_seal)['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256'],row['path']
base={r['path']:r['sha256'] for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base)==1467
prior=read(OLD/'native-inputs.json')
assert sha(OLD/'native-inputs.json')=='a4433f982b2453c976e414a7bf873f0a95ebbf18d218e154adbcb125f66fcb64'
for group,folder in (('gateOwners',OLD),('overrides',OLD/'source'),('additions',OLD/'source')):
    for n,d in prior[group].items():assert sha(folder/n)==d,n
for group in ('preparationFailureArtifacts','protocolRevisionArtifacts','preFreezeReviewArtifacts','nativeFailureArtifacts','nativeRuntimeFailureArtifacts'):
    for n,d in prior[group].items():assert sha(OLD/n)==d,n
protocol=read(HERE/'protocol.json')
for n,d in dict(protocol['publishedInputs'],**protocol['classificationOwnerSha256']).items():assert sha(ROOT/n)==d,n
overrides=set(prior['overrides']);assert len(overrides)==4
additions=set(prior['additions'])|{'tests/contracts/PcbRoutedSlideDescentContractTest.java','tests/contracts/PcbRoutedSlideDescentTrialContractTest.java'}
assert len(additions)==6 and not additions&set(base)
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
assert len(actual)==1473 and set(actual)==set(base)|additions
assert {n for n in base if actual[n]!=base[n]}==overrides
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py')],check=True)
ack=read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256']==sha(HERE/'source-review.diff')
assert ack['protocolSha256']==sha(HERE/'protocol.json')
for n,d in protocol['suiteManifestSha256'].items():assert sha(HERE/n)==d,n
canaries=read(HERE/'assessment-canary-results.json')
assert canaries['status']=='PASS' and canaries['canaryCount']>=60
assert canaries['canaryCount']==ack['syntheticReaderCases']
assert canaries['fixtureCounts']==protocol['unitFixtureCounts']
transport=read(HERE/'suite-transport-canaries.json');assert transport['status']=='PASS' and transport['caseCount']==12
for n,d in transport['consumedSha256'].items():assert sha(HERE/n)==d,n
owners=('scope.md','prepare.py','preparation-audit.json','declare_protocol.py','protocol.json',
    'unit-suites.json','routes-suites.json','bootstrap_host.py','host-reuse.json',
    'inputs.py','review_source.py','adapter-reversal.json','source-review.diff','source-review.json',
    'review-ack.json','source-audit.md','run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py',
    'suite_transport.ps1','check_suite_transport.ps1','suite-transport-canaries.json',
    'assess.py','check_assessment.py','assessment-canary-results.json','reader-notes.md','worker-notes.md')
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
record={'baseMainSealSha256':sha(main_seal),'baseCount':1467,'copyCount':1473,
    'overrides':{n:actual[n] for n in sorted(overrides)},'additions':{n:actual[n] for n in sorted(additions)},
    'gateOwners':{n:sha(HERE/n) for n in owners},'publishedProbeSealSha256':sha(OLD/'native-inputs.json')}
if mode=='freeze':
    (HERE/'native-inputs.json').write_text(json.dumps(record,indent=2)+'\n')
    print('FROZEN generic descent COPY1473/4overrides6adds',len(owners),'gates; main1475 exact')
else:
    assert read(HERE/'native-inputs.json')==record,'Frozen descent source/gates changed'
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS','main1475Exact':True,
        'copyCount':1473,'sourceDeltas':10,'gateOwners':len(owners),'publishedInputsExact':True},indent=2)+'\n')
    print('PASS frozen descent source/gates/prior exact; main1475 exact')
