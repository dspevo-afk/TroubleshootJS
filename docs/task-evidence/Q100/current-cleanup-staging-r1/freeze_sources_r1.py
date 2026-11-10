"""Freeze root-reviewed cleanup component Source before final native gates."""
from pathlib import Path
import ast,hashlib,json,re,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
names=['CurrentAssemblyMetadata.java','CleanupStagingCanary.java','cleanup_oracle.py',
 'verify_cleanup_staging_r1.py','test_cleanup_oracle.py','ROOT_ORACLE_SOURCE_R1.json',
 'CONTRACT_R1.md','REFERENCE_R1.json','WIRE_R1.json','CANARY_NOTES_R1.md','ORACLE_NOTES_R1.md',
 'freeze_fixture_r1.py','author_metadata_r1.py','run_cleanup_gates_r1.py',
 'run_cleanup_controls_r1.py','run_cleanup_public_r1.py','run_reader_host_r1.py',
 'read_policy_canary_r1.py','freeze_sources_r1.py','check_source_r3.py',
 'assemble_evidence_r3.py']
private=re.compile(rb'(?i)(?:(?<![A-Za-z0-9_])[A-Z]:[\\/]|[/]U[s]ers[/]|\\\\U[s]ers\\\\)')
rows=[]
for name in names:
    p=R/name;assert not p.is_symlink();raw=p.read_bytes()
    assert 0<len(raw)<=4194304 and not private.search(raw),name
    assert b'\r' not in raw and raw.endswith(b'\n') and not raw.endswith(b'\n\n'),name
    assert not any(x.endswith((b' ',b'\t')) for x in raw.splitlines()),name
    if name.endswith('.py'):ast.parse(raw,filename=name)
    if name.endswith('.json'):json.loads(raw)
    rows.append({'name':name,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
pin=json.loads((R/'ROOT_ORACLE_SOURCE_R1.json').read_bytes())
oracle=(R/'cleanup_oracle.py').read_bytes()
assert pin=={'sha256':hashlib.sha256(oracle).hexdigest(),'bytes':len(oracle)}
for name in ['cleanup_oracle.py','verify_cleanup_staging_r1.py','test_cleanup_oracle.py','run_cleanup_gates_r1.py']:
    tree=ast.parse((R/name).read_bytes());constants={}
    for node in tree.body:
        if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name):
            key=node.targets[0].id
            if key in ('CONTRACT','REFERENCE','WIRE','CONTRACT_SHA256','REFERENCE_SHA256','WIRE_SHA256','CONTRACT_BYTES','REFERENCE_BYTES'):
                constants[key]=ast.literal_eval(node.value)
    for role in ('CONTRACT','REFERENCE','WIRE'):
        content=(R/(role+'_R1.'+('md' if role=='CONTRACT' else 'json'))).read_bytes()
        for key in (role,role+'_SHA256'):
            if key in constants:assert constants[key]==hashlib.sha256(content).hexdigest(),(name,key)
        if role+'_BYTES' in constants:assert constants[role+'_BYTES']==len(content),(name,role+'_BYTES')
value={'status':'ROOT_REVIEWED_FINAL_CLEANUP_STAGING_SOURCE','files':rows,
       'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
with (R/'SOURCE_FREEZE_R1.json').open('xb') as f:f.write(raw)
print(hashlib.sha256(raw).hexdigest())
