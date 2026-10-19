"""Preserve a completed failed freeze before narrowly retiring its current artifacts."""
import hashlib
import json
import re
import stat
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
assert re.fullmatch(r'attempt[1-9][0-9]*', sys.argv[1])
TARGET = HERE / sys.argv[1]
assert not TARGET.exists()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = json.loads((HERE / 'native-inputs.json').read_text())
phases=[p for p in ('unit','routes') if (HERE/(p+'-timing.json')).exists()]
assert phases and any(json.loads((HERE/(p+'-timing.json')).read_text(encoding='utf-8-sig'))['status']=='FAIL' for p in phases)
for p in phases:
    assert json.loads((HERE/(p+'-process-cleanup.json')).read_text(encoding='utf-8-sig'))['status']=='PASS'
assert json.loads((HERE / 'native-input-audit.json').read_text())['status'] == 'PASS'
for name, expected in seal['gateOwners'].items():
    assert sha(HERE / name) == expected
source = {'source/' + p: h for p, h in dict(seal['overrides'], **seal['additions']).items()}
for name, expected in source.items():
    assert sha(HERE / name) == expected
artifacts=['native-inputs.json','native-input-audit.json']
for phase in phases:
    for suffix in ('.txt','-timing.json','-process-launch.json','-process-cleanup.json','-log-sanitization.json','-receipt.txt'):
        name=phase+suffix
        if (HERE/name).exists():artifacts.append(name)
for name in ('unit-validation.json','assessment.json'):
    if (HERE/name).exists():artifacts.append(name)
names = sorted(set(seal['gateOwners']) | set(source) | set(artifacts))
manifest = {}


def plain(path):
    assert path.resolve().is_relative_to(HERE)
    for part in [path] + list(path.parents):
        assert not part.is_symlink() and not part.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        if part == HERE:
            break


for name in names:
    path = HERE / name
    plain(path)
    target = TARGET / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(path.read_bytes())
    manifest[name] = sha(path)
    assert sha(target) == manifest[name]
(TARGET / 'archive-sha256.json').write_text(json.dumps({'status': 'PASS',
    'scope': 'Completed failed native freeze, exact gates/source and terminal cleanup; private copy state excluded',
    'files': manifest}, indent=2) + '\n')
# Retire only the explicitly frozen source archive and completed phase outputs.
for name in sorted(source):
    path = HERE / name
    plain(path)
    assert sha(path) == manifest[name]
    path.unlink()
parents = {parent for name in source for parent in (HERE / name).parents
    if parent.is_relative_to(HERE / 'source')}
for path in sorted(parents, key=lambda p: len(p.parts), reverse=True):
    plain(path)
    path.rmdir()
for name in artifacts:
    path = HERE / name
    plain(path)
    assert sha(path) == manifest[name]
    path.unlink()
print('PASS exact completed failure archived; current frozen source/phase artifacts retired')
