"""Freeze/audit exact phase2 source deltas and gate owners, plus accepted main1475."""
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
prefix = 'src/com/lushprojects/circuitjs1/client/'
overrides = {prefix + name for name in ('PcbVacancyTargets.java', 'Rb56Placement.java',
    'PcbPlacementPlanner.java', 'SeededPcbLayoutGenerator.java', 'PcbPlacementConstraints.java')}
overrides |= {'scripts/verify-current-contracts.ps1', 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'}
additions = {'tests/contracts/PcbVacancyOriginGridContractTest.java',
    'tests/contracts/PcbVacancyGridTrialTargetContractTest.java', 'tests/contracts/PcbVacancyGridTrialContractTest.java'}
additions |= {'tests/contracts/PcbVacancyGridTrialContractTestGroup' + str(n) + '.java' for n in range(2, 8)}
current = {}
for p in sorted(COPY.rglob('*')):
    if p.is_file():
        assert not p.is_symlink() and '__pycache__' not in p.parts
        current[p.relative_to(COPY).as_posix()] = sha(p)
assert len(base) == 1467 and len(current) == 1476 and set(current) == set(base) | additions
assert {p for p in base if base[p] != current[p]} == overrides, 'Unexpected changed native owner'
unit = json.loads((HERE / 'unit-inputs.json').read_text())
assert current[prefix + 'PcbVacancyTargets.java'] == unit['overrides'][prefix + 'PcbVacancyTargets.java']
assert current['tests/contracts/PcbVacancyOriginGridContractTest.java'] == unit['additions']['tests/contracts/PcbVacancyOriginGridContractTest.java']
main_seal = HERE.parent / 'silkscreen-scale-r1/final-inputs.json'
for row in json.loads(main_seal.read_text())['files']:
    assert sha(ROOT / row['path']) == row['sha256'], 'Main accepted owner changed: ' + row['path']
archive = HERE / 'phase2-source'
if sys.argv[1] == 'freeze':
    assert not archive.exists() and not (HERE / 'trial-inputs.json').exists()
    for relative in sorted(overrides | additions):
        target = archive / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((COPY / relative).read_bytes())
for relative in sorted(overrides | additions):
    assert sha(archive / relative) == current[relative], 'Archive drift: ' + relative
owners = ('proposal.json', 'phase2-hook-clarification.json', 'phase2-plan.json', 'copy-inputs.json',
    'accepted15-baselines.json', 'baseline-seal.json', 'unit-inputs.json', 'unit-input-audit.json',
    'unit-timing.json', 'unit-process-cleanup.json', 'unit-native.txt', 'unit-native-receipt.txt',
    'phase2-worker-notes.md', 'phase2-source-review.json', 'review_trial_source.py', 'register_trial.py', 'trial_inputs.py',
    'run_trial.ps1', 'check_trial_process_cleanup.ps1', 'sanitize_trial.py',
    'assess_trial.py', 'check_trial_reader.py', 'trial-reader-canaries.json', 'retire_copy.py')
actual = {'baseSha256': sha(base_path), 'copyCount': len(current),
    'overrides': {p: current[p] for p in sorted(overrides)},
    'additions': {p: current[p] for p in sorted(additions)},
    'gateOwners': {p: sha(HERE / p) for p in owners}, 'main1475SealSha256': sha(main_seal)}
target = HERE / 'trial-inputs.json'
if sys.argv[1] == 'freeze':
    target.write_text(json.dumps(actual, indent=2) + '\n')
    print('FROZEN native1476 via exact1467 base,7overrides/9additions; main1475 exact')
elif sys.argv[1] == 'audit':
    same = json.loads(target.read_text()) == actual
    (HERE / 'trial-input-audit.json').write_text(json.dumps({'status': 'PASS' if same else 'FAIL',
        'copyCount': len(current), 'baseCount': len(base), 'changedOwners': sorted(overrides),
        'addedOwners': sorted(additions), 'main1475Exact': True}, indent=2) + '\n')
    print('PASS exact trial inputs/main1475' if same else 'FAIL trial input seal')
    raise SystemExit(not same)
else:
    raise ValueError('Expected freeze/audit')
