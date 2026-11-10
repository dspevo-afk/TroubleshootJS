"""Copy accepted synthetic component evidence only after all private gates."""
from pathlib import Path
import argparse,hashlib,json,re,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',type=Path,required=True);a=p.parse_args();F=a.repo
T=F/'tests/qualification/rb100-layout/current-cleanup-staging-r1'
E=F/'docs/task-evidence/Q100/current-cleanup-staging-r1'
N1=R.parent/'tsj-q100-r7-cleanup-staging-7c129905f6f44c04bcf3a43441c69c4d'
N2=R.parent/'tsj-q100-cleanup-reader-r2-5e31deb43864425aa454c188dd3572f0'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def read(path):return Path(path).read_bytes()
def parse(path):return json.loads(read(path))
private=re.compile(rb'(?i)(?:(?<![A-Za-z0-9_])[A-Z]:[\\/]|[/]U[s]ers[/]|\\\\U[s]ers\\\\)')
def put(path,raw):
    assert not private.search(raw),str(path.relative_to(F))
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists():assert read(path)==raw
    else:
        with path.open('xb') as f:f.write(raw)
def dump(path,value):put(path,(json.dumps(value,indent=2)+'\n').encode('ascii'))
driver=R/'run_cleanup_gates_r1.py'
d=types.ModuleType('_accepted_cleanup_evidence_driver');d.__file__=str(driver)
exec(compile(read(driver),str(driver),'exec',dont_inherit=True,optimize=0),d.__dict__)
freeze=d.frozen();compile_gate=d.audit_classes()
gates={name:parse(R/(name+'_GATE_R1.json')) for name in ['HOST','PURE','SMOKE','SUITE','REGRESSION','CONTROLS']}
assert all(v['status'].startswith('PASS_') for v in gates.values())
assert gates['PURE']['testCount']==16 and gates['SMOKE']['summary']['caseCount']==2
assert gates['SUITE']['summary']['caseCount']==22
assert gates['CONTROLS']['readerExpectedExit2']==9 and gates['CONTROLS']['protocolExpectedExit1']==2
assert parse(R/'READER_HOST_GATE_R3.json')['status'].startswith('PASS_')
source=parse(R/'SOURCE_FREEZE_R1.json')
runtime=['CleanupStagingCanary.java','cleanup_oracle.py','verify_cleanup_staging_r1.py',
         'test_cleanup_oracle.py','ROOT_ORACLE_SOURCE_R1.json','CONTRACT_R1.md','REFERENCE_R1.json','WIRE_R1.json']
for name in runtime:put(T/name,read(R/name))
assert read(F/'tests/qualification/rb100-layout/qualified-metadata-r1/CurrentAssemblyMetadata.java')==read(R/'CurrentAssemblyMetadata.java')
for name in ['CONTRACT_R1.md','REFERENCE_R1.json','WIRE_R1.json','SOURCE_FREEZE_R1.json',
             'SOURCE_REVIEW_R3.json','CANARY_NOTES_R1.md','ORACLE_NOTES_R1.md',
             'READER_HOST_GATE_R3.json','HOST_GATE_R1.json','PURE_GATE_R1.json',
             'SMOKE_GATE_R1.json','SUITE_GATE_R1.json','REGRESSION_GATE_R1.json','CONTROLS_GATE_R1.json',
             'freeze_fixture_r1.py','freeze_sources_r1.py','check_source_r3.py',
             'run_cleanup_gates_r1.py','run_cleanup_controls_r1.py','run_cleanup_public_r1.py',
             'run_reader_host_r1.py','read_policy_canary_r1.py','assemble_evidence_r3.py']:
    put(E/name,read(R/name))
for name in ['SOURCE_INPUT_R1.json','SOURCE_INPUT_R2.json','ADMISSION_R2.md','ADMISSION_R3.md','ADMISSION_R4.md']:
    put(E/name,read(N1/name))
for label,phase in [('SMOKE','cleanup-native-smoke-r1'),('SUITE','cleanup-native-suite-r1'),('REGRESSION','cleanup-maintained-81-r1')]:
    put(E/('ACTUAL_'+label+'_STDOUT_R1.json'),read(R/phase/'stdout.txt'))
summary={k:compile_gate[k] for k in ['status','sourceFreezeSha256','call','sourceCount','classCount','classBytes',
    'allMajor51','noDuplicateOrShadowClasses','acceptedDependencyCompileReauthenticated','nativeProducerCalls','genuineEnvelope']}
