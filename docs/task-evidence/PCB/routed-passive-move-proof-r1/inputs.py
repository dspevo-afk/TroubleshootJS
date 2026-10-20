"""Freeze/audit the one-private-candidate COPY, reviewed gates and preserved setup failure."""
import hashlib
import json
import stat
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
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
    'PcbRoutedPassiveLegCensusContractTest','PcbRoutedPassiveMoveProofContractTest',
    'PcbRoutedPassiveMoveProofTrialContractTest')}

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
for name, digest in protocol['publishedInputs'].items():
    assert sha(ROOT/name) == digest, name
prior = read(OLD/'native-inputs.json')
for name, digest in prior['gateOwners'].items():
    assert sha(OLD/name) == digest, name
for name, digest in dict(prior['overrides'],**prior['additions']).items():
    assert sha(OLD/'source'/name) == digest, name
reader = protocol['priorCensusReader']
assert sha(ROOT/reader['path']) == reader['sha256']
for name, digest in protocol['suiteManifestSha256'].items():
    assert sha(HERE/name) == digest, name
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['canaryCount'] > 0
assert canaries['canaryCount'] == ack['syntheticReaderCases']
assert canaries['fixtureCounts'] == protocol['unitFixtureCounts']
consumed = canaries['consumedSha256']
assert set(consumed) == {'assess.py','check_assessment.py','protocol.json',
    'priorAssess.py','publishedInputs','suiteManifests'}
for name in ('assess.py','check_assessment.py','protocol.json'):
    assert sha(HERE/name) == consumed[name], name
assert consumed['priorAssess.py'] == reader['sha256']
assert consumed['publishedInputs'] == protocol['publishedInputs']
assert consumed['suiteManifests'] == protocol['suiteManifestSha256']
transport = read(HERE/'suite-transport-canaries.json')
assert transport['status'] == 'PASS' and transport['caseCount'] == 12
for name, digest in transport['consumedSha256'].items():
    assert sha(HERE/name) == digest, name
failed = HERE/'preparation-attempt-1'
failure_files = {p.relative_to(HERE).as_posix():sha(p) for p in failed.rglob('*') if p.is_file()}
assert len(failure_files) == 6
failure = read(failed/'failure.json')
assert failure['status'] == 'FAIL' and failure['nativeExecution'] == 'NOT_RUN'
for name, digest in dict(failure['overrides'],**failure['additions']).items():
    assert sha(failed/'source'/name) == digest
assert sha(failed/'bootstrap.py') == failure['bootstrapSha256']
native_failed = HERE/'native-attempt-1'
native_failure = read(native_failed/'failure.json')
assert native_failure['status'] == 'FAIL' and native_failure['nativeExitCode'] == 2
assert native_failure['physicalMoveExecution'] == 'NOT_RUN'
assert len(native_failure['files']) == 42
for name, digest in native_failure['files'].items():
    assert sha(native_failed/name) == digest, name
native_failure_files = {p.relative_to(HERE).as_posix():sha(p)
    for p in native_failed.rglob('*') if p.is_file()}
assert len(native_failure_files) == 43
runtime_failed = HERE/'native-attempt-2'
runtime_failure = read(runtime_failed/'failure.json')
assert runtime_failure['status'] == 'FAIL' and runtime_failure['nativeExitCode'] == 2
assert runtime_failure['unitStatus'] == 'PASS' and runtime_failure['physicalValidators'] == 'NOT_REACHED'
assert len(runtime_failure['files']) == 49
for name, digest in runtime_failure['files'].items():
    assert sha(runtime_failed/name) == digest, name
runtime_failure_files = {p.relative_to(HERE).as_posix():sha(p)
    for p in runtime_failed.rglob('*') if p.is_file()}
assert len(runtime_failure_files) == 50
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
    assert not raw.endswith(b'\n\n') and not raw.endswith(b'\r\n\r\n'), name
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
    'preparationFailureArtifacts':failure_files,
    'nativeFailureArtifacts':native_failure_files,
    'nativeRuntimeFailureArtifacts':runtime_failure_files}
if mode == 'freeze':
    (HERE/'native-inputs.json').write_text(json.dumps(record,indent=2)+'\n')
    print('FROZEN private-proof COPY1470 /3 overrides /3 additions /29 gates; main1475 exact')
else:
    assert read(HERE/'native-inputs.json') == record, 'Frozen private-proof inputs changed'
    (HERE/'native-input-audit.json').write_text(json.dumps({'status':'PASS',
        'main1475Exact':True,'copyCount':1470,'sourceDeltas':6,'gateOwners':29,
        'publishedCensusInputsExact':True,'preparationFailurePreserved':True},indent=2)+'\n')
    print('PASS frozen private-proof COPY/source/gates/prior census/failure exact; main1475 exact')
