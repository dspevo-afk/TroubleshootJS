"""Selected real host for synthetic input and pre-body staging components."""
from pathlib import Path
import argparse,hashlib,json,os,struct,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
U=R.parent/'tsj-q100-result-projections-6f06ff895ac54a989055a0bd8f1cd61a'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def read(path,cap=4194304):
    p=Path(path);s=p.lstat();assert not p.is_symlink() and not getattr(s,'st_file_attributes',0)&0x400
    assert 0<=s.st_size<=cap
    with p.open('rb') as f:
        a=os.fstat(f.fileno());raw=f.read(cap+1);b=os.fstat(f.fileno())
    z=p.lstat()
    def identity(x):return x.st_dev,x.st_ino,x.st_size,x.st_mtime_ns
    assert identity(s)==identity(a)==identity(b)==identity(z) and len(raw)==s.st_size
    return raw
def parse(raw):
    def pairs(rows):
        d={}
        for k,v in rows:assert k not in d;d[k]=v
        return d
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda x:(_ for _ in ()).throw(ValueError(x)))
def write(name,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
    with (R/name).open('xb') as f:f.write(raw)
    return sha(raw)
def load_dependency():
    freeze=read(U/'SOURCE_FREEZE_R1.json')
    assert sha(freeze)=='dacf1c353dbb972457c612f0a9fde42529cc25ea377791a658bcc1b268844e4e'
    pin=next(x['sha256'] for x in parse(freeze)['files'] if x['role']=='root_projection_gates_r1.py')
    path=U/'root_projection_gates_r1.py';raw=read(path);assert sha(raw)==pin
    d=types.ModuleType('_accepted_projection_runtime');d.__file__=str(path)
    exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
    m=d.dependency_runtime(U);d.classes(U,m)
    return d,m
def frozen():
    raw=read(R/'SOURCE_FREEZE_R1.json');f=parse(raw)
    assert f['status']=='ROOT_REVIEWED_FINAL_RUN_POLICY_STAGING_SOURCE'
    for row in f['files']:
        content=read(R/row['name']);assert len(content)==row['bytes'] and sha(content)==row['sha256']
    assert sha(read(R/'CONTRACT_R1.md'))=='1e03b5b290eaf5acf3f6a628c72b629c5edeeea11446fb127ed554c81d76354c'
    return sha(raw)
def call(d,m,phase,exe,argv,expected=0,limit=60000):
    return d.actual_call(R,m,phase,exe,argv,limit=limit,expected=expected)
def host(d,m,final=False):
    suffix='r2' if final else 'r1'
    if final:frozen()
    good=call(d,m,'run-host-positive-'+suffix,m.g.JDK/'java.exe',['-version'])
    bad=call(d,m,'run-host-negative-'+suffix,m.g.JDK/'java.exe',['-XtsjRunStagingInvalidOption'],expected=1)
    assert b'1.8.0_502' in read(R/good['phase']/'stderr.txt')
    assert b'Unrecognized option: -XtsjRunStagingInvalidOption' in read(R/bad['phase']/'stderr.txt')
    assert not read(R/good['phase']/'stdout.txt') and not read(R/bad['phase']/'stdout.txt')
    d.recheck_dependencies(m)
    return {'status':'PASS_REAL_SELECTED_JDK8_HOST_CANARIES','positive':good,'negative':bad}
def pure(d,m):
    pin=frozen()
    result=call(d,m,'run-profile-pure-r1',m.g.PY,['-I','-B',str(R/'test_run_profile_r1.py'),
        '--reference-root',str(R),'--codec-source',str(R/'source/context_codec.py'),'-v'],limit=180000)
    stderr=read(R/result['phase']/'stderr.txt')
    import re
    need=re.search(rb'Ran 12 tests? in [0-9.]+s\s+OK\s*\Z',stderr)
    assert need and not read(R/result['phase']/'stdout.txt') and frozen()==pin
    d.recheck_dependencies(m)
    return {'status':'PASS_12_FRESH_PROFILE_PURE_TESTS','call':result,'testCount':12}
REPLACED=('CurrentV2InputSetup','CurrentV2InputSetupCanary','CurrentAssemblyMetadata','CohortTerminalTelemetryInputV2')
def inventory(directory):
    rows=[]
    for p in sorted(directory.rglob('*')):
        assert not p.is_symlink() and not getattr(p.lstat(),'st_file_attributes',0)&0x400
        if p.is_dir():continue
        raw=read(p);assert p.suffix=='.class' and raw[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',raw[6:8])[0]==51
        rows.append({'path':p.relative_to(directory).as_posix(),'bytes':len(raw),'sha256':sha(raw),'major':51})
    assert len(rows)<=10000 and sum(x['bytes'] for x in rows)<=268435456
    return rows
def replaced(path):
    leaf=Path(path).name[:-6]
    return any(leaf==n or leaf.startswith(n+'$') for n in REPLACED)
def classpath(m):
    # Keep authenticated jar order, without old classes in the accepted classpath.
    parts=m.classpath.split(';')
    assert parts[0]==str(m.classes) and all(Path(x).suffix=='.jar' for x in parts[1:])
    return ';'.join([str(R/'classes'),str(R/'dependencies')]+parts[1:])
def audit_classes():
    receipt=parse(read(R/'COMPILE_GATE_R1.json'))
    assert receipt['sourceFreezeSha256']==frozen()
    assert inventory(R/'classes')==receipt['classes']
    assert inventory(R/'dependencies')==receipt['dependencies']
    a={x['path'] for x in receipt['classes']};b={x['path'] for x in receipt['dependencies']}
    assert not a&b and not any(replaced(x) for x in b)
    return receipt
def compile_component(d,m):
    pin=frozen();deps=R/'dependencies';deps.mkdir();(R/'classes').mkdir();(R/'empty-sourcepath').mkdir()
    removed=[]
    for row in inventory(m.classes):
        if replaced(row['path']):removed.append(row);continue
        p=deps/row['path'];p.parent.mkdir(parents=True,exist_ok=True)
        with p.open('xb') as f:f.write(read(m.classes/row['path']))
    p=U/'classes/com/lushprojects/circuitjs1/client/CurrentResultProjections.class'
    target=deps/p.relative_to(U/'classes');assert not target.exists()
    with target.open('xb') as f:f.write(read(p))
    names=['CurrentV2InputSetup.java','CurrentV2InputSetupCanary.java','CurrentAssemblyMetadata.java',
           'CohortTerminalTelemetryInputV2.java','CurrentRunStagingCanary.java']
    argv=['-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-classpath',classpath(m),
          '-sourcepath',str(R/'empty-sourcepath'),'-d',str(R/'classes')]+[str(R/x) for x in names]
    result=call(d,m,'run-staging-jdk8-compile-r1',m.g.JDK/'javac.exe',argv)
    assert not read(R/result['phase']/'stdout.txt')
    classes=inventory(R/'classes');dependencies=inventory(deps)
    assert len(classes)+len(dependencies)<=10000 and sum(x['bytes'] for x in classes+dependencies)<=268435456
    assert not list((R/'empty-sourcepath').iterdir()) and frozen()==pin
    d.recheck_dependencies(m)
    return {'status':'PASS_FINAL_SOURCE_JDK8_RUN_STAGING_COMPILE','sourceFreezeSha256':pin,'call':result,
            'sourceCount':5,'classes':classes,'dependencies':dependencies,'removedOldClasses':removed,
            'classCount':len(classes),'classBytes':sum(x['bytes'] for x in classes),'allMajor51':True,
            'noDuplicateOrShadowClasses':True,'acceptedDependencyCompileReauthenticated':True,
            'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
def regression(d,m):
    audit_classes();pin=frozen()
    result=call(d,m,'run-input-81-regression-r1',m.g.JDK/'java.exe',['-cp',classpath(m),
        'com.lushprojects.circuitjs1.client.CurrentV2InputSetupCanary','10007'])
    value=parse(read(R/result['phase']/'stdout.txt',1048576))
    assert value=={'status':'PASS_SYNTHETIC_V2_INPUT_SETUP_COMPONENT','cases':81,'assertions':12089,
        'successfulUnits':10007,'validatorUnits':9140,'copiedEntries':478,'groups':1,'copyChunks':3,
        'freshPositiveMeters':2,'ownerInstances':0,'sessions':0,'originalCaptures':0,'routingRuns':0,
        'normalHostAcceptance':False}
    assert not read(R/result['phase']/'stderr.txt') and frozen()==pin
    d.recheck_dependencies(m)
    return {'status':'PASS_MAINTAINED_81_CASE_12089_ASSERTION_NATIVE_REGRESSION','call':result,'summary':value}
def staging(d,m):
    audit_classes();pin=frozen()
    result=call(d,m,'run-staging-suite-r1',m.g.JDK/'java.exe',['-cp',classpath(m),
        'com.lushprojects.circuitjs1.client.CurrentRunStagingCanary','SUITE'])
    assert not read(R/result['phase']/'stderr.txt') and frozen()==pin
    verify=call(d,m,'run-staging-reader-r1',m.g.PY,['-I','-B',str(R/'verify_run_staging_r1.py'),
        '--input-json',str(R/result['phase']/'stdout.txt'),'--reference-root',str(R)],limit=180000)
    assert not read(R/verify['phase']/'stderr.txt')
    d.recheck_dependencies(m);audit_classes()
    return {'status':'PASS_ACTUAL_NATIVE_STAGING_AND_INDEPENDENT_READER','call':result,'reader':verify,
            'summary':parse(read(R/verify['phase']/'stdout.txt',1048576))}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--case',choices=('host','host-final','pure','compile','regression','staging'),required=True)
    a=p.parse_args();d,m=load_dependency()
    value=host(d,m,True) if a.case=='host-final' else globals()[{'compile':'compile_component'}.get(a.case,a.case)](d,m)
    name={'host':'HOST_GATE_R1.json','host-final':'HOST_GATE_R2.json','pure':'PURE_GATE_R1.json','compile':'COMPILE_GATE_R1.json','regression':'REGRESSION_GATE_R1.json','staging':'STAGING_GATE_R1.json'}[a.case]
    pin=write(name,value);print(json.dumps({'status':value['status'],'receipt':name,'sha256':pin}))
