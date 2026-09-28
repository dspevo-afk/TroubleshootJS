from pathlib import Path
import json,sys,subprocess,time
repo=Path("<REPO>");scratch=Path("<TASK_TEMP>");source=Path("<TASK_TEMP>/lu-repetition-profile-clean-a056a49ccd5548deb32b0b06442ff27d")
label,mode=sys.argv[1:3]
assert mode in ('profile','off')
query='&tsjQ30LuRepetitionProfile=true' if mode=='profile' else ''
case={'name':label,'path':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed=7'+query,'stateAttribute':'data-tsj-q30-coordinator-state','expectedState':'PASS:complete','reportAttribute':'data-tsj-q30-coordinator-report','timeoutSeconds':600}
spec=scratch/(label+'-spec.json');out=scratch/label
assert not out.exists() and not spec.exists()
spec.write_text(json.dumps({'cases':[case]},indent=2)+'\n',encoding='utf-8')
wall=time.time();mono=time.monotonic()
with (scratch/(label+'.log')).open('w',encoding='utf-8') as log:
    p=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(source),str(out),str(spec)],stdout=log,stderr=subprocess.STDOUT)
duration=time.monotonic()-mono
summary={'label':label,'mode':mode,'exitCode':p.returncode,'hostMonotonicSeconds':duration,'wallMinusMonotonicSeconds':time.time()-wall-duration}
if (out/'result.json').exists():
    result=json.loads((out/'result.json').read_text(encoding='utf-8'))
    summary['runnerOutcome']=result['outcome'];summary['cleanup']=result['cleanup']
reports=list((out/'cases').glob('*.report.json')) if (out/'cases').exists() else []
if reports:
    report=json.loads(reports[0].read_text(encoding='utf-8'))
    for phase in ('cold','warm'):
        row=report.get(phase,{})
        summary[phase]={k:row.get(k) for k in ['elapsedMs','proofElapsedMs','routingElapsedMs','totalWork','hypothesisWork','cleanupComplete']}
        if 'luRepetitionProfile' in row:
            summary[phase]['luRepetitionProfile']={k:v for k,v in row['luRepetitionProfile'].items() if not k.endswith('Positions')}
(scratch/(label+'-summary.json')).write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary),flush=True)
sys.exit(p.returncode)
