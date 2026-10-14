"""Restore only frozen accepted13 bytes after Git's checkout EOL conversion."""
import hashlib
import json
from pathlib import Path
here=Path(__file__).resolve().parent
root=here.parents[3]
prior=here.parent/'nearest-vacancy-preference-r1'
expected={e['path']:e['sha256'] for e in json.loads((prior/'final-inputs.json').read_text())['files']}
changed=json.loads((here/'restored13-input-audit.json').read_text())['changedPaths']
repairs=[]
for name in changed:
    path=root/name
    if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()): raise ValueError(name)
    candidate=path.read_bytes().replace(b'\r\n',b'\n')
    if hashlib.sha256(candidate).hexdigest()!=expected[name]: raise ValueError('not an EOL-only restoration: '+name)
    repairs.append((path,candidate))
for path,data in repairs:path.write_bytes(data)
(here/'checkout-eol-restoration.json').write_text(json.dumps(dict(status='PASS',scope='Only bytes independently matching the accepted13 frozen hashes were written',paths=changed),indent=2)+'\n')
print('Restored exact accepted13 bytes after checkout EOL conversion: '+str(len(repairs)))
