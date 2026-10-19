"""Copy only accepted manifest inputs; keep local paths in private state."""
import hashlib
import json
import os
import stat
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda b: hashlib.sha256(b).hexdigest()
def plain(path):
    assert path.exists() and not path.is_symlink(), 'Missing or symlink input'
    assert not (path.stat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT), 'Reparse input'
    return path

assert not (HERE / 'local-state.json').exists(), 'Preparation already performed'
accepted = json.loads((HERE.parent / 'post-selection-edge-trim-r1/final-inputs.json').read_text())['files']
assert len(accepted) == 1466
old_state = json.loads((HERE.parent / 'silkscreen-scale-r1/local-state.json').read_text())
recovery = plain(Path(old_state['scratch']).resolve())
temp = Path(tempfile.gettempdir()).resolve()
assert recovery.parent == temp and recovery.name.startswith('tsj-silkscreen-scale-')
owners = ['src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java',
          'scripts/verify-current-contracts.ps1']
sources = []
for row in accepted:
    relative = Path(row['path'])
    assert not relative.is_absolute() and '..' not in relative.parts
    assert relative.parts[0] in ('.tools', 'docs', 'scripts', 'src', 'tests')
    assert '.git' not in relative.parts and '__pycache__' not in relative.parts
    source = recovery / str(owners.index(row['path'])) if row['path'] in owners else ROOT / relative
    plain(source)
    data = source.read_bytes()
    assert sha(data) == row['sha256'], 'Accepted input mismatch: ' + row['path']
    sources.append((row, data))
scratch = Path(tempfile.mkdtemp(prefix='tsj-score-term-')).resolve()
assert scratch.parent == temp and scratch.name.startswith('tsj-score-term-')
plain(scratch)
(HERE / 'local-state.json').write_text(json.dumps({'scratch': str(scratch)}, indent=2) + '\n')
for row, data in sources:
    target = scratch / row['path']
    assert target.resolve().is_relative_to(scratch)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    assert sha(target.read_bytes()) == row['sha256']
assert len(list(scratch.rglob('*'))) >= len(accepted)
(HERE / 'copy-inputs.json').write_text(json.dumps({'status': 'PASS', 'count': len(accepted),
    'boundary': 'Accepted native/source inputs only; no compiled web identity claim',
    'recoveredOwners': owners, 'files': accepted}, indent=2) + '\n')
print('PASS exact accepted1466 OS-temp copy; private state retained')
