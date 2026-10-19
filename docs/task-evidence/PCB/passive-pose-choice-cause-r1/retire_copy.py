"""Restore exact policy1504, then accepted1467, and retire only the owned copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
POLICY=HERE.parent/'passive-pose-policy-r1'
state=HERE/'local-state.json'
raw=Path(json.loads(state.read_text())['scratch'])
COPY=raw.resolve()
assert raw==COPY and COPY.parent==Path(tempfile.gettempdir()).resolve() and COPY.name.startswith('tsj-passive-choice-')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()


def plain(path):
    assert path.exists() and not path.is_symlink()
    assert not path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    assert path.resolve().is_relative_to(COPY)


def inventory(directory):
    plain(directory);paths=[]
    for child in directory.iterdir():
        plain(child);paths.append(child)
        if child.is_dir():paths.extend(inventory(child))
    return paths


assert json.loads((HERE/'native-input-audit.json').read_text())['status']=='PASS'
for phase in ('unit','routes'):
    if (HERE/(phase+'-timing.json')).exists():
        assert json.loads((HERE/(phase+'-process-cleanup.json')).read_text(encoding='utf-8-sig'))['status']=='PASS'
prepared=json.loads((HERE/'copy-inputs.json').read_text())
base={r['path']:r['sha256'] for r in prepared['files']}
accepted={r['path']:r['sha256'] for r in prepared['acceptedBase']}
seal=json.loads((HERE/'native-inputs.json').read_text())
expected=dict(base);expected.update(seal['overrides']);expected.update(seal['additions'])
paths=inventory(COPY)
assert {p.relative_to(COPY).as_posix():sha(p) for p in paths if p.is_file()}==expected
for name in sorted(seal['overrides']):
    source=POLICY/'source'/name
    assert sha(source)==base[name]
    (COPY/name).write_bytes(source.read_bytes())
for name,digest in sorted(seal['additions'].items()):
    target=COPY/name;plain(target);assert sha(target)==digest;target.unlink()
paths=inventory(COPY)
assert len(base)==1504 and {p.relative_to(COPY).as_posix():sha(p) for p in paths if p.is_file()}==base
policy=json.loads((POLICY/'native-inputs.json').read_text())
for name in sorted(policy['overrides']):
    source=ROOT/name
    assert source.resolve().is_relative_to(ROOT) and not source.is_symlink()
    assert not source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    assert sha(source)==accepted[name]
    (COPY/name).write_bytes(source.read_bytes())
for name,digest in sorted(policy['additions'].items()):
    target=COPY/name;plain(target);assert sha(target)==digest;target.unlink()
paths=inventory(COPY)
assert len(accepted)==1467 and {p.relative_to(COPY).as_posix():sha(p) for p in paths if p.is_file()}==accepted
main=json.loads((HERE.parent/'silkscreen-scale-r1/final-inputs.json').read_text())['files']
assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256']
(HERE/'copy-restoration.json').write_text(json.dumps({'status':'PASS','policy1504RestoredFirst':True,
 'acceptedNativeInputsRestored':1467,'diagnosticAdditionsRetired':4,'policyAdditionsRetired':37,
 'main1475Exact':True},indent=2)+'\n')
for path in sorted(paths,key=lambda p:len(p.parts),reverse=True):
    plain(path)
    path.unlink() if path.is_file() else path.rmdir()
plain(COPY);COPY.rmdir();state.unlink()
(HERE/'copy-cleanup.json').write_text(json.dumps({'status':'PASS','ownership':'Unique tsj-passive-choice OS-temp copy',
 'verifiedExactFilesRemoved':1467,'symlinkOrReparseBoundariesCrossed':False,'privateStateRemoved':True,
 'mainProductionMutated':False,'sharedResourcesTouched':False},indent=2)+'\n')
print('PASS restored policy1504 then accepted1467; contained copy/private state retired; main1475 exact')
