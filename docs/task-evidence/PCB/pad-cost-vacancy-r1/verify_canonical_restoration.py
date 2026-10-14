"""Verify text restoration to accepted Git content; never rewrite sources."""
import hashlib,json,subprocess
from pathlib import Path
here=Path(__file__).resolve().parent;root=here.parents[3]
paths=json.loads((here/'restored13-input-audit.json').read_text())['changedPaths']
rows=[]
for name in paths:
    current=(root/name).read_bytes()
    blob=subprocess.run(['git','show','96bd1b1414866733927f93bdf0b039bd09cae204:'+name],cwd=root,check=True,capture_output=True).stdout
    same=current.replace(b'\r\n',b'\n')==blob.replace(b'\r\n',b'\n')
    if not same:raise ValueError('canonical content differs: '+name)
    rows.append(dict(path=name,rawSha256=hashlib.sha256(current).hexdigest(),acceptedBlobSha256=hashlib.sha256(blob).hexdigest(),canonicalEqual=True))
result=dict(status='PASS',scope='Text equality to accepted13 Git blobs after newline normalization only; not a raw-input PASS or substitute for fresh qualification',cases=rows)
(here/'canonical-restoration-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS canonical restored content paths='+str(len(rows)))
