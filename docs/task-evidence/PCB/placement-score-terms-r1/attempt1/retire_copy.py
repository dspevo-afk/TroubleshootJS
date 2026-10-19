"""Restore then remove only the exact owned accepted-source copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def plain(path):
    need = path.exists() and not path.is_symlink()
    assert need and not (path.stat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT), 'Unexpected reparse/missing path'

state = HERE / 'local-state.json'
copy_root = Path(json.loads(state.read_text())['scratch']).resolve()
assert copy_root.parent == Path(tempfile.gettempdir()).resolve() and copy_root.name.startswith('tsj-score-term-')
plain(copy_root)
assert json.loads((HERE / 'native-input-audit.json').read_text())['status'] == 'PASS'
assert json.loads((HERE / 'process-cleanup.json').read_text())['status'] == 'PASS'
manifest = json.loads((HERE / 'copy-inputs.json').read_text())['files']
expected = {r['path']: r['sha256'] for r in manifest}
test = copy_root / 'tests/contracts/PcbPlacementScoreTermsContractTest.java'
assert test.read_bytes() == (HERE / test.name).read_bytes(), 'Fixture archive mismatch'
assert (copy_root / 'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java').read_bytes() == (HERE / 'planner-instrumentation.java').read_bytes()
recovery = Path(json.loads((HERE.parent / 'silkscreen-scale-r1/local-state.json').read_text())['scratch']).resolve()
assert recovery.parent == copy_root.parent and recovery.name.startswith('tsj-silkscreen-scale-')
plain(recovery)
restore = {'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java': ROOT / 'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java',
           'scripts/verify-current-contracts.ps1': recovery / '1'}
for relative, source in restore.items():
    plain(source)
    assert sha(source) == expected[relative], 'Recovery source changed'
    (copy_root / relative).write_bytes(source.read_bytes())
plain(test)
test.unlink()
paths = list(copy_root.rglob('*'))
for path in paths:
    plain(path)
    assert path.resolve().is_relative_to(copy_root), 'Path escaped owned copy'
actual = {p.relative_to(copy_root).as_posix(): sha(p) for p in paths if p.is_file()}
assert actual == expected and len(actual) == 1466, 'Restored accepted inventory differs'
(HERE / 'copy-restoration.json').write_text(json.dumps({'status': 'PASS', 'restoredAcceptedInputs': 1466,
    'temporaryFixtureRetired': True, 'mainProductionMutated': False}, indent=2) + '\n')
for path in sorted(paths, key=lambda p: len(p.parts), reverse=True):
    plain(path)
    assert path.resolve().is_relative_to(copy_root)
    path.unlink() if path.is_file() else path.rmdir()
copy_root.rmdir()
assert not copy_root.exists()
state.unlink()
(HERE / 'copy-cleanup.json').write_text(json.dumps({'status': 'PASS', 'ownership': 'Unique tsj-score-term OS-temp copy',
    'verifiedExactFilesRemoved': 1466, 'symlinkOrReparseBoundariesCrossed': False,
    'privateStateRemoved': True, 'rendererRecoverySnapshotsPreserved': True}, indent=2) + '\n')
print('PASS restored accepted1466 and removed verified owned copy/private state')
