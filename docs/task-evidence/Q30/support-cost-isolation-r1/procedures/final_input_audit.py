from pathlib import Path
import hashlib
import json

REC = Path(__file__).resolve().parent
paths = json.loads((REC/'paths.json').read_text())
root = Path(paths['scratch'])
base = json.loads((root/'baseline-source-audit.json').read_text())['armInputs']['control']
final = json.loads((REC/'diagnostic-r3-source-audit.json').read_text())['inputs']
checks = {}
for arm, entries in [('control',base),('diagnostic',final)]:
    mismatches = []
    for entry in entries:
        raw = (root/arm/entry['path']).read_bytes()
        if len(raw) != entry['bytes'] or hashlib.sha256(raw).hexdigest() != entry['sha256']:
            mismatches.append(entry['path'])
    checks[arm] = {'files':len(entries),'mismatches':mismatches,'status':'FAIL' if mismatches else 'PASS'}
out = {'status':'PASS' if all(v['status']=='PASS' for v in checks.values()) else 'FAIL',
       'scope':'Every frozen build input rehashed after the 14-row sequence; generated build output is separately audited by maintained runtime manifests',
       'arms':checks}
(REC/'final-input-audit.json').write_bytes((json.dumps(out,indent=2)+'\n').encode())
print(json.dumps(out,indent=2))
assert out['status']=='PASS'
