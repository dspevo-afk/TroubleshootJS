"""Seal a task-only exact NPN observation over unchanged published inputs."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='ce6f3fef9ac86b3157f0ecac08ab53350022134d'
OLD=HERE.parent/'sparse-board-diagnostic-r1';PRIOR=HERE.parent/'to92-molded-detail-r2'
GATES=('prepare_export.py','run_export.ps1','record_exit.ps1','qualify.py',
       'export-source/SparseNormalBoardDiagnostic.java','export-source/NOTES.md',
       'adapter-manifest.json','adapter-diff.txt')

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    b=read(PRIOR/'baseline-inputs.json');c=read(PRIOR/'candidate-inputs.json')
    expected=dict(b['files'],**c['changedSources']);assert len(expected)==1482
    assert {n:sha(ROOT/n) for n in expected}==expected
    assert hashlib.sha256(json.dumps(expected,sort_keys=True,separators=(',',':')).encode()).hexdigest()==c['allCompilerInputsDigest']
    java={p.relative_to(ROOT).as_posix() for f in ('src','tests/contracts') for p in (ROOT/f).rglob('*.java')}
    assert java=={n for n in expected if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    assert len(b['machinery'])==24 and {n:sha(ROOT/n) for n in b['machinery']}==b['machinery']
    identity=read(PRIOR/'npn-public-identity.json')
    assert identity['replay']=='tsj-alpha/6/EASY/NPN_LOW_SIDE_SWITCH/380469407799690561'
    refs=[OLD/n for n in ('make_export_adapter.py','qualify.py','run_export.ps1','record_exit.ps1',
          'export-source/SparseNormalBoardDiagnostic.java','export-source/NOTES.md')]
    refs += [PRIOR/n for n in ('baseline-inputs.json','candidate-inputs.json','player-inputs.json',
             'npn-public-identity.json','npn-generated-fitted.jpg','publication.json','postpublication-execution.json')]
    pins={p.relative_to(ROOT).as_posix():sha(p) for p in refs}
    for n,h in pins.items():
        assert hashlib.sha256(subprocess.check_output(['git','show',BASE+':'+n],cwd=ROOT)).hexdigest()==h,n
    return {'baseCommit':BASE,'applicationInputs':1482,'machineryInputs':24,
            'allCompilerInputsDigest':c['allCompilerInputsDigest'],
            'publishedInputs':pins,'baselineHelpers':{n:sha(HERE/n) for n in ('scope.md','inputs.py')}}

def audit_export():
    assert record()==read(HERE/'baseline-inputs.json')
    value={'baselineSealSha256':sha(HERE/'baseline-inputs.json'),'gates':{n:sha(HERE/n) for n in GATES}}
    assert value==read(HERE/'export-inputs.json')
    assert {p.relative_to(HERE/'export-frozen-source').as_posix():sha(p) for p in (HERE/'export-frozen-source').rglob('*') if p.is_file()}==value['gates']
    return value

if __name__=='__main__':
    command,=sys.argv[1:]
    if command=='freeze-baseline':
        value=record();target=HERE/'baseline-inputs.json'
        with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    elif command=='freeze-export':
        assert record()==read(HERE/'baseline-inputs.json')
        target=HERE/'export-inputs.json';archive=HERE/'export-frozen-source'
        assert not target.exists() and not archive.exists()
        value={'baselineSealSha256':sha(HERE/'baseline-inputs.json'),'gates':{n:sha(HERE/n) for n in GATES}}
        for n in GATES:
            dest=archive/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
        audit_export()
    elif command=='audit':audit_export()
    else:raise AssertionError('Unknown operation')
    print('PASS exact NPN unchanged source1482 machinery24 sealed observation inputs '+command)
