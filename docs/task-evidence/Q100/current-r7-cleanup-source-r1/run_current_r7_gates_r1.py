"""Selected host gates for the current R7 Source-emission prerequisite."""
from pathlib import Path
import argparse,hashlib,json,os,re,struct,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
P=R.parent/'tsj-q100-cleanup-credit-r3-fe1e3f00cd6a4fa5863cb1ee025df489'
A=R/'working/authored-flat-work-plan-r1'
C=R/'working/native-body-ownership-r1'
PREVIOUS_FREEZE='b40afa038a477acc7300765509f4b5b8ca00c70415fb8265b90de973ed2c1615'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def read(path,cap=4194304):
    p=Path(path);s=p.lstat();assert not p.is_symlink() and not getattr(s,'st_file_attributes',0)&0x400
    assert 0<=s.st_size<=cap
    with p.open('rb') as f:
        opened=os.fstat(f.fileno());raw=f.read(cap+1);closed=os.fstat(f.fileno())
    after=p.lstat()
    def identity(x):return x.st_dev,x.st_ino,x.st_size,x.st_mtime_ns
    assert identity(s)==identity(opened)==identity(closed)==identity(after) and len(raw)==s.st_size
    return raw
def parse(raw):
    def pairs(items):
        value={}
        for key,item in items:assert key not in value;value[key]=item
        return value
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda x:(_ for _ in ()).throw(ValueError(x)))
def write(name,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
    with (R/name).open('xb') as f:f.write(raw)
    return sha(raw)
def dependency():
    freeze=read(P/'SOURCE_FREEZE_R1.json');assert sha(freeze)==PREVIOUS_FREEZE
    row=next(x for x in parse(freeze)['files'] if x['name']=='run_cleanup_gates_r1.py')
    path=P/row['name'];raw=read(path);assert len(raw)==row['bytes'] and sha(raw)==row['sha256']
    d=types.ModuleType('_accepted_cleanup_source_gate');d.__file__=str(path)
    exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
    assert d.frozen()==PREVIOUS_FREEZE;d.audit_classes()
    b,old,m=d.dependencies();return d,b,old,m
def recheck(d,b,old,m):
    assert d.frozen()==PREVIOUS_FREEZE;d.audit_classes();d.recheck(b,old,m)
def frozen():
    raw=read(R/'SOURCE_FREEZE_R1.json');value=parse(raw)
    assert value['status']=='ROOT_REVIEWED_CURRENT_R7_EMITTER_FINAL_SOURCE'
    for row in value['files']:
        content=read(R/row['name']);assert len(content)==row['bytes'] and sha(content)==row['sha256']
    return sha(raw)
def call(old,m,phase,exe,args,expected=0,limit=60000):return old.actual_call(R,m,phase,exe,args,expected=expected,limit=limit)
def host(d,b,old,m):
    good=call(old,m,'current-r7-host-positive-r1',m.g.JDK/'java.exe',['-version'])
    bad=call(old,m,'current-r7-host-negative-r1',m.g.JDK/'java.exe',['-XtsjCurrentR7InvalidOption'],expected=1)
    assert b'1.8.0_502' in read(R/good['phase']/'stderr.txt')
    assert b'Unrecognized option: -XtsjCurrentR7InvalidOption' in read(R/bad['phase']/'stderr.txt')
    assert not read(R/good['phase']/'stdout.txt') and not read(R/bad['phase']/'stdout.txt')
    recheck(d,b,old,m)
    return {'status':'PASS_REAL_CURRENT_R7_HOST_CONTROLS','positive':good,'negative':bad}
def pure(d,b,old,m):
    pin=frozen();calls=[];counts=[]
    for label,script,args in [
        ('authored',A/'test_flat_work_overlay.py',['--study-source',str(R/'baseline/CURRENT_STUDY.java')]),
        ('ownership',C/'test_body_ownership_source.py',['--study-source',str(R/'baseline/CURRENT_STUDY.java'),'--authored-root',str(A)])]:
        c=call(old,m,'current-r7-pure-'+label+'-r1',m.g.PY,['-I','-B',str(script)]+args+['-v'],limit=180000)
        assert not read(R/c['phase']/'stdout.txt')
        match=re.search(rb'Ran (\d+) tests? in [0-9.]+s\s+OK\s*\Z',read(R/c['phase']/'stderr.txt'))
        assert match;counts.append(int(match[1]));calls.append(c)
    assert counts[0]>=14 and counts[1]>=11 and sum(counts)>=25 and frozen()==pin
    recheck(d,b,old,m)
    return {'status':'PASS_CURRENT_R7_EMITTER_PURE_TESTS','counts':counts,'testCount':sum(counts),'calls':calls}
def inventory(directory):
    rows=[]
    for path in sorted(directory.rglob('*')):
        assert not path.is_symlink() and not getattr(path.lstat(),'st_file_attributes',0)&0x400
        if path.is_dir():continue
        raw=read(path);assert path.suffix=='.class' and raw[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',raw[6:8])[0]==51
        rows.append({'path':path.relative_to(directory).as_posix(),'bytes':len(raw),'sha256':sha(raw),'major':51})
    assert len(rows)<=10000 and sum(x['bytes'] for x in rows)<=268435456
    return rows
def classpath(d,b,m):return ';'.join([str(R/'classes'),str(R/'dependencies')]+d.classpath(b,m).split(';')[2:])
def audit_classes():
    receipt=parse(read(R/'COMPILE_GATE_R1.json'));assert receipt['sourceFreezeSha256']==frozen()
    assert inventory(R/'classes')==receipt['classes'] and inventory(R/'dependencies')==receipt['dependencies']
    assert not {x['path'] for x in receipt['classes']}&{x['path'] for x in receipt['dependencies']}
    return receipt
def compile_component(d,b,old,m):
    pin=frozen();generation=parse(read(R/'GENERATION_GATE_R1.json'))
    assert generation['status']=='PASS_REAL_CURRENT_R7_SOURCE_EMISSION_AND_CONTROLS'
    final=R/'emitted-source-r1/CohortTerminalTelemetryStudy.java'
    expected=parse(read(R/'REFERENCE_R1.json'))['expectedFinalStudy']
    content=read(final);assert sha(content)==expected['sha256'] and len(content)==expected['bytes']
    deps=R/'dependencies';deps.mkdir();(R/'classes').mkdir();(R/'empty-sourcepath').mkdir()
    replaced=('CohortTerminalTelemetryStudy','CurrentAssemblyMetadata','CurrentBodyOwnership','CleanupStagingCanary')
    merged={};removed=[]
    for source in [P/'classes',P/'dependencies']:
        for row in inventory(source):
            assert row['path'] not in merged;merged[row['path']]=row
            leaf=Path(row['path']).name
            if any(leaf==name+'.class' or leaf.startswith(name+'$') for name in replaced):removed.append(row);continue
            target=deps/row['path'];target.parent.mkdir(parents=True,exist_ok=True)
            with target.open('xb') as f:f.write(read(source/row['path']))
    assert len(merged)==2518 and removed
    names=[final,R/'baseline/CurrentAssemblyMetadata.java',C/'CurrentBodyOwnership.java',P/'CleanupStagingCanary.java']
    args=['-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-classpath',classpath(d,b,m),
        '-sourcepath',str(R/'empty-sourcepath'),'-d',str(R/'classes')]+[str(x) for x in names]
    result=call(old,m,'current-r7-jdk8-compile-r1',m.g.JDK/'javac.exe',args)
    assert not read(R/result['phase']/'stdout.txt')
    classes=inventory(R/'classes');dependencies=inventory(deps)
    assert not {x['path'] for x in classes}&{x['path'] for x in dependencies}
    assert len(classes)+len(dependencies)<=10000 and sum(x['bytes'] for x in classes+dependencies)<=268435456
    assert frozen()==pin and not list((R/'empty-sourcepath').iterdir());recheck(d,b,old,m)
    return {'status':'PASS_FINAL_SOURCE_JDK8_CURRENT_R7_COMPILE','sourceFreezeSha256':pin,
        'sourceCount':4,'call':result,'classes':classes,'dependencies':dependencies,'removedOldClasses':removed,
        'allMajor51':True,'noDuplicateOrShadowClasses':True,'acceptedClosureReauthenticated':True,
        'actualAggregationHook':'NOT_RUN_UNSPENT_GATED','genuineEnvelope':'UNSPENT_GATED'}
def native(d,b,old,m,mode):
    pin=frozen();audit_classes()
    result=call(old,m,'current-r7-native-'+mode+'-r1',m.g.JDK/'java.exe',['-cp',classpath(d,b,m),
        'com.lushprojects.circuitjs1.client.CleanupStagingCanary',mode.upper()])
    assert not read(R/result['phase']/'stderr.txt')
    reader=call(old,m,'current-r7-reader-'+mode+'-r1',m.g.PY,['-I','-B',str(P/'verify_cleanup_staging_r1.py'),
        '--case',mode,'--input-json',str(R/result['phase']/'stdout.txt'),'--reference-root',str(P)],limit=180000)
    assert not read(R/reader['phase']/'stderr.txt')
    summary=parse(read(R/reader['phase']/'stdout.txt',1048576))
    assert summary['status']=='PASS_INDEPENDENT_CLEANUP_STAGING_READER' and summary['caseCount']==(2 if mode=='smoke' else 22)
    assert frozen()==pin;recheck(d,b,old,m);audit_classes()
    return {'status':'PASS_CURRENT_STUDY_DETACHED_'+mode.upper()+'_AND_READER','call':result,'reader':reader,
        'summary':summary,'actualAggregationHook':'NOT_RUN_UNSPENT_GATED'}
def regression(d,b,old,m):
    pin=frozen();audit_classes()
    result=call(old,m,'current-r7-maintained-81-r1',m.g.JDK/'java.exe',['-cp',classpath(d,b,m),
        'com.lushprojects.circuitjs1.client.CurrentV2InputSetupCanary','10007'])
    value=parse(read(R/result['phase']/'stdout.txt',1048576))
    expected=parse(read(P/'REGRESSION_GATE_R1.json'))['summary']
    assert value==expected and value['cases']==81 and value['assertions']==12089
    assert not read(R/result['phase']/'stderr.txt') and frozen()==pin;recheck(d,b,old,m)
    return {'status':'PASS_CURRENT_STUDY_RUN_81_12089','call':result,'summary':value}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--case',required=True,choices=['host','pure','compile','smoke','suite','regression']);a=p.parse_args()
    d,b,old,m=dependency()
    if a.case in ('smoke','suite'):value=native(d,b,old,m,a.case)
    else:value=globals()[{'compile':'compile_component'}.get(a.case,a.case)](d,b,old,m)
    name={'host':'HOST','pure':'PURE','compile':'COMPILE','smoke':'SMOKE','suite':'SUITE','regression':'REGRESSION'}[a.case]+'_GATE_R1.json'
    print(json.dumps({'status':value['status'],'receipt':name,'sha256':write(name,value)}))
