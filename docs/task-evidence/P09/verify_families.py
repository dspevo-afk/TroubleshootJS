# P09 current-family normal admission regression; real menu input, no developer permission.
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
   result['cases']=[]
   families=['Indicator board','Protected indicator','Dual indicator','Timing board','BJT output driver','MOSFET output driver','Relay output reference','Procedural control board','Two-channel controller']
   for index,family in enumerate(families):
    page.goto(base+'/circuitjs.html?tsjVerifyLayout=true&tsjVerifyGeometry=true',wait_until='domcontentloaded',timeout=30000)
    page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'",timeout=30000)
    profile='MEDIUM' if family=='Two-channel controller' else 'EASY'
    page.locator('label.tsj-product-field').filter(has_text='Difficulty').locator('select').select_option(profile)
    page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option(label=family)
    page.get_by_text('Enter an exact seed',exact=True).click()
    page.get_by_label('Exact seed (signed decimal integer)',exact=True).fill('0')
    began=time.monotonic()
    page.get_by_role('button',name='Prepare exact seed',exact=True).click()
    page.wait_for_function("() => ['TICKET','ERROR'].includes(document.body.getAttribute('data-player-screen'))",timeout=120000)
    row={'family':family,'seed':'0','seconds':time.monotonic()-began,'screen':page.locator('body').get_attribute('data-player-screen')}
    result['cases'].append(row); save('progress.json',result)
    assert row['screen']=='TICKET',row
    page.get_by_role('button',name='Accept ticket and start',exact=True).click()
    page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'WORKBENCH'",timeout=30000)
    page.get_by_role('button',name='Board view',exact=True).click()
    page.get_by_role('button',name='View bottom copper',exact=True).click()
    page.get_by_role('button',name='View top copper',exact=True).wait_for(state='visible')
    page.get_by_role('button',name='Board view',exact=True).click()
    row['bottomFaceInput']=True
    for attr in ['data-tsj-verification','data-tsj-p09-report']:
     assert page.locator('html').get_attribute(attr) is None,'Developer metadata without debug: '+attr
    row['privacy']=True
    if index in [0,7,8]: page.screenshot(path=str(evidence/('family-'+str(index)+'.png')),full_page=True)
    print('PASS normal family',family,row['seconds'],flush=True)
   result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')
   assert len(result['cases'])==9 and not result['errors'],result
   result['outcome']='PASS'
  except BaseException:
   try:
    page.screenshot(path=str(evidence/'failure.png'),full_page=True)
    save('failure-dom.json',{'text':page.locator('body').inner_text()})
   except Exception: pass
   raise
  finally: context.close()
except BaseException:
 result['outcome']='FAIL'; result['failure']=traceback.format_exc(); print(result['failure'],flush=True)
finally:
 server.shutdown(); server.server_close(); thread.join(timeout=5)
 survivors=[item for item in process_snapshot() if any(item['pid']==before['pid'] and item['created']==before['created'] for before in owned)]
 result['cleanup']={'serverThreadStopped':not thread.is_alive(),'ownedSurvivors':survivors}
 save('result.json',result)
 sys.exit(0 if result['outcome']=='PASS' and not survivors and not thread.is_alive() else 1)
