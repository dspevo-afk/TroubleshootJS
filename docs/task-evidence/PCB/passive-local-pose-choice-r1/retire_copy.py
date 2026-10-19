"""Restore1547->1528->1510->1504->1467; retire only the exact owned COPY."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
SOURCE=HERE.parent/'passive-source-sizing-trial-r1'
CONTROL=HERE.parent/'passive-sizing-control-r1'
POLICY=HERE.parent/'passive-pose-policy-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
state=HERE/'local-state.json';raw=Path(read(state)['scratch']);COPY=raw.resolve()
assert raw==COPY and COPY.parent==Path(tempfile.gettempdir()).resolve() and COPY.name.startswith('tsj-passive-local-pose-')
def plain(path):
    assert path.exists() and not path.is_symlink() and path.resolve().is_relative_to(COPY)
    assert not path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
def inventory(directory):
    plain(directory);paths=[]
    for child in directory.iterdir():
        plain(child);paths.append(child)
        if child.is_dir():paths.extend(inventory(child))
    return paths
def verify(expected):
    paths=inventory(COPY)
    assert {p.relative_to(COPY).as_posix():sha(p) for p in paths if p.is_file()}==expected
    return paths
def restore(seal,before):
    for name in sorted(seal['overrides']):
        references=[SOURCE/'source'/name,CONTROL/'source'/name,POLICY/'source'/name,ROOT/name]
        source=next((p for p in references if p.is_file() and sha(p)==before[name]),None)
        assert source is not None,name
        assert not source.is_symlink() and not source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        (COPY/name).write_bytes(source.read_bytes())
    for name,digest in seal['additions'].items():
        target=COPY/name;plain(target);assert sha(target)==digest;target.unlink()
    verify(before)
assert read(HERE/'native-input-audit.json')['status']=='PASS'
for phase in ('unit','routes'):
    if (HERE/(phase+'-timing.json')).exists():assert read(HERE/(phase+'-process-cleanup.json'))['status']=='PASS'
base=read(HERE/'copy-inputs.json')
base1528={r['path']:r['sha256'] for r in base['files']}
base1510={r['path']:r['sha256'] for r in read(SOURCE/'copy-inputs.json')['files']}
base1504={r['path']:r['sha256'] for r in read(CONTROL/'copy-inputs.json')['files']}
accepted={r['path']:r['sha256'] for r in base['acceptedBase']}
seal=read(HERE/'native-inputs.json');current=dict(base1528)
current.update(seal['overrides']);current.update(seal['additions']);verify(current)
restore(seal,base1528)
restore(read(SOURCE/'native-inputs.json'),base1510)
restore(read(CONTROL/'native-inputs.json'),base1504)
restore(read(POLICY/'native-inputs.json'),accepted)
paths=verify(accepted)
assert [len(base1528),len(base1510),len(base1504),len(accepted)]==[1528,1510,1504,1467]
main=read(HERE.parent/'silkscreen-scale-r1/final-inputs.json')['files'];assert len(main)==1475
for row in main:assert sha(ROOT/row['path'])==row['sha256']
(HERE/'copy-restoration.json').write_text(json.dumps({
    'status':'PASS','restorationCounts':[1528,1510,1504,1467],'main1475Exact':True},indent=2)+'\n')
for path in sorted(paths,key=lambda p:len(p.parts),reverse=True):
    plain(path);path.unlink() if path.is_file() else path.rmdir()
plain(COPY);COPY.rmdir();state.unlink()
(HERE/'copy-cleanup.json').write_text(json.dumps({
    'status':'PASS','verifiedExactFilesRemoved':1467,'privateStateRemoved':True,
    'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False},indent=2)+'\n')
print('PASS restored1528 then1510 then1504 then1467; owned local-pose copy/private state retired; main1475 exact')
