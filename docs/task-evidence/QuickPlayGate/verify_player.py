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

   page.goto(base+'/circuitjs.html?tsjVerifyQuickPlayGate=true',wait_until='domcontentloaded',timeout=30000)

   page.wait_for_function("() => document.body.getAttribute('data-player-screen')==='MENU'",timeout=30000)

   def state(): return page.evaluate('window.tsjProduct.snapshot(false)')

   def screen(value): page.wait_for_function('(s) => document.body.getAttribute("data-player-screen")===s',arg=value,timeout=20000)

   def family(): page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option('RB15_CONTROL')

   def click(label): page.get_by_role('button',name=label,exact=True).click()

   def watch(label):

       began=time.monotonic(); samples=[]; captured=False

       while time.monotonic()-began<110:

           current=state(); progress=current.get('progress')

           if progress:

               samples.append(progress)

               if progress.get('candidate',0)==2 and not captured:

                   page.screenshot(path=str(evidence/'01-second-candidate.png'),full_page=True); captured=True

           if current['screen'] in ['TICKET','ERROR']: break

           page.wait_for_timeout(150)

       else: raise AssertionError('Normal preparation did not terminate within the unchanged guard')

       save(label+'-progress.json',samples)

       assert all(p['units']<=640 and p['candidates'] in [1,4] for p in samples)

       result[label]={'state':current,'seconds':time.monotonic()-began,'samples':len(samples),'candidateCounts':sorted({p['candidates'] for p in samples}),'ordinals':sorted({p['candidate'] for p in samples})}

       return current

   initial=state(); assert [f['id'] for f in initial['families'] if f['procedural']]==['RB15_CONTROL']

   page.screenshot(path=str(evidence/'00-menu.png'),full_page=True)

   family(); click('New board')

   fresh=watch('unmodified-random')

   assert fresh['screen'] in ['TICKET','ERROR']

   if fresh['screen']=='TICKET': click('Accept ticket and start'); screen('WORKBENCH')

   click('Main menu'); screen('MENU'); family()

   # Only the entropy source is controlled for this boundary fixture; actions are real DOM clicks.

   launch_seed=-7564325972933069162

   expected_seed=((launch_seed+0x9e3779b97f4a7c15+(1<<63))%(1<<64))-(1<<63)

   bytes8=list((launch_seed&((1<<64)-1)).to_bytes(8,'big'))

   page.evaluate('(b) => { const original=crypto.getRandomValues.bind(crypto); crypto.getRandomValues=function(a) { if(a instanceof Uint8Array && a.length===8) { a.set(b); return a; } return original(a); }; }',bytes8)

   click('New board'); accepted=watch('controlled-retry')

   assert accepted['screen']=='TICKET',accepted

   assert accepted['replay']=='tsj-alpha/2/EASY/RB15_CONTROL/'+str(expected_seed),accepted

   assert 2 in result['controlled-retry']['ordinals'] and result['controlled-retry']['candidateCounts']==[4]

   replay=accepted['replay']; click('Accept ticket and start'); screen('WORKBENCH')

   page.screenshot(path=str(evidence/'02-accepted-board.png'),full_page=True)

   page.wait_for_function('() => window.tsjProduct.snapshot(false).ready===true',timeout=20000)

   before_retest=state()['message']; click('Run customer retest')

   page.wait_for_function('(before) => { const s=window.tsjProduct.snapshot(false); return s.screen!=="RETEST" && s.message.length>0 && s.message!==before; }',arg=before_retest,timeout=20000)

   assert not state()['completed'],'Unrepaired fault passed customer retest'

   result['unrepairedRetest']=state()

   click('Board Power: ON')

   page.wait_for_function('() => { const s=window.tsjProduct.snapshot(false); return s.isolated===true && s.ready===true; }',timeout=20000)

   click('Main menu'); screen('MENU'); family()

   page.get_by_text('Enter an exact seed',exact=True).click()

   page.get_by_label('Exact seed (signed decimal integer)',exact=True).fill(str(launch_seed))

   click('Prepare exact seed'); rejected=watch('exact-rejection')

   assert rejected['screen']=='ERROR' and rejected['replay']==replay and rejected['isolated'],rejected

   assert set(result['exact-rejection']['candidateCounts']) <= {1},'Exact replay exposed search candidates'
   result['exact-rejection']['noIntermediateSample'] = not result['exact-rejection']['candidateCounts']

   click('Main menu'); screen('MENU'); family(); click('New board')

   page.wait_for_function('() => window.tsjProduct.snapshot(false).screen==="PREPARING"',timeout=10000)

   page.get_by_role('button',name='Cancel preparation',exact=True).click()

   screen('WORKBENCH'); cancelled=state()

   assert cancelled['replay']==replay and cancelled['isolated'],cancelled

   result['normalCancellation']=cancelled

   click('Main menu'); screen('MENU')

   page.get_by_text('Open a saved replay code',exact=True).click()

   page.get_by_label('Open current replay',exact=True).fill('tsj-alpha/1/EASY/RB15_CONTROL/3')

   click('Prepare replay'); page.wait_for_timeout(200)

   assert state()['screen']=='MENU','Obsolete replay epoch was accepted'

   assert 'Unsupported replay' in page.locator('[role=dialog][aria-modal=true]').inner_text()

   page.get_by_label('Open current replay',exact=True).fill(replay); click('Prepare replay')

   replayed=watch('accepted-replay')

   assert replayed['screen']=='TICKET' and replayed['replay']==replay,replayed

   assert result['accepted-replay']['candidateCounts']==[1]

   click('Accept ticket and start'); screen('WORKBENCH')

   click('Board view'); click('View bottom copper'); click('Board view')

   box=max((item.bounding_box() for item in page.locator('canvas:visible').all()),key=lambda b:b['width']*b['height'])

   x=box['x']+box['width']*.55; y=box['y']+box['height']*.5

   page.mouse.move(x,y); page.mouse.down(button='middle'); page.mouse.move(x+100,y+50,steps=10); page.mouse.up(button='middle')

   page.mouse.wheel(0,-160); page.wait_for_timeout(300)

   page.screenshot(path=str(evidence/'03-replayed-copper.png'),full_page=True)

   for attr in ['data-tsj-verification','data-tsj-quickplay-gate','data-tsj-q15-report','data-tsj-p09-report']:

       assert page.locator('html').get_attribute(attr) is None,'Developer proof exposed: '+attr

   assert page.evaluate('typeof window.tsjQuickPlayGate')=='undefined'

   assert not ({'fault','solution','netlist','hypotheses','manifest'} & set(state()))

   result['final']=state(); result['browser']=context.browser.version if context.browser else None

   result['resources']=page.evaluate('performance.getEntriesByType("resource").map(x=>x.name).filter(x=>x.includes("cache.js"))')

   result['controlledEntropyOnly']={'seed':str(launch_seed),'accepted':str(expected_seed),'byteVector':bytes8}

   result['inputs']=['unmodified random New board','controlled entropy with real New board/retry','unrepaired customer retest','power off','exact rejection preserves isolated prior board','real cancel restores isolated prior board','old epoch rejected','accepted replay is exact','bottom face','middle pan','wheel zoom']

   assert not result['errors'],result

   result['outcome']='PASS'; print('PASS normal Quick Play gate input/replay/privacy',flush=True)

  except BaseException:

   try:

    page.screenshot(path=str(evidence/'failure.png'),full_page=True)

    save('failure-dom.json',{'text':page.locator('body').inner_text()})

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
