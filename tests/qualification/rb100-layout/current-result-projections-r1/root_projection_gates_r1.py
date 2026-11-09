"""Root gates for two current result projection adapters, never a producer."""
from pathlib import Path
import argparse,hashlib,json,os,struct,sys,time,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
SOURCE_CAP=4194304
REPORT_CAP=1048576
CONTRACT_SHA='24f40e39a9d52704a51aefbdf05170c25056447009c4b25605e45ad9de04fd4f'
REFERENCE_SHA='bd0c6f081a6a9d11c47befe531debc87ddbccf02541542253b202a6debe491bc'
WIRE_SHA='b5b20358c18806889b53c8394be627bd5078f0fd7c282e09e285f0f60a357b59'
JAVA_CLASS='com.lushprojects.circuitjs1.client.CurrentResultProjectionsCanary'
JAVA_SOURCES=('CurrentResultProjections.java','CurrentResultProjectionsCanary.java')
PY_SOURCES=('projection_oracle.py','verify_projection_r1.py','test_projection_oracle.py')

def sha(raw):return hashlib.sha256(raw).hexdigest()
def read(path,cap=SOURCE_CAP,empty=False):
    path=Path(path);st=path.lstat()
    assert not path.is_symlink() and not getattr(st,'st_file_attributes',0)&0x400
    assert (0 if empty else 1)<=st.st_size<=cap
    with path.open('rb') as source:
        handle=os.fstat(source.fileno());chunks=[];size=0
        assert (st.st_dev,st.st_ino,st.st_size)==(handle.st_dev,handle.st_ino,handle.st_size)
        while True:
            raw=source.read(min(4096,cap+1-size))
            if not raw:break
            chunks.append(raw);size+=len(raw);assert size<=cap
        after=os.fstat(source.fileno())
    final=path.lstat()
    def identity(v):return v.st_dev,v.st_ino,v.st_size,v.st_mtime_ns
    assert identity(st)==identity(handle)==identity(after)==identity(final)
    assert size==st.st_size
    return b''.join(chunks)
def no_duplicates(rows):
    value={}
    for key,item in rows:
        assert key not in value;value[key]=item
    return value
def parse(raw):return json.loads(raw,object_pairs_hook=no_duplicates,parse_constant=lambda _:(_ for _ in ()).throw(ValueError('nonfinite')))
def write(path,value):
    raw=(json.dumps(value,indent=2,ensure_ascii=True)+'\n').encode('ascii')
    with Path(path).open('xb') as output:output.write(raw)
    return sha(raw)
def info(path,role=None):
    raw=read(path)
    return {'role':role or Path(path).name,'bytes':len(raw),'sha256':sha(raw)}

def dependency_runtime(U):
    Q=U.parent/'tsj-q100-current-export-composition-ff7bb69379ae48eda94f89b3c69edc68'
    source=Q/'run_export_composition_r1.py';raw=read(source)
    assert sha(raw)=='c7514ea198a9f569302bea77093a3dcd1ab36e9775ca77ee3824b39180fc9610'
    marker=b'_audit_compile()\nif MODE == "suite":\n    pins.extend(_accepted_smoke())'
    assert raw.count(marker)==1
    module=types.ModuleType('_accepted_projection_dependency_runtime');module.__file__=str(source)
    saved=sys.argv
    try:
        sys.argv=[str(source),'--case','suite']
        exec(compile(raw.split(marker)[0],str(source),'exec',dont_inherit=True,optimize=0),module.__dict__)
    finally:sys.argv=saved
    module._audit_compile();module._accepted_smoke()
    assert module._recheck(tuple(module.pins),module.jars,module.classes,module.inventory,module.tools)==(2486,11953557)
    return module

def recheck_dependencies(m):
    assert m._recheck(tuple(m.pins),m.jars,m.classes,m.inventory,m.tools)==(2486,11953557)
    m._audit_compile();m._accepted_smoke()

