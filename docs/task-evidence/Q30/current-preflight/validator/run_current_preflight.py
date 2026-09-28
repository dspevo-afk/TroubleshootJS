from pathlib import Path
import hashlib,json,sys,subprocess,time
s=Path(__file__).resolve().parent
repo=Path('<REPO>')
source=Path(sys.argv[1]);label=sys.argv[2]
raw=(s/'current-preflight-measurement-plan.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()=='e822950a95469bd5b547fdc27494756ec85f264cd7817c3c804cb3d1f548fe5c'
plan=json.loads(raw);rows=[r for r in plan['sequence'] if r['label']==label];assert len(rows)==1
row=rows[0];mode='profile' if row['mode']=='profile' else 'off'
query='&tsjQ30LuRepetitionProfile=true' if mode=='profile' else ''
case={'name':label,'path':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed='+row['seed']+query,'stateAttribute':'data-tsj-q30-coordinator-state','expectedState':'PASS:complete','reportAttribute':'data-tsj-q30-coordinator-report','timeoutSeconds':600}
spec=s/(label+'-spec.json');out=s/label
assert not out.exists() and not spec.exists()
spec.write_text(json.dumps({'cases':[case]},indent=2)+'\n')
wall=time.time();mono=time.monotonic()
with (s/(label+'.log')).open('w',encoding='utf-8') as log:
 p=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(source),str(out),str(spec)],stdout=log,stderr=subprocess.STDOUT)
duration=time.monotonic()-mono
summary={'label':label,'mode':mode,'exitCode':p.returncode,'hostMonotonicSeconds':duration,'wallMinusMonotonicSeconds':time.time()-wall-duration}
if (out/'result.json').exists():
 result=json.loads((out/'result.json').read_bytes());summary['runnerOutcome']=result['outcome'];summary['cleanup']=result['cleanup']
reports=list((out/'cases').glob('*.report.json')) if (out/'cases').exists() else []
if reports:
 report=json.loads(reports[0].read_bytes())
 for phase in ('cold','warm'):
  data=report.get(phase,{})
  summary[phase]={k:data.get(k) for k in ['elapsedMs','proofElapsedMs','routingElapsedMs','totalWork','hypothesisWork','cleanupComplete']}
  if 'luRepetitionProfile' in data:summary[phase]['luRepetitionProfile']={k:v for k,v in data['luRepetitionProfile'].items() if not k.endswith('Positions')}
(s/(label+'-summary.json')).write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary),flush=True)
sys.exit(p.returncode)
