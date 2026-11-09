"""Authenticate integrated bytes and exercise the actual public pure entry points."""
from pathlib import Path
import argparse,hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',type=Path,required=True);a=p.parse_args()
F=a.repo;T=F/'tests/qualification/rb100-layout/current-run-staging-r1'
raw=(R/'run_staging_gates_r1.py').read_bytes();freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
assert hashlib.sha256(raw).hexdigest()==next(x['sha256'] for x in freeze['files'] if x['name']=='run_staging_gates_r1.py')
d=types.ModuleType('_final_public_staging_driver');d.__file__=str(R/'run_staging_gates_r1.py')
exec(compile(raw,d.__file__,'exec',dont_inherit=True,optimize=0),d.__dict__)
d.frozen();old,m=d.load_dependency();d.audit_classes()
paths={
 'CurrentV2InputSetup.java':F/'tests/qualification/rb100-layout/native-v2-input-setup-r1/CurrentV2InputSetup.java',
 'CurrentV2InputSetupCanary.java':F/'tests/qualification/rb100-layout/native-v2-input-setup-r1/CurrentV2InputSetupCanary.java',
 'CurrentAssemblyMetadata.java':F/'tests/qualification/rb100-layout/qualified-metadata-r1/CurrentAssemblyMetadata.java'}
for name in ['CurrentRunStagingCanary.java','CohortTerminalTelemetryInputV2.java','FIXTURE_REFERENCE_R1.json',
             'SYNTHETIC_RUN_CONTEXT_R1.json','test_run_profile_r1.py','verify_run_staging_r1.py','STAGING_CANARY_SCHEMA_R1.json']:
    paths[name]=T/name
rows=[]
for name,path in paths.items():
    content=d.read(path);assert content==d.read(R/name)
    rows.append({'path':path.relative_to(F).as_posix(),'bytes':len(content),'sha256':d.sha(content)})
codec=F/'tests/qualification/rb100-layout/current-context-kernel-r1/context_codec.py'
assert d.read(codec)==d.read(R/'source/context_codec.py')
pure=d.call(old,m,'run-public-profile-pure-r1',m.g.PY,['-I','-B',str(T/'test_run_profile_r1.py'),
    '--reference-root',str(T),'--codec-source',str(codec),'-v'],limit=180000)
import re
assert re.search(rb'Ran 12 tests? in [0-9.]+s\s+OK\s*\Z',d.read(R/pure['phase']/'stderr.txt'))
assert not d.read(R/pure['phase']/'stdout.txt')
reader=d.call(old,m,'run-public-staging-reader-r1',m.g.PY,['-I','-B',str(T/'verify_run_staging_r1.py'),
    '--input-json',str(R/'run-staging-suite-r1/stdout.txt'),'--reference-root',str(T)],limit=180000)
assert not d.read(R/reader['phase']/'stderr.txt')
for name,path in paths.items():assert d.read(path)==d.read(R/name)
d.frozen();d.audit_classes();old.recheck_dependencies(m)
expected=[]
for gate in ['HOST_GATE_R2.json','PURE_GATE_R1.json','COMPILE_GATE_R1.json','REGRESSION_GATE_R1.json','STAGING_GATE_R1.json','READER_CONTROLS_GATE_R1.json']:
    value=d.parse(d.read(R/gate));assert value['status'].startswith('PASS')
    expected.append({'name':gate,'sha256':d.sha(d.read(R/gate)),'status':value['status']})
audited=[]
for directory in sorted(R.iterdir()):
    if directory.is_dir() and directory.name.startswith('run-') and (directory/'result.json').is_file():
        result=d.parse(d.read(directory/'result.json'));code=result['exitCode']
        assert code in (0,1,2)
        audit=m.g.audit_call(directory,code)
        assert audit['terminationProven'] and audit['ownedRootAndLogsAbsent']
        audited.append(audit)
value={'status':'PASS_FINAL_PUBLIC_RUNTIME_BYTES_PURE_READER_AND_PROCESS_AUDIT',
       'sourceFreezeSha256':d.frozen(),'publicRuntimeSource':rows,'pure':pure,'reader':reader,
       'pureTestCount':12,'nativeNotRerunAfterCopy':True,'unchangedNativeSourceByteEquality':True,
       'gates':expected,'actualCallsReauthenticated':audited,'nativeProducerCalls':0,
       'genuineEnvelope':'UNSPENT_GATED','normalHostAcceptance':False}
print(json.dumps({'status':value['status'],'receiptSha256':d.write('FINAL_PUBLIC_GATE_R1.json',value)}))
