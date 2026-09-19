from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from threading import Thread
from playwright.sync_api import sync_playwright
import sys, json, time, os, hashlib, traceback, subprocess
root=Path(sys.argv[1]); evidence=Path(sys.argv[2]); mode=sys.argv[3]
evidence.mkdir(parents=True,exist_ok=False)
class Handler(SimpleHTTPRequestHandler):
    def log_message(self,*args): pass
server=ThreadingHTTPServer(('127.0.0.1',0),partial(Handler,directory=str(root/'war')))
thread=Thread(target=server.serve_forever,daemon=True);thread.start()
base='http://127.0.0.1:'+str(server.server_port)
result={'mode':mode,'outcome':'NOT_RUN','errors':[],'cases':[]};owned=[]
def save(name,obj): (evidence/name).write_text(json.dumps(obj,indent=2),encoding='utf-8')
def processes():
    q="Get-CimInstance Win32_Process | Select-Object @{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},ExecutablePath,CommandLine | ConvertTo-Json -Compress"
    p=subprocess.run(['powershell.exe','-NoProfile','-Command',q],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=20)
    if p.returncode!=0: raise RuntimeError('Process identity query failed: '+p.stderr)
    return json.loads(p.stdout)
def owned_processes():
    rows=processes();pids={os.getpid()}
    while True:
        expanded=pids | {p['pid'] for p in rows if p['parent'] in pids}
        if expanded==pids: break
        pids=expanded
    return [p for p in rows if p['pid'] in pids and 'msedge.exe' in (p['ExecutablePath'] or '').lower()]
