"""Freeze the accepted14 routed regression rows before advice-trial edits."""
import hashlib,json,subprocess
from pathlib import Path
here=Path(__file__).resolve().parent
root=here.parents[3]
commit='410bcd73dc96d87d1d4428df7cc553216d05d691'
source='docs/task-evidence/PCB/canvas-owner-fit-r1/final-native.txt'
raw=(root/source).read_bytes()
blob=subprocess.run(['git','show',commit+':'+source],cwd=root,check=True,stdout=subprocess.PIPE).stdout
if blob!=raw:raise ValueError('Accepted log differs from published blob')
proposal=json.loads((here/'proposal.json').read_text(encoding='utf-8-sig'))
rows={}
for line in raw.decode('utf-8-sig').splitlines():
    if line.startswith('PCB_CANVAS_OWNER_CASE '):
        row=json.loads(line[len('PCB_CANVAS_OWNER_CASE '):])
        if row['seed'] in rows:raise ValueError('Duplicate baseline seed')
        if row['status']!='PASS' or row['version']!=14 or row['density']!=13:raise ValueError('Wrong baseline interpretation')
        rows[row['seed']]=row
if set(rows)!=set(proposal['roots']):raise ValueError('Missing/extra predeclared baseline seed')
result=dict(status='FROZEN_ACCEPTED14_BASELINE',baseCommit=commit,source=source,
    sourceSha256=hashlib.sha256(raw).hexdigest(),caseCount=len(rows),
    cases=[rows[seed] for seed in proposal['roots']],
    limits='All14 are informed regression data. Frozen before trial source edits; no unseen or population claim.')
output=here/'published14-baselines.json'
if output.exists():raise ValueError('Do not overwrite frozen baseline')
output.write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
print('FROZEN accepted14 cases='+str(len(rows))+' sha256='+hashlib.sha256(output.read_bytes()).hexdigest())
