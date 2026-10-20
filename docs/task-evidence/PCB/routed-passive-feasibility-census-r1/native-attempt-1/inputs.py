"""Seal the independent physical census without accepting any candidate outcome."""
import hashlib
import json
import stat
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
PROOF = HERE.parent/'routed-passive-move-proof-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
COPY = Path(read(HERE/'local-state.json')['scratch']).resolve()
SEAL = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(SEAL) == '9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
main = read(SEAL)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'], row['path']
base = {r['path']:r['sha256'] for r in main
    if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base) == 1467
overrides = set(read(HERE/'bootstrap-audit.json')['overrides'])
assert len(overrides) == 3
additions = {'tests/contracts/'+n+'.java' for n in (
    'PcbRoutedPassiveLegCensusContractTest','PcbRoutedPassiveFeasibilityCensusContractTest',
    'PcbRoutedPassiveFeasibilityTrialContractTest')}

def inventory(folder):
    assert not folder.is_symlink() and folder.resolve().is_relative_to(COPY)
    assert not folder.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    result = {}
    for p in folder.iterdir():
        assert not p.is_symlink() and p.resolve().is_relative_to(COPY)
        assert not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        if p.is_dir():
            result.update(inventory(p))
        else:
            result[p.relative_to(COPY).as_posix()] = sha(p)
    return result

actual = inventory(COPY)
assert len(actual) == 1470 and set(actual) == set(base)|additions
assert {n for n in base if actual[n] != base[n]} == overrides
subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'review_source.py')],check=True)
ack = read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256'] == sha(HERE/'source-review.diff')
protocol = read(HERE/'protocol.json')
for name, digest in dict(protocol['publishedInputs'],**protocol['classificationOwnerSha256']).items():
    assert sha(ROOT/name) == digest, name
for prior_dir in (OLD, PROOF):
    prior = read(prior_dir/'native-inputs.json')
    for name, digest in prior['gateOwners'].items():
        assert sha(prior_dir/name) == digest, name
    for name, digest in dict(prior['overrides'],**prior['additions']).items():
        assert sha(prior_dir/'source'/name) == digest, name
    for group in ('preparationFailureArtifacts','nativeFailureArtifacts','nativeRuntimeFailureArtifacts'):
        for name, digest in prior.get(group,{}).items():
            assert sha(prior_dir/name) == digest, name
reader = protocol['priorCensusReader']
assert sha(ROOT/reader['path']) == reader['sha256']
for name, digest in protocol['suiteManifestSha256'].items():
    assert sha(HERE/name) == digest, name
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['canaryCount'] >= 40
assert canaries['canaryCount'] == ack['syntheticReaderCases']
assert canaries['fixtureCounts'] == protocol['unitFixtureCounts']
consumed = canaries['consumedSha256']
assert set(consumed) == {'assess.py','check_assessment.py','protocol.json',
    'priorAssess.py','publishedInputs','suiteManifests','classificationOwners'}
for name in ('assess.py','check_assessment.py','protocol.json'):
    assert sha(HERE/name) == consumed[name], name
assert consumed['priorAssess.py'] == reader['sha256']
assert consumed['publishedInputs'] == protocol['publishedInputs']
assert consumed['suiteManifests'] == protocol['suiteManifestSha256']
assert consumed['classificationOwners'] == protocol['classificationOwnerSha256']
transport = read(HERE/'suite-transport-canaries.json')
assert transport['status'] == 'PASS' and transport['caseCount'] == 12
for name, digest in transport['consumedSha256'].items():
    assert sha(HERE/name) == digest, name
failed = HERE/'preparation-attempt-1'
failure_files = {p.relative_to(HERE).as_posix():sha(p) for p in failed.rglob('*') if p.is_file()}
assert len(failure_files) == 2
failure = read(failed/'failure.json')
assert failure['status'] == 'FAIL' and failure['nativeExecution'] == 'NOT_RUN'
assert failure['copyChanged'] is False and sha(failed/'declare_protocol.py') == failure['scriptSha256']
revision = HERE/'protocol-revision-1'
revision_files = {p.relative_to(HERE).as_posix():sha(p)
    for p in revision.rglob('*') if p.is_file()}
assert len(revision_files) == 3
revision_record = read(revision/'revision.json')
assert revision_record['status'] == 'REVIEWED_PRE_FREEZE_TIGHTENING'
assert revision_record['nativeExecution'] == 'NOT_RUN'
assert sha(revision/'protocol.json') == revision_record['priorProtocolSha256']
assert sha(revision/'declare_protocol.py') == revision_record['priorDeclarationSha256']
assert 'DRILL_LAND_CLEARANCE' not in protocol['twoLayerExactReasons']
assert 'Outer actual validator invocation' in protocol['stageSemantics']
revision2 = HERE/'protocol-revision-2'
revision2_record = read(revision2/'revision.json')
assert revision2_record['status'] == 'REVIEWED_PRE_FREEZE_SCHEMA_CLARIFICATION'
assert revision2_record['nativeExecution'] == 'NOT_RUN'
assert sha(revision2/'protocol.json') == revision2_record['priorProtocolSha256']
assert sha(revision2/'declare_protocol.py') == revision2_record['priorDeclarationSha256']
revision_files.update({p.relative_to(HERE).as_posix():sha(p)
    for p in revision2.rglob('*') if p.is_file()})
