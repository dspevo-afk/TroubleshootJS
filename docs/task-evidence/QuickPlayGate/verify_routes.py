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
thread=Thread(target=server.serve_forever,daemon=True); thread.start()
base='http://127.0.0.1:'+str(server.server_port)
identity={'pid':os.getpid(),'root':str(root),'port':server.server_port,'profile':str(evidence/'profile'),'started':time.time(),'nocacheSha256':hashlib.sha256((root/'war/circuitjs1/circuitjs1.nocache.js').read_bytes()).hexdigest()}
result={'mode':mode,'outcome':'NOT_RUN','errors':[],'httpErrors':[]}; owned=[]
def save(name,value): (evidence/name).write_text(json.dumps(value,indent=2),encoding='utf-8')
save('identity.json',identity)
def process_snapshot():
 command="Get-CimInstance Win32_Process | Select-Object @{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},ExecutablePath,CommandLine | ConvertTo-Json -Compress"
 return json.loads(subprocess.check_output(['powershell.exe','-NoProfile','-Command',command],text=True,encoding='utf-8',errors='replace',timeout=20))
def owned_processes():
 processes=process_snapshot(); descendants={os.getpid()}
 while True:
  expanded=descendants | {item['pid'] for item in processes if item['parent'] in descendants}
  if expanded==descendants: break
  descendants=expanded
 return [item for item in processes if item['pid'] in descendants and item['pid']!=os.getpid() and 'msedge.exe' in (item['ExecutablePath'] or '').lower()]
