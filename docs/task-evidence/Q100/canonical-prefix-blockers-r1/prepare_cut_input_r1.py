from pathlib import Path
from collections import deque
import hashlib,json,sys
P=Path(__file__).resolve().parent
P4=P.parent/'canonical-prefix-capture-r1'
def sha(raw):return hashlib.sha256(raw).hexdigest()
qraw=(P4/'qualification-r4.json').read_bytes()
assert sha(qraw)=='0cc91c276bba8b6e1de867a732d9f34d3cea78dcadb28250ef99a3948687ce53'
q=json.loads(qraw)
copies={row['path']:row for row in q['publishedCopies']}
for name in ('read_capture.py','oracles-r1/read_fixed_prefix_graph.py','oracles-r1/fixed_prefix_minimum_vias.py'):
    raw=(P4/name).read_bytes();assert len(raw)==copies[name]['bytes'] and sha(raw)==copies[name]['sha256']
sys.path.insert(0,str(P4))
import read_capture as reader
raw=(P4/'capture-r2/data/fixed-prefix.jsonl').read_bytes()
assert sha(raw)=='75efe4303fa987f8ea499fa0e3c44c7743866ad213e158f2c5b58a38ccaf50dc'
decoded=reader.decode_transport(raw);g=decoded['graph'];f=decoded['facts']
assert sha(decoded['graphBytes'])=='ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a'
assert f['prefixGeometrySha256']=='6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb'
directions=((1,0,-1,'N'),(2,1,0,'E'),(4,0,1,'S'),(8,-1,0,'W'))
seen=set(g.starts);queue=deque(sorted(seen));edges=0
while queue:
    state=queue.popleft();assert not g.goals[state]
    layer,cell=divmod(state,g.cells);x,y=cell%g.width,cell//g.width
    for bit,dx,dy,name in directions:
        if not g.steps[state]&bit:continue
        nx,ny=x+dx,y+dy;assert 0<=nx<g.width and 0<=ny<g.height
        target=layer*g.cells+ny*g.width+nx;edges+=1
        if target not in seen:seen.add(target);queue.append(target)
assert len(seen)==12 and edges==23
queries=[]
for state in sorted(seen):
    layer,cell=divmod(state,g.cells);x,y=cell%g.width,cell//g.width
    for bit,dx,dy,name in directions:
        nx,ny=x+dx,y+dy
        if not (0<=nx<g.width and 0<=ny<g.height):continue
        target=layer*g.cells+ny*g.width+nx
        if target in seen:continue
        assert g.steps[state]&bit==0
        queries.append(dict(ordinal=len(queries),layer=layer,fromState=state,toState=target,
            fromCell=cell,toCell=target%g.cells,direction=name,bit=bit,
            fromX=f['originX']+x*f['gridPitch'],fromY=f['originY']+y*f['gridPitch'],
            toX=f['originX']+nx*f['gridPitch'],toY=f['originY']+ny*f['gridPitch'],
            expectedPermitsLayerStep=False))
assert 0<len(queries)<=48
value=dict(schema='tsj-canonical-prefix-cut-queries-v1',scope='COMPLETE_OUTGOING_PLANAR_CUT_OF_REACHABLE_SET',
 p4QualificationSha256=sha(qraw),p4InputPinsSha256=q['inputPinsSha256'],
 graphSha256=sha(decoded['graphBytes']),prefixGeometrySha256=f['prefixGeometrySha256'],
 target=dict(rootSeed=f['rootSeed'],placementSeed=f['placementSeed'],routingSeed=f['routingSeed'],
  requestIndex=0,selectedAttempt=4,passNumber=2,receiptBranchOrdinal=307,lineageBranchOrdinal=77,
  netId='A_COIL_LOW',startPadId='QA.C',rootPadId='DA.A',prefixHoles=48),
 grid=dict(width=g.width,height=g.height,cells=g.cells,pitch=f['gridPitch'],originX=f['originX'],originY=f['originY'],primary=g.primary),
 reachableStates=sorted(seen),directedPlanarEdgesExamined=edges,verticalTransitionsAllowed=0,
 cutQueries=queries,queryCount=len(queries),attribution='NOT_RUN',productionFeasibility='UNKNOWN')
out=P/'cut-input-r1.json';assert not out.exists()
data=(json.dumps(value,indent=2,sort_keys=True)+'\n').encode();out.write_bytes(data)
assert (P4/'qualification-r4.json').read_bytes()==qraw and (P4/'capture-r2/data/fixed-prefix.jsonl').read_bytes()==raw
print(json.dumps(dict(status='PREPARED_FROZEN_CASE_INPUT_NOT_ATTRIBUTION',reachableStates=len(seen),queryCount=len(queries),sha256=sha(data))))
