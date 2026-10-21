"""Audit the original relay control and the rejected fixed-stream experiment."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
PRIOR=HERE.parent/'packing-route-stream-probe-r1'
CONTROL=HERE.parent/'fresh-relay-stage-diagnostic-r1'
BASE='3460f1c9e26161d910249a850adb65f562fcffbe'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    old=runpy.run_path(str(PRIOR/'export_inputs.py'))
    value=old['record'](PRIOR/'export-frozen-source/private-inputs')
    assert value==read(PRIOR/'export-inputs.json');old['audit_archive'](value)
    baseline=read(PRIOR/'baseline-inputs.json')
    assert read(PRIOR/'publication.json')['remoteExactSha']==BASE
    assert read(PRIOR/'postpublication-execution.json')==baseline['expectedExecution']
    a=read(PRIOR/'assessment.json');assert a['observation']['status']=='NO_GO_PRIVATE_TARGET'
    advice=read(HERE/'source-advice.json')
    for n,h in advice['sourceBlobs'].items():
        assert subprocess.check_output(['git','hash-object',n],cwd=ROOT).decode().strip()==h
    manifest=read(CONTROL/'private-inputs.json')
    assert manifest['privateInputs']==baseline['controlPrivateInputs']
    assert {n:sha(CONTROL/'export-frozen-source/private-inputs'/n) for n in manifest['privateInputs']}==manifest['privateInputs']
    return {'baseCommit':BASE,'production':baseline['production'],
        'expectedExecution':baseline['expectedExecution'],'request':baseline['request'],
        'candidateInterpretation':'PRIVATE_OPTIONAL_STRIP_BEFORE_PAIRS@1;NOT_PRODUCTION_MODEL',
        'gates':{n:sha(HERE/n) for n in ('scope.md','source-advice.json','inputs.py')},
        'controlPrivateInputs':manifest['privateInputs'],
        'controlEvidence':baseline['controlEvidence'],
        'previousExperimentEvidence':{n:sha(PRIOR/n) for n in ('assessment.json','acceptance.json',
            'export-inputs.json','RESULTS.md','publication.json','postpublication-execution.json')}}
if __name__=='__main__':
    command,=sys.argv[1:];assert command in ('freeze','audit');value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();(HERE/'baseline-source').mkdir()
        for n in value['gates']:(HERE/'baseline-source'/n).write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target)
    assert {f.name:sha(f) for f in (HERE/'baseline-source').iterdir()}==value['gates']
    print('PASS strip-before-pairs baseline; original relay control and rejected stream exact')
