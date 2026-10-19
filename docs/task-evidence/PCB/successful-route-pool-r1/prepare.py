"""Prepare exact accepted1467 in an owned copy, excluding every rejected trial."""
import hashlib
import json
import stat
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SEAL = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(SEAL) == '9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
assert not (HERE/'local-state.json').exists()
rows = json.loads(SEAL.read_text())['files']
assert len(rows) == 1475
for row in rows:
    source = ROOT/row['path']
    assert source.resolve().is_relative_to(ROOT) and not source.is_symlink()
    assert not source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    assert sha(source) == row['sha256'],row['path']
base = [r for r in rows if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')]
assert len(base) == 1467
copy = Path(tempfile.mkdtemp(prefix='tsj-successful-route-pool-')).resolve()
assert copy.parent == Path(tempfile.gettempdir()).resolve()
assert not copy.is_symlink() and not copy.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
(HERE/'local-state.json').write_text(json.dumps({'scratch':str(copy)},indent=2)+'\n')
for row in base:
    target = copy/row['path']
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes((ROOT/row['path']).read_bytes())
actual = {p.relative_to(copy).as_posix():sha(p) for p in copy.rglob('*') if p.is_file()}
assert actual == {r['path']:r['sha256'] for r in base}
(HERE/'main-input-audit.json').write_text(json.dumps({
    'status':'PASS','main1475Exact':True,'baseCopyCount':1467,'mainSealSha256':sha(SEAL),
    'rejectedTrials16through21Included':False},indent=2)+'\n')
print('PASS acceptedmain1475 exact; fresh accepted1467 successful-route-pool COPY; rejected16-21 excluded')
