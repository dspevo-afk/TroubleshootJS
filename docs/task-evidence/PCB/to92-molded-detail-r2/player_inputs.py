"""Bind visible player work to the accepted final-source build and helpers."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
GATES=('player-plan.json','player_inputs.py','launch_player.ps1','stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','player_bootstrap.mjs','review_player.py')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    assert runpy.run_path(str(HERE/'candidate_inputs.py'))['record']()==read(HERE/'candidate-inputs.json')
    for phase in ('contracts','census','build'):assert read(HERE/(phase+'-assessment.json'))['status']=='PASS'
    return {'candidateSealSha256':sha(HERE/'candidate-inputs.json'),'gates':{n:sha(HERE/n) for n in GATES},
            'acceptedFinalGates':{p:sha(HERE/(p+'-assessment.json')) for p in ('contracts','census','build')}}
def main():
    assert sys.argv[1:] in (['freeze'],['audit','candidate'])
    value=record();target=HERE/'player-inputs.json'
    if sys.argv[1]=='freeze':
        assert not target.exists();archive=HERE/'player-source';archive.mkdir()
        for n in GATES:(archive/n).write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert value==read(target)
    assert {p.name:sha(p) for p in (HERE/'player-source').iterdir()}==value['gates']
    print('PASS final TO92 molded player source1482 machinery24 helpers8')
if __name__=='__main__':main()
