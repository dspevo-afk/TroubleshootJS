"""Bind this finite experiment to unchanged production and exact private inputs."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
GATES=('scope.md','inputs.py','make_experiment.py','export-source/SparseNormalBoardDiagnostic.java',
       'exporter-notes.md','integration-boundary.md','private-input-manifest.json','owner-diff.txt','adapter-diff.txt',
       'run_experiment.ps1','record_exit.ps1','qualify_experiment.py')
PRIVATE=('PcbPlacementPlanner.java','baseline-adapter.ps1','experiment-adapter.ps1','private-input-manifest.json','owner-diff.txt','adapter-diff.txt')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def baseline():
    b=read(HERE/'baseline-inputs.json')
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==b['baseCommit']
    assert len(b['files'])==1482 and {n:sha(ROOT/n) for n in b['files']}==b['files']
    assert len(b['machinery'])==24 and {n:sha(ROOT/n) for n in b['machinery']}==b['machinery']
    java={p.relative_to(ROOT).as_posix() for f in ('src','tests/contracts') for p in (ROOT/f).rglob('*.java')}
    assert java=={n for n in b['files'] if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert sha(HERE/'scope.md')==b['scopeSha256']
    assert {n:sha(ROOT/n) for n in b['publishedInputs']}==b['publishedInputs']
    for n,h in b['publishedInputs'].items():assert hashlib.sha256(subprocess.check_output(['git','show',b['baseCommit']+':'+n],cwd=ROOT)).hexdigest()==h,n
    return b
def seal(private):
    b=baseline();m=read(HERE/'private-input-manifest.json')
    assert m['publishedOwnerSha256']==b['files'][m['publishedOwner']]
    assert sha(private/'PcbPlacementPlanner.java')==m['privateOwnerSha256']
    assert sha(private/'baseline-adapter.ps1')==m['baselineAdapterSha256']
    assert sha(private/'experiment-adapter.ps1')==m['experimentAdapterSha256']
    assert sha(HERE/'export-source/SparseNormalBoardDiagnostic.java')==m['exporterSha256']
    return {'baseCommit':b['baseCommit'],'baselineSha256':sha(HERE/'baseline-inputs.json'),
            'helpers':{n:sha(HERE/n) for n in GATES},'privateInputs':{n:sha(private/n) for n in PRIVATE},
            'request':'tsj-alpha/6/EASY/PARALLEL_DUAL_INDICATOR/-9223372036854775808',
            'interpretation':'TASK_ONLY_GENERIC_PAIRED_TRANSLATION_NOT_CURRENT_PRODUCTION'}
if __name__=='__main__':
    command,folder=sys.argv[1:];assert command in ('freeze','audit')
    value=seal(Path(folder));target=HERE/'experiment-inputs.json'
    if command=='freeze':
        with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    assert value==read(target)
    print('PASS paired probe unchanged source1482 machinery24 sealed helpers/private inputs')
