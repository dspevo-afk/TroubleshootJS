import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    raise SystemExit('ISOLATION_REQUIRED')
import hashlib
import json
from pathlib import Path
p=Path(__file__).resolve().parent/'native-data-r2/result.json'
raw=p.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='e5921bb2ec2f4fcaa0226c50f4dec1da864bf5526ac72242ba60f5f0c0bb4b1f'
doc=json.loads(raw)
pending=[doc]
units=0
while pending:
    value=pending.pop()
    if isinstance(value,dict):
        units+=len(value)
        pending.extend(value.values())
    elif isinstance(value,list):
        units+=len(value)
        pending.extend(value)
    else:
        units+=1
assert units==doc['work']['preExportUnits'],(units,doc['work']['preExportUnits'])
print(json.dumps({'status':'PASS_COMPONENT_TREE_COUNT_ONLY','independentUnits':units,'nativeReportedUnits':doc['work']['preExportUnits']},separators=(',',':')))
