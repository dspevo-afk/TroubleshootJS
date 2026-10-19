"""Restore the exact accepted source then retire only the verified owned copy."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
state=HERE/'local-state.json';raw=Path(read(state)['scratch']);COPY=raw.resolve()
assert raw==COPY and COPY.parent==Path(tempfile.gettempdir()).resolve() and COPY.name.startswith('tsj-compactor-cut-cause-')
def plain(p):
    assert not p.is_symlink() and p.resolve().is_relative_to(COPY)
    assert not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
def paths(folder):
    plain(folder);result=[]
    for p in folder.iterdir():
        plain(p);result.append(p)
        if p.is_dir():result.extend(paths(p))
    return result
def verify(expected):
    result=paths(COPY)
    assert {p.relative_to(COPY).as_posix():sha(p) for p in result if p.is_file()}==expected
    return result
assert read(HERE/'native-input-audit.json')['status']=='PASS'
for phase in ('unit','routes'):
    if (HERE/(phase+'-timing.json')).exists():assert read(HERE/(phase+'-process-cleanup.json'))['status']=='PASS'
seal=read(HERE/'native-inputs.json')
main=read(ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json')['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256']
base={r['path']:r['sha256'] for r in main if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
expected=dict(base);expected.update(seal['overrides']);expected.update(seal['additions']);verify(expected)
for name in seal['overrides']:
    assert sha(ROOT/name)==base[name];(COPY/name).write_bytes((ROOT/name).read_bytes())
for name,digest in seal['additions'].items():
    p=COPY/name;plain(p);assert sha(p)==digest;p.unlink()
remaining=verify(base);assert len(base)==1467
(HERE/'copy-restoration.json').write_text(json.dumps({'status':'PASS','fromCount':1475,'restoredCount':1467,'main1475Exact':True},indent=2)+'\n')
for p in sorted(remaining,key=lambda p:len(p.parts),reverse=True):
    plain(p);p.unlink() if p.is_file() else p.rmdir()
plain(COPY);COPY.rmdir();state.unlink()
(HERE/'copy-cleanup.json').write_text(json.dumps({'status':'PASS','verifiedExactFilesRemoved':1467,'privateStateRemoved':True,'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False},indent=2)+'\n')
print('PASS restored exact1467 and retired owned COPY/private state; main1475 exact')
