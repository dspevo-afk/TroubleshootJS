"""Freeze reviewed Source and statically verify every pinned current input."""
from pathlib import Path
import ast,hashlib,json,re,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
names=['CONTRACT_R1.md','REFERENCE_R1.json','baseline/CURRENT_STUDY.java',
 'baseline/CurrentAssemblyMetadata.java','baseline/AuthoredFlatWorkMembers.java',
 'working/authored-flat-work-plan-r1/AuthoredFlatWorkMembers.java',
 'working/authored-flat-work-plan-r1/generate_authored_flat_work_overlay.py',
 'working/authored-flat-work-plan-r1/test_flat_work_overlay.py',
 'working/native-body-ownership-r1/CurrentBodyOwnership.java',
 'working/native-body-ownership-r1/generate_body_ownership.py',
 'working/native-body-ownership-r1/test_body_ownership_source.py',
 'run_current_r7_gates_r1.py','run_current_r7_generation_r1.py',
 'current_r7_file_policy_canary_r1.py','freeze_current_r7_sources_r1.py']
def sha(raw):return hashlib.sha256(raw).hexdigest()
private=re.compile(rb'(?i)(?:(?<![A-Za-z0-9_])[A-Z]:[\\/]|[/]U[s]ers[/]|\\\\U[s]ers\\\\)')
rows=[]
for name in names:
    path=R/name;assert not path.is_symlink() and not getattr(path.lstat(),'st_file_attributes',0)&0x400
    raw=path.read_bytes();assert 0<len(raw)<=4194304 and not private.search(raw),name
    assert b'\r' not in raw and raw.endswith(b'\n') and not raw.endswith(b'\n\n'),name
    assert not any(line.endswith((b' ',b'\t')) for line in raw.splitlines()),name
    if name.endswith('.py'):ast.parse(raw,filename=name)
    if name.endswith('.json'):json.loads(raw)
    rows.append({'name':name,'bytes':len(raw),'sha256':sha(raw)})
reference=json.loads((R/'REFERENCE_R1.json').read_bytes())
assert sha((R/'CONTRACT_R1.md').read_bytes())=='fb6aae2e0b030a658a75c6c9870075c600f4388293ed7ea5a8ef517e8b3b2a4e'
assert sha((R/'REFERENCE_R1.json').read_bytes())=='9e3d95aeeb236156e1c071e7151cd98232d75587ce778cce7afa21d32a5fe785'
hook=reference['hookAscii'].encode('ascii');old=(R/'baseline/AuthoredFlatWorkMembers.java').read_bytes()
authored=(R/'working/authored-flat-work-plan-r1/AuthoredFlatWorkMembers.java').read_bytes()
assert authored.count(hook)==1 and authored.replace(hook,b'',1)==old
assert len(authored)==reference['expectedAuthoredFragment']['bytes'] and sha(authored)==reference['expectedAuthoredFragment']['sha256']
body_path=R/'working/native-body-ownership-r1/generate_body_ownership.py'
constants={}
for node in ast.parse(body_path.read_bytes()).body:
    if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name):
        if node.targets[0].id.startswith('PINNED_'):constants[node.targets[0].id]=ast.literal_eval(node.value)
for label,name in [('STUDY','baseline/CURRENT_STUDY.java'),('HELPER','working/native-body-ownership-r1/CurrentBodyOwnership.java'),
 ('AUTHORED_MEMBERS','working/authored-flat-work-plan-r1/AuthoredFlatWorkMembers.java'),
 ('AUTHORED_MODULE','working/authored-flat-work-plan-r1/generate_authored_flat_work_overlay.py')]:
    raw=(R/name).read_bytes()
    assert constants['PINNED_'+label+'_SHA256']==sha(raw) and constants['PINNED_'+label+'_BYTES']==len(raw),label
for label,key in [('PRE_OWNERSHIP','expectedPreOwnershipStudy'),('FINAL_SOURCE','expectedFinalStudy')]:
    assert constants['PINNED_'+label+'_SHA256']==reference[key]['sha256']
    assert constants['PINNED_'+label+'_BYTES']==reference[key]['bytes']
value={'status':'ROOT_REVIEWED_CURRENT_R7_EMITTER_FINAL_SOURCE','files':rows,
       'actualAggregationHook':'NOT_RUN_UNSPENT_GATED','nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
with (R/'SOURCE_FREEZE_R1.json').open('xb') as f:f.write(raw)
print(sha(raw))
