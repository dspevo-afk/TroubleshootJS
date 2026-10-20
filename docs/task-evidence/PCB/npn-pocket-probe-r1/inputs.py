"""Pin unchanged production and published inputs before the private NPN probe."""
import hashlib,json,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='a73eeaeb3edd9898a29156b761d20021c3c6db18'
PRIOR=HERE.parent/'npn-sparse-diagnostic-r1';OLD=HERE.parent/'composed-pocket-probe-r1'

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    accepted=HERE.parent/'to92-molded-detail-r2';b=read(accepted/'baseline-inputs.json');c=read(accepted/'candidate-inputs.json')
    files=dict(b['files'],**c['changedSources']);assert len(files)==1482 and {n:sha(ROOT/n) for n in files}==files
    assert len(b['machinery'])==24 and {n:sha(ROOT/n) for n in b['machinery']}==b['machinery']
    java={p.relative_to(ROOT).as_posix() for f in ('src','tests/contracts') for p in (ROOT/f).rglob('*.java')}
    assert java=={n for n in files if n.endswith('.java') and n.startswith(('src/','tests/contracts/'))}
    refs=[PRIOR/n for n in ('assessment.json','npn/receipt.txt','qualify.py','export-source/SparseNormalBoardDiagnostic.java','adapter-manifest.json')]
    refs += [OLD/n for n in ('make_experiment.py','run_experiment.ps1','record_exit.ps1','qualify_experiment.py','experiment_inputs.py','source-findings.md','assessment.json')]
    pins={p.relative_to(ROOT).as_posix():sha(p) for p in refs}
    for n,h in pins.items():assert hashlib.sha256(subprocess.check_output(['git','show',BASE+':'+n],cwd=ROOT)).hexdigest()==h,n
    return {'baseCommit':BASE,'files':files,'machinery':b['machinery'],'publishedInputs':pins,'baselineHelpers':{n:sha(HERE/n) for n in ('scope.md','inputs.py')}}

if __name__=='__main__':
    command,=sys.argv[1:];assert command in ('freeze','audit');value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    assert value==read(target)
    print('PASS NPN probe unchanged source1482 machinery24 baseline helpers2')
