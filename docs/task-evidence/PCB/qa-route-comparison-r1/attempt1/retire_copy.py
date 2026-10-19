"""Restore exact accepted inputs, then remove only this verified owned copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def plain(path):
    assert path.exists() and not path.is_symlink()
    assert not (path.stat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)

state = HERE / 'local-state.json'
copy_root = Path(json.loads(state.read_text())['scratch']).resolve()
assert copy_root.parent == Path(tempfile.gettempdir()).resolve() and copy_root.name.startswith('tsj-qa-route-')
plain(copy_root)
assert json.loads((HERE / 'native-input-audit.json').read_text())['status'] == 'PASS'
assert json.loads((HERE / 'process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'
expected = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
tests = ('PcbQaRouteComparisonContractTest.java', 'PcbQaRouteOriginalContractTest.java', 'PcbQaRouteTranslatedContractTest.java')
for name in tests:
    assert (copy_root / 'tests/contracts' / name).read_bytes() == (HERE / name).read_bytes()
owner = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
assert (copy_root / owner).read_bytes() == (HERE / 'generator-adapter.java').read_bytes()
recovery = Path(json.loads((HERE.parent / 'silkscreen-scale-r1/local-state.json').read_text())['scratch']).resolve()
assert recovery.parent == copy_root.parent and recovery.name.startswith('tsj-silkscreen-scale-')
plain(recovery)
for relative, source in ((owner, ROOT / owner), ('scripts/verify-current-contracts.ps1', recovery / '1')):
    plain(source)
    assert sha(source) == expected[relative], 'Recovery source mismatch'
    (copy_root / relative).write_bytes(source.read_bytes())
for name in tests:
    path = copy_root / 'tests/contracts' / name
    plain(path)
    path.unlink()
paths = list(copy_root.rglob('*'))
for path in paths:
    plain(path)
    assert path.resolve().is_relative_to(copy_root)
actual = {p.relative_to(copy_root).as_posix(): sha(p) for p in paths if p.is_file()}
assert actual == expected and len(actual) == 1466, 'Restored accepted inventory differs'
(HERE / 'copy-restoration.json').write_text(json.dumps({'status': 'PASS', 'restoredAcceptedInputs': 1466,
    'temporaryFixturesRetired': 3, 'mainProductionMutated': False}, indent=2) + '\n')
for path in sorted(paths, key=lambda p: len(p.parts), reverse=True):
    plain(path)
    assert path.resolve().is_relative_to(copy_root)
    path.unlink() if path.is_file() else path.rmdir()
copy_root.rmdir()
state.unlink()
(HERE / 'copy-cleanup.json').write_text(json.dumps({'status': 'PASS', 'verifiedExactFilesRemoved': 1466,
    'ownership': 'Unique tsj-qa-route OS-temp copy', 'symlinkOrReparseBoundariesCrossed': False,
    'privateStateRemoved': True, 'rendererRecoverySnapshotsPreserved': True}, indent=2) + '\n')
print('PASS accepted1466 restored and verified task-only copy/private state removed')
