"""Restore accepted native1467 and retire only the verified owned temporary copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
state = HERE / 'local-state.json'
raw_copy = Path(json.loads(state.read_text())['scratch'])
COPY = raw_copy.resolve()
assert raw_copy == COPY and COPY.parent == Path(tempfile.gettempdir()).resolve()
assert COPY.name.startswith('tsj-routed-strip-')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()


def plain(path):
    assert path.exists() and not path.is_symlink(), 'Missing/link path'
    assert not path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT, 'Reparse path'
    assert path.resolve().is_relative_to(COPY), 'Path outside owned copy'


def inventory(directory):
    plain(directory)
    paths = []
    for child in directory.iterdir():
        plain(child)  # Check the boundary BEFORE descending, including junctions.
        paths.append(child)
        if child.is_dir():
            paths.extend(inventory(child))
    return paths


assert json.loads((HERE / 'native-input-audit.json').read_text())['status'] == 'PASS'
assert json.loads((HERE / 'process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'
base = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
seal = json.loads((HERE / 'native-inputs.json').read_text())
expected_trial = dict(base)
expected_trial.update(seal['overrides'])
expected_trial.update(seal['additions'])
paths = inventory(COPY)
assert {p.relative_to(COPY).as_posix(): sha(p) for p in paths if p.is_file()} == expected_trial
for relative in sorted(seal['overrides']):
    accepted = ROOT / relative
    assert accepted.resolve().is_relative_to(ROOT) and not accepted.is_symlink()
    assert not accepted.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    assert sha(accepted) == base[relative], 'Accepted recovery bytes changed'
    (COPY / relative).write_bytes(accepted.read_bytes())
for relative in sorted(seal['additions']):
    target = COPY / relative
    plain(target)
    assert sha(target) == seal['additions'][relative]
    target.unlink()
paths = inventory(COPY)
actual = {p.relative_to(COPY).as_posix(): sha(p) for p in paths if p.is_file()}
assert len(actual) == 1467 and actual == base, 'Restored accepted inventory differs'
main = json.loads((HERE.parent / 'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT / row['path']) == row['sha256'], 'Main accepted input changed'
(HERE / 'copy-restoration.json').write_text(json.dumps({'status': 'PASS',
    'acceptedNativeInputsRestored': 1467, 'temporaryFixturesRetired': len(seal['additions']),
    'overridesRestored': len(seal['overrides']), 'main1475Exact': True}, indent=2) + '\n')
for path in sorted(paths, key=lambda p: len(p.parts), reverse=True):
    plain(path)
    path.unlink() if path.is_file() else path.rmdir()
plain(COPY)
COPY.rmdir()
state.unlink()
(HERE / 'copy-cleanup.json').write_text(json.dumps({'status': 'PASS',
    'verifiedExactFilesRemoved': 1467, 'ownership': 'Unique tsj-routed-strip OS-temp copy',
    'symlinkOrReparseBoundariesCrossed': False, 'privateStateRemoved': True,
    'mainProductionMutated': False, 'sharedResourcesTouched': False}, indent=2) + '\n')
print('PASS restored accepted1467; verified owned copy/private state retired; main1475 exact')
