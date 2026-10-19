"""Archive the fully completed first trial attempt before density registration repair."""
import hashlib
import json
import stat
from pathlib import Path
HERE = Path(__file__).resolve().parent
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert json.loads((HERE / 'trial-timing.json').read_text(encoding='utf-8-sig'))['status'] == 'FAIL'
assert json.loads((HERE / 'trial-process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'
assert json.loads((HERE / 'trial-input-audit.json').read_text())['status'] == 'PASS'
prior = HERE / 'trial-attempt1'
assert not prior.exists()
prior.mkdir()
rows = []
for source in sorted(HERE.iterdir()):
    if not source.is_file() or source.name == 'local-state.json':
        continue
    target = prior / source.name
    target.write_bytes(source.read_bytes())
    assert sha(target) == sha(source)
    rows.append({'path': source.name, 'sha256': sha(target)})
seal = json.loads((HERE / 'trial-inputs.json').read_text())
archive = HERE / 'phase2-source'
target_archive = prior / archive.name
assert archive.resolve().is_relative_to(HERE) and target_archive.resolve().is_relative_to(HERE)
assert not target_archive.exists()
for p in [archive] + list(archive.rglob('*')):
    assert not p.is_symlink() and not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    assert p.resolve().is_relative_to(archive.resolve())
for relative, digest in dict(seal['overrides'], **seal['additions']).items():
    assert sha(archive / relative) == digest
assert len([p for p in archive.rglob('*') if p.is_file()]) == 15
archive.rename(target_archive)
(prior / 'preserved-files.json').write_text(json.dumps({'status': 'PASS', 'files': rows,
    'exactSourceArchiveMoved': True, 'archivedSourceFiles': 15}, indent=2) + '\n')
for name in ('trial-process-launch.json', 'trial-timing.json', 'trial-native.txt',
             'trial-inputs.json', 'trial-input-audit.json', 'trial-process-cleanup.json',
             'trial-log-sanitization.json'):
    source = HERE / name
    assert sha(source) == sha(prior / name)
    source.unlink()
assert not (HERE / 'trial-native-receipt.txt').exists(), 'Target failed before final receipt emission'
print('Preserved complete trial attempt1 and exact15-file source archive; no source repair yet')
