from pathlib import Path
import hashlib, json, subprocess, uuid

s = Path(__file__).resolve().parent
root = Path('[REDACTED_USER_PATH]
draft = s / 'scratch/owned-zero-row-draft'
pointer = s / 'owned-zero-row-source-pair-path.txt'
assert not pointer.exists()
cir = 'src/com/lushprojects/circuitjs1/client/CirSim.java'
test = 'tests/contracts/Q30OwnedLuZeroRowContractTest.java'
driver = 'scripts/verify-current-contracts.ps1'
def sha(raw): return hashlib.sha256(raw).hexdigest()
assert sha((root/cir).read_bytes()) == 'f836bfc58bdfb87d67f25184ebcd51e60d9eb820d6cc5acb966bcde98a64296b'
assert (root/cir).read_bytes() == (draft/'baseline'/cir).read_bytes()
assert sha((draft/'candidate'/cir).read_bytes()) == 'deb593ff2471904a72690cf0332a53bc968a4c93f6f2ac28e3140fa59dc1e242'
assert sha((draft/'candidate'/test).read_bytes()) == '797e0240b84977891e1f1852bed4cfdcbf11e6f13032abebdc482d32ce7a3544'
paths = [p for p in subprocess.check_output(['git','ls-files','-z','--','src','scripts','tests','war','.settings','.classpath','.project'], cwd=root).decode().split('\0') if p]
paths += ['src/com/lushprojects/circuitjs1/client/Q30NormalAcceptanceVerifier.java']
paths += sorted(p.relative_to(root).as_posix() for p in (root/'.tools/gwt-2.7.0').glob('*.jar'))
assert len(paths) == len(set(paths)) == 1338
assert len([p for p in paths if p.endswith('.jar')]) == 9
assert not any('__pycache__' in p or p.startswith('war/circuitjs1/') for p in paths)
assert test not in paths
raw_driver = (root/driver).read_bytes()
anchor = b"        @{ Name = 'A07ExecutionContractTest'; Marker = 'A07 execution contracts ' },"
assert raw_driver.count(anchor) == 1
newline = b'\r\n' if b'\r\n' in raw_driver else b'\n'
new_driver = raw_driver.replace(anchor, anchor + newline + b"        @{ Name = 'Q30OwnedLuZeroRowContractTest'; Marker = 'Q30 owned LU zero-row contracts ' },")
records = []
target = s / ('trial-owned-zero-row-' + uuid.uuid4().hex)
target.mkdir()
for name in sorted(paths):
    source = root/name
    assert source.is_file() and not source.is_symlink(), name
    raw = source.read_bytes()
    records.append({'path': name, 'sha256': sha(raw), 'bytes': len(raw)})
    for arm in ('control','candidate'):
        dst = target/arm/name
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_bytes(raw)
for arm in ('control','candidate'):
    (target/arm/driver).write_bytes(new_driver)
    (target/arm/test).write_bytes((draft/'candidate'/test).read_bytes())
    assert len([p for p in (target/arm/'war').rglob('*') if p.is_file()]) == 29
(target/'candidate'/cir).write_bytes((draft/'candidate'/cir).read_bytes())
manifests = {}
for arm in ('control','candidate'):
    manifests[arm] = [{'path': name, 'sha256': sha((target/arm/name).read_bytes()), 'bytes': (target/arm/name).stat().st_size} for name in sorted(paths+[test])]
differences = [a['path'] for a,b in zip(manifests['control'], manifests['candidate']) if a != b]
assert differences == [cir]
audit = {'status': 'SOURCE_ONLY_PAIR_PREPARED_NOT_VALIDATED', 'head': subprocess.check_output(['git','rev-parse','HEAD'], cwd=root,text=True).strip(), 'scope': 'current unaccepted plan4; same new native test and driver registration; no generated WAR reused', 'generatedWarCopied': False, 'rootInputs': records, 'armInputs': manifests, 'differences': differences, 'sharedTestChanges': [driver, test]}
(target/'source-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
pointer.write_text(str(target))
print(json.dumps({'directory': str(target), 'rootInputs': len(records), 'armInputs':len(manifests['candidate']), 'differences': differences}))