assert len(revision_files) == 6
reviewed_draft = HERE/'pre-freeze-review-1'
reviewed_draft_record = read(reviewed_draft/'review.json')
assert reviewed_draft_record['status'] == 'CHANGES_REQUIRED_PRE_FREEZE'
assert reviewed_draft_record['nativeExecution'] == 'NOT_RUN'
assert len(reviewed_draft_record['files']) == 6
for name,digest in reviewed_draft_record['files'].items():
    assert sha(reviewed_draft/name) == digest,name
reviewed_draft_files = {p.relative_to(HERE).as_posix():sha(p)
    for p in reviewed_draft.rglob('*') if p.is_file()}
assert len(reviewed_draft_files) == 7
reviewed_reader = HERE/'pre-freeze-review-2'
reviewed_reader_record = read(reviewed_reader/'review.json')
assert reviewed_reader_record['status'] == 'CHANGES_REQUIRED_PRE_FREEZE'
assert reviewed_reader_record['nativeExecution'] == 'NOT_RUN'
assert len(reviewed_reader_record['files']) == 4
for name,digest in reviewed_reader_record['files'].items():
    assert sha(reviewed_reader/name) == digest,name
reviewed_draft_files.update({p.relative_to(HERE).as_posix():sha(p)
    for p in reviewed_reader.rglob('*') if p.is_file()})
assert len(reviewed_draft_files) == 12
assert protocol['summaryStatuses'] == {'5':'NO_TARGET','2':'CENSUS_COMPLETE'}
assert protocol['rowDispositions'] == ['PHYSICAL_REJECTED','PHYSICAL_VALIDATED']
assert protocol['rowStages'] == ['PLACEMENT','GEOMETRY','TWO_LAYER','COMPLETE']
assert protocol['validatedRowReason'] is None
owners = ('source-audit.md','construction-audit.md','reuse-audit.json',
    'bootstrap-audit.json','host-reuse.json','prepare.py','bootstrap.py','bootstrap_host.py',
    'declare_protocol.py','protocol.json','unit-suites.json','routes-suites.json','inputs.py',
    'review_source.py','source-review.diff','source-review.json','review-ack.json',
    'run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py',
    'assess.py','check_assessment.py','assessment-canary-results.json','reader-notes.md',
    'worker-notes.md','suite_transport.ps1','check_suite_transport.ps1','suite-transport-canaries.json')
assert len(owners) == 29 and all((HERE/n).is_file() for n in owners)
for name in owners:
    raw = (HERE/name).read_bytes()
    assert not raw.endswith((b'\n\n',b'\r\n\r\n')), name
mode = sys.argv[1]
assert mode in ('freeze','audit')
archive = HERE/'source'
if mode == 'freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for name in sorted(overrides|additions):
        p = archive/name
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_bytes((COPY/name).read_bytes())
for name in overrides|additions:
    assert sha(archive/name) == actual[name], name
record = {'baseMainSealSha256':sha(SEAL),'baseCount':1467,'copyCount':1470,
    'overrides':{n:actual[n] for n in sorted(overrides)},
    'additions':{n:actual[n] for n in sorted(additions)},
    'gateOwners':{n:sha(HERE/n) for n in owners},
    'publishedCensusSealSha256':sha(OLD/'native-inputs.json'),
    'publishedProofSealSha256':sha(PROOF/'native-inputs.json'),
    'preparationFailureArtifacts':failure_files, 'protocolRevisionArtifacts':revision_files,
    'preFreezeReviewArtifacts':reviewed_draft_files}
if mode == 'freeze':
    (HERE/'native-inputs.json').write_text(json.dumps(record,indent=2)+'\n')
    print('FROZEN physical census COPY1470 /3 overrides /3 additions /29 gates; main1475 exact')
else:
    assert read(HERE/'native-inputs.json') == record, 'Frozen census inputs changed'
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS',
        'main1475Exact':True,'copyCount':1470,'sourceDeltas':6,'gateOwners':29,
        'publishedCensusAndProofInputsExact':True,'classificationOwnersExact':7,
        'preparationFailurePreserved':True,'preFreezeCatalogTighteningPreserved':True},indent=2)+'\n')
    print('PASS frozen physical census/source/gates/prior/classification exact; main1475 exact')