save('identity.json',{'pid':os.getpid(),'started':time.time(),'root':str(root),'port':server.server_port,'profile':str(evidence/'profile'),'nocacheSha256':hashlib.sha256((root/'war/circuitjs1/circuitjs1.nocache.js').read_bytes()).hexdigest()})
try:
    with sync_playwright() as pw:
        context=pw.chromium.launch_persistent_context(str(evidence/'profile'),channel='msedge',headless=True,viewport={'width':1440,'height':1000},args=['--disable-background-timer-throttling','--disable-renderer-backgrounding'])
        try:
            owned=owned_processes();save('owned-processes.json',owned)
            page=context.pages[0]
            page.on('pageerror',lambda e:result['errors'].append(str(e)))
            logs=[];page.on('console',lambda m:logs.append({'type':m.type,'text':m.text}) if m.type=='error' or 'generation_failure' in m.text else None)
            def click(name): page.get_by_role('button',name=name,exact=True).click(timeout=15000)
            def state(): return page.evaluate('window.tsjProduct.snapshot(false)')
            def settled(): page.wait_for_function('() => window.tsjProduct.snapshot(false).ready',timeout=30000)
            def watch():
                began=time.monotonic();samples=[]
                while time.monotonic()-began<115:
                    s=state()
                    if s.get('progress'):samples.append(s['progress'])
                    if s['screen'] in ['TICKET','ERROR']:return s,samples,time.monotonic()-began
                    page.wait_for_timeout(150)
                raise AssertionError('Board preparation exceeded external observation bound')
            def menu():
                page.goto(base+'/circuitjs.html',wait_until='domcontentloaded')
                page.wait_for_function("() => window.tsjProduct && document.body.getAttribute('data-player-screen')==='MENU'",timeout=30000)
            def accept():
                click('Accept ticket and start')
                page.wait_for_function("() => document.body.getAttribute('data-player-screen')==='WORKBENCH'",timeout=30000);settled()
            if mode=='normal':
                families=['LED_INDICATOR','DIODE_PROTECTED_INDICATOR','PARALLEL_DUAL_INDICATOR','RC_DELAY','NPN_LOW_SIDE_SWITCH','NMOS_LOW_SIDE_SWITCH','RELAY_OUTPUT','RB15_CONTROL','COMPOSED_CONTROLLED_INDICATOR']
                previous={}
                for index,(family,seed) in enumerate((f,s) for f in families for s in ['0','17']):
                    menu();initial=state()
                    assert len(initial['families'])==9 and all(f['procedural'] for f in initial['families']),initial
                    profile='MEDIUM' if family=='COMPOSED_CONTROLLED_INDICATOR' else 'EASY'
                    page.locator('label.tsj-product-field').filter(has_text='Difficulty').locator('select').select_option(profile)
                    page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option(family)
                    data=list((int(seed)&((1<<64)-1)).to_bytes(8,'big'))
                    page.evaluate('(b)=>{const original=crypto.getRandomValues.bind(crypto);crypto.getRandomValues=function(a){if(a instanceof Uint8Array && a.length===8){a.set(b);return a;}return original(a);};}',data)
                    began=time.monotonic();click('New board');s,samples,seconds=watch()
                    row={'family':family,'launchSeed':seed,'profile':profile,'state':s,'seconds':seconds,'progressSamples':samples}
                    result['cases'].append(row);save('progress.json',result)
                    assert s['screen']=='TICKET',row
                    assert s['replay'].startswith('tsj-alpha/3/'+profile+'/'+family+'/'),s
                    accepted=int(s['replay'].rsplit('/',1)[1])
                    candidates=[((int(seed)+0x9e3779b97f4a7c15*i+(1<<63))%(1<<64))-(1<<63) for i in range(4)]
                    assert accepted in candidates and all(p['candidates']==4 and p['units']<=640 for p in samples),row
                    row['noIntermediateProgressSample']=not any(p['percent']>0 for p in samples)
                    # A sub-second success can finish between initial0% and the
                    # next poll. Longer preparations still must show progress.
                    if seconds>=1: assert not row['noIntermediateProgressSample'],row
                    accept();row['workbench']=True
                    for attr in ['data-tsj-verification','data-tsj-a08-report','data-tsj-quickplay-gate']:
                        assert page.locator('html').get_attribute(attr) is None,'Private debug metadata in normal player'
                    row['privacy']=True
                    if family=='RELAY_OUTPUT':
                        page.screenshot(path=str(evidence/('relay-'+seed+'-top.png')))
                        click('Board view');click('View bottom copper');click('Board view')
                        page.screenshot(path=str(evidence/('relay-'+seed+'-bottom.png')))
                        click('Board view');click('View top copper');click('Board view')
                    previous.setdefault(family,[]).append(s['replay'])
                    print('PASS normal',family,seed,'accepted',accepted,round(seconds,2),flush=True)
                assert all(len(set(v))==2 for v in previous.values()),previous
                for family in ['RELAY_OUTPUT','COMPOSED_CONTROLLED_INDICATOR']:
                    replay=previous[family][-1];menu()
                    page.get_by_text('Open a saved replay code',exact=True).click()
                    page.get_by_label('Open current replay',exact=True).fill(replay);click('Prepare replay')
                    s,samples,seconds=watch();assert s['screen']=='TICKET' and s['replay']==replay,s
                    assert all(p['candidates']==1 for p in samples),samples
                    accept();result.setdefault('replays',[]).append({'replay':replay,'seconds':seconds,'exact':True})
                # A real shop purchase on the final settled, isolated player board.
                click('Board Power: ON');page.wait_for_function('() => { const s=window.tsjProduct.snapshot(false);return s.ready && s.isolated; }',timeout=30000)
                replay=state()['replay'];click('Shop')
                count=lambda:sum(c['looseCount'] for c in page.evaluate('window.tsjProduct.snapshot(true)').get('catalogs',[]))
                before=count()
                page.locator('.tsj-store-card').filter(has_text='Resistors').first.get_by_role('button').click()
                page.wait_for_function('(n) => window.tsjProduct.snapshot(true).catalogs.reduce((s,c)=>s+c.looseCount,0)===n+1',arg=before,timeout=20000)
                assert state()['replay']==replay
                result['shop']={'before':before,'after':count(),'sameBoard':True,'isolated':state()['isolated']}
                page.screenshot(path=str(evidence/'shop-purchased.png'));click('Close')
                assert len(result['cases'])==18 and len(result['replays'])==2
                retained=state()['replay'];click('Main menu')
                page.locator('label.tsj-product-field').filter(has_text='Difficulty').locator('select').select_option('EASY')
                page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option('RC_DELAY')
                click('New board')
                page.wait_for_function("() => document.body.getAttribute('data-player-screen')==='PREPARING'",timeout=10000)
                click('Cancel preparation')
                page.wait_for_function("() => document.body.getAttribute('data-player-screen')==='WORKBENCH'",timeout=30000)
                settled();assert state()['replay']==retained and state()['isolated'],state()
                result['normalCancellation']={'sameReplay':True,'isolated':True}
                click('Main menu');page.get_by_text('Open a saved replay code',exact=True).click()
                page.get_by_label('Open current replay',exact=True).fill(retained.replace('tsj-alpha/3/','tsj-alpha/2/'))
                click('Prepare replay');page.wait_for_timeout(200)
                assert state()['screen']=='MENU' and state()['replay']==retained,state()
                assert 'Unsupported replay' in page.locator('[role=dialog][aria-modal=true]').inner_text()
                result['oldEpochRejected']=True
                reported='tsj-alpha/3/EASY/RELAY_OUTPUT/4'
                page.get_by_label('Open current replay',exact=True).fill(reported);click('Prepare replay')
                s,samples,seconds=watch();assert s['screen']=='TICKET' and s['replay']==reported,s
                assert all(p['candidates']==1 for p in samples),samples
                accept();result['reportedRelay']={'replay':reported,'seconds':seconds,'workbench':True}
                page.screenshot(path=str(evidence/'reported-relay-4.png'))
            else:
                flags={'a08':'tsjVerifyA08=true','a08negative':'tsjVerifyA08=true&tsjA08Fail=true','e03':'tsjVerifyE03=true','e03negative':'tsjVerifyE03=true&tsjE03Fail=true','a10':'tsjVerifyA10=true'}
                page.goto(base+'/circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&'+flags[mode],wait_until='domcontentloaded')
                page.wait_for_function("() => /^(PASS|FAIL):/.test(document.documentElement.getAttribute('data-tsj-verification') || '')",timeout=600000)
                status=page.locator('html').get_attribute('data-tsj-verification')
                attrs=page.evaluate('Object.fromEntries([...document.documentElement.attributes].filter(a=>a.name.startsWith("data-tsj")).map(a=>[a.name,a.value]))')
                result['status']=status;result['attributes']=attrs
                print('VERIFIER',mode,status,flush=True)
                save('observed.json',result)
                if mode.endswith('negative'):
                    assert status.startswith('FAIL:') and 'explicit-failure-canary' in status,status
                else:
                    assert status=='PASS:'+mode,status
                    assert not result['errors'],result['errors']
                    if mode=='a08':
                        report=json.loads(attrs['data-tsj-a08-report'])
                        assert report['status']=='PASS' and report['cleanup']=='PASS'
                        assert any(c['case']=='resistor-shop-unformed-rollback-and-electrical-repair' for c in report['cases']),report
                page.screenshot(path=str(evidence/(mode+'.png')))
            result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')
            result['console']=logs
            if not mode.endswith('negative'): assert not result['errors'],result['errors']
            result['outcome']='PASS'
        except BaseException:
            try:
                save('failure-dom.json',{'body':page.locator('body').inner_text(),'logs':logs})
                page.screenshot(path=str(evidence/'failure.png'))
            except Exception: pass
            raise
        finally:
            owned=list({(r['pid'],r['created']):r for r in owned+owned_processes()}.values());save('owned-processes.json',owned)
            context.close()
except BaseException:
    result['outcome']='FAIL';result['failure']=traceback.format_exc();print(result['failure'],flush=True)
finally:
    server.shutdown();server.server_close();thread.join(timeout=5)
    survivors=[r for r in processes() if any(r['pid']==p['pid'] and r['created']==p['created'] for p in owned)]
    result['cleanup']={'serverThreadStopped':not thread.is_alive(),'ownedSurvivors':survivors}
    save('result.json',result)
    print('FINISHED',mode,result['outcome'],flush=True)
    sys.exit(0 if result['outcome']=='PASS' and not survivors and not thread.is_alive() else 1)
