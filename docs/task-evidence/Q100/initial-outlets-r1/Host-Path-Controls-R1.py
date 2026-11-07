import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    raise SystemExit('ISOLATION_REQUIRED')
import hashlib
import json
from pathlib import Path
import types
if len(sys.argv)!=3:
    raise SystemExit('EXPECTED_HELPER_SHA_AND_LINK')
root=Path(__file__).resolve().parent
path=root/'build_inputs.py'
raw=path.read_bytes()
if hashlib.sha256(raw).hexdigest()!=sys.argv[1]:
    raise SystemExit('SELECTED_HELPER_HASH')
module=types.ModuleType('selected_initial_outlet_build')
module.__file__=str(path)
sys.modules[module.__name__]=module
exec(compile(raw,str(path),'exec'),module.__dict__)
positive=module._canonical(root,'CANARY_PATH')
assert positive==root and isinstance(positive,Path)
assert module._canonical(str(root),'CANARY_STRING')==root
link=Path(sys.argv[2])
assert link.parent==root and link.name.startswith('host-link-r')
try:
    module._canonical(link,'CANARY_LINK')
except module.BuildInputError as failure:
    assert failure.code=='IO_CANARY_LINK_CANONICAL', failure.code
else:
    raise SystemExit('REAL_JUNCTION_ACCEPTED')
print(json.dumps({'status':'PASS_SELECTED_HOST_PATH_CONTROLS_ONLY','positiveWindowsPath':True,'positiveStringPath':True,'realJunctionRejected':'IO_CANARY_LINK_CANONICAL','helperSha256':sys.argv[1]},separators=(',',':')))
