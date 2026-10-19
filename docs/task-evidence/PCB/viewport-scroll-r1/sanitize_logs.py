"""Sanitize task log paths, preserving raw/sanitized SHA256 receipts."""
import hashlib,json
from pathlib import Path
here=Path(__file__).resolve().parent
root=here.parents[3]
if (here/'log-sanitization.json').exists():
    raise SystemExit('Already sanitized: audit existing raw/sanitized receipts; do not overwrite them')
receipts=[]
for name in ('final-build.txt','u04_ui_contract-attempt1.txt','u04_ui_contract.txt'):
    path=here/name
    raw=path.read_bytes()
    text=raw.decode('utf-8-sig')
    count=0
    for original in (root.as_uri(),str(root),root.as_posix()):
        count+=text.count(original)
        text=text.replace(original,'<workspace>')
    result=text.encode('utf-8')
    path.write_bytes(result)
    receipts.append(dict(file=name,rawSha256=hashlib.sha256(raw).hexdigest(),
        sanitizedSha256=hashlib.sha256(result).hexdigest(),pathReplacements=count))
(here/'log-sanitization.json').write_bytes((json.dumps(receipts,indent=2)+'\n').encode('utf-8'))
print('Sanitized',len(receipts),'task logs')
