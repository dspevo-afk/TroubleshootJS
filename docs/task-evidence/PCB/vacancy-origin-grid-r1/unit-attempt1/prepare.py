"""Create only an exact bounded native-source copy outside the repository."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
state = HERE / 'local-state.json'
assert not state.exists(), 'Existing task copy must be audited first'
frozen = json.loads((HERE.parent / 'silkscreen-scale-r1/final-inputs.json').read_text())['files']
for row in frozen:
    assert sha(ROOT / row['path']) == row['sha256'], 'Accepted main owner changed: ' + row['path']
native = [r for r in frozen if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(frozen) == 1475 and len(native) == 1467
scratch = Path(tempfile.mkdtemp(prefix='tsj-vacancy-grid-')).resolve()
assert scratch.parent == Path(tempfile.gettempdir()).resolve()
assert not scratch.is_symlink() and not scratch.stat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
state.write_text(json.dumps({'scratch': str(scratch)}, indent=2) + '\n')
for row in native:
    source = ROOT / row['path']
    assert source.resolve().is_relative_to(ROOT) and not source.is_symlink()
    assert not source.stat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    target = scratch / row['path']
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(source.read_bytes())
    assert sha(target) == row['sha256']
assert len([p for p in scratch.rglob('*') if p.is_file()]) == 1467
(HERE / 'copy-inputs.json').write_text(json.dumps({'status': 'PASS', 'acceptedBase':
    '5c85b0a7cddfac801439ca12c3ca63b00e8440df', 'files': native,
    'excluded': 'Git, caches, historical packets, compiled web and private state; native consumed files only'}, indent=2) + '\n')
(HERE / 'main-input-audit.json').write_text(json.dumps({'status': 'PASS',
    'frozenAcceptedInputs': 1475, 'nativeCopiedInputs': 1467, 'mainProductionMutated': False}, indent=2) + '\n')
print('PASS exact accepted native1467 copy; main1475 unchanged; PRIVATE ' + str(state))