def host(U,m):
    positive=actual_call(U,m,'projection-host-jdk8-positive-r1',m.g.JDK/'java.exe',['-version'])
    negative=actual_call(U,m,'projection-host-jdk8-negative-r1',m.g.JDK/'java.exe',
                        ['-XtsjProjectionInvalidOption'],expected=1)
    for item in (positive,negative):
        assert not read(U/item['phase']/'stdout.txt',REPORT_CAP,empty=True)
    version=read(U/positive['phase']/'stderr.txt',REPORT_CAP)
    rejected=read(U/negative['phase']/'stderr.txt',REPORT_CAP)
    assert b'1.8.0_502' in version and b'Unrecognized option: -XtsjProjectionInvalidOption' in rejected
    recheck_dependencies(m)
    return {'status':'PASS_SELECTED_REAL_JDK8_HOST_POSITIVE_AND_NEGATIVE_CANARIES',
            'positive':positive,'negative':negative,'versionStderrSha256':sha(version),
            'rejectionStderrSha256':sha(rejected),'actualComponentCalls':0,'nativeProducerCalls':0,
            'genuineEnvelope':'UNSPENT_GATED'}

def actual_call(U,m,name,exe,argv,limit=60000,expected=0):
    old_h,old_r=m.root.H,m.g.R
    try:
        m.root.H=U;m.g.R=U
        result=m.root.call(name,exe,argv,limit=limit,expected=expected)
        assert m.g.audit_call(U/name,expected)==result
        return result
    finally:m.root.H=old_h;m.g.R=old_r

def frozen(U):
    for name,digest in (('PROJECTION_CONTRACT_R1.md',CONTRACT_SHA),('REFERENCE_R1.json',REFERENCE_SHA),('CANARY_WIRE_R1.json',WIRE_SHA)):
        assert sha(read(U/name))==digest
    raw=read(U/'SOURCE_FREEZE_R1.json');value=parse(raw)
    assert value['status']=='ROOT_REVIEWED_FINAL_PROJECTION_COMPONENT_SOURCE'
    names=set()
    for row in value['files']:
        assert set(row)=={'role','bytes','sha256'} and row['role'] not in names
        names.add(row['role']);content=read(U/row['role'])
        assert len(content)==row['bytes'] and sha(content)==row['sha256']
    assert set(JAVA_SOURCES+PY_SOURCES+('ROOT_ORACLE_SOURCE_R1.json','root_projection_gates_r1.py'))<=names
    return sha(raw),value

