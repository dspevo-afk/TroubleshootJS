"""Selected real host for pending-cleanup and detached-R7 component gates."""
from pathlib import Path
import argparse,hashlib,json,os,struct,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
B=R.parent/'tsj-q100-run-input-policy-r2-061d83b05aad44bcbc973417357e031b'
CONTRACT='212ac9daea16bafeae9feb397bfbfaafe9d752260dfe0d4a2346a364f7351443'
REFERENCE='d5744cfe7118d1f480e88d3fabec06749c32cba1c84cd0cf5379307d670d549d'
WIRE='3d0ba4ed424ae0251bfa7d151cd4da6a043f41b5183858d117906c3584ced375'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def read(path,cap=4194304):
    p=Path(path);s=p.lstat();assert not p.is_symlink() and not getattr(s,'st_file_attributes',0)&0x400
    assert 0<=s.st_size<=cap
    with p.open('rb') as f:
        a=os.fstat(f.fileno());raw=f.read(cap+1);b=os.fstat(f.fileno())
    z=p.lstat()
    def same(x):return x.st_dev,x.st_ino,x.st_size,x.st_mtime_ns
    assert same(s)==same(a)==same(b)==same(z) and len(raw)==s.st_size
    return raw
def parse(raw):
    def pairs(items):
        d={}
        for k,v in items:assert k not in d;d[k]=v
        return d
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda x:(_ for _ in ()).throw(ValueError(x)))
def write(name,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
    with (R/name).open('xb') as f:f.write(raw)
    return sha(raw)
def dependencies():
    path=B/'run_staging_gates_r1.py';raw=read(path)
    assert sha(raw)=='6477dc312bcfe8eb009f75a7bdf23d3543231d3bdd99b9d0f12f844ab8d13ff5'
    d=types.ModuleType('_accepted_run_staging_runtime');d.__file__=str(path)
    exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
    assert d.frozen()=='bb445458907e7ab757e0bb5bc2acfffd6c9352bba5909a91ca43051f3705cdd9'
    old,m=d.load_dependency();d.audit_classes()
    return d,old,m
def recheck(d,old,m):
    assert d.frozen()=='bb445458907e7ab757e0bb5bc2acfffd6c9352bba5909a91ca43051f3705cdd9'
    d.audit_classes();old.recheck_dependencies(m)
def frozen():
    for name,pin in [('CONTRACT_R1.md',CONTRACT),('REFERENCE_R1.json',REFERENCE),('WIRE_R1.json',WIRE)]:assert sha(read(R/name))==pin
    raw=read(R/'SOURCE_FREEZE_R1.json');value=parse(raw)
    assert value['status']=='ROOT_REVIEWED_FINAL_CLEANUP_STAGING_SOURCE'
    for row in value['files']:
        content=read(R/row['name']);assert len(content)==row['bytes'] and sha(content)==row['sha256']
    return sha(raw)
def call(old,m,phase,exe,argv,expected=0,limit=60000):
    return old.actual_call(R,m,phase,exe,argv,expected=expected,limit=limit)
def host(d,old,m):
    good=call(old,m,'cleanup-host-positive-final-r1',m.g.JDK/'java.exe',['-version'])
    bad=call(old,m,'cleanup-host-negative-final-r1',m.g.JDK/'java.exe',['-XtsjCleanupInvalidOption'],expected=1)
    assert b'1.8.0_502' in read(R/good['phase']/'stderr.txt')
    assert b'Unrecognized option: -XtsjCleanupInvalidOption' in read(R/bad['phase']/'stderr.txt')
    assert not read(R/good['phase']/'stdout.txt') and not read(R/bad['phase']/'stdout.txt')
    recheck(d,old,m)
    return {'status':'PASS_REAL_SELECTED_CLEANUP_HOST_CONTROLS','positive':good,'negative':bad}
def inventory(directory):
    rows=[]
    for p in sorted(directory.rglob('*')):
        assert not p.is_symlink() and not getattr(p.lstat(),'st_file_attributes',0)&0x400
        if p.is_dir():continue
        raw=read(p);assert p.suffix=='.class' and raw[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',raw[6:8])[0]==51
        rows.append({'path':p.relative_to(directory).as_posix(),'bytes':len(raw),'sha256':sha(raw),'major':51})
    assert len(rows)<=10000 and sum(x['bytes'] for x in rows)<=268435456
    return rows
def classpath(d,m):return ';'.join([str(R/'classes'),str(R/'dependencies')]+d.classpath(m).split(';')[2:])
def audit_classes():
    receipt=parse(read(R/'COMPILE_GATE_R1.json'));assert receipt['sourceFreezeSha256']==frozen()
    assert inventory(R/'classes')==receipt['classes'] and inventory(R/'dependencies')==receipt['dependencies']
    assert not {x['path'] for x in receipt['classes']}&{x['path'] for x in receipt['dependencies']}
    return receipt
def compile_component(d,old,m):
    pin=frozen();deps=R/'dependencies';deps.mkdir();(R/'classes').mkdir();(R/'empty-sourcepath').mkdir()
    merged={};removed=[]
    for source in [B/'classes',B/'dependencies']:
        for row in inventory(source):
            assert row['path'] not in merged;merged[row['path']]=row
            leaf=Path(row['path']).name
            if leaf=='CurrentAssemblyMetadata.class' or leaf.startswith('CurrentAssemblyMetadata$'):
                removed.append(row);continue
            p=deps/row['path'];p.parent.mkdir(parents=True,exist_ok=True)
            with p.open('xb') as f:f.write(read(source/row['path']))
    assert len(merged)==2508 and len(removed)==1
    names=['CurrentAssemblyMetadata.java','CleanupStagingCanary.java']
    argv=['-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-classpath',classpath(d,m),
          '-sourcepath',str(R/'empty-sourcepath'),'-d',str(R/'classes')]+[str(R/x) for x in names]
    result=call(old,m,'cleanup-jdk8-compile-r1',m.g.JDK/'javac.exe',argv)
    assert not read(R/result['phase']/'stdout.txt')
    classes=inventory(R/'classes');dep=inventory(deps)
    assert not {x['path'] for x in classes}&{x['path'] for x in dep}
    assert len(classes)+len(dep)<=10000 and sum(x['bytes'] for x in classes+dep)<=268435456
    assert len(dep)==2507 and frozen()==pin and not list((R/'empty-sourcepath').iterdir())
    recheck(d,old,m)
    return {'status':'PASS_FINAL_SOURCE_JDK8_CLEANUP_STAGING_COMPILE','sourceFreezeSha256':pin,'call':result,
        'sourceCount':2,'classes':classes,'dependencies':dep,'removedOldClasses':removed,
        'classCount':len(classes),'classBytes':sum(x['bytes'] for x in classes),'allMajor51':True,
        'noDuplicateOrShadowClasses':True,'acceptedDependencyCompileReauthenticated':True,
        'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
def pure(d,old,m):
    pin=frozen()
    result=call(old,m,'cleanup-pure-tests-r1',m.g.PY,['-I','-B',str(R/'test_cleanup_oracle.py'),'--reference-root',str(R),'-v'],limit=180000)
    import re
    match=re.search(rb'Ran (\d+) tests? in [0-9.]+s\s+OK\s*\Z',read(R/result['phase']/'stderr.txt'))
    assert match and int(match[1])==16 and not read(R/result['phase']/'stdout.txt') and frozen()==pin
    recheck(d,old,m)
    return {'status':'PASS_CLEANUP_INDEPENDENT_PURE_TESTS','testCount':int(match[1]),'call':result}
def native(d,old,m,mode):
    audit_classes();pin=frozen()
    result=call(old,m,'cleanup-native-'+mode+'-r1',m.g.JDK/'java.exe',['-cp',classpath(d,m),
        'com.lushprojects.circuitjs1.client.CleanupStagingCanary',mode.upper()])
    assert not read(R/result['phase']/'stderr.txt') and frozen()==pin
    reader=call(old,m,'cleanup-reader-'+mode+'-r1',m.g.PY,['-I','-B',str(R/'verify_cleanup_staging_r1.py'),
        '--case',mode,'--input-json',str(R/result['phase']/'stdout.txt'),'--reference-root',str(R)],limit=180000)
    assert not read(R/reader['phase']/'stderr.txt')
    summary=parse(read(R/reader['phase']/'stdout.txt',1048576))
    assert summary['status']=='PASS_INDEPENDENT_CLEANUP_STAGING_READER'
    recheck(d,old,m);audit_classes()
    return {'status':'PASS_ACTUAL_CLEANUP_'+mode.upper()+'_AND_INDEPENDENT_READER','call':result,'reader':reader,'summary':summary}
def regression(d,old,m):
    audit_classes();pin=frozen()
    result=call(old,m,'cleanup-maintained-81-r1',m.g.JDK/'java.exe',['-cp',classpath(d,m),
        'com.lushprojects.circuitjs1.client.CurrentV2InputSetupCanary','10007'])
    v=parse(read(R/result['phase']/'stdout.txt',1048576))
    assert v=={'status':'PASS_SYNTHETIC_V2_INPUT_SETUP_COMPONENT','cases':81,'assertions':12089,
        'successfulUnits':10007,'validatorUnits':9140,'copiedEntries':478,'groups':1,'copyChunks':3,
        'freshPositiveMeters':2,'ownerInstances':0,'sessions':0,'originalCaptures':0,'routingRuns':0,'normalHostAcceptance':False}
    assert not read(R/result['phase']/'stderr.txt') and frozen()==pin
    recheck(d,old,m)
    return {'status':'PASS_MAINTAINED_RUN_81_12089_ON_CURRENT_METADATA','call':result,'summary':v}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--case',required=True,choices=['host','compile','pure','smoke','suite','regression']);a=p.parse_args()
    d,old,m=dependencies()
    if a.case in ('smoke','suite'):value=native(d,old,m,a.case)
    else:value=globals()[{'compile':'compile_component'}.get(a.case,a.case)](d,old,m)
    name={'host':'HOST_GATE_R1.json','compile':'COMPILE_GATE_R1.json','pure':'PURE_GATE_R1.json','smoke':'SMOKE_GATE_R1.json','suite':'SUITE_GATE_R1.json','regression':'REGRESSION_GATE_R1.json'}[a.case]
    print(json.dumps({'status':value['status'],'receipt':name,'sha256':write(name,value)}))
