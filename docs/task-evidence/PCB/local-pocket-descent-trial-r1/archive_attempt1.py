"""Preserve the first failed native fixture attempt before any source correction."""
import hashlib
import json
import stat
from pathlib import Path

HERE = Path(__file__).resolve().parent
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert len(seal['gateOwners']) == 26 and len(seal['overrides']) == 7 and len(seal['additions']) == 8
timing = read(HERE/'unit-timing.json')
assert timing['status'] == 'FAIL' and timing['exitCode'] == 2
assert timing['elapsedSeconds'] == 48.4835479
assert read(HERE/'unit-process-cleanup.json')['status'] == 'PASS'
assert 'Topology graph requires a connector component' in (HERE/'unit.txt').read_text()
dest = HERE/'attempt1'
assert not dest.exists()
names = set(seal['gateOwners'])
names.update('source/'+name for name in dict(seal['overrides'],**seal['additions']))
names.update(('native-inputs.json','native-input-audit.json','main-input-audit.json',
    'unit.txt','unit-timing.json','unit-process-launch.json','unit-process-cleanup.json',
    'unit-log-sanitization.json','README.md'))
if (HERE/'unit-receipt.txt').exists():
    names.add('unit-receipt.txt')

def plain(p):
    assert p.resolve().is_relative_to(HERE) and not p.is_symlink()
    assert not p.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT

for name,digest in seal['gateOwners'].items():
    assert sha(HERE/name) == digest,name
for name,digest in dict(seal['overrides'],**seal['additions']).items():
    assert sha(HERE/'source'/name) == digest,name
for name in sorted(names):
    source = HERE/name
    plain(source)
    target = dest/name
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(source.read_bytes())
rows = {name:sha(dest/name) for name in sorted(names)}
assert all(sha(HERE/name) == digest for name,digest in rows.items())
(dest/'archive-sha256.json').write_text(json.dumps({
    'status':'PASS','failure':'Tie fixture omitted required connector; TopologyPlacementGraph rejects before tie helper call; compile succeeded but unit suite failed',
    'nativeStatus':'FAIL','metadataExitCode':2,'jvmExitCode':1,'elapsedSeconds':48.4835479,
    'gateOwners':26,'sourceDeltas':15,'artifactCount':len(rows),'files':rows},indent=2)+'\n')
archive = HERE/'source'
for name in dict(seal['overrides'],**seal['additions']):
    p = archive/name
    plain(p)
    p.unlink()
directories = [p for p in archive.rglob('*') if p.is_dir()]
for p in sorted(directories,key=lambda p:len(p.parts),reverse=True):
    plain(p)
    p.rmdir()
plain(archive)
archive.rmdir()
for name in ('native-inputs.json','native-input-audit.json','unit.txt','unit-receipt.txt',
             'unit-timing.json','unit-process-launch.json','unit-process-cleanup.json','unit-log-sanitization.json'):
    p = HERE/name
    if p.exists():
        plain(p)
        assert name in rows and sha(p) == rows[name]
        p.unlink()
print('PASS failed attempt1 archived exact '+str(len(rows))+' artifacts/26 gates/15 deltas; final phase slots cleared')
