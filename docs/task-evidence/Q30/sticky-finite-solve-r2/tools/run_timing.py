from pathlib import Path
import json,subprocess,sys,time
repo=Path(sys.argv[1]); scratch=Path(sys.argv[2]); root=Path(sys.argv[3]);label=sys.argv[4];seed=sys.argv[5]
profile=len(sys.argv)>6 and sys.argv[6]=='profile'
query='&tsjQ30KernelProfile=true' if profile else ''
case={'name':label,'path':f'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed={seed}{query}',
      'stateAttribute':'data-tsj-q30-coordinator-state','expectedState':'PASS:complete',
      'reportAttribute':'data-tsj-q30-coordinator-report','timeoutSeconds':600}
spec=scratch/(label+'-spec.json');spec.write_text(json.dumps({'cases':[case]}),encoding='utf-8')
out=scratch/label
wallBegan=time.time()
began=time.monotonic()
with (scratch/(label+'.log')).open('w',encoding='utf-8') as log:
 proc=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(root),str(out),str(spec)],stdout=log,stderr=subprocess.STDOUT)
result={'label':label,'seed':seed,'profile':profile,'exitCode':proc.returncode,'hostMonotonicSeconds':time.monotonic()-began}
result['wallMinusMonotonicSeconds']=time.time()-wallBegan-result['hostMonotonicSeconds']
if (out/'result.json').exists():
 runner=json.loads((out/'result.json').read_text(encoding='utf-8'));result['runnerOutcome']=runner['outcome'];result['cleanup']=runner['cleanup']
reports=list((out/'cases').glob('*.report.json')) if (out/'cases').exists() else []
if reports:
 report=json.loads(reports[0].read_text(encoding='utf-8'))
 result['reportKeys']=list(report)
 for phase in ['cold','warm']:
  if phase not in report:continue
  row=report[phase]; result[phase]={k:row.get(k) for k in ['elapsedMs','proofElapsedMs','routingElapsedMs','totalWork','hypothesisWork','cleanupComplete']}
  result[phase]['profileKeys']=[k for k in row if 'profile' in k.lower()]
(scratch/(label+'-summary.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result),flush=True)
sys.exit(proc.returncode)

