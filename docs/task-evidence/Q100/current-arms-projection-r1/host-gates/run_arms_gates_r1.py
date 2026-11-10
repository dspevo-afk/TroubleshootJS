"""Selected real host gates for synthetic current arms DTO projections."""
from pathlib import Path
import argparse,hashlib,json,os,re,struct,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
P=R.parent/'tsj-q100-r7-authored-root-r3-5d70f44bcfff46669449b5bff6767790'
U=R.parent/'tsj-q100-result-projections-6f06ff895ac54a989055a0bd8f1cd61a'
PREVIOUS='31e66d3c61543a11288270d4ae0480c54aed15006627d94466b3476673b19f53'
CONTRACT='4c7f3220424643694c58ae42405e447ed18d0588b3708428e72cfe1b0419079a'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def read(path,cap=4194304):
 p=Path(path);s=p.lstat();assert not p.is_symlink() and not getattr(s,'st_file_attributes',0)&0x400
 assert 0<=s.st_size<=cap
 with p.open('rb') as f:a=os.fstat(f.fileno());raw=f.read(cap+1);b=os.fstat(f.fileno())
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
def dependency():
 f=read(P/'SOURCE_FREEZE_R1.json');assert sha(f)==PREVIOUS
 row=next(x for x in parse(f)['files'] if x['name']=='run_current_r7_gates_r1.py')
 path=P/row['name'];raw=read(path);assert sha(raw)==row['sha256'] and len(raw)==row['bytes']
 p=types.ModuleType('_arms_accepted_r7');p.__file__=str(path)
 exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),p.__dict__)
 d,b,old,m=p.dependency();assert p.frozen()==PREVIOUS;p.audit_classes()
 return p,d,b,old,m
def recheck(p,d,b,old,m):
 assert p.frozen()==PREVIOUS;p.audit_classes();p.recheck(d,b,old,m)
def frozen():
 assert sha(read(R/'CONTRACT_R1.md'))==CONTRACT
 f=read(R/'SOURCE_FREEZE_R1.json');v=parse(f);assert v['status']=='ROOT_REVIEWED_FINAL_ARMS_PROJECTION_SOURCE'
 for row in v['files']:
  raw=read(R/row['name']);assert sha(raw)==row['sha256'] and len(raw)==row['bytes']
 return sha(f)
