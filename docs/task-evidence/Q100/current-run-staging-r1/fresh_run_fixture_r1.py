"""Create explicitly synthetic RUN data from admitted literal Source only."""
from pathlib import Path
import ast,copy,hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
def digest(raw):return hashlib.sha256(raw).hexdigest()
def source(name,pin):
    p=R/'source'/name;assert not p.is_symlink()
    raw=p.read_bytes();assert len(raw)<=4194304 and digest(raw)==pin
    return raw
def write(name,raw):
    with (R/name).open('xb') as f:f.write(raw)
    return {'name':name,'bytes':len(raw),'sha256':digest(raw)}
codec=source('context_codec.py','de21e233e27f93c8183101a89ed6fdb9a00812d69e1fe38cc47c3f3e573b3eb8')
test=source('test_context_codec.py','2a9479e97393273341eb44b71aa6870cb9c0661c87e8985e2bd4c83c193f7f36')
m=types.ModuleType('_admitted_run_fixture_codec')
exec(compile(codec,'context_codec.py','exec',dont_inherit=True,optimize=0),m.__dict__)
nodes=[n for n in ast.parse(test).body if isinstance(n,ast.FunctionDef) and n.name=='_synthetic_context']
assert len(nodes)==1
scope={'copy':copy,'_CODEC':m}
exec(compile(ast.Module(body=nodes,type_ignores=[]),'literal_synthetic_fixture','exec',dont_inherit=True,optimize=0),scope)
context=scope['_synthetic_context']()
native=context['nativeProjection'];captured=native['capture']['capturedNetIds']
assert len(captured)==60 and captured[43]=='A_COIL_LOW' and captured[50]=='B_COIL_LOW'
order=[43,50,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,44,45,46,47,48,49,51,52,53,54,55,56,57,58,59]
assert sorted(order)==list(range(60))
requested=[captured[i] for i in order]
old_lengths=[]
for trial in (native['prior']['trialDisposition'],native['capture']['trialDisposition']):
    assert trial['orderChanged'] is False and trial['status']=='RUN'
    old_lengths.append(sorted(map(len,trial['requestedNetIds'])))
    trial['orderChanged']=True;trial['requestedNetIds']=list(requested)
    assert sorted(map(len,trial['requestedNetIds']))==old_lengths[-1]
assert native['prior']['trialDisposition'] is not native['capture']['trialDisposition']
assert native['prior']['trialDisposition']['requestedNetIds'] is not native['capture']['trialDisposition']['requestedNetIds']
raw=m.canonical_context(context);sha=digest(raw)
factory=m.generate_input_java(context,sha)
if isinstance(factory,str):factory=factory.encode('utf-8')
assert len(raw)==66483 and m.data_entries(native)==271
rows=[write('SYNTHETIC_RUN_CONTEXT_R1.json',raw),write('CohortTerminalTelemetryInputV2.java',factory)]
ref={'status':'FROZEN_FRESH_SYNTHETIC_RUN_PROFILE_NOT_OWNER_EVIDENCE','files':rows,
     'requestedIndices':order,'expectedRequested':requested,'expectedCaptured':captured,
     'setup':{'units':10007,'copiedEntries':478,'copyChunks':3,'groups':1},
     'staging':{'units':264,'copiedEntries':125,'copyChunks':64},
     'combined':{'units':10271,'copiedEntries':603,'copyChunks':67,'groups':2},
     'factoryEntries':271,'genuineEnvelope':'UNSPENT_GATED','nativeCalls':0}
write('FIXTURE_REFERENCE_R1.json',(json.dumps(ref,indent=2)+'\n').encode('ascii'))
print(json.dumps({'status':ref['status'],'files':rows}))
