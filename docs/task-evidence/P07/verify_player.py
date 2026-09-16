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
   page.goto(base+'/circuitjs.html?tsjVerifyP07=true&tsjP07Bench=true&tsjP07Fail=true',wait_until='domcontentloaded',timeout=30000)
   page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'",timeout=30000)
   page.screenshot(path=str(evidence/'00-initial-menu.png'),full_page=True)
   save('initial-dom.json',{'text':page.locator('body').inner_text()})
   page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option(label='Procedural control board')
   page.get_by_text('Enter an exact seed',exact=True).click()
   page.get_by_label('Exact seed (signed decimal integer)',exact=True).fill('3')
   page.screenshot(path=str(evidence/'01-menu.png'),full_page=True)
   result['family']=page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').input_value()
   result['seed']='3'
   began=time.monotonic()
   page.get_by_role('button',name='Prepare exact seed',exact=True).click()
   page.wait_for_function("() => ['TICKET','ERROR'].includes(document.body.getAttribute('data-player-screen'))",timeout=120000)
   result['preparationSeconds']=time.monotonic()-began
   result['preparedScreen']=page.locator('body').get_attribute('data-player-screen')
   result['ticketText']=page.locator('[role=dialog][aria-modal=true]').inner_text()
   page.screenshot(path=str(evidence/'02-ticket.png'),full_page=True)
   assert result['preparedScreen']=='TICKET',result['ticketText']
   page.get_by_role('button',name='Accept ticket and start',exact=True).click()
   page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'WORKBENCH'",timeout=30000)
   page.screenshot(path=str(evidence/'03-board-front.png'),full_page=True)
   result['buttons']=page.get_by_role('button').all_text_contents()
   page.get_by_role('button',name='Board view',exact=True).click()
   page.get_by_role('button',name='View bottom copper',exact=True).click()
   page.get_by_role('button',name='View top copper',exact=True).wait_for(state='visible')
   page.get_by_role('button',name='Board view',exact=True).click()
   boxes=[item.bounding_box() for item in page.locator('canvas:visible').all()]
   box=max((item for item in boxes if item),key=lambda item:item['width']*item['height'])
   x=box['x']+box['width']*.55; y=box['y']+box['height']*.5
   page.mouse.move(x,y);page.mouse.down(button='middle');page.mouse.move(x+100,y+50,steps=10);page.mouse.up(button='middle')
   page.mouse.wheel(0,-160);page.wait_for_timeout(500)
   page.screenshot(path=str(evidence/'04-copper-navigation.png'),full_page=True)
   result['finalScreen']=page.locator('body').get_attribute('data-player-screen')
   result['browser']=context.browser.version if context.browser else None
   result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')
   result['inputs']=['normal menu','family selection','exact seed 3','prepare','accept ticket','flip to bottom copper','middle-drag pan','wheel zoom']
   for attribute in ['data-tsj-verification','data-tsj-p07-report','data-tsj-p07-targets','data-tsj-p07-cleanup']:
    assert page.locator('html').get_attribute(attribute) is None, 'Developer metadata escaped without tsjDebug: '+attribute
   result['p07FlagsIgnoredWithoutDebug']=True
   assert result['finalScreen']=='WORKBENCH',result
   assert not result['errors'],result['errors']
   result['outcome']='PASS'
   print('PASS normal player',result['preparationSeconds'],flush=True)
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
