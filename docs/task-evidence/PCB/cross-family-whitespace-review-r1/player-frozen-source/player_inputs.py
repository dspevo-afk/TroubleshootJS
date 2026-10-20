"""Bind fresh production captures to the unchanged accepted survey candidate."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
GATES=('player-plan.json','player_inputs.py','prepare_player_helpers.py','launch_player.ps1',
       'stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','player_bootstrap.mjs',
       'observe_browser.ps1','review_player.py')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    survey=runpy.run_path(str(HERE/'survey.py'))['survey']();assert survey==read(HERE/'survey.json')
    old=HERE.parent/'to92-molded-detail-r2'
    return {'surveySha256':sha(HERE/'survey.json'),'scopeSha256':sha(HERE/'scope.md'),
            'surveyHelperSha256':sha(HERE/'survey.py'),'gates':{n:sha(HERE/n) for n in GATES},
            'sourceReuseAudit':survey['sourceReuseAudit'],
            'borrowedPublishedHelpers':{n:sha(old/n) for n in ('launch_player.ps1','stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','player_bootstrap.mjs')},
            'expectedExecutionSha256':sha(old/'postpublication-execution.json')}
if __name__=='__main__':
    assert sys.argv[1:] in (['freeze'],['audit','candidate']);value=record();target=HERE/'player-inputs.json';archive=HERE/'player-frozen-source'
    if sys.argv[1]=='freeze':
        assert not target.exists() and not archive.exists()
        for n in GATES:
            dest=archive/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    assert value==read(target)
    assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob('*') if p.is_file()}==value['gates']
    print('PASS cross-family player unchanged source1482 machinery24 survey44 helpers10')