def classes(U,m):
    build_raw=read(U/'COMPILE_GATE_R1.json');build=parse(build_raw)
    assert build['status']=='PASS_FINAL_SOURCE_JDK8_PROJECTION_COMPONENT_COMPILE'
    assert build['sourceFreezeSha256']==frozen(U)[0]
    directory=U/'classes';rows=[];total=0
    for path in sorted(directory.rglob('*')):
        assert not path.is_symlink() and not getattr(path.lstat(),'st_file_attributes',0)&0x400
        if not path.is_file():continue
        assert path.suffix=='.class'
        relative=path.relative_to(directory).as_posix()
        assert relative.startswith('com/lushprojects/circuitjs1/client/CurrentResultProjections')
        assert not (m.classes/relative).exists()
        raw=read(path);assert raw[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',raw[6:8])[0]==51
        rows.append({'path':relative,'bytes':len(raw),'sha256':sha(raw),'major':51});total+=len(raw)
    assert rows==build['classes'] and 2<=len(rows)<=32 and total==build['classBytes']
    assert total+11953557<=268435456 and len(rows)+2486<=10000
    return build,sha(build_raw),str(directory)+';'+m.classpath

def pure(U,m):
    freeze_sha,_=frozen(U)
    call=actual_call(U,m,'projection-pure-tests-r1',m.g.PY,
        ['-I','-B',str(U/'test_projection_oracle.py'),'--reference-root',str(U),'-v'],limit=180000)
    stderr=read(U/call['phase']/'stderr.txt',REPORT_CAP)
    import re
    found=re.search(rb'Ran (\d+) tests? in [0-9.]+s\s+OK\s*\Z',stderr)
    assert found and not read(U/call['phase']/'stdout.txt',REPORT_CAP,empty=True)
    count=int(found[1]);assert count==15
    recheck_dependencies(m);assert frozen(U)[0]==freeze_sha
    return {'status':'PASS_PROJECTION_PURE_ORACLE_TESTS','sourceFreezeSha256':freeze_sha,
            'testCount':count,'call':call,'stderrSha256':sha(stderr)}

def compile_component(U,m):
    freeze_sha,_=frozen(U)
    (U/'classes').mkdir();(U/'empty-sourcepath').mkdir()
    argv=['-source','7','-target','7','-encoding','UTF-8','-Xlint:-options',
          '-classpath',m.classpath,'-sourcepath',str(U/'empty-sourcepath'),'-d',str(U/'classes')]
    argv.extend(str(U/name) for name in JAVA_SOURCES)
    call=actual_call(U,m,'projection-jdk8-compile-r1',m.g.JDK/'javac.exe',argv)
    assert not read(U/call['phase']/'stdout.txt',REPORT_CAP,empty=True)
    stderr=read(U/call['phase']/'stderr.txt',REPORT_CAP,empty=True)
    rows=[];total=0
    for path in sorted((U/'classes').rglob('*')):
        assert not path.is_symlink() and not getattr(path.lstat(),'st_file_attributes',0)&0x400
        if not path.is_file():continue
        assert path.suffix=='.class';raw=read(path);relative=path.relative_to(U/'classes').as_posix()
        assert relative.startswith('com/lushprojects/circuitjs1/client/CurrentResultProjections')
        assert not (m.classes/relative).exists()
        assert raw[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',raw[6:8])[0]==51
        rows.append({'path':relative,'bytes':len(raw),'sha256':sha(raw),'major':51});total+=len(raw)
    assert 2<=len(rows)<=32 and len(rows)+2486<=10000 and total+11953557<=268435456
    assert not list((U/'empty-sourcepath').iterdir())
    recheck_dependencies(m);assert frozen(U)[0]==freeze_sha
    return {'status':'PASS_FINAL_SOURCE_JDK8_PROJECTION_COMPONENT_COMPILE','sourceFreezeSha256':freeze_sha,
        'sourceCount':2,'source': [info(U/name) for name in JAVA_SOURCES],
        'classes':rows,'classCount':len(rows),'classBytes':total,'allMajor51':True,'call':call,
        'stderrSha256':sha(stderr),'dependencySourceCount':863,'dependencyClassCount':2486,
        'dependencyClassBytes':11953557,'dependencyInputsReauthenticated':True,'dependencyFreshCompileReused':True,
        'actualPriorDataReads':0,'oldActualDataClassReads':0,'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}

def reader(U,m,mode,stdout,phase):
    call=actual_call(U,m,phase,m.g.PY,['-I','-B',str(U/'verify_projection_r1.py'),
        '--case',mode,'--input-json',str(stdout),'--reference-root',str(U)],limit=180000)
    assert not read(U/phase/'stderr.txt',REPORT_CAP,empty=True)
    raw=read(U/phase/'stdout.txt',REPORT_CAP);value=parse(raw)
    assert value['status']=='PASS_INDEPENDENT_RESULT_PROJECTIONS'
    return {'call':call,'summary':value,'stdoutSha256':sha(raw)}

def native(U,m,mode):
    freeze_sha,_=frozen(U);build,build_sha,classpath=classes(U,m)
    if mode=='suite':
        smoke_raw=read(U/'NATIVE_SMOKE_GATE_R1.json');smoke=parse(smoke_raw)
        assert smoke['status']=='PASS_NATIVE_PROJECTION_SMOKE_AND_INDEPENDENT_READER'
        assert smoke['sourceFreezeSha256']==freeze_sha and smoke['compileGateSha256']==build_sha
    phase='projection-native-'+mode+'-r1'
    call=actual_call(U,m,phase,m.g.JDK/'java.exe',['-ea','-Xmx512m','-cp',classpath,JAVA_CLASS,mode.upper()])
    assert not read(U/phase/'stderr.txt',REPORT_CAP,empty=True)
    raw=read(U/phase/'stdout.txt',REPORT_CAP);actual=parse(raw)
    assert actual['mode']==mode.upper() and actual['status']=='PASS'
    expected=[r['name'] for r in parse(read(U/'REFERENCE_R1.json'))['cases'] if r['mode']==mode.upper()]
    assert actual['names']==expected and len(actual['cases'])==len(expected)
    verified=reader(U,m,mode,U/phase/'stdout.txt','projection-native-reader-'+mode+'-r1')
    recheck_dependencies(m);assert frozen(U)[0]==freeze_sha and classes(U,m)[1]==build_sha
    assert sha(read(U/phase/'stdout.txt',REPORT_CAP))==sha(raw)
    return {'status':'PASS_NATIVE_PROJECTION_'+mode.upper()+'_AND_INDEPENDENT_READER',
        'mode':mode,'sourceFreezeSha256':freeze_sha,'compileGateSha256':build_sha,
        'call':call,'nativeStdoutSha256':sha(raw),'nativeSummary':actual,'reader':verified,
        'nativeCases':len(expected),'actualProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}

def protocol(U,m):
    freeze_sha,_=frozen(U);_,build_sha,classpath=classes(U,m);calls=[]
    for tag,args in (('invalid-mode',['INVALID']),('missing-mode',[]),('extra-argument',['SMOKE','EXTRA'])):
        phase='projection-protocol-'+tag+'-r1'
        call=actual_call(U,m,phase,m.g.JDK/'java.exe',['-ea','-Xmx512m','-cp',classpath,JAVA_CLASS]+args,expected=1)
        assert not read(U/phase/'stderr.txt',REPORT_CAP,empty=True)
        raw=read(U/phase/'stdout.txt',REPORT_CAP);value=parse(raw)
        assert list(value)==parse(read(U/'CANARY_WIRE_R1.json'))['topKeys']
        assert value=={'schema':'TSJ_CURRENT_RESULT_PROJECTIONS_CANARY_R1','mode':'INVALID','status':'FAIL',
                      'names':[],'cases':[],'parentConstructions':0,'error':'CANARY_ARGUMENTS'}
        calls.append({'name':tag,'call':call,'stdoutSha256':sha(raw),'summary':value})
    recheck_dependencies(m);assert frozen(U)[0]==freeze_sha and classes(U,m)[1]==build_sha
    return {'status':'PASS_THREE_ACTUAL_JVM_PROTOCOL_REJECTIONS','calls':calls,
            'sourceFreezeSha256':freeze_sha,'compileGateSha256':build_sha,'genuineEnvelope':'UNSPENT_GATED'}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--work-root',required=True,type=Path)
    parser.add_argument('mode',choices=('deps','host','pure','compile','smoke','suite','protocol'))
    args=parser.parse_args();U=args.work_root.resolve(strict=True)
    assert U.is_dir() and U.name.startswith('tsj-q100-result-projections-')
    stage=args.mode;t0=time.monotonic_ns()
    try:
        m=dependency_runtime(U)
        if stage=='deps':
            result={'status':'PASS_ACCEPTED_SYNTHETIC_DEPENDENCY_SOURCE_TOOL_JAR_CLASS_COMPILE_RECEIPT_REAUTHENTICATION',
                    'sourceCount':863,'classCount':2486,'classBytes':11953557,'allMajor51':True,
                    'noNewNativeCall':True,'noOldActualDataClassRead':True,'genuineEnvelope':'UNSPENT_GATED'}
            target='DEPENDENCY_GATE_R1.json'
        elif stage=='host':result=host(U,m);target='HOST_GATE_R1.json'
        elif stage=='pure':result=pure(U,m);target='PURE_GATE_R1.json'
        elif stage=='compile':result=compile_component(U,m);target='COMPILE_GATE_R1.json'
        elif stage in ('smoke','suite'):result=native(U,m,stage);target='NATIVE_'+stage.upper()+'_GATE_R1.json'
        else:result=protocol(U,m);target='PROTOCOL_GATE_R1.json'
        result['rootOperationElapsedMs']=(time.monotonic_ns()-t0)/1000000
        result['separateRootCleanupDurationMs']=None
        write(U/target,result);print(result['status'])
    except BaseException as failure:
        target=U/('FAIL_ROOT_'+stage.upper()+'_R1.json')
        if not target.exists():write(target,{'status':'FAIL_NO_ACCEPTANCE','phase':stage,
            'exceptionType':type(failure).__name__,'rootOperationElapsedMs':(time.monotonic_ns()-t0)/1000000,
            'separateRootCleanupDurationMs':None,'genuineEnvelope':'UNSPENT_GATED'})
        raise
if __name__=='__main__':main()
