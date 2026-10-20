"""Known-prefix privacy replacements only after the named terminal driver exits."""
import hashlib
import json
import sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
phase=sys.argv[1]
assert phase in ('unit','routes')
assert (HERE/(phase+'-timing.json')).is_file()
assert json.loads((HERE/(phase+'-process-cleanup.json')).read_text(encoding='utf-8-sig'))['status']=='PASS'
known=[(str(COPY),'[TASK_COPY]'),(str(ROOT),'[WORKTREE]')]
if len(sys.argv)>2:
    known.append((str(Path(sys.argv[2]).resolve()),'[JDK8]'))
rows=[]
for name in (phase+'.txt',phase+'-receipt.txt'):
    path=HERE/name
    if not path.exists():continue
    raw=path.read_bytes();text=raw.decode('utf-8-sig');replacements=[]
    for prefix,label in known:
        count=0
        for form in sorted({prefix,prefix.replace('\\','/'),json.dumps(prefix)[1:-1]},key=len,reverse=True):
            count+=text.count(form);text=text.replace(form,label)
        replacements.append({'prefixKind':label,'count':count})
    if any(r['count'] for r in replacements):path.write_bytes(text.encode('utf-8'))
    rows.append({'file':name,'rawSha256':hashlib.sha256(raw).hexdigest(),
      'sanitizedSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'replacements':replacements})
(HERE/(phase+'-log-sanitization.json')).write_text(json.dumps({'status':'PASS',
 'scope':'Known prefixes only; no status/assertion/timing changes','files':rows},indent=2)+'\n')
print('PASS named phase known-prefix-only privacy audit')
