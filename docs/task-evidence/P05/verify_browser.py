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
result={'mode':mode,'outcome':'NOT_RUN','errors':[]}; owned=[]
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
   page.on('console',lambda message:print('CONSOLE',message.type,message.text[:800],flush=True) if message.type=='error' else None)
   flags={'layout':'tsjVerifyLayout=true&tsjVerifyGeometry=true','q15':'tsjVerifyQ15=true'}
   started=time.monotonic()
   page.goto(base+'/circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&'+flags[mode],wait_until='domcontentloaded',timeout=30000)
   print('START',mode,base,flush=True)
   # Keep the existing 600-second whole-verifier bound. Do not mistake a
   # synchronous developer matrix for a failed 10-second DOM polling request.
   page.wait_for_function("() => /^(PASS|FAIL):/.test(document.documentElement.getAttribute('data-tsj-verification') || '')",timeout=600000)
   status=page.locator('html').get_attribute('data-tsj-verification')
   result.update(status=status,elapsedSeconds=time.monotonic()-started,browser=context.browser.version if context.browser else None)
   report=page.locator('html').get_attribute('data-tsj-q15-report') if mode=='q15' else None
   result['report']=json.loads(report) if report else None
   result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')
   page.screenshot(path=str(evidence/(mode+'.png')),full_page=True)
   assert status=='PASS:'+mode, str(result)
   assert not result['errors'], str(result['errors'])
   if mode=='q15': assert result['report']['status']=='PASS' and len(result['report']['cases'])==11 and result['report']['ownerRestored']
   result['outcome']='PASS'; print('PASS',mode,result['elapsedSeconds'],flush=True)
  finally: context.close()
except BaseException:
 result['outcome']='FAIL'; result['failure']=traceback.format_exc(); print(result['failure'],flush=True)
finally:
 server.shutdown(); server.server_close(); thread.join(timeout=5)
 survivors=[item for item in process_snapshot() if any(item['pid']==before['pid'] and item['created']==before['created'] for before in owned)]
 result['cleanup']={'serverThreadStopped':not thread.is_alive(),'ownedSurvivors':survivors}
 save('result.json',result)
 sys.exit(0 if result['outcome']=='PASS' and not survivors and not thread.is_alive() else 1)
