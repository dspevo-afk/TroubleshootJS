"""Freeze current production and exact fresh relay evidence before adaptation."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='1327ca3bd359d7ffc3ec4a9969d648ad211812e7'
FRESH=HERE.parent/'fresh-board-visual-r5'
BORROW=HERE.parent/'two-pad-span-probe-r1/export-frozen-source/private-inputs'
NAMES=('common/SparseNormalBoardDiagnostic.java','common/verify-two-pad-span.ps1',
       'control/SeededPcbLayoutGenerator.java','control/PcbPlacementPlanner.java','control/PcbPlacementCompactor.java')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    old=runpy.run_path(str(FRESH/'inputs.py'));assert old['runtime']()==read(FRESH/'runtime-inputs.json')
    baseline=read(FRESH/'baseline-inputs.json')
    assert read(FRESH/'publication.json')['remoteExactSha']==BASE
    assert read(FRESH/'postpublication-execution.json')==baseline['expectedExecution']
    a=read(FRESH/'assessment.json');assert a['status']=='VISUAL_OBSERVATIONS_COMPLETE_RESOURCE_CLEANUP_BLOCKED'
    assert a['boards'][2]['replay']=='tsj-alpha/6/EASY/RELAY_OUTPUT/-397178238694963708'
    for image in a['images']:assert sha(FRESH/image['path'])==image['sha256']
    advice=read(FRESH/'relay-source-review.json');assert advice['status']=='SOURCE_ONLY_NO_IMPLEMENTATION_GO'
    for n,h in advice['sourceBlobs'].items():assert subprocess.check_output(['git','hash-object',n],cwd=ROOT).decode().strip()==h
    probe=read(HERE.parent/'two-pad-span-probe-r1/private-inputs.json')
    assert {n:sha(BORROW/n) for n in NAMES}=={n:probe['privateInputs'][n] for n in NAMES}
    return {'baseCommit':BASE,'production':baseline['acceptedProductionAudit'],
        'expectedExecution':baseline['expectedExecution'],'request':a['boards'][2]['replay'],
        'gates':{n:sha(HERE/n) for n in ('scope.md','inputs.py')},
        'freshEvidence':{n:sha(FRESH/n) for n in ('assessment.json','root-review.json','runtime-inputs.json','publication.json','postpublication-execution.json','relay-source-review.json','attempt-2/public-identities.json','attempt-2/board-3-top.jpg','attempt-2/board-3-ticket-public.txt','attempt-2/board-3-off-public.txt')},
        'borrowedPrivateSources':{n:sha(BORROW/n) for n in NAMES},
        'limits':'Finite private relay observation; fresh visual cleanup stays BLOCKED; no access to any retained profile.'}
if __name__=='__main__':
    command,=sys.argv[1:];assert command in ('freeze','audit');value=record();target=HERE/'baseline-inputs.json'
    if command=='freeze':
        assert not target.exists();(HERE/'baseline-source').mkdir()
        for n in value['gates']:(HERE/'baseline-source'/n).write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target)
    assert {f.name:sha(f) for f in (HERE/'baseline-source').iterdir()}==value['gates']
    print('PASS exact fresh relay baseline source1482 machinery24 native13 player10 modules5 evidence and private5')
