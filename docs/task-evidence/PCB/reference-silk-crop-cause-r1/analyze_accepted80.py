from collections import Counter
from pathlib import Path
import json,re

ROOT=Path(__file__).resolve().parents[4]
PACKET=Path(__file__).resolve().parent
RECEIPT=ROOT/'docs/task-evidence/PCB/reference-lead-clearance-r1/baseline-receipt.txt'
rows=[json.loads(line.split(' ',1)[1]) for line in RECEIPT.read_text(encoding='utf-8-sig').splitlines()
      if line.startswith('PCB_REFERENCE_ORACLE ')]
if len(rows)!=80 or any(row.get('layoutVersion')!=17 for row in rows):
    raise SystemExit(f'FAIL expected retained accepted17 canonical rows=80; got {len(rows)}')
pattern=re.compile(r'connectedLead=(-?\d+),(-?\d+),(\d+),(\d+);')
def kind(label_id):
    if label_id=='board-title': return 'board-title'
    if label_id.startswith('component:'): return 'component-reference'
    if label_id.startswith('net:'): return 'net-label'
    return 'other'
def intersects(a,b):
    ax,ay,aw,ah=a; bx,by,bw,bh=b
    return ax<bx+bw and ax+aw>bx and ay<by+bh and ay+ah>by
kinds=('board-title','component-reference','net-label')
pairs=Counter({k:0 for k in kinds}); labels=Counter({k:0 for k in kinds}); rows_with=Counter({k:0 for k in kinds}); leads_total=0; label_total=0
for row in rows:
    leads=[tuple(map(int,m.groups())) for m in pattern.finditer(row['layoutFingerprint'])]
    leads_total+=len(leads); label_total+=len(row['labels']); row_kinds=set()
    if len(row['labels'])!=row['labelCount']:
        raise SystemExit('FAIL label records do not match retained canonical count')
    for label in row['labels']:
        k=kind(label['id']); bounds=tuple(label['bounds'])
        hit=sum(1 for lead in leads if intersects(bounds,lead))
        if hit:
            pairs[k]+=hit; labels[k]+=1; row_kinds.add(k)
    for k in row_kinds: rows_with[k]+=1
result={'schema':1,'status':'PASS','source':'retained accepted17 baseline receipt canonical oracle rows',
        'canonicalOracleRows':len(rows),'labelsChecked':label_total,'placedConnectedLeadRectangles':leads_total,
        'intersectionPairsByLabelKind':dict(sorted(pairs.items())),
        'labelsWithAtLeastOneLeadIntersectionByKind':dict(sorted(labels.items())),
        'oracleRowsWithAtLeastOneIntersectionByKind':dict(sorted(rows_with.items())),
        'intersectionRule':'positive-area overlap of half-open integer rectangles'}
out=PACKET/'accepted80-lead-intersections.json'
out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))