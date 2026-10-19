"""Copy exact accepted native inputs and retain the full predeclared baseline."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
state = HERE / 'local-state.json'
assert not state.exists()
rows = json.loads((HERE.parent / 'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(rows) == 1475
for row in rows:
    assert sha(ROOT / row['path']) == row['sha256']
native = [r for r in rows if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(native) == 1467
base = HERE.parent / 'vacancy-origin-grid-r1/accepted15-baselines.json'
assert sha(base) == '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
(HERE / 'accepted15-baselines.json').write_bytes(base.read_bytes())
copy = Path(tempfile.mkdtemp(prefix='tsj-passive-pose-')).resolve()
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
(HERE / 'copy-inputs.json').write_text(json.dumps({'status': 'PASS',
    'baseCommit': '9f29107ccf9c21c84558c71ac9d334760e1a97a0', 'files': native}, indent=2) + '\n')
(HERE / 'main-input-audit.json').write_text(json.dumps({'status': 'PASS', 'acceptedMainExact': 1475,
    'copiedNativeExact': 1467, 'excluded': 'Git, caches, history, compiled web and private state'}, indent=2) + '\n')
print('PASS main1475/copy1467/full14 baseline exact; PRIVATE copy=' + str(copy))
