"""Qualify finite normal screenshot observations after explicit root review."""
import hashlib,json,runpy
from pathlib import Path
HERE=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
if __name__=='__main__':
    assert runpy.run_path(str(HERE/'player_inputs.py'))['record']()==read(HERE/'player-inputs.json')
    expected=read(HERE.parent/'to92-molded-detail-r2/postpublication-execution.json')
    assert read(HERE/'player-expected-execution.json')==read(HERE/'post-player-provenance.json')==expected
    for side in ('before','after'):
        owner=read(HERE/('preview-owner-'+side+'.json'));assert owner['status']=='PASS' and owner['servedIdentityEqualsIndependentProjection'] is True
    assert read(HERE/'served-execution-before.json')==read(HERE/'served-execution-after.json')==expected
    events=read(HERE/'player-events.json');assert events and all(x['status']=='PASS' for x in events)
    plan=read(HERE/'player-plan.json')
    for name,replay in plan['replays'].items():
        identity=read(HERE/(name+'-public-identity.json'));assert identity['replay']==replay and '347032FDADA50729040B5D58ADBC4063' in identity['build']
        final=(HERE/(name+'-off-public.txt')).read_text(encoding='utf-8-sig')
        assert 'Board Power: OFF' in final and 'Customer retest did not pass' in final
    images=read(HERE/'player-artifacts.json')['images'];assert 2<=len(images)<=5
    for item in images:assert sha(HERE/item['file'])==item['sha256']
    review=read(HERE/'visual-review.json');assert review['status']=='PASS' and len(review['images'])==len(images)
    cleanup=read(HERE/'player-resource-cleanup.json');assert cleanup['status']=='PASS'
    for prefix in ('preview','preview-stop'):
        driver=read(HERE/(prefix+'-process-cleanup.json'));assert driver['status']=='PASS' and driver['actualExecExitCode']==0 and all(x['recordedBirthExited'] for x in driver['checks'])
    target=HERE/'player-assessment.json';assert not target.exists()
    target.write_text(json.dumps({'status':'PASS_FINITE_NORMAL_REPLAY_VISUAL_REVIEW','inputSealSha256':sha(HERE/'player-inputs.json'),'events':len(events),'images':images,'visualReviewSha256':sha(HERE/'visual-review.json'),'cleanupSha256':sha(HERE/'player-resource-cleanup.json'),'limits':'Three exact normal replay observations, unchanged production source. No new electrical/repair/size/all-seed/full-matrix claim. Repeated FAIL banner has no independent freshness token; prior blocked profiles preserved.'},indent=2)+'\n',encoding='utf-8')
    print('PASS finite three normal replay screenshots/source/identity/owned cleanup')
