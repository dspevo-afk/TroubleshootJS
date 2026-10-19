"""Create an exact native-only accepted-source copy and seal two prior rows."""
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
main_seal = HERE.parent / 'silkscreen-scale-r1/final-inputs.json'
rows = json.loads(main_seal.read_text())['files']
assert len(rows) == 1475
for row in rows:
    assert sha(ROOT / row['path']) == row['sha256'], 'Accepted source changed'
native = [r for r in rows if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(native) == 1467
baseline_source = HERE.parent / 'vacancy-origin-grid-r1/accepted15-baselines.json'
assert sha(baseline_source) == '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
base = {r['seed']: r for r in json.loads(baseline_source.read_text())['rows']}
(HERE / 'accepted15-baselines.json').write_text(json.dumps({'status': 'PINNED_BEFORE_INSTRUMENTATION',
    'source': 'docs/task-evidence/PCB/vacancy-origin-grid-r1/accepted15-baselines.json',
    'sourceSha256': sha(baseline_source), 'rows': [base[s] for s in ('42', '-1022')]}, indent=2) + '\n')
copy = Path(tempfile.mkdtemp(prefix='tsj-routed-strip-')).resolve()
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
(HERE / 'copy-inputs.json').write_text(json.dumps({'status': 'PASS', 'baseCommit':
    '0031b8a5e38ebe308e41505f7c36a99faef38307', 'files': native}, indent=2) + '\n')
(HERE / 'main-input-audit.json').write_text(json.dumps({'status': 'PASS', 'acceptedMainExact': 1475,
    'nativeCopiedExact': 1467, 'excluded': 'Git, caches, history, compiled web and private state'}, indent=2) + '\n')
print('PASS main1475 and copied native1467; two baselines sealed; PRIVATE copy=' + str(copy))
