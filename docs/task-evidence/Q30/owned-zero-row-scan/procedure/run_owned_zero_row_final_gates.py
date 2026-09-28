from pathlib import Path
import json,subprocess,sys,time
s=Path(__file__).resolve().parent
repo=Path('[REDACTED_USER_PATH]
fixture=Path((s/'owned-zero-row-index-path.txt').read_text())
out=s/'owned-zero-row-final-gates.json';assert not out.exists()
result={'status':'RUNNING','scope':'exact staged source gates followed by working-source production build; no normal acceptance','steps':[]}
started=time.monotonic()
def save():
    result['elapsedSeconds']=time.monotonic()-started
    out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
def fail(kind,error,trace):
    result['status']='FAIL';result['error']=str(error);save();sys.__excepthook__(kind,error,trace)
sys.excepthook=fail
def run(label,args):
    print('START '+label,flush=True);t=time.monotonic()
    p=subprocess.run(args,check=False)
    result['steps'].append({'label':label,'command':args,'exitCode':p.returncode,'elapsedSeconds':time.monotonic()-t});save()
    if p.returncode:
        result['status']='STOPPED_GATE_FAILURE';save();sys.exit(p.returncode)
    print('PASS '+label,flush=True)
save()
run('index-native',['pwsh','-NoProfile','-File',str(s/'native-owned-zero-row.ps1'),'-SourceRoot',str(fixture),'-Label','native-owned-zero-row-index'])
run('index-gwt',['pwsh','-NoProfile','-File',str(s/'build-run.ps1'),'-SourceRoot',str(fixture),'-Label','owned-zero-row-index-gwt'])
run('index-compiled-a07',[sys.executable,'-B',str(s/'run_staged_solver_canary.py'),str(repo),str(s),str(fixture),'owned-zero-row-index-canary'])
host=json.loads((s/'owned-zero-row-index-canary/result.json').read_bytes())
assert host['outcome']=='PASS' and host['inputAudit']['status']=='PASS' and len(host['cases'])==1
assert host['cleanup']['status']=='PASS' and host['cleanup']['serverStopped'] and not host['cleanup']['ownedSurvivors'] and not host['cleanup']['errors']
case=host['cases'][0]
assert case['outcome']=='PASS' and case['stateMatch'] and case['observedState']=='PASS:a07' and case['reportMatch'] and not case['timedOut'] and not case['navigationError']
for key in ['pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors']:assert case[key]==[]
run('index-strict-a07',['pwsh','-NoProfile','-File',str(s/'read_a07.ps1'),'-Repo',str(repo),'-Report',str(s/'owned-zero-row-index-canary'/case['reportFile'])])
run('root-gwt',['pwsh','-NoProfile','-File',str(s/'build-run.ps1'),'-SourceRoot',str(repo),'-Label','owned-zero-row-root-gwt'])
result['status']='FINAL_SOURCE_GATES_PASS_PENDING_ROOT_INPUT_EQUALITY';save();print(json.dumps(result),flush=True)
