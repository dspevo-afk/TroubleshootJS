from pathlib import Path
import json,subprocess,sys,time
repo=Path(sys.argv[1]);scratch=Path(sys.argv[2]);source=Path(sys.argv[3]);label=sys.argv[4]
spec=scratch/(label+'-spec.json');out=scratch/label
assert not out.exists() and not spec.exists()
document=json.loads((scratch/'root-routing-integrated-canaries-spec.json').read_text(encoding='utf-8'))
for case in document['cases']:case['name']=case['name'].replace('root-routing-integrated',label)
spec.write_text(json.dumps(document,indent=2)+'\n',encoding='utf-8')
started=time.monotonic()
with (scratch/(label+'.log')).open('w',encoding='utf-8') as log:
    result=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(source),str(out),str(spec)],stdout=log,stderr=subprocess.STDOUT)
receipt=json.loads((out/'result.json').read_text(encoding='utf-8')) if (out/'result.json').exists() else {}
summary={'status':receipt.get('outcome','NO_RESULT'),'exitCode':result.returncode,'elapsedSeconds':time.monotonic()-started,'cleanup':receipt.get('cleanup')}
(scratch/(label+'-summary.json')).write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary));sys.exit(result.returncode)
