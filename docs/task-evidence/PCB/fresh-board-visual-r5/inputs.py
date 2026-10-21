"""Audit accepted production and seal fresh visible sampling helpers."""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
BASE='3278a79cd9893e2c41a6304c513cb1b7753bf29a'
OLD=HERE.parent/'paired-packing-r2'
NAMES=('launch_player.ps1','stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','observe_browser.ps1','player_bootstrap.mjs')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    assert subprocess.check_output(['git','branch','--show-current'],cwd=ROOT).decode().strip()=='codex/q60-faulted-profile-counts'
    old=runpy.run_path(str(HERE.parent/'random-composed-whitespace-r1/inputs.py'))['record']()
    return {'baseCommit':BASE,'acceptedProductionAudit':old,'expectedExecution':old['expectedExecution'],
            'gates':{n:sha(HERE/n) for n in ('scope.md','inputs.py')},
            'borrowedHelpers':{n:sha(OLD/n) for n in NAMES}}
def runtime():
    value=record();assert value==read(HERE/'baseline-inputs.json')
    assert {f.name:sha(f) for f in (HERE/'baseline-source').iterdir()}==value['gates']
    for n in NAMES:
        source=(OLD/n).read_bytes()
        if n=='observe_player_host.ps1':
            old=b"'player_inputs.py') audit candidate";new=b"'inputs.py') audit"
            assert source.count(old)==1;source=source.replace(old,new)
        assert (HERE/n).read_bytes()==source,n
    return {'baselineSealSha256':sha(HERE/'baseline-inputs.json'),'helpers':{n:sha(HERE/n) for n in NAMES}}
def main():
    command,=sys.argv[1:]
    if command=='freeze-baseline':
        assert not (HERE/'baseline-inputs.json').exists();(HERE/'baseline-source').mkdir()
        value=record()
        for n in value['gates']:(HERE/'baseline-source'/n).write_bytes((HERE/n).read_bytes())
        (HERE/'baseline-inputs.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    elif command=='freeze-runtime':
        value=runtime();assert not (HERE/'runtime-inputs.json').exists();(HERE/'runtime-source').mkdir()
        for n in NAMES:(HERE/'runtime-source'/n).write_bytes((HERE/n).read_bytes())
        (HERE/'runtime-inputs.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    elif command=='audit':
        value=runtime();assert value==read(HERE/'runtime-inputs.json')
        assert {f.name:sha(f) for f in (HERE/'runtime-source').iterdir()}==value['helpers']
    else:raise ValueError(command)
    print('PASS fresh sampling source1482 machinery24 accepted native13 player10 modules5 sealed helpers')
if __name__=='__main__':main()
