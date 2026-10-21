"""Freeze the unchanged failed candidate and task-only diagnostic inputs."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
GATES=('scope.md','inputs.py','run_diagnostic.ps1','record_exit.ps1',
       'make_diagnostic.py','export-source/SparseNormalBoardDiagnostic.java',
       'exporter-notes.md','qualify.py',
       'preseal-findings.json','source-review.json')
PRIVATE=('current-adapter.ps1','nohook-adapter.ps1','SeededPcbLayoutGenerator.java',
         'private-input-manifest.json','owner-diff.txt','adapter-diff.txt')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(folder):
    b=read(HERE/'baseline-inputs.json')
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==b['baseCommit']
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    assert sha(HERE/'scope.md')==b['scopeSha256']
    assert len(b['files'])==1482 and {n:sha(ROOT/n) for n in b['files']}==b['files']
    assert len(b['machinery'])==24 and {n:sha(ROOT/n) for n in b['machinery']}==b['machinery']
    java={p.relative_to(ROOT).as_posix() for f in ('src','tests/contracts') for p in (ROOT/f).rglob('*.java')}
    assert java=={n for n in b['files'] if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert {n:sha(ROOT/n) for n in b['consumedInputs']}==b['consumedInputs']
    manifest=read(folder/'private-input-manifest.json')
    for key,name in (('currentAdapterSha256','current-adapter.ps1'),('nohookAdapterSha256','nohook-adapter.ps1'),('privateGeneratorSha256','SeededPcbLayoutGenerator.java')):
        assert manifest[key]==sha(folder/name)
    assert manifest['exporterSha256']==sha(HERE/'export-source/SparseNormalBoardDiagnostic.java')
    assert manifest['publishedGeneratorSha256']==sha(ROOT/'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java')
    return {'baseCommit':b['baseCommit'],'baselineSealSha256':sha(HERE/'baseline-inputs.json'),
            'sourceCount':1482,'files':b['files'],'machinery':b['machinery'],
            'gates':{n:sha(HERE/n) for n in GATES},'privateInputs':{n:sha(folder/n) for n in PRIVATE}}
if __name__=='__main__':
    command,folder=sys.argv[1:];assert command in ('freeze','audit');folder=Path(folder)
    value=record(folder);target=HERE/'diagnostic-inputs.json';archive=HERE/'input-source'
    if command=='freeze':
        assert not archive.exists();archive.mkdir()
        for n in GATES:
            p=archive/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((HERE/n).read_bytes())
        for n in PRIVATE:
            p=archive/'private-inputs'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((folder/n).read_bytes())
        with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    assert value==read(target)
    expected=dict(value['gates'],**{'private-inputs/'+n:h for n,h in value['privateInputs'].items()})
    assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob('*') if p.is_file()}==expected
    print('PASS diversity diagnostic unchanged1482 machinery24 sealed helpers/private inputs')
