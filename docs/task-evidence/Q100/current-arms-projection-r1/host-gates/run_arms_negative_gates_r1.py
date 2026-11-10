"""Root-owned selected negative controls; no producer or actual data inputs."""
from pathlib import Path
import hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
raw=(R/'SOURCE_FREEZE_R1.json').read_bytes();freeze=json.loads(raw)
row=next(x for x in freeze['files'] if x['name']=='run_arms_gates_r1.py')
source=R/row['name'];code=source.read_bytes();assert hashlib.sha256(code).hexdigest()==row['sha256']
g=types.ModuleType('_arms_final_gates');g.__file__=str(source);exec(compile(code,str(source),'exec',dont_inherit=True,optimize=0),g.__dict__)
p,d,b,old,m=g.dependency();pin=g.frozen();g.audit_classes()
base=g.parse(g.read(R/'arms-native-smoke-r1/stdout.txt'))
assert g.parse(g.read(R/'SMOKE_GATE_R1.json'))['status']=='PASS_ARMS_NATIVE_SMOKE_AND_READER'
work=R/'reader-negative-inputs-r1';work.mkdir();calls=[]
def mutation(name,change):
 v=json.loads(json.dumps(base));change(v);return name,(json.dumps(v,separators=(',',':'))+'\n').encode('ascii')
rows=[mutation('missing-case',lambda v:v['cases'].pop()),
 mutation('extra-field',lambda v:v.update({'unexpected':1})),
 mutation('boolean-counter',lambda v:v['cases'][0].update({'helperUnits':True})),
 mutation('wrong-debit',lambda v:v['cases'][0].update({'helperUnits':14})),
 mutation('wrong-identity',lambda v:v['cases'][0]['identity'].update({'childMapsPreserved':False})),
 mutation('wrong-payload',lambda v:v['cases'][0].update({'payloadJson':v['cases'][0]['payloadJson'].replace('SYNTH_NULL','SYNTH_WRONG')})),
 mutation('clock-overrun',lambda v:v['cases'][0].update({'totalElapsedNanos':90000000001})),
 mutation('missing-final-open',lambda v:v['cases'][0].pop('finalOpen'))]
original=g.read(R/'arms-native-smoke-r1/stdout.txt');rows.append(('duplicate-key',b'{"schema":"DUPLICATE",'+original.lstrip()[1:]))
rows.append(('oversize',b' '*(1048576+1)))
for name,content in rows:
 path=work/(name+'.json')
 with path.open('xb') as f:f.write(content)
 c=g.call(old,m,'arms-reader-negative-'+name+'-r1',m.g.PY,['-I','-B',str(R/'verify_arms_projection_r1.py'),'--case','smoke','--input-json',str(path),'--reference-root',str(R)],expected=2,limit=180000)
 v=g.parse(g.read(R/c['phase']/'stdout.txt'));assert v['status']=='REJECTED' and isinstance(v.get('code'),str) and v['code']!='ORACLE_FAILURE'
 expected_codes={'missing-case':'SUMMARY_CASE_ORDER','extra-field':'SUMMARY_TOP_KEYS','boolean-counter':'SUMMARY_COUNTER','wrong-debit':'SUMMARY_COUNTER','wrong-identity':'SUMMARY_IDENTITY','wrong-payload':'SUMMARY_PAYLOAD','clock-overrun':'SUMMARY_CLOCK','missing-final-open':'SUMMARY_CASE_KEYS','duplicate-key':'INPUT_JSON','oversize':'INPUT_SIZE'}
 assert v['code']==expected_codes[name]
 assert not g.read(R/c['phase']/'stderr.txt')
 calls.append({'control':name,'inputSha256':g.sha(content),'expectedChildExitCode':2,'call':c,'observed':v})
protocol=[]
for label,args in [('invalid-mode',['INVALID']),('extra-argument',['SMOKE','EXTRA'])]:
 c=g.call(old,m,'arms-jvm-negative-'+label+'-r1',m.g.JDK/'java.exe',['-cp',g.cp(d,b,m),'com.lushprojects.circuitjs1.client.CurrentArmsProjectionCanary']+args,expected=1)
 v=g.parse(g.read(R/c['phase']/'stdout.txt'));assert v['status']=='FAIL' and v['error']=='CANARY_ARGUMENTS' and v['cases']==[] and v['parentConstructions']==0
 assert not g.read(R/c['phase']/'stderr.txt');protocol.append({'control':label,'expectedChildExitCode':1,'call':c,'observed':v})
assert g.frozen()==pin;g.recheck(p,d,b,old,m);g.audit_classes()
value={'status':'PASS_EXPECTED_READER_AND_PROTOCOL_REJECTIONS','readerNegativeCount':len(calls),'protocolNegativeCount':len(protocol),'reader':calls,'protocol':protocol,'sourceFreezeSha256':pin,'rawNonzeroExits':'EXPECTED_REJECTIONS_NOT_POSITIVE_READER_PASS','nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
print(json.dumps({'status':value['status'],'sha256':g.write('NEGATIVE_GATE_R1.json',value)}))