summary.update(dependencyClassCount=len(compile_gate['dependencies']),
    dependencyClassBytes=sum(x['bytes'] for x in compile_gate['dependencies']),
    retainedFullCompileGateSha256=sha(read(R/'COMPILE_GATE_R1.json')),
    freshClasses=compile_gate['classes'],removedOldClasses=compile_gate['removedOldClasses'])
dump(E/'COMPILE_SUMMARY_R1.json',summary)
failures=[]
for phase,cause in [('cleanup-pure-preflight-r1','Missing reference-root argument parsing; no tests ran'),
                    ('cleanup-pure-preflight-r2','Test namespace retained function instead of module dictionary;16 ran/13errors')]:
    folder=E/('failed-'+phase.removeprefix('cleanup-'))
    raw=read(N1/phase/'stderr.txt');safe=raw.replace(str(N1).encode(),b'<N1>').replace(str(N1).replace('\\','/').encode(),b'<N1>')
    safe=safe.replace(b'\r\n',b'\n')
    put(folder/'STDERR_SANITIZED_R1.txt',safe)
    receipt=parse(N1/('FAIL_'+phase+'.json'))
    dump(folder/'FAILURE_R1.json',dict(receipt,cause=cause,rawStderrSha256=sha(raw),sanitizedStderr=True))
    failures.append({'phase':phase,'status':'FAIL_NO_ACCEPTANCE','cause':cause,'receipt':str((folder/'FAILURE_R1.json').relative_to(E))})
folder=E/'failed-reader-r1'
for src,dest in [('PRESERVED_READER_FAILURE_R1.json','FAILURE_R1.json'),('SOURCE_FREEZE_R1.json','SOURCE_FREEZE_R1.json'),
                 ('verify_cleanup_staging_r1.py','READER_SOURCE_R1.py'),('SIGNATURE_CANARY_R1.json','SIGNATURE_CANARY_R1.json'),
                 ('SIGNATURE_CANARY_R2.json','SIGNATURE_CANARY_R2.json')]:put(folder/dest,read(N1/src))
put(folder/'ACTUAL_READER_STDOUT_R1.json',read(N1/'cleanup-reader-smoke-r1/stdout.txt'))
failures.append({'phase':'cleanup-reader-smoke-r1','status':'FAIL_FILE_CHANGED','cause':'Windows cross-API ctime interpretation; within-API ctime retained in correction'})
folder=E/'failed-native-r2'
for src,dest in [('PRESERVED_NATIVE_FAILURE_R2.json','FAILURE_R1.json'),('SOURCE_FREEZE_R1.json','SOURCE_FREEZE_R1.json'),
                 ('CONTRACT_R1.md','CONTRACT_R1.md'),('REFERENCE_R1.json','REFERENCE_R1.json'),
                 ('CleanupStagingCanary.java','CANARY_SOURCE_R1.java')]:put(folder/dest,read(N2/src))
put(folder/'ACTUAL_SUITE_STDOUT_R1.json',read(N2/'cleanup-native-suite-r1/stdout.txt'))
dump(folder/'OBSERVED_CALL_R1.json',parse(N2/'cleanup-native-suite-r1/result.json'))
failures.append({'phase':'cleanup-native-suite-r1','status':'FAIL_17_PASS_4_FAIL','cause':'639-group setup assertion and three unsupported zero-credit snapshot preconditions; existing validators unchanged'})
dump(E/'SOURCE_HISTORY_R1.json',{'status':'PRESERVED_FAILURES_AND_KNOWN_SOURCE_CORRECTIONS',
 'failures':failures,'draftSourcesRetainedPrivately':True,'completeSourceHistoryClaimed':False,
 'sourceCorrections':['Constructor base versus generated Source role caught before consumption',
 'Authoring hash transcription and LF/EOF formatting corrected before freeze',
 'Pure-fixture below-sum payload and false-alias input/output expectations corrected',
 'Native closed-groupUnits and report phase expectations corrected; no native-check suppression',
 'R3 reference reproduced independently from fresh generator and old helper methods byte-identical'],
 'genuineEnvelope':'UNSPENT_GATED'})
dump(R/'ASSEMBLY_R3.json',{'status':'COPIED_PRIVATE_ACCEPTED_COMPONENT_FOR_PUBLIC_GATES',
 'sourceFreezeSha256':freeze,'proofPendingPublicGates':True,'runtimeFiles':runtime})
print('Private evidence copied; public final gate and publication proof remain required')
