"""Copy private-passing Source prerequisite for fresh public gates."""
from pathlib import Path
import argparse,hashlib,json,re,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',required=True,type=Path);a=p.parse_args();F=a.repo
T=F/'tests/qualification/rb100-layout/current-r7-cleanup-source-r1'
E=F/'docs/task-evidence/Q100/current-r7-cleanup-source-r1'
AUDIT=R.parent/'tsj-q100-current-producer-audit-r1-1c5bb3953ce04760952cadcfd33d2894'
N5=R.parent/'tsj-q100-r7-cleanup-emitter-r1-effde34b8ba54ce38645772ee1a077bb'
N6=R.parent/'tsj-q100-r7-collision-fixture-r2-94c76bfc5efa49ba9cfa99826b95ea04'
def read(path):return Path(path).read_bytes()
def parse(path):return json.loads(read(path))
def sha(raw):return hashlib.sha256(raw).hexdigest()
private=re.compile(rb'(?i)(?:(?<![A-Za-z0-9_])[A-Z]:[\\/]|[/]U[s]ers[/]|\\\\U[s]ers\\\\)')
def put(path,raw,replace=False):
    assert not private.search(raw),path.name
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and not replace:assert read(path)==raw
    else:path.write_bytes(raw)
def dump(path,value):put(path,(json.dumps(value,indent=2)+'\n').encode('ascii'))
path=R/'run_current_r7_gates_r1.py';d=types.ModuleType('_current_r7_evidence_copy');d.__file__=str(path)
exec(compile(read(path),str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
freeze=d.frozen();compile_gate=d.audit_classes()
for name in ['PURE','GENERATION','HOST','COMPILE','SMOKE','SUITE','REGRESSION']:
    assert parse(R/(name+'_GATE_R1.json'))['status'].startswith('PASS_'),name
mapping={d.A/name:F/'tests/qualification/rb100-layout/authored-flat-work-plan-r1'/name
 for name in ['AuthoredFlatWorkMembers.java','generate_authored_flat_work_overlay.py','test_flat_work_overlay.py']}
mapping.update({d.C/name:F/'tests/qualification/rb100-layout/native-body-ownership-r1'/name
 for name in ['generate_body_ownership.py','test_body_ownership_source.py']})
for source,target in mapping.items():put(target,read(source),replace=True)
put(T/'CURRENT_STUDY.java',read(R/'baseline/CURRENT_STUDY.java'))
put(T/'REFERENCE_R1.json',read(R/'REFERENCE_R1.json'))
put(T/'CONTRACT_R1.md',read(R/'CONTRACT_R1.md'))
put(T/'.gitattributes',b'* -text\n')
put(E/'.gitattributes',b'* -text\nACTUAL_*_STDOUT_R1.json -text whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol\n')
for name in ['CONTRACT_R1.md','REFERENCE_R1.json','SOURCE_FREEZE_R1.json','PURE_GATE_R1.json',
 'GENERATION_GATE_R1.json','HOST_GATE_R1.json','SMOKE_GATE_R1.json','SUITE_GATE_R1.json','REGRESSION_GATE_R1.json',
 'run_current_r7_gates_r1.py','run_current_r7_generation_r1.py','current_r7_file_policy_canary_r1.py',
 'freeze_current_r7_sources_r1.py','run_current_r7_public_r1.py','assemble_current_r7_evidence_r1.py']:
    put(E/name,read(R/name))
summary={k:compile_gate[k] for k in ['status','sourceFreezeSha256','sourceCount','call','allMajor51',
 'noDuplicateOrShadowClasses','acceptedClosureReauthenticated','actualAggregationHook','genuineEnvelope']}
summary.update(freshClasses=compile_gate['classes'],freshClassCount=len(compile_gate['classes']),
 freshClassBytes=sum(x['bytes'] for x in compile_gate['classes']),dependencyClassCount=len(compile_gate['dependencies']),
 dependencyClassBytes=sum(x['bytes'] for x in compile_gate['dependencies']),removedOldClassCount=len(compile_gate['removedOldClasses']),
 retainedFullCompileGateSha256=sha(read(R/'COMPILE_GATE_R1.json')))
dump(E/'COMPILE_SUMMARY_R1.json',summary)
for label,phase in [('GENERATION','current-r7-emit-positive-r1'),('SMOKE','current-r7-native-smoke-r1'),
 ('SUITE','current-r7-native-suite-r1'),('REGRESSION','current-r7-maintained-81-r1')]:
    put(E/('ACTUAL_'+label+'_STDOUT_R1.json'),read(R/phase/'stdout.txt'))
for name in ['FINDINGS_R1.md','SOURCE_MAP_R1.json','ADMISSION_R4.md','ADMISSION_R5.md','ADMISSION_R6.md',
 'SOURCE_INPUT_R1.json','SOURCE_INPUT_R2.json','SOURCE_INPUT_R3.json','SOURCE_INPUT_R4.json','SOURCE_INPUT_R5.json','SOURCE_INPUT_R6.json']:
    put(E/'source-audit-r1'/name,read(AUDIT/name))
failures=[]
for label,root,phase,receipt in [('failed-collision-fixtures-r1',N5,'current-r7-pure-authored-r1','PRESERVED_PURE_FAILURE_R1.json'),
 ('failed-root-identifier-r2',N6,'current-r7-pure-ownership-r1','PRESERVED_PURE_FAILURE_R2.json')]:
    folder=E/label;put(folder/'FAILURE_R1.json',read(root/receipt));put(folder/'SOURCE_FREEZE_R1.json',read(root/'SOURCE_FREEZE_R1.json'))
    stderr=read(root/phase/'stderr.txt');safe=stderr
    for form in [str(root),str(root).replace('\\','/')]:safe=safe.replace(form.encode(),b'<FAILED_CANDIDATE>')
    safe=b'\n'.join(line.rstrip(b' \t') for line in safe.splitlines())+b'\n';put(folder/'STDERR_SANITIZED_R1.txt',safe)
    dump(folder/'RAW_IDENTITIES_R1.json',{'rawStderrSha256':sha(stderr),'sanitizedStderr':True,
      'result':parse(root/phase/'result.json'),'sourceFreezeSha256':sha(read(root/'SOURCE_FREEZE_R1.json'))})
    failures.append({'phase':phase,'status':'FAIL_NO_ACCEPTANCE','packet':label,'implementationChangedForFix':False})
dump(E/'SOURCE_HISTORY_R1.json',{'status':'PRESERVED_TWO_SOURCE_TEST_FAILURES','failures':failures,
 'corrections':['Use canonical multiline retained member fixtures for line-oriented declaration parser',
 'Replace nonexistent uppercase variable token assertion with exact AST routing of four fixed Source reads'],
 'oldFrozenSourcesAndRawReceiptsRetained':True,'allTestMethodsAndRejectionAssertionsRetained':True,
 'implementationUnchangedAcrossCorrections':True,'genuineEnvelope':'UNSPENT_GATED'})
dump(R/'ASSEMBLY_R1.json',{'status':'PRIVATE_PASSING_SOURCE_COPIED_PUBLIC_GATES_PENDING',
 'sourceFreezeSha256':freeze,'changedSourcePaths':[x.relative_to(F).as_posix() for x in mapping.values()]})
print('Private Source evidence copied; public gates and acceptance proof remain required')
