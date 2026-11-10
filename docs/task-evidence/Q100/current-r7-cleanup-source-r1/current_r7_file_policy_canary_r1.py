"""Real stable-rewrite and during-read mutation controls for the selected reader."""
from pathlib import Path
import hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
name='working/native-body-ownership-r1/generate_body_ownership.py'
source=R/name;raw=source.read_bytes();freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
assert hashlib.sha256(raw).hexdigest()==next(x['sha256'] for x in freeze['files'] if x['name']==name)
module=types.ModuleType('_current_r7_real_reader_canary');module.__file__=str(source)
exec(compile(raw,str(source),'exec',dont_inherit=True,optimize=0),module.__dict__)
directory=R/'emitter-file-policy-fixtures-r1';directory.mkdir()
stable=directory/'stable-rewritten-source.bin';stable.write_bytes(b'first');stable.write_bytes(b'stable-rewritten-source')
assert module._read_stable(stable,'CANARY_STABLE_INPUT')==b'stable-rewritten-source'
target=directory/'changed-during-read-source.bin';target.write_bytes(b'initial-source-artifact')
real_open=Path.open;changed=False
class ReaderProxy:
    def __init__(self,stream):self.stream=stream
    def __enter__(self):self.stream.__enter__();return self
    def __exit__(self,*args):return self.stream.__exit__(*args)
    def fileno(self):return self.stream.fileno()
    def read(self,count=-1):
        global changed
        data=self.stream.read(count)
        if not changed:
            changed=True
            with real_open(target,'ab') as writer:writer.write(b'X')
        return data
def inject(path,*args,**kwargs):
    stream=real_open(path,*args,**kwargs)
    if path==target and args and args[0]=='rb':return ReaderProxy(stream)
    return stream
observed=None
try:
    Path.open=inject
    try:module._read_stable(target,'CANARY_CHANGED_INPUT')
    except module.EmitError as failure:observed=str(failure)
finally:Path.open=real_open
assert changed and observed=='CANARY_CHANGED_INPUT' and Path.open is real_open
print(json.dumps({'status':'PASS_REAL_CURRENT_R7_FILE_STABILITY_CONTROLS',
    'readerSourceSha256':hashlib.sha256(raw).hexdigest(),'stableRewrittenAccepted':True,
    'actualChangedFileRejected':observed,'pathOpenRestored':True,'ownedHandlesClosed':True,
    'fixturesRetained':True,'genuineEnvelope':'UNSPENT_GATED'}))
