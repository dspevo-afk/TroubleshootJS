"""Seal fresh player work to the accepted final-source gate set and actual build tree."""
import hashlib,json,re,runpy,subprocess,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
GATES=('player-plan.json','player_inputs.py','prepare_player_helpers.py','launch_player.ps1',
       'stop_player.ps1','observe_player_host.ps1','record_preview_exit.ps1','player_bootstrap.mjs',
       'observe_browser.ps1','review_player.py')
PHASES=('contracts','target','census','matrix','build')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record():
    candidate=read(HERE/'candidate-inputs.json')
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==candidate['baseCommit']
    assert {n:sha(ROOT/n) for n in candidate['files']}==candidate['files'] and len(candidate['files'])==1482
    assert {n:sha(ROOT/n) for n in candidate['machinery']}==candidate['machinery'] and len(candidate['machinery'])==24
    assert {n:sha(HERE/n) for n in candidate['gates']}==candidate['gates']
    accepted={}
    for phase in PHASES:
        p=HERE/(phase+'-assessment.json');r=read(p)
        assert r['status']=='PASS' and r['phase']==phase and r['candidateSealSha256']==sha(HERE/'candidate-inputs.json')
        accepted[phase]=sha(p)
    expected=read(HERE/'player-baseline-execution.json')
    assert set(expected)=={'Protocol','SourceDigest','ScriptDigest','WebDigest','Digest','FileCount'}
    module_root=ROOT/'war/circuitjs1'
    bootstrap=module_root/'circuitjs1.nocache.js'
    strong_names=sorted(set(re.findall(r'\b[A-F0-9]{32}\b',bootstrap.read_text(encoding='utf-8'))))
    assert len(strong_names)==5
    modules=read(HERE/'production-modules.json')
    assert modules=={'schema':'paired-production-modules@1','modules':[
        {'strongName':name,'sha256':sha(module_root/(name+'.cache.js')),
         'bytes':(module_root/(name+'.cache.js')).stat().st_size} for name in strong_names]}
    review=read(HERE/'player-preseal-review.json')
    assert review['status']=='CORRECTED_BEFORE_PLAYER_FREEZE_OR_EXECUTION'
    assert review['currentReviewSha256']==sha(HERE/'review_player.py')
    return {'candidateSealSha256':sha(HERE/'candidate-inputs.json'),'acceptedFinalGates':accepted,
            'expectedExecutionSha256':sha(HERE/'player-baseline-execution.json'),
            'productionModuleMapSha256':sha(HERE/'production-modules.json'),
            'productionBootstrapSha256':sha(bootstrap),
            'playerPresealReviewSha256':sha(HERE/'player-preseal-review.json'),
            'borrowedHelpers':runpy.run_path(str(HERE/'prepare_player_helpers.py'))['verify'](),
            'gates':{n:sha(HERE/n) for n in GATES},'scopeSha256':sha(HERE/'scope.md'),
            'interpretation':{'packing':4,'layout':19,'replay':'tsj-alpha/6'}}
if __name__=='__main__':
    assert sys.argv[1:] in (['freeze'],['audit','candidate']);value=record();target=HERE/'player-inputs.json';archive=HERE/'player-frozen-source'
    if sys.argv[1]=='freeze':
        assert not archive.exists();archive.mkdir()
        for n in GATES:(archive/n).write_bytes((HERE/n).read_bytes())
        with target.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    assert value==read(target)
    assert {p.name:sha(p) for p in archive.iterdir()}==value['gates']
    print('PASS player final packing4 source1482 machinery24 five accepted gates and ten sealed helpers')
