"""Restore policy1504 then accepted1467 and retire only the verified owned copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
POLICY=HERE.parent/'passive-pose-policy-r1'
state=HERE/'local-state.json'
raw=Path(json.loads(state.read_text())['scratch']);COPY=raw.resolve()
assert raw==COPY and COPY.parent==Path(tempfile.gettempdir()).resolve() and COPY.name.startswith('tsj-passive-sizing-')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))

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

assert read(HERE/'native-input-audit.json')['status']=='PASS'
for phase in ('unit','routes'):
    if (HERE/(phase+'-timing.json')).exists():
        assert read(HERE/(phase+'-process-cleanup.json'))['status']=='PASS'
base=read(HERE/'copy-inputs.json')
policy_base={r['path']:r['sha256'] for r in base['files']}
accepted={r['path']:r['sha256'] for r in base['acceptedBase']}
seal=read(HERE/'native-inputs.json')
expected=dict(policy_base);expected.update(seal['overrides']);expected.update(seal['additions'])
paths=inventory(COPY)
assert {p.relative_to(COPY).as_posix():sha(p) for p in paths if p.is_file()}==expected
for name in sorted(seal['overrides']):
    source=POLICY/'source'/name
    if not source.is_file():source=ROOT/name
    assert sha(source)==policy_base[name]
    (COPY/name).write_bytes(source.read_bytes())
for name,digest in sorted(seal['additions'].items()):
    target=COPY/name;plain(target);assert sha(target)==digest;target.unlink()
paths=inventory(COPY)
assert len(policy_base)==1504 and {p.relative_to(COPY).as_posix():sha(p) for p in paths if p.is_file()}==policy_base
policy=read(POLICY/'native-inputs.json')
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
main=read(HERE.parent/'silkscreen-scale-r1/final-inputs.json')['files']
assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256']
(HERE/'copy-restoration.json').write_text(json.dumps({'status':'PASS','policy1504RestoredFirst':True,
 'acceptedNativeInputsRestored':1467,'controlAdditionsRetired':len(seal['additions']),
 'policyAdditionsRetired':37,'main1475Exact':True},indent=2)+'\n')
for path in sorted(paths,key=lambda p:len(p.parts),reverse=True):
    plain(path)
    path.unlink() if path.is_file() else path.rmdir()
plain(COPY);COPY.rmdir();state.unlink()
(HERE/'copy-cleanup.json').write_text(json.dumps({'status':'PASS','ownership':'Unique tsj-passive-sizing OS-temp copy',
 'verifiedExactFilesRemoved':1467,'symlinkOrReparseBoundariesCrossed':False,
 'privateStateRemoved':True,'mainProductionMutated':False,'sharedResourcesTouched':False},indent=2)+'\n')
print('PASS restored policy1504 then accepted1467; contained sizing copy/private state retired; main1475 exact')
