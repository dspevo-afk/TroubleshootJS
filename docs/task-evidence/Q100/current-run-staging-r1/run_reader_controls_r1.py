"""Real selected reader and JVM protocol controls, all synthetic."""
from pathlib import Path
import copy,hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
path=R/'run_staging_gates_r1.py';raw=path.read_bytes()
pin=next(x['sha256'] for x in freeze['files'] if x['name']==path.name)
assert hashlib.sha256(raw).hexdigest()==pin
d=types.ModuleType('_final_run_staging_driver');d.__file__=str(path)
exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
d.frozen();old,m=d.load_dependency();d.audit_classes()
base=d.parse(d.read(R/'run-staging-suite-r1/stdout.txt',1048576))
data=R/'reader-controls-r1';data.mkdir();calls=[]
def run(name,input_path,expected,reference=R):
    call=d.call(old,m,'run-reader-control-'+name+'-r1',m.g.PY,['-I','-B',str(R/'verify_run_staging_r1.py'),
        '--input-json',str(input_path),'--reference-root',str(reference)],expected=expected,limit=180000)
    assert not d.read(R/call['phase']/'stderr.txt')
    result=d.parse(d.read(R/call['phase']/'stdout.txt',1048576))
    assert result['status']==('PASS_INDEPENDENT_SYNTHETIC_RUN_STAGING_READER' if expected==0 else 'REJECTED')
    calls.append({'name':name,'expectedExit':expected,'call':call,'summary':result})
def mutate(name,change):
    v=copy.deepcopy(base);change(v)
    p=data/(name+'.json');p.write_text(json.dumps(v)+'\n',encoding='ascii',newline='\n');run(name,p,2)
run('positive',R/'run-staging-suite-r1/stdout.txt',0)
mutate('payload',lambda v:v['cases'][0]['payload']['requestedNetIds'].__setitem__(0,'B_COIL_LOW'))
mutate('counter',lambda v:v['cases'][0].__setitem__('stageDeltaUnits',263))
mutate('parent',lambda v:v.__setitem__('parentConstructions',8))
mutate('clock',lambda v:v['cases'][0].__setitem__('maxGroupNanos',5000000001))
p=data/'duplicate.json';p.write_bytes(b'{"schema":"DUPLICATE",'+json.dumps(base).encode('ascii')[1:]);run('duplicate',p,2)
p=data/'oversize.json';p.write_bytes(b' '*1048577);run('oversize',p,2)
bad=data/'changed-reference';bad.mkdir();(bad/'FIXTURE_REFERENCE_R1.json').write_bytes(d.read(R/'FIXTURE_REFERENCE_R1.json')+b'\n')
run('changed-reference',R/'run-staging-suite-r1/stdout.txt',2,bad)
junction=Path(R.anchor)/'Documents and Settings'
st=junction.lstat();assert getattr(st,'st_file_attributes',0)&0x400
run('existing-junction',junction/'tsj-synthetic-no-file-read.json',2)
assert 'REPARSE' in calls[-1]['summary']['error']
protocol=[]
for name,args in [('no-argument',[]),('lowercase',['suite'])]:
    c=d.call(old,m,'run-staging-protocol-'+name+'-r1',m.g.JDK/'java.exe',
        ['-cp',d.classpath(m),'com.lushprojects.circuitjs1.client.CurrentRunStagingCanary']+args,expected=1)
    v=d.parse(d.read(R/c['phase']/'stdout.txt',1048576))
    assert v['status']=='FAIL' and v['parentConstructions']==0 and v['names']==v['cases']==[]
    assert not d.read(R/c['phase']/'stderr.txt')
    protocol.append({'name':name,'call':c,'summary':v})
d.frozen();d.audit_classes();old.recheck_dependencies(m)
result={'status':'PASS_REAL_SELECTED_READER_AND_JVM_PROTOCOL_CONTROLS','readerPositive':1,'readerExpectedExit2':8,
        'reader':calls,'protocolExpectedExit1':2,'protocol':protocol,'genuineEnvelope':'UNSPENT_GATED'}
print(json.dumps({'status':result['status'],'receiptSha256':d.write('READER_CONTROLS_GATE_R1.json',result)}))
