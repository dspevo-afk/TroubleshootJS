"""One headed Edge page; commands use only ordinary Playwright UI/read APIs."""
import importlib.util
import json
import os
import subprocess
import sys
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(sys.argv[1]).resolve()
private = Path(sys.argv[2]).resolve()
root.mkdir(exist_ok=True)
(root / 'commands').mkdir(exist_ok=False)
(root / 'responses').mkdir(exist_ok=False)
(root / 'screenshots').mkdir(exist_ok=False)
now = lambda: datetime.now(timezone.utc).isoformat()
def save(name, value):
    (root / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
def processes():
    query = "Get-CimInstance Win32_Process | Select-Object @{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},ExecutablePath,CommandLine | ConvertTo-Json -Compress"
    p = subprocess.run(['powershell.exe','-NoProfile','-Command',query],capture_output=True,text=True,timeout=20)
    if p.returncode: raise RuntimeError('Process query failed: '+p.stderr)
    value=json.loads(p.stdout); return value if isinstance(value,list) else [value]
def owned(rows):
    ids={os.getpid()}
    while True:
        expanded=ids|{r['pid'] for r in rows if r['parent'] in ids}
        if expanded==ids: break
        ids=expanded
    return [r for r in rows if r['pid'] in ids]

build=json.loads((private/'private-menu-result.json').read_text(encoding='utf-8-sig'))
app=Path(build['privateSourceRoot'])
spec=importlib.util.spec_from_file_location('input_owner',private/'run_epoch15_private_menu.corrected.py')
input_owner=importlib.util.module_from_spec(spec);spec.loader.exec_module(input_owner)
snapshot=json.loads((private/'source-snapshot.json').read_text(encoding='utf-8-sig'))
reuse=json.loads((private/'private-build-reuse-audit.json').read_text(encoding='utf-8-sig'))
def inputs():
    input_owner.require_inventory_matches(input_owner.inventory_raw_source_roots(app),input_owner.expected_raw_source_inventory(snapshot,build['catalogSourceDelta']),'Headed UI input boundary')
    compiled=input_owner.inventory_tree(app,'war/circuitjs1')
    input_owner.require(compiled==build['compiledInventoryAfter'],'Compiled inputs changed')
    input_owner.require(input_owner.inventory_tree(app,'war/WEB-INF/deploy')==reuse['deploymentOutputs'],'Deployment changed')
    input_owner.verify_build_support_unchanged(build['buildSupport'])
    return {'status':'PASS','rawInputs':1354,'compiledFiles':len(compiled),'permutations':len(input_owner.validate_five_gwt_permutations(compiled,'Headed UI')),'deploymentFiles':6,'pinnedJars':9}

result={'schema':1,'outcome':'NOT_RUN','startedUtc':now(),'headed':True,'browserCount':1,'gameFunctionsCalled':False,'pageEvaluateUsed':False,'actions':[],'errors':[]}
preview=None; context=None; owned_rows=[]; log=None
try:
    result['inputsBefore']=inputs(); save('result-progress.json',result)
    owner=next(r for r in processes() if r['pid']==os.getpid())
    save('controller-identity.json',owner)
    import socket
    with socket.socket() as probe:
        probe.bind(('127.0.0.1',0));port=probe.getsockname()[1]
    log=(root/'preview.log').open('wb')
    pwsh=r'<USER_HOME>\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
    preview=subprocess.Popen([pwsh,'-NoProfile','-File',str(app/'scripts/preview.ps1'),'-Port',str(port),'-ForceTcpListener'],stdout=log,stderr=subprocess.STDOUT)
    preview_row=next(r for r in processes() if r['pid']==preview.pid)
    save('preview-process-identity.json',preview_row)
    import urllib.request
    deadline=time.monotonic()+60
    while True:
        if preview.poll() is not None: raise RuntimeError('Preview exited before ready')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/__tsj/verify-identity',timeout=2) as response:
                identity=json.load(response)
            break
        except Exception:
            if time.monotonic()>deadline: raise
            time.sleep(.2)
    if Path(identity['repositoryRoot'])!=app or identity['processId']!=preview.pid or identity['previewPort']!=port: raise RuntimeError('Preview identity mismatch')
    save('preview-serving-identity.json',identity)
    with sync_playwright() as pw:
        context=pw.chromium.launch_persistent_context(str(root/'profile'),channel='msedge',headless=False,viewport={'width':1440,'height':1000},args=['--window-size=1460,1100'])
        owned_rows=owned(processes()); save('owned-processes.json',owned_rows)
        edges=[r for r in owned_rows if 'msedge.exe' in (r['ExecutablePath'] or '').lower()]
        if not edges or any('--headless' in (r['CommandLine'] or '') for r in edges): raise RuntimeError('Headed Edge identity not proven')
        page=context.pages[0]
        page.set_default_timeout(15000)
        page.on('pageerror',lambda error:result['errors'].append(str(error)))
        page.goto(f'http://127.0.0.1:{port}/circuitjs.html?tsjQuickPlay=true',wait_until='domcontentloaded')
        page.get_by_role('button',name='New board',exact=True).wait_for(state='visible',timeout=30000)
        page.screenshot(path=str(root/'screenshots/000-headed-menu.png'))
        save('ready.json',{'status':'READY_HEADED_MENU','observedUtc':now(),'port':port,'profile':str(root/'profile'),'app':str(app),'pageCount':len(context.pages),'edgeProcesses':edges})
        seq=1; expires=time.monotonic()+3600
        while time.monotonic()<expires:
            path=root/'commands'/f'{seq:04d}.json'
            if not path.exists(): time.sleep(.1); continue
            command=json.loads(path.read_text(encoding='utf-8-sig'))
            action={'sequence':seq,'command':command,'startedUtc':now()}
            result['actions'].append(action);save('result-progress.json',result)
            started=time.monotonic()
            try:
                op=command['op']
                if op=='close':
                    action['status']='PASS';save(f'responses/{seq:04d}.json',action);break
                elif op=='click':
                    locator=page.get_by_role(command.get('role','button'),name=command['name'],exact=True)
                    if 'index' in command: locator=locator.nth(command['index'])
                    locator.click(button=command.get('button','left'))
                elif op=='select':
                    page.get_by_label(command['field'],exact=True).select_option(label=command['label'])
                elif op=='fill': page.get_by_label(command['field'],exact=True).fill(command['text'])
                elif op=='textclick': page.get_by_text(command['text'],exact=True).click()
                elif op=='wait': page.get_by_text(command['text'],exact=command.get('exact',True)).wait_for(state='visible',timeout=min(int(command.get('timeoutMs',30000)),115000))
                elif op=='keys': page.keyboard.press(command['key'])
                elif op=='mouse': page.mouse.click(command['x'],command['y'],button=command.get('button','left'))
                elif op=='wheel': page.mouse.move(command['x'],command['y']);page.mouse.wheel(0,command['deltaY'])
                elif op=='options': action['options']=page.get_by_label(command['field'],exact=True).locator('option').all_text_contents()
                elif op=='dump':
                    action['body']=page.locator('body').inner_text()
                    action['buttons']=[x.inner_text() for x in page.get_by_role('button').all() if x.is_visible()]
                    action['canvas']=[x.bounding_box() for x in page.locator('canvas').all() if x.is_visible()]
                elif op=='shot':
                    name=command['name']
                    if Path(name).name!=name or not name.endswith('.png'): raise RuntimeError('Unsafe screenshot name')
                    page.screenshot(path=str(root/'screenshots'/name));action['screenshot']=name
                elif op=='pause': page.wait_for_timeout(min(int(command['milliseconds']),3000))
                else: raise RuntimeError('Unsupported UI operation')
                action['status']='PASS'
            except Exception as error:
                action['status']='FAIL';action['error']=repr(error)
            action['elapsedSeconds']=time.monotonic()-started;action['finishedUtc']=now()
            save(f'responses/{seq:04d}.json',action);save('result-progress.json',result)
            seq+=1
        else: raise RuntimeError('UI session operation deadline expired')
        owned_rows=list({(r['pid'],r['created']):r for r in owned_rows+owned(processes())}.values());save('owned-processes.json',owned_rows)
        context.close();context=None
    result['inputsAfter']=inputs();result['outcome']='UI_SESSION_CLOSED_PENDING_FLOW_REVIEW'
except BaseException:
    result['outcome']='FAIL_HOST_OR_SESSION';result['failure']=traceback.format_exc();print(result['failure'],flush=True)
finally:
    cleanup=time.monotonic()
    if context is not None:
        try: context.close()
        except Exception as e: result['errors'].append('Context cleanup: '+repr(e))
    if preview is not None and preview.poll() is None:
        current=next((r for r in processes() if r['pid']==preview.pid),None)
        if current and current['created']==preview_row['created'] and current['ExecutablePath']==preview_row['ExecutablePath'] and current['CommandLine']==preview_row['CommandLine']:
            preview.terminate()
            try: preview.wait(timeout=15)
            except subprocess.TimeoutExpired: result['errors'].append('Preview exact process did not terminate')
        else: result['errors'].append('Preview cleanup identity changed; left untouched')
    if log: log.close()
    remaining=[r for r in processes() if r['pid']!=os.getpid() and any(r['pid']==p['pid'] and r['created']==p['created'] for p in owned_rows)]
    result['cleanup']={'seconds':time.monotonic()-cleanup,'recordedSurvivors':remaining,'previewExitCode':preview.poll() if preview else None}
    result['finishedUtc']=now();save('result.json',result)
    print(json.dumps({'outcome':result['outcome'],'cleanup':result['cleanup'],'actionCount':len(result['actions'])}),flush=True)
    sys.exit(0 if result['outcome']=='UI_SESSION_CLOSED_PENDING_FLOW_REVIEW' and not result['errors'] and not remaining and (preview is None or preview.poll() is not None) else 1)
