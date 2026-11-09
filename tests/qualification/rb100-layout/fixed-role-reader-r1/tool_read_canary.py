"""Selected current reader/tool path canary, no prior-role or manifest reads."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

assert sys.flags.isolated and sys.dont_write_bytecode
parser = argparse.ArgumentParser()
parser.add_argument('--reader-source', required=True)
parser.add_argument('--tool-config', required=True)
args = parser.parse_args()
path = Path(args.reader_source)
assert path.stat().st_size <= 4194304
with path.open('rb') as stream:
    raw = stream.read(4194305)
assert 0 < len(raw) <= 4194304
assert hashlib.sha256(raw).hexdigest() == '863a258cbab888cf112b9e927e16a099436632e9a1d43eefe0b70690c30543d8'
spec = importlib.util.spec_from_file_location('_selected_reader_tool_canary', path)
reader = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = reader
spec.loader.exec_module(reader)
config = json.loads(Path(args.tool_config).read_bytes())
assert type(config) is list and len(config) == 5
facts = []
for row in config:
    assert set(row) == {'role', 'path', 'sha256'}
    try:
        body, digest, token = reader._read_file_once(
            row['path'], 8388608, None, row['sha256'], 'PINNED_SOURCE_TOOL')
    except reader.ReaderError as failure:
        print(row['role'] + ':' + failure.code, file=sys.stderr)
        raise SystemExit(2)
    facts.append(dict(role=row['role'], bytes=len(body), sha256=digest,
                      openedPathAndHandleIdentityChecked=bool(token)))
print(json.dumps(dict(status='PASS_SELECTED_PINNED_TOOL_READ_ONLY', tools=facts,
    actualPriorBodiesRead=0, observerAdapterQualified=False, normalPriorAccepted=False),
    separators=(',', ':')))
