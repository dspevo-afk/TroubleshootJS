from pathlib import Path
import json,subprocess,sys,time
repo=Path(sys.argv[1]);s=Path(sys.argv[2]);source=Path(sys.argv[3]);label=sys.argv[4]
spec=s/(label+'-spec.json');out=s/label;assert not spec.exists() and not out.exists()
original=json.loads((s/'root-routing-integrated-canaries-spec.json').read_text())
case=next(c for c in original['cases'] if 'tsjVerifyA07=true' in c['path']);case['name']=label+'-a07'
spec.write_text(json.dumps({'cases':[case]},indent=2)+'\n')
started=time.monotonic()
with (s/(label+'.log')).open('w',encoding='utf-8') as log:
 p=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(source),str(out),str(spec)],stdout=log,stderr=subprocess.STDOUT)
r=json.loads((out/'result.json').read_text()) if (out/'result.json').exists() else {}
summary={'status':r.get('outcome','NO_RESULT'),'exitCode':p.returncode,'elapsedSeconds':time.monotonic()-started,'cleanup':r.get('cleanup'),'scope':'actual compiled A07 on exact staged source; unaccepted normal-verifier API absent'}
(s/(label+'-summary.json')).write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary));sys.exit(p.returncode)
