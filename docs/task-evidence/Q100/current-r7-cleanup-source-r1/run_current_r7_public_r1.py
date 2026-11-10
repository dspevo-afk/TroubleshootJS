"""Fresh repository entry points and exact Source/process proof after copy."""
from pathlib import Path
import argparse,hashlib,json,re,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',required=True,type=Path);args=p.parse_args();F=args.repo
FA=F/'tests/qualification/rb100-layout/authored-flat-work-plan-r1'
FC=F/'tests/qualification/rb100-layout/native-body-ownership-r1'
FT=F/'tests/qualification/rb100-layout/current-r7-cleanup-source-r1'
FR=F/'tests/qualification/rb100-layout/current-cleanup-staging-r1'
path=R/'run_current_r7_gates_r1.py';raw=path.read_bytes();freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
assert hashlib.sha256(raw).hexdigest()==next(x['sha256'] for x in freeze['files'] if x['name']==path.name)
d=types.ModuleType('_current_r7_public_gate');d.__file__=str(path)
exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
previous,b,old,m=d.dependency();pin=d.frozen();d.audit_classes()
mapping={d.A/name:FA/name for name in ['AuthoredFlatWorkMembers.java','generate_authored_flat_work_overlay.py','test_flat_work_overlay.py']}
mapping.update({d.C/name:FC/name for name in ['CurrentBodyOwnership.java','generate_body_ownership.py','test_body_ownership_source.py']})
mapping[R/'baseline/CURRENT_STUDY.java']=FT/'CURRENT_STUDY.java'
rows=[]
for source,target in mapping.items():
    content=d.read(target);assert content==d.read(source)
    rows.append({'path':target.relative_to(F).as_posix(),'bytes':len(content),'sha256':d.sha(content)})
pure_calls=[];counts=[]
for label,script,options in [('authored',FA/'test_flat_work_overlay.py',['--study-source',str(FT/'CURRENT_STUDY.java')]),
 ('ownership',FC/'test_body_ownership_source.py',['--study-source',str(FT/'CURRENT_STUDY.java'),'--authored-root',str(FA)])]:
    call=d.call(old,m,'current-r7-public-pure-'+label+'-r1',m.g.PY,['-I','-B',str(script)]+options+['-v'],limit=180000)
    match=re.search(rb'Ran (\d+) tests? in [0-9.]+s\s+OK\s*\Z',d.read(R/call['phase']/'stderr.txt'))
    assert match and not d.read(R/call['phase']/'stdout.txt');counts.append(int(match[1]));pure_calls.append(call)
assert counts==[14,12]
output=R/'public-emitted-source-r1';output.mkdir()
call=d.call(old,m,'current-r7-public-emission-r1',m.g.PY,['-I','-B',str(FC/'generate_body_ownership.py'),
 '--source-root',str(FT),'--component-root',str(FC),'--authored-root',str(FA),'--output-root',str(output)])
assert not d.read(R/call['phase']/'stderr.txt')
private=d.read(R/'current-r7-emit-positive-r1/stdout.txt',1048576)
assert d.read(R/call['phase']/'stdout.txt',1048576)==private
assert d.read(output/'CohortTerminalTelemetryStudy.java')==d.read(R/'emitted-source-r1/CohortTerminalTelemetryStudy.java')
reader_inputs=['verify_cleanup_staging_r1.py','cleanup_oracle.py','ROOT_ORACLE_SOURCE_R1.json','CONTRACT_R1.md','REFERENCE_R1.json','WIRE_R1.json']
for name in reader_inputs:assert d.read(FR/name)==d.read(d.P/name)
readers=[]
for mode in ('smoke','suite'):
    reader=d.call(old,m,'current-r7-public-reader-'+mode+'-r1',m.g.PY,['-I','-B',str(FR/'verify_cleanup_staging_r1.py'),
        '--case',mode,'--input-json',str(R/('current-r7-native-'+mode+'-r1')/'stdout.txt'),'--reference-root',str(FR)],limit=180000)
    assert not d.read(R/reader['phase']/'stderr.txt')
    assert d.parse(d.read(R/reader['phase']/'stdout.txt'))['status']=='PASS_INDEPENDENT_CLEANUP_STAGING_READER';readers.append(reader)
for source,target in mapping.items():assert d.read(source)==d.read(target)
assert d.frozen()==pin;d.audit_classes();d.recheck(previous,b,old,m)
expected={name:0 for name in ['current-r7-host-positive-r1','current-r7-emit-positive-r1',
 'current-r7-real-file-policy-r1','current-r7-pure-authored-r1','current-r7-pure-ownership-r1',
 'current-r7-jdk8-compile-r1','current-r7-native-smoke-r1','current-r7-reader-smoke-r1',
 'current-r7-native-suite-r1','current-r7-reader-suite-r1','current-r7-maintained-81-r1',
 'current-r7-public-pure-authored-r1','current-r7-public-pure-ownership-r1',
 'current-r7-public-emission-r1','current-r7-public-reader-smoke-r1','current-r7-public-reader-suite-r1']}
expected['current-r7-host-negative-r1']=1
for name in ['source-pin','helper-pin','module-pin','template-pin','existing-output','old-cli','junction']:
    expected['current-r7-emit-'+name+'-r1']=2
seen=set();calls=[]
for folder in sorted(R.iterdir()):
    if folder.is_dir() and folder.name.startswith('current-r7-') and (folder/'result.json').is_file():
        assert folder.name in expected
        assert d.parse(d.read(folder/'result.json'))['exitCode']==expected[folder.name]
        record=m.g.audit_call(folder,expected[folder.name]);assert record['terminationProven'] and record['ownedRootAndLogsAbsent']
        calls.append(record);seen.add(folder.name)
assert seen==set(expected) and len(calls)==24
value={'status':'PASS_CURRENT_R7_PUBLIC_BYTES_SOURCE_TESTS_EMIT_READERS_PROCESS_AUDIT',
 'sourceFreezeSha256':pin,'publicSource':rows,'pureCounts':counts,'pureCalls':pure_calls,
 'actualPublicEmission':call,'actualManifestSha256':d.sha(private),'readers':readers,
 'actualCallsReauthenticated':calls,'publicGateSourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
 'nativeNotRerunForExactCopiedBytes':True,'actualAggregationHook':'NOT_RUN_UNSPENT_GATED','genuineEnvelope':'UNSPENT_GATED'}
print(d.write('FINAL_PUBLIC_GATE_R1.json',value))
