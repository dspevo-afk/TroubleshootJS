"""Real selected CLI negatives and native protocol controls, all synthetic."""
from pathlib import Path
import copy,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent;p=R/'run_cleanup_gates_r1.py';raw=p.read_bytes()
freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
import hashlib
assert hashlib.sha256(raw).hexdigest()==next(x['sha256'] for x in freeze['files'] if x['name']==p.name)
d=types.ModuleType('_cleanup_control_runtime');d.__file__=str(p)
exec(compile(raw,str(p),'exec',dont_inherit=True,optimize=0),d.__dict__)
b,old,m=d.dependencies();d.frozen();d.audit_classes()
base=d.parse(d.read(R/'cleanup-native-suite-r1/stdout.txt',1048576))
folder=R/'reader-controls-r1';folder.mkdir();calls=[]
def run(name,path,expected,error=None,reference=R):
    c=d.call(old,m,'cleanup-reader-control-'+name+'-r1',m.g.PY,['-I','-B',str(R/'verify_cleanup_staging_r1.py'),
        '--case','suite','--input-json',str(path),'--reference-root',str(reference)],expected=expected,limit=180000)
    assert not d.read(R/c['phase']/'stderr.txt')
    v=d.parse(d.read(R/c['phase']/'stdout.txt',1048576))
    assert v['status']==('PASS_INDEPENDENT_CLEANUP_STAGING_READER' if expected==0 else 'FAIL_INDEPENDENT_CLEANUP_STAGING_READER')
    if error is not None:assert v['error']==error
    calls.append({'name':name,'expectedExit':expected,'call':c,'summary':v})
def mutate(name,fn):
    v=copy.deepcopy(base);fn(v);p=folder/(name+'.json')
    p.write_text(json.dumps(v)+'\n',encoding='ascii',newline='\n');run(name,p,2,'REPORT_REJECTED')
run('positive',R/'cleanup-native-suite-r1/stdout.txt',0)
def payload(v):
    q=json.loads(v['cases'][0]['payloadJson']);q['nullArmAliasesDropped']=True
    v['cases'][0]['payloadJson']=json.dumps(q,separators=(',',':'))
mutate('payload',payload)
mutate('counter',lambda v:v['cases'][0].__setitem__('helperUnits',66))
mutate('parent',lambda v:v.__setitem__('parentConstructions',20))
mutate('clock',lambda v:v['cases'][0].__setitem__('maxGroupNanos',5000000001))
p=folder/'duplicate.json';p.write_bytes(b'{"schema":"DUPLICATE",'+json.dumps(base).encode('ascii')[1:]);run('duplicate',p,2,'DUPLICATE_JSON_KEY')
p=folder/'oversize.json';p.write_bytes(b' '*1048577);run('oversize',p,2,'FILE_TOO_LARGE')
for name,changed,error in [('changed-reference','REFERENCE_R1.json','REFERENCE_PIN_MISMATCH'),('changed-oracle','cleanup_oracle.py','ORACLE_PIN_MISMATCH')]:
    root=folder/name;root.mkdir()
    for leaf in ['CONTRACT_R1.md','REFERENCE_R1.json','WIRE_R1.json','ROOT_ORACLE_SOURCE_R1.json','cleanup_oracle.py']:
        (root/leaf).write_bytes(d.read(R/leaf)+(b'\n' if leaf==changed else b''))
    run(name,R/'cleanup-native-suite-r1/stdout.txt',2,error,root)
junction=Path(R.anchor)/'Documents and Settings'
assert getattr(junction.lstat(),'st_file_attributes',0)&0x400
run('existing-junction',junction/'tsj-no-file-read.json',2,'REPARSE_ANCESTOR')
protocol=[]
for name,args in [('no-argument',[]),('lowercase',['smoke'])]:
    c=d.call(old,m,'cleanup-protocol-'+name+'-r1',m.g.JDK/'java.exe',['-cp',d.classpath(b,m),
        'com.lushprojects.circuitjs1.client.CleanupStagingCanary']+args,expected=1)
    v=d.parse(d.read(R/c['phase']/'stdout.txt',1048576))
    assert v['status']=='FAIL' and v['mode']=='INVALID' and v['error']=='CANARY_ARGUMENTS'
    assert v['parentConstructions']==0 and v['names']==v['cases']==[] and not d.read(R/c['phase']/'stderr.txt')
    protocol.append({'name':name,'call':c,'summary':v})
d.frozen();d.audit_classes();d.recheck(b,old,m)
v={'status':'PASS_REAL_CLEANUP_READER_AND_PROTOCOL_CONTROLS','readerPositive':1,'readerExpectedExit2':9,
   'reader':calls,'protocolExpectedExit1':2,'protocol':protocol,'genuineEnvelope':'UNSPENT_GATED'}
print(d.write('CONTROLS_GATE_R1.json',v))
