"""Check raw normal-player artifacts and exact final build/resource binding."""
import hashlib,json,re,runpy
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
REQUIRED=('rc-new-board-request','rc-first-generated-identity','rc-original-fail-off','rc-installed-lid-view',
          'rc-capacitor-loose','rc-capacitor-original-restored','led-original-fail-off','led-isolated-ol',
          'led-shop-installed','led-off-330','led-fresh-customer-pass','led-final-off')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    assert runpy.run_path(str(HERE/'player_inputs.py'))['record']()==read(HERE/'player-inputs.json')
    expected=read(HERE/'player-expected-execution.json')
    assert set(expected)=={'Protocol','SourceDigest','ScriptDigest','WebDigest','Digest','FileCount'}
    assert expected==read(HERE/'post-player-provenance.json')==read(HERE/'served-execution-before.json')==read(HERE/'served-execution-after.json')
    for side in ('before','after'):
        assert (HERE/('player-input-audit-'+side+'.txt')).read_text(encoding='utf-8-sig').strip()=='PASS final capacitor lid player source1482 machinery24 helpers8'
        owner=read(HERE/('preview-owner-'+side+'.json'))
        assert owner['status']=='PASS' and owner['rootScriptCommandBirthExact'] is True and owner['servedIdentityEqualsIndependentProjection'] is True and owner['http200'] is True and owner['port']==8907
    a=read(HERE/'preview-owner-before.json');b=read(HERE/'preview-owner-after.json')
    assert (a['pid'],a['startTicks'])==(b['pid'],b['startTicks'])
    events=read(HERE/'player-events.json')
    for name in REQUIRED:assert len([e for e in events if e['name']==name and e['status']=='PASS'])==1,name
    assert len([e for e in events if e['name']=='rc-new-board-request'])==1
    failures=[e for e in events if e['status']=='FAIL']
    for event in events:assert event['status'] in ('PASS','FAIL') and type(event['elapsedMillis']) is int and event['elapsedMillis']>=0
    artifacts=read(HERE/'player-artifacts.json')
    forbidden=('normal-packing@','protectedVoid','generationRoutingExpansions','Injected fault:','Hidden fault:','Answer key:','tsjDebug=')
    for record in artifacts['bodies']:
        file=HERE/record['file'];raw=file.read_bytes();assert sha(file)==record['sha256'] and len(raw)==record['bytes']
        assert record['file'].endswith('-public.txt')
        assert not any(token in raw.decode('utf-8') for token in forbidden),record['file']
    planned=read(HERE/'player-plan.json')['primaryImages'];assert [r['file'] for r in artifacts['images']]==planned
    reviewed=read(HERE/'visual-review.json');assert reviewed['status']=='PASS' and set(reviewed['images'])==set(planned)
    for record in artifacts['images']:
        file=HERE/record['file'];assert sha(file)==record['sha256'] and file.stat().st_size==record['bytes'] and file.read_bytes().startswith(b'\xff\xd8\xff')
        assert reviewed['images'][record['file']]['sha256']==record['sha256'] and reviewed['images'][record['file']]['judgment']=='PASS'
    rc=read(HERE/'rc-public-identity.json');led=read(HERE/'led-public-identity.json')
    fields=rc['replay'].split('/');assert len(fields)==5 and fields[:4]==['tsj-alpha','6','EASY','RC_DELAY'] and str(int(fields[4]))==fields[4] and -(1<<63)<=int(fields[4])<(1<<63)
    assert led['replay']==read(HERE/'player-plan.json')['ledReplay']
    assert rc['build']==led['build'] and rc['build'].startswith('tsj-alpha/6 / ') and re.fullmatch('[A-F0-9]{32}',rc['build'].split(' / ')[1])
    cleanup=read(HERE/'player-resource-cleanup.json')
    assert cleanup['status']=='PASS' and cleanup['browser']['apiClose'] is True and cleanup['browser']['processExitCode']==0 and cleanup['browser']['profileAbsent'] is True and cleanup['browser']['recordedBirthExited'] is True
    assert cleanup['preview']['stateAbsent'] is True and cleanup['preview']['portReleased'] is True and cleanup['preview']['recordedBirthExited'] is True
    for prefix in ('preview','preview-stop'):
        launch=read(HERE/(prefix+'-process-launch.json'));closed=read(HERE/(prefix+'-process-cleanup.json'))
        assert closed['status']=='PASS' and type(closed['actualExecExitCode']) is int and closed['actualExecExitCode']==0 and closed['launchSha256']==sha(HERE/(prefix+'-process-launch.json'))
        assert len(closed['checks'])==len(launch['drivers'])==2 and all(c['recordedBirthExited'] is True for c in closed['checks'])
        assert [{k:c[k] for k in ('pid','startTicks','executable')} for c in closed['checks']]==launch['drivers']
    result={'status':'PASS_FINAL_PRODUCTION_CAPACITOR_LID_PLAYER','inputSealSha256':sha(HERE/'player-inputs.json'),'events':len(events),'resolvedObserverFailures':failures,'rcPublicIdentity':rc,'ledPublicIdentity':led,'images':planned,'bodyCount':len(artifacts['bodies']),'execution':expected,'limits':'Actual finite normal UI and raw screenshot evidence, root visual review, fresh PASS modal awaited from hidden state. Untouched FAIL banner has no separate public freshness token. No all-seed/manufacturing/optimality/performance/Edge/CDP/FileReader/full-matrix claim; failed V1 reader control preserved.'}
    target=HERE/'player-assessment.json';assert not target.exists();target.write_text(json.dumps(result,indent=2)+'\n')
    print('PASS final production capacitor lid player/identity/artifacts/normal owned cleanup; observer failures='+str(len(failures)))
if __name__=='__main__':main()
