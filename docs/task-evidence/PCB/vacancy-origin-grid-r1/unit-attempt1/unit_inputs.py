"""Bind every copy input through its immutable base and explicit overrides."""
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
overrides = {'src/com/lushprojects/circuitjs1/client/PcbVacancyTargets.java',
             'scripts/verify-current-contracts.ps1'}
addition = 'tests/contracts/PcbVacancyOriginGridContractTest.java'
current = {}
for p in sorted(COPY.rglob('*')):
    if p.is_file():
        assert not p.is_symlink() and '__pycache__' not in p.parts
        current[p.relative_to(COPY).as_posix()] = sha(p)
assert len(base) == 1467 and set(current) == set(base) | {addition}
assert {p for p in base if base[p] != current[p]} == overrides
assert sha(HERE / 'PcbVacancyTargets.phase1.java') == current['src/com/lushprojects/circuitjs1/client/PcbVacancyTargets.java']
assert sha(HERE / 'PcbVacancyOriginGridContractTest.java') == current[addition]
main_seal = HERE.parent / 'silkscreen-scale-r1/final-inputs.json'
for row in json.loads(main_seal.read_text())['files']:
    assert sha(ROOT / row['path']) == row['sha256'], 'Main accepted owner changed: ' + row['path']
owners = ('proposal.json', 'phase2-hook-clarification.json', 'prepare.py', 'copy-inputs.json', 'seal_baseline.py',
          'accepted15-baselines.json', 'baseline-seal.json', 'register_unit.py',
          'run_unit.ps1', 'unit_inputs.py', 'sanitize_unit.py',
          'check_unit_process_cleanup.ps1', 'worker-notes.md',
          'PcbVacancyTargets.phase1.java', 'PcbVacancyOriginGridContractTest.java')
actual = {'baseSha256': sha(base_path), 'copyCount': len(current),
    'overrides': {p: current[p] for p in sorted(overrides)}, 'additions': {addition: current[addition]},
    'gateOwners': {p: sha(HERE / p) for p in owners}, 'main1475SealSha256': sha(main_seal)}
target = HERE / 'unit-inputs.json'
if sys.argv[1] == 'freeze':
    assert not target.exists(), 'Unit phase already frozen'
    target.write_text(json.dumps(actual, indent=2) + '\n')
    print('FROZEN native1468 via exact1467 base, two overrides/one addition; main1475 exact')
elif sys.argv[1] == 'audit':
    same = json.loads(target.read_text()) == actual
    (HERE / 'unit-input-audit.json').write_text(json.dumps({'status': 'PASS' if same else 'FAIL',
        'copyCount': len(current), 'baseCount': len(base), 'changedOwners': sorted(overrides),
        'addedOwners': [addition], 'main1475Exact': True}, indent=2) + '\n')
    print('PASS exact unit phase inputs/main1475' if same else 'FAIL unit input seal')
    raise SystemExit(not same)
else:
    raise ValueError('Expected freeze/audit')
