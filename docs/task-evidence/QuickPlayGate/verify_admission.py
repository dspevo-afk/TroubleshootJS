from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from threading import Thread
from playwright.sync_api import sync_playwright
import sys, json, time, os, hashlib, traceback, subprocess
root=Path(sys.argv[1]); evidence=Path(sys.argv[2]); mode='admission'; specPath=Path(sys.argv[3])
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
   spec=json.loads(specPath.read_text(encoding='utf-8-sig'))
   result['cases']=[]
   result['browser']=context.browser.version if context.browser else None
   result['scope']='Actual normal admission classifications; expected rejection is not a playable board.'
   page.goto(base+'/circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQuickPlayGate=true',wait_until='domcontentloaded',timeout=30000)
   page.wait_for_function("() => window.tsjQuickPlayGate && JSON.parse(document.documentElement.getAttribute('data-tsj-quickplay-gate') || '{}').status === 'READY'",timeout=60000)
   for index,case in enumerate(spec['cases']):
    began=time.monotonic()
    seed=str(case['seed']); search=bool(case.get('search',False)); cancel=int(case.get('cancelAfter',-1))
    page.evaluate('(c) => window.tsjQuickPlayGate.run(c.seed,c.search,c.cancelAfter)',{'seed':seed,'search':search,'cancelAfter':cancel})
    page.wait_for_function("() => ['COMPLETE','FAIL'].includes(JSON.parse(document.documentElement.getAttribute('data-tsj-quickplay-gate') || '{}').status)",timeout=115000)
    row=json.loads(page.locator('html').get_attribute('data-tsj-quickplay-gate'))
    row['cohort']=case.get('cohort','pilot'); row['wallSeconds']=time.monotonic()-began
    result['cases'].append(row); save('cases.json',result['cases'])
    print('CASE',index+1,'/',len(spec['cases']),seed,row.get('outcome'),round(row['wallSeconds'],3),flush=True)
    assert row['status']=='COMPLETE' and row['ownerRestored'] and row['sequence']==index+1,row
    assert row['requestedSeed']==seed and row['units']<=640 and len(row['attempts'])<=(4 if search else 1),row
    if 'expected' in case: assert row['outcome']==case['expected'],row
    if cancel>=0: assert row['outcome']=='CANCELLED',row
    if row['outcome']=='PASS':
     def signed(n):
      n=n&((1<<64)-1)
      return n-(1<<64) if n>>63 else n
     candidates=[str(signed(int(seed)+0x9e3779b97f4a7c15*i)) for i in range(4 if search else 1)]
     assert row['board']['seed'] in candidates and row['replay'].endswith('/'+row['board']['seed'])
     assert row['hypotheses']==3 and row['proofUnits']>0 and row['attempts'][-1]['outcome']=='PASS',row
     if case.get('native'):
      for key in ['width','height','design','parts']: assert row['board'][key]==case['native'][key],(key,row,case)
    else: assert row['outcome'] in ['EXPECTED_REJECTION','TIMEOUT','WORK_EXHAUSTED','CANCELLED'],row
   result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')
   result['accepted']=sum(row['outcome']=='PASS' for row in result['cases'])
   result['rejected']=len(result['cases'])-result['accepted']
   assert not result['errors'] and not result['httpErrors'],result
   result['outcome']='PASS'
   print('PASS admission runner',result['accepted'],'accepted of',len(result['cases']),flush=True)
  finally:
   final_owned=owned_processes()
   owned=list({(item['pid'],item['created']):item for item in owned+final_owned}.values())
   save('owned-processes.json',owned)
   context.close()

except BaseException:
 result['outcome']='FAIL'; result['failure']=traceback.format_exc(); print(result['failure'],flush=True)
finally:
 server.shutdown(); server.server_close(); thread.join(timeout=5)
 survivors=[item for item in process_snapshot() if any(item['pid']==before['pid'] and item['created']==before['created'] for before in owned)]
 result['cleanup']={'serverThreadStopped':not thread.is_alive(),'ownedSurvivors':survivors}
 save('result.json',result)
 sys.exit(0 if result['outcome']=='PASS' and not survivors and not thread.is_alive() else 1)
