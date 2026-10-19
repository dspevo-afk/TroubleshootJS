"""Reconstruct exact archived1510 input in a unique owned OS-temp copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
POLICY = HERE.parent/'passive-pose-policy-r1'
CONTROL = HERE.parent/'passive-sizing-control-r1'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
assert not (HERE/'local-state.json').exists()
main_seal = HERE.parent/'silkscreen-scale-r1/final-inputs.json'
main = read(main_seal)['files']
assert len(main) == 1475
for row in main: assert sha(ROOT/row['path']) == row['sha256']
accepted = [r for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(accepted) == 1467
policy = read(POLICY/'native-inputs.json')
control = read(CONTROL/'native-inputs.json')
assert policy['copyCount'] == 1504 and control['copyCount'] == 1510
assert read(CONTROL/'root-review.json')['nativeControlStatus'] == 'FAIL'
for packet, seal in [(POLICY, policy), (CONTROL, control)]:
    for name, digest in seal['gateOwners'].items(): assert sha(packet/name) == digest
    for name, digest in dict(seal['overrides'], **seal['additions']).items():
        assert sha(packet/'source'/name) == digest
copy = Path(tempfile.mkdtemp(prefix='tsj-passive-source-size-')).resolve()
assert copy.parent == Path(tempfile.gettempdir()).resolve()
assert not copy.is_symlink() and not copy.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
(HERE/'local-state.json').write_text(json.dumps({'scratch': str(copy)}, indent=2)+'\n')
expected = {}
for row in accepted:
    source = ROOT/row['path']
    assert source.resolve().is_relative_to(ROOT) and not source.is_symlink()
    assert not source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    target = copy/row['path']; target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(source.read_bytes()); expected[row['path']] = row['sha256']
for packet, seal in [(POLICY, policy), (CONTROL, control)]:
    for name, digest in dict(seal['overrides'], **seal['additions']).items():
        target = copy/name; target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((packet/'source'/name).read_bytes()); expected[name] = digest
actual = {p.relative_to(copy).as_posix(): sha(p) for p in copy.rglob('*') if p.is_file()}
assert len(actual) == 1510 and actual == expected
for name in ('accepted15-baselines.json', 'raw-baselines.json'):
    (HERE/name).write_bytes((CONTROL/name).read_bytes())
(HERE/'copy-inputs.json').write_text(json.dumps({
    'status': 'PASS', 'acceptedBase': accepted,
    'files': [{'path': p, 'sha256': h} for p, h in sorted(expected.items())],
    'policySealSha256': sha(POLICY/'native-inputs.json'),
    'controlSealSha256': sha(CONTROL/'native-inputs.json'),
    'main1475SealSha256': sha(main_seal),
    'accepted15BaselinesSha256': sha(HERE/'accepted15-baselines.json'),
    'rawBaselinesSha256': sha(HERE/'raw-baselines.json')}, indent=2)+'\n')
(HERE/'main-input-audit.json').write_text(json.dumps({
    'status': 'PASS', 'main1475Exact': True, 'acceptedBase': 1467,
    'policyIntermediate': 1504, 'controlBase': 1510,
    'priorNativeControlStatus': 'FAIL; preserved, no runtime PASS reuse'}, indent=2)+'\n')
print('PASS main1475 / accepted1467 / policy1504 / reconstructed1510; owned copy prepared')
