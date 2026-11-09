"""Actual Windows path checks plus a fail-on-read observer, no DATA reads."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,os,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
p=argparse.ArgumentParser();p.add_argument('--emitter',type=Path);args=p.parse_args()
source=args.emitter or Path(__file__).with_name('generate_body_ownership.py')
assert hashlib.sha256(source.read_bytes()).hexdigest()=='29ebb32d23cd4f66b000499cb0d599c678fcbe718bd4f91d726f0e49ef075970'
spec=importlib.util.spec_from_file_location('_selected_emitter_host_canary',source)
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
link=Path('C:/Documents and Settings')
before=os.lstat(link)
assert link.is_junction() and getattr(before,'st_file_attributes',0)&0x400
try:m._assert_safe_directory(link,'HOST_REPARSE_REJECTED')
except m.EmitError as rejected:assert str(rejected)=='HOST_REPARSE_REJECTED'
else:raise AssertionError('REAL_HOST_DIRECTORY_GUARD_ACCEPTED_JUNCTION')
read_calls=0
def deny_read(*args,**kwargs):
    global read_calls
    read_calls+=1
    raise AssertionError('SOURCE_READ_ATTEMPT_BEFORE_REPARSE_REJECTION')
m._read_stable=deny_read
try:m.emit(link,source.parent,source.parent)
except m.EmitError as rejected:assert str(rejected)=='EMIT_SOURCE_INPUT'
else:raise AssertionError('EMITTER_ACCEPTED_JUNCTION')
assert read_calls==0
after=os.lstat(link)
assert (before.st_dev,before.st_ino,before.st_mtime_ns,before.st_file_attributes)==(after.st_dev,after.st_ino,after.st_mtime_ns,after.st_file_attributes)
print(json.dumps({'status':'PASS_ACTUAL_HOST_REPARSE_PHASE','sourceReadCalls':read_calls,
    'hostPathChecksMocked':False,'readObserver':'FAIL_ON_ANY_SOURCE_READ','linkMutation':False},separators=(',',':')))
