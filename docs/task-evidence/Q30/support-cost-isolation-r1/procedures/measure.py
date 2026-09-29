from pathlib import Path
import json, os, subprocess, sys, time

REC=Path(__file__).resolve().parent
PATHS=json.loads((REC/'paths.json').read_text())
ROOT=Path(PATHS['scratch']);REPO=Path(PATHS['repo'])
label,arm,mode,profile=sys.argv[1:5]
profile=profile=='on';out=ROOT/label
assert not out.exists()
query=''
if arm=='diagnostic':
    assert mode in ['off','track','restricted']
    query='&tsjQ30SupportMode='+mode+('&tsjQ30CostProfile=true' if profile else '')
case={'name':label,'path':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed=10014'+query,'stateAttribute':'data-tsj-q30-coordinator-state','expectedState':'PASS:complete','reportAttribute':'data-tsj-q30-coordinator-report','timeoutSeconds':600}
spec=REC/(label+'-spec.json');assert not spec.exists();spec.write_text(json.dumps({'cases':[case]},indent=2)+'\n')
def host():
    cmd=r'''$p=Get-CimInstance Win32_Processor | Select-Object Name,CurrentClockSpeed,MaxClockSpeed,LoadPercentage; $rows=Get-Process | ForEach-Object { try { [pscustomobject]@{name=$_.ProcessName;pid=$_.Id;started=$_.StartTime.ToUniversalTime().ToString('o');cpu=$_.CPU;workingSet=$_.WorkingSet64} } catch {} }; [ordered]@{utc=[DateTime]::UtcNow.ToString('o');processors=@($p);processes=@($rows)} | ConvertTo-Json -Depth 5 -Compress'''
    return json.loads(subprocess.check_output(['pwsh','-NoProfile','-Command',cmd],text=True))
before=host();(REC/(label+'-host-before.json')).write_text(json.dumps(before,indent=2)+'\n')
env=os.environ.copy();env['PYTHONPATH']=str(ROOT/'runtime-packages')
started=time.monotonic();wall=time.time()
with (REC/(label+'.log')).open('w',encoding='utf-8') as log:
    result=subprocess.run([sys.executable,'-B',str(REPO/'tests/browser/compiled_attribute_acceptance.py'),str(ROOT/arm),str(out),str(spec)],stdout=log,stderr=subprocess.STDOUT,env=env)
summary={'label':label,'arm':arm,'mode':mode,'profile':profile,'seed':'10014','exitCode':result.returncode,'outerMonotonicSeconds':time.monotonic()-started}
summary['wallMinusMonotonicSeconds']=time.time()-wall-summary['outerMonotonicSeconds']
after=host();(REC/(label+'-host-after.json')).write_text(json.dumps(after,indent=2)+'\n')
bmap={(p['pid'],p['started']):p for p in before['processes']};deltas=[]
for p in after['processes']:
    old=bmap.get((p['pid'],p['started']))
    if old and isinstance(p['cpu'],(int,float)) and isinstance(old['cpu'],(int,float)):
        deltas.append({'name':p['name'],'pid':p['pid'],'cpuSecondsDelta':p['cpu']-old['cpu'],'workingSetBytes':p['workingSet']})
summary['competingProcesses']=sorted(deltas,key=lambda p:-p['cpuSecondsDelta'])[:20]
summary['hostLimits']='Nominal WMI endpoint clocks; no turbo/thermal/throttling observation; process deltas exclude exited processes.'
if (out/'result.json').exists():
    raw=(out/'result.json').read_bytes();(REC/(label+'-host-result.json')).write_bytes(raw)
    runner=json.loads(raw);summary.update(runnerOutcome=runner['outcome'],cleanup=runner['cleanup'],inputAudit=runner['inputAudit'],cases=[{k:c[k] for k in ['outcome','operationSeconds','observedState']} for c in runner['cases']])
reports=list((out/'cases').glob('*.report.json')) if (out/'cases').exists() else []
if reports:
    raw=reports[0].read_bytes();(REC/(label+'-raw-report.json')).write_bytes(raw);report=json.loads(raw)
    for phase in ['cold','warm']:
        if phase in report:
            summary[phase]={k:report[phase].get(k) for k in ['elapsedMs','proofElapsedMs','routingElapsedMs','totalWork','hypothesisWork','cleanupComplete']}
(REC/(label+'-summary.json')).write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary),flush=True)
sys.exit(result.returncode)
