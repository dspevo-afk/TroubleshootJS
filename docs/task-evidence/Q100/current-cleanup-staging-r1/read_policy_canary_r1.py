"""Actual corrected reader with stable and genuinely changed files."""
from pathlib import Path
import hashlib,json,os,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent;p=R/'verify_cleanup_staging_r1.py';raw=p.read_bytes()
m=types.ModuleType('_corrected_real_file_reader');m.__file__=str(p)
exec(compile(raw,str(p),'exec',dont_inherit=True,optimize=0),m.__dict__)
directory=R/'read-policy-fixtures-r1';directory.mkdir()
stable=directory/'stable-rewritten.bin';stable.write_bytes(b'first');stable.write_bytes(b'stable-rewritten')
assert m._read_regular_file(str(stable),1024)==b'stable-rewritten'
target=directory/'changed-during-read.bin';target.write_bytes(b'synthetic-initial')
actual_read=os.read;changed=False
def inject(fd,count):
    global changed
    data=actual_read(fd,count)
    if not changed:
        changed=True
        with target.open('ab') as writer:writer.write(b'X')
    return data
error=None
try:
    os.read=inject
    try:m._read_regular_file(str(target),1024)
    except m.ReaderFailure as failure:error=failure.code
finally:os.read=actual_read
assert changed and error=='FILE_CHANGED'
print(json.dumps({'status':'PASS_REAL_STABLE_AND_CHANGED_FILE_READER_CANARIES',
 'readerSourceSha256':hashlib.sha256(raw).hexdigest(),'stableRewrittenFileAccepted':True,
 'actualMutationRejected':error,'osReadHookRestored':os.read is actual_read}))