def inventory(directory):
 rows=[]
 for path in sorted(directory.rglob('*')):
  assert not path.is_symlink() and not getattr(path.lstat(),'st_file_attributes',0)&0x400
  if path.is_dir():continue
  raw=read(path);assert path.suffix=='.class' and raw[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',raw[6:8])[0]==51
  rows.append({'path':path.relative_to(directory).as_posix(),'bytes':len(raw),'sha256':sha(raw),'major':51})
 assert len(rows)<=10000 and sum(x['bytes'] for x in rows)<=268435456
 return rows
def cp(d,b,m):return ';'.join([str(R/'classes'),str(R/'dependencies')]+d.classpath(b,m).split(';')[2:])
def audit_classes():
 v=parse(read(R/'COMPILE_GATE_R1.json'));assert v['sourceFreezeSha256']==frozen()
 assert inventory(R/'classes')==v['classes'] and inventory(R/'dependencies')==v['dependencies']
 assert not {x['path'] for x in v['classes']}&{x['path'] for x in v['dependencies']}
 return v
def call(old,m,phase,exe,args,expected=0,limit=60000):return old.actual_call(R,m,phase,exe,args,expected=expected,limit=limit)
def compile_component(p,d,b,old,m):
 pin=frozen();deps=R/'dependencies';deps.mkdir();(R/'classes').mkdir();(R/'empty-sourcepath').mkdir()
 merged={};removed=[]
 for folder in (P/'classes',P/'dependencies'):
  for row in inventory(folder):
   assert row['path'] not in merged;merged[row['path']]=row
   leaf=Path(row['path']).name
   if leaf=='CurrentResultProjections.class' or leaf.startswith('CurrentResultProjections$'):removed.append(row);continue
   target=deps/row['path'];target.parent.mkdir(parents=True,exist_ok=True)
   with target.open('xb') as f:f.write(read(folder/row['path']))
 assert len(merged)==2518 and len(removed)==1
 names=['CurrentResultProjections.java','CurrentArmsProjectionCanary.java','CurrentResultProjectionsCanary.java']
 args=['-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-classpath',cp(d,b,m),'-sourcepath',str(R/'empty-sourcepath'),'-d',str(R/'classes')]+[str(R/'source'/x) for x in names]
 c=call(old,m,'arms-jdk8-compile-r1',m.g.JDK/'javac.exe',args)
 assert not read(R/c['phase']/'stdout.txt')
 own=inventory(R/'classes');dep=inventory(deps)
 assert all(Path(x['path']).name in ('CurrentResultProjections.class','CurrentArmsProjectionCanary.class','CurrentResultProjectionsCanary.class') or Path(x['path']).name.startswith(('CurrentArmsProjectionCanary$','CurrentResultProjectionsCanary$')) for x in own)
 assert not {x['path'] for x in own}&{x['path'] for x in dep}
 assert len(own)+len(dep)<=10000 and sum(x['bytes'] for x in own+dep)<=268435456
 assert not list((R/'empty-sourcepath').iterdir()) and frozen()==pin;recheck(p,d,b,old,m)
 return {'status':'PASS_FINAL_SOURCE_JDK8_ARMS_COMPONENT_COMPILE','sourceFreezeSha256':pin,'sourceCount':3,'call':c,'classes':own,'dependencies':dep,'removedOldClasses':removed,'classCount':len(own),'classBytes':sum(x['bytes'] for x in own),'noDuplicateOrShadowClasses':True,'allMajor51':True,'acceptedDependencyReauthenticated':True,'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
def pure(p,d,b,old,m):
 pin=frozen();c=call(old,m,'arms-pure-r1',m.g.PY,['-I','-B',str(R/'test_arms_oracle.py'),'-v'],limit=180000)
 assert not read(R/c['phase']/'stdout.txt')
 match=re.search(rb'Ran (\d+) tests? in [0-9.]+s\s+OK\s*\Z',read(R/c['phase']/'stderr.txt'));assert match and int(match[1])==17
 assert frozen()==pin;recheck(p,d,b,old,m)
 return {'status':'PASS_ARMS_PURE_SOURCE_AND_ORACLE','testCount':int(match[1]),'call':c}
def native(p,d,b,old,m,mode):
 pin=frozen();audit_classes();c=call(old,m,'arms-native-'+mode+'-r1',m.g.JDK/'java.exe',['-cp',cp(d,b,m),'com.lushprojects.circuitjs1.client.CurrentArmsProjectionCanary',mode.upper()])
 assert not read(R/c['phase']/'stderr.txt')
 q=call(old,m,'arms-reader-'+mode+'-r1',m.g.PY,['-I','-B',str(R/'verify_arms_projection_r1.py'),'--case',mode,'--input-json',str(R/c['phase']/'stdout.txt'),'--reference-root',str(R)],limit=180000)
 value=parse(read(R/q['phase']/'stdout.txt'));assert value['status']=='PASS_INDEPENDENT_CURRENT_ARMS_PROJECTION' and not read(R/q['phase']/'stderr.txt')
 assert frozen()==pin;recheck(p,d,b,old,m);audit_classes()
 return {'status':'PASS_ARMS_NATIVE_'+mode.upper()+'_AND_READER','call':c,'reader':q,'summary':value,'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
def regression(p,d,b,old,m,mode):
 pin=frozen();audit_classes();c=call(old,m,'arms-regression-'+mode+'-r1',m.g.JDK/'java.exe',['-cp',cp(d,b,m),'com.lushprojects.circuitjs1.client.CurrentResultProjectionsCanary',mode.upper()])
 assert not read(R/c['phase']/'stderr.txt')
 q=call(old,m,'arms-regression-reader-'+mode+'-r1',m.g.PY,['-I','-B',str(U/'verify_projection_r1.py'),'--case',mode,'--input-json',str(R/c['phase']/'stdout.txt'),'--reference-root',str(U)],limit=180000)
 value=parse(read(R/q['phase']/'stdout.txt'));assert value['status']=='PASS_INDEPENDENT_RESULT_PROJECTIONS' and value['caseCount']==(2 if mode=='smoke' else 15)
 assert not read(R/q['phase']/'stderr.txt') and frozen()==pin;recheck(p,d,b,old,m);audit_classes()
 return {'status':'PASS_EXISTING_PROJECTIONS_'+mode.upper(),'call':c,'reader':q,'summary':value}
if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('--case',required=True,choices=['compile','pure','smoke','suite','regression-smoke','regression-suite']);a=ap.parse_args()
 p,d,b,old,m=dependency()
 if a.case in ('smoke','suite'):v=native(p,d,b,old,m,a.case)
 elif a.case.startswith('regression-'):v=regression(p,d,b,old,m,a.case.split('-')[1])
 else:v=globals()[{'compile':'compile_component','pure':'pure'}[a.case]](p,d,b,old,m)
 name=a.case.upper().replace('-','_')+'_GATE_R1.json';print(json.dumps({'status':v['status'],'receipt':name,'sha256':write(name,v)}))
