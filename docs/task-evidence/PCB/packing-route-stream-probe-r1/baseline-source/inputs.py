"""Audit the accepted relay control before the finite optional-stream experiment."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
PRIOR=HERE.parent/'fresh-relay-stage-diagnostic-r1'
BASE='be7098819b3bdba15012a7f8d6869645bca0ca58'
TUPLE={'derivationVersion':1,'root':'session.routingSeed','intent':'pcb-packing-route',
       'intentVersion':1,'scope':'device','blockKey':'-','concern':'routing',
       'concernRevision':1,'semanticKey':'optional-p05-proposal'}
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    old=runpy.run_path(str(PRIOR/'export_inputs.py'))
    assert old['record'](PRIOR/'export-frozen-source/private-inputs')==read(PRIOR/'export-inputs.json')
    baseline=read(PRIOR/'baseline-inputs.json')
    assert read(PRIOR/'publication.json')['remoteExactSha']==BASE
    assert read(PRIOR/'postpublication-execution.json')==baseline['expectedExecution']
    assessment=read(PRIOR/'assessment.json')
    assert assessment['status']=='PASS_FINITE_PRIVATE_RELAY_STAGE_DIAGNOSIS'
    advice=read(HERE/'source-advice.json')
    for n,h in advice['sourceBlobs'].items():
        assert subprocess.check_output(['git','hash-object',n],cwd=ROOT).decode().strip()==h
    for n,k in [('RESULTS.md','priorResultBlob'),('assessment.json','priorAssessmentBlob')]:
        assert subprocess.check_output(['git','hash-object',str(PRIOR/n)],cwd=ROOT).decode().strip()==advice[k]
    names=('baseline-inputs.json','export-inputs.json','acceptance.json','assessment.json',
           'relay/receipt.txt','relay/native.txt','relay/timing.json','relay/process-cleanup.json',
           'publication.json','postpublication-execution.json','qualify.py','review_export.py','source-review.json')
    return {'baseCommit':BASE,'production':baseline['production'],
        'expectedExecution':baseline['expectedExecution'],'request':baseline['request'],
        'gates':{n:sha(HERE/n) for n in ('scope.md','inputs.py','source-advice.json')},
        'controlPrivateInputs':read(PRIOR/'private-inputs.json')['privateInputs'],
        'controlEvidence':{n:sha(PRIOR/n) for n in names},'derivation':TUPLE,
        'candidateInterpretation':'PRIVATE_OPTIONAL_P05_STREAM@1;NOT_PRODUCTION_MODEL'}
if __name__=='__main__':
    command,=sys.argv[1:];assert command in ('freeze','audit');value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();(HERE/'baseline-source').mkdir()
        for n in value['gates']:(HERE/'baseline-source'/n).write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target)
    assert {f.name:sha(f) for f in (HERE/'baseline-source').iterdir()}==value['gates']
    print('PASS fixed optional stream baseline; accepted source and relay control exact')
