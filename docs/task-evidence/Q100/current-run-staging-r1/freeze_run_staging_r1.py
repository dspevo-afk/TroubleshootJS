"""Freeze reviewed component inputs before final-source execution."""
from pathlib import Path
import ast,hashlib,json,re,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
names=['CONTRACT_R1.md','FIXTURE_REFERENCE_R1.json','SYNTHETIC_RUN_CONTEXT_R1.json',
 'CohortTerminalTelemetryInputV2.java','CurrentV2InputSetup.java','CurrentV2InputSetupCanary.java',
 'CurrentAssemblyMetadata.java','CurrentRunStagingCanary.java','STAGING_CANARY_SCHEMA_R1.json',
 'STAGING_CANARY_NOTES_R1.md','CANARY_MIGRATION_R1.md','fresh_run_fixture_r1.py','author_input_stage_r1.py',
 'test_run_profile_r1.py','verify_run_staging_r1.py','run_staging_gates_r1.py','run_reader_controls_r1.py',
 'run_profile_preflight_r1.py','SOURCE_INPUT_R1.json','source/context_codec.py','source/test_context_codec.py']
rows=[]
private=re.compile(rb'(?i)(?:(?<![A-Za-z0-9_])[A-Z]:[\\/]|[/]U[s]ers[/]|\\\\U[s]ers\\\\)')
for name in names:
    p=R/name;raw=p.read_bytes();assert 0<len(raw)<=4194304 and not p.is_symlink()
    if not name.startswith('source/'):
        assert b'\r' not in raw and raw.endswith(b'\n') and not raw.endswith(b'\n\n')
        assert not any(line.endswith((b' ',b'\t')) for line in raw.splitlines())
        assert not private.search(raw),name
    if name.endswith('.py'):ast.parse(raw,filename=name)
    if name.endswith('.json'):json.loads(raw)
    rows.append({'name':name,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
ref=json.loads((R/'FIXTURE_REFERENCE_R1.json').read_bytes())
schema=json.loads((R/'STAGING_CANARY_SCHEMA_R1.json').read_bytes())
payload=schema['properties']['cases']['items']['properties']['payload']['oneOf'][1]['properties']
assert payload['requestedNetIds']['const']==ref['expectedRequested']
value={'status':'ROOT_REVIEWED_FINAL_RUN_POLICY_STAGING_SOURCE','files':rows,
       'contractFrozenBeforeImplementation':True,'freshSyntheticFixture':True,
       'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
with (R/'SOURCE_FREEZE_R1.json').open('xb') as f:f.write(raw)
print(hashlib.sha256(raw).hexdigest())
