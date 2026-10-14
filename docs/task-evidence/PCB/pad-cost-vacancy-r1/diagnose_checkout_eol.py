import hashlib,json
from pathlib import Path
here=Path(__file__).resolve().parent;root=here.parents[3]
expected={e['path']:e['sha256'] for e in json.loads((here.parent/'nearest-vacancy-preference-r1/final-inputs.json').read_text())['files']}
changed=json.loads((here/'restored13-input-audit.json').read_text())['changedPaths']
for name in changed:
    raw=(root/name).read_bytes();lf=raw.replace(b'\r\n',b'\n');crlf=lf.replace(b'\n',b'\r\n')
    print(name, 'LF='+str(hashlib.sha256(lf).hexdigest()==expected[name]),'CRLF='+str(hashlib.sha256(crlf).hexdigest()==expected[name]),'CRs='+str(raw.count(b'\r')),'LFs='+str(raw.count(b'\n')))