try:
 with sync_playwright() as p:
  context=p.chromium.launch_persistent_context(str(evidence/'profile'),channel='msedge',headless=True,viewport={'width':1440,'height':1000},timeout=30000,args=['--disable-background-timer-throttling','--disable-renderer-backgrounding'])
  try:
   owned=owned_processes()
   save('owned-processes.json',owned)
   page=context.pages[0]
   page.on('pageerror',lambda error:result['errors'].append(str(error)))
   page.on('response',lambda response:result['httpErrors'].append({'status':response.status,'url':response.url}) if response.status>=400 else None)
   page.on('console',lambda message:print('CONSOLE',message.type,message.text[:800],flush=True) if message.type=='error' else None)
   flags={'a10':'tsjVerifyA10=true','a10negative':'tsjVerifyA10=true&tsjA10Fail=true','rc':'tsjVerifyRc=true','p07':'tsjVerifyP07=true','negative':'tsjVerifyP07=true&tsjP07Fail=true','bench':'tsjVerifyP07=true&tsjP07Bench=true','layout':'tsjVerifyLayout=true&tsjVerifyGeometry=true','q15':'tsjVerifyQ15=true','q15negative':'tsjVerifyQ15=true&tsjQ15Fail=true'}
   started=time.monotonic()
   challenge='rc' if mode=='rc' else 'led'
   page.goto(base+'/circuitjs.html?tsjChallenge='+challenge+'&seed=3&tsjDebug=true&'+flags[mode],wait_until='domcontentloaded',timeout=30000)
   print('START',mode,base,flush=True)
   # Keep the existing 600-second whole-verifier bound. Do not mistake a
   # synchronous developer matrix for a failed 10-second DOM polling request.
   page.wait_for_function("() => /^(PASS|FAIL):/.test(document.documentElement.getAttribute('data-tsj-verification') || '')",timeout=600000)
   status=page.locator('html').get_attribute('data-tsj-verification')
   result.update(status=status,elapsedSeconds=time.monotonic()-started,browser=context.browser.version if context.browser else None)
   report=page.locator('html').get_attribute('data-tsj-q15-report' if mode.startswith('q15') else 'data-tsj-a10-report' if mode.startswith('a10') else 'data-tsj-p07-report')
   result['report']=json.loads(report) if report else None
   cleanup=page.locator('html').get_attribute('data-tsj-p07-cleanup')
   result['ownerCleanup']=json.loads(cleanup) if cleanup else None
   if mode=='layout':
    result['physicalEnvelope']=json.loads(page.locator('html').get_attribute('data-tsj-p09-report') or 'null')
    assert result['physicalEnvelope']['assertions']>=40 and result['physicalEnvelope']['normalRejectedBeforeMutation']
    assert result['physicalEnvelope']['twoLayerProduction'] is False and result['physicalEnvelope']['factoryLinksProduction'] is False
   if mode=='negative': assert result['ownerCleanup']=={'ownerRestored':True,'prototypeRetained':False}
   result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')
   if mode=='bench':
    targets=json.loads(page.locator('html').get_attribute('data-tsj-p07-targets'))
    save('targets.json',targets)
    page.screenshot(path=str(evidence/'00-top-copper.png'),full_page=True)
    page.get_by_role('button',name='DC V',exact=True).click()
    readings=[]
    def click_target(target,button='left'):
     canvas=max(page.locator('canvas:visible').all(),key=lambda item:item.bounding_box()['width']*item.bounding_box()['height'])
     box=canvas.bounding_box()
     x=box['x']+target['x']*box['width']/int(canvas.get_attribute('width'))
     y=box['y']+target['y']*box['height']/int(canvas.get_attribute('height'))
     page.mouse.click(x,y,button=button)
    for face in ['TOP','BOTTOM']:
     if face=='BOTTOM': page.get_by_role('button',name='View bottom copper',exact=True).click()
     ground=next(t for t in targets if t['kind']=='ground' and t['face']==face)
     click_target(ground,'right')
     if face=='TOP':
      # This symmetric frozen crossing has the same screen centre on both faces.
      # Its top conductor is 5 V; the required underside conductor is independently 7 V.
      crossing=next(t for t in targets if t['face']=='BOTTOM' and t['kind']=='trace')
      click_target(crossing)
      page.wait_for_function('() => document.querySelector(".tsj-meter-display").textContent === "5 V"',timeout=10000)
      readings.append({'face':face,'kind':'opposite-layer-crossing','reading':'5 V','expectedVolts':5})
     for target in [t for t in targets if t['face']==face and t['kind']!='ground']:
      click_target(target)
      expected=str(int(round(target['volts'])))+' V'
      page.wait_for_function('(expected) => document.querySelector(".tsj-meter-display").textContent === expected',arg=expected,timeout=10000)
      reading=page.locator('.tsj-meter-display').inner_text()
      readings.append({'face':face,'kind':target['kind'],'reading':reading,'expectedVolts':target['volts']})
     page.screenshot(path=str(evidence/('01-top-via-measurement.png' if face=='TOP' else '02-bottom-trace-measurement.png')),full_page=True)
    page.get_by_role('button',name='View top copper',exact=True).click()
    page.wait_for_function('() => document.querySelector(".tsj-meter-display").textContent === "--- V"',timeout=10000)
    page.screenshot(path=str(evidence/'03-flip-invalidates-hidden-contact.png'),full_page=True)
    result['measurements']=readings
    result['inputs']=['DC V','physical right-click ground','physical left-click each top via',
       'View bottom copper','physical right-click ground','physical left-click underside trace and vias',
       'View top copper','hidden-face contact invalidated']
    assert len(readings)==6,readings
    assert result['report']['prototypeRetained'] and result['report']['normalAdoption'] is False
   page.screenshot(path=str(evidence/(mode+'.png')),full_page=True)
   expected = 'FAIL:a10-explicit-failure-canary' if mode=='a10negative' else 'FAIL:q15:q15-explicit-failure-canary' if mode=='q15negative' else 'FAIL:P07 forced-negative' if mode=='negative' else 'PASS:'+('p07' if mode=='bench' else mode)
   assert status==expected, str(result)
   if mode=='q15negative':
    assert result['report']['status']=='FAIL' and result['report']['cases']==[] and result['report']['ownerRestored'], str(result)
   if mode=='a10negative':
    assert result['report'] is None and all('a10-explicit-failure-canary' in x for x in result['errors']),result
   else: assert not result['errors'],str(result['errors'])
   if mode=='a10':
    save('actual-a10.json',result['report'])
    reader=subprocess.run([sys.executable,str(root/'tests/contracts/a10_generation_report.py'),'--report',str(evidence/'actual-a10.json')],capture_output=True,text=True)
    save('reader.json',{'exit':reader.returncode,'stdout':reader.stdout,'stderr':reader.stderr})
    assert reader.returncode==0,reader.stdout+reader.stderr
   if mode=='p07': assert result['report']['ownerRestored'] and result['report']['normalAdoption'] is False
   save('dom.json',{'text':page.locator('body').inner_text(),'buttons':page.get_by_role('button').all_text_contents()})
   if mode=='q15': assert result['report']['status']=='PASS' and len(result['report']['cases'])==11 and result['report']['ownerRestored']
   result['outcome']='PASS'; print('PASS',mode,result['elapsedSeconds'],flush=True)
  except BaseException:
   try:
    result["failureMeter"] = page.locator(".tsj-meter-display").inner_text()
    page.screenshot(path=str(evidence/"failure.png"),full_page=True)
    save("failure-dom.json",{"text":page.locator("body").inner_text()})
   except Exception: pass
   raise
  finally:
   owned=list({(item["pid"],item["created"]):item for item in owned+owned_processes()}.values())
   save("owned-processes.json",owned)
   context.close()
except BaseException:
 result['outcome']='FAIL'; result['failure']=traceback.format_exc(); print(result['failure'],flush=True)
finally:
 server.shutdown(); server.server_close(); thread.join(timeout=5)
 survivors=[item for item in process_snapshot() if any(item['pid']==before['pid'] and item['created']==before['created'] for before in owned)]
 result['cleanup']={'serverThreadStopped':not thread.is_alive(),'ownedSurvivors':survivors}
 save('result.json',result)
 sys.exit(0 if result['outcome']=='PASS' and not survivors and not thread.is_alive() else 1)
