"""Prepare an exact accepted native base plus the passed mechanical prerequisite."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
FOUNDATION = HERE.parent / 'passive-pose-foundation-r1'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
state = HERE / 'local-state.json'
assert not state.exists()
rows = json.loads((HERE.parent / 'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(rows) == 1475
for row in rows:
    assert sha(ROOT / row['path']) == row['sha256']
native = [r for r in rows if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(native) == 1467
base = FOUNDATION / 'accepted15-baselines.json'
assert sha(base) == '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
assert json.loads((FOUNDATION / 'root-review.json').read_text())['status'] == 'PASS'
foundation_seal = json.loads((FOUNDATION / 'native-inputs.json').read_text())
for name, digest in foundation_seal['gateOwners'].items():
    assert sha(FOUNDATION / name) == digest
reused = ['src/com/lushprojects/circuitjs1/client/PhysicalPackages.java',
    'tests/contracts/PcbPassivePoseFoundationContractTest.java',
    'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java',
    'tests/contracts/PcbPassivePoseSourceContractTest.java']
foundation_sources = dict(foundation_seal['overrides'], **foundation_seal['additions'])
for name in reused:
    assert sha(FOUNDATION / 'source' / name) == foundation_sources[name]
(HERE / 'accepted15-baselines.json').write_bytes(base.read_bytes())
copy = Path(tempfile.mkdtemp(prefix='tsj-passive-policy-')).resolve()
assert copy.parent == Path(tempfile.gettempdir()).resolve()
assert not copy.is_symlink() and not copy.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
state.write_text(json.dumps({'scratch': str(copy)}, indent=2) + '\n')
for row in native:
    source = ROOT / row['path']
    assert source.resolve().is_relative_to(ROOT) and not source.is_symlink()
    assert not source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    target = copy / row['path']
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(source.read_bytes())
    assert sha(target) == row['sha256']
for name in reused:
    target = copy / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((FOUNDATION / 'source' / name).read_bytes())
(HERE / 'copy-inputs.json').write_text(json.dumps({'status': 'PASS',
    'baseCommit': '5e6b1f66f31b9826b3583f8ad025f3b686f6d005', 'files': native,
    'reusedMechanicalPrerequisite': {p: foundation_sources[p] for p in reused}}, indent=2) + '\n')
(HERE / 'main-input-audit.json').write_text(json.dumps({'status': 'PASS', 'acceptedMainExact': 1475,
    'acceptedNativeBase': 1467, 'mechanicalSourcesReused': len(reused),
    'excluded': 'Git, caches, history, compiled web and private state'}, indent=2) + '\n')
print('PASS main1475/nativebase1467/foundation4/full14 baseline exact; PRIVATE copy=' + str(copy))
