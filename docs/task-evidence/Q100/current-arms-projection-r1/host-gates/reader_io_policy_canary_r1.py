"""Actual selected reader I/O controls on task-owned synthetic files."""
from pathlib import Path
import hashlib,json,os,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
pin=json.loads((R/'ROOT_ORACLE_SOURCE_R1.json').read_bytes())
assert pin['status']=='ROOT_ACCEPTED_CURRENT_ARMS_ORACLE_SOURCE_BEFORE_EXECUTION'
work=R/'io-policy-files-r1';work.mkdir();observations=[]
for name,method in [('arms_oracle.py','read_bounded_file'),('verify_arms_projection_r1.py','_read_bounded')]:
 raw=(R/name).read_bytes();assert hashlib.sha256(raw).hexdigest()==pin['files'][name]
 module=types.ModuleType('_actual_arms_io_'+name.replace('.','_'));module.__file__=str(R/name)
 exec(compile(raw,str(R/name),'exec',dont_inherit=True,optimize=0),module.__dict__)
 reader=getattr(module,method)
 for mode in ['stable','directory-child','append']:
  path=work/(name+'-'+mode+'.txt');path.write_bytes(b'{}')
  original=os.read;triggered=[False]
  def controlled(fd,count):
   value=original(fd,count)
   if not triggered[0]:
    triggered[0]=True
    if mode=='directory-child':(work/(name+'-unrelated-child.txt')).write_bytes(b'other')
    elif mode=='append':
     with path.open('ab') as f:f.write(b'x');f.flush();os.fsync(f.fileno())
   return value
  os.read=controlled
  try:
   try:
    value=reader(str(path),1024,'INPUT_PATH')
    assert mode!='append' and value==b'{}';code='STABLE_BYTES'
   except Exception as error:
    assert mode=='append' and getattr(error,'code',None)=='INPUT_CHANGED';code='INPUT_CHANGED'
  finally:os.read=original
  assert triggered[0];observations.append({'source':name,'mode':mode,'result':code})
print(json.dumps({'status':'PASS_REAL_SELECTED_READER_STABLE_DIRECTORY_AND_APPEND_CONTROLS','cases':observations,'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'},separators=(',',':')))
