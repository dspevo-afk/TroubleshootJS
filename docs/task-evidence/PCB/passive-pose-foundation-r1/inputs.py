"""Freeze/audit complete native inputs through immutable base plus explicit deltas."""
import hashlib
import json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
base_path = HERE / 'copy-inputs.json'
base = {r['path']: r['sha256'] for r in json.loads(base_path.read_text())['files']}
overrides = {'src/com/lushprojects/circuitjs1/client/PhysicalPackages.java',
    'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java', 'scripts/verify-current-contracts.ps1'}
additions = {'tests/contracts/PcbPassivePoseFoundationContractTest.java',
    'tests/contracts/PcbPassivePoseSourceContractTest.java',
    'tests/contracts/PcbPassivePoseCurrent15ContractTest.java',
    'tests/contracts/PcbPassivePoseCurrent15ContractTestGroup2.java'}
current = {}
for p in sorted(COPY.rglob('*')):
    if p.is_file():
        assert not p.is_symlink() and '__pycache__' not in p.parts
        current[p.relative_to(COPY).as_posix()] = sha(p)
assert len(base) == 1467 and len(current) == 1471 and set(current) == set(base) | additions
assert {p for p in base if base[p] != current[p]} == overrides, 'Unexpected changed native owner'
main_seal = HERE.parent / 'silkscreen-scale-r1/final-inputs.json'
for row in json.loads(main_seal.read_text())['files']:
    assert sha(ROOT / row['path']) == row['sha256'], 'Main accepted owner changed'
owners = ('proposal.json', 'prepare.py', 'prepare_gates.py', 'copy-inputs.json', 'accepted15-baselines.json', 'worker-notes.md', 'source-review.json', 'review_source.py', 'register.py', 'inputs.py', 'run_native.ps1', 'check_process_cleanup.ps1', 'sanitize.py', 'final_rows.py', 'check_final_rows.py', 'final-row-canaries.json', 'retire_copy.py', 'preserve_attempt.py')
assert all((HERE / p).is_file() for p in owners), 'Gate owner missing before source archive'
archive = HERE / 'source'
if sys.argv[1] == 'freeze':
    assert not archive.exists() and not (HERE / 'native-inputs.json').exists()
    for relative in sorted(overrides | additions):
        target = archive / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((COPY / relative).read_bytes())
for relative in sorted(overrides | additions):
    assert sha(archive / relative) == current[relative], 'Archive mismatch'
actual = {'baseSha256': sha(base_path), 'copyCount': len(current),
    'overrides': {p: current[p] for p in sorted(overrides)},
    'additions': {p: current[p] for p in sorted(additions)},
    'gateOwners': {p: sha(HERE / p) for p in owners}, 'main1475SealSha256': sha(main_seal)}
target = HERE / 'native-inputs.json'
if sys.argv[1] == 'freeze':
    target.write_text(json.dumps(actual, indent=2) + '\n')
    print('FROZEN native1471 via accepted1467 base,3overrides/4additions; main1475 exact')
elif sys.argv[1] == 'audit':
    same = json.loads(target.read_text()) == actual
    (HERE / 'native-input-audit.json').write_text(json.dumps({'status': 'PASS' if same else 'FAIL',
        'copyCount': len(current), 'changedOwners': sorted(overrides), 'addedOwners': sorted(additions),
        'main1475Exact': True}, indent=2) + '\n')
    print('PASS exact native1471 and main1475' if same else 'FAIL native seal')
    raise SystemExit(not same)
else:
    raise ValueError('Expected freeze/audit')
