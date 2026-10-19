"""Seal root-reviewed1528 inputs and complete pre-runtime gate owners."""
import hashlib
import json
import stat
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
mode = sys.argv[1]
assert mode in ('freeze', 'audit')
base = read(HERE/'copy-inputs.json')
plan = read(HERE/'source-plan.json')
assert plan['rootReviewed'] is True and plan['baseCopyCount'] == 1510 and plan['copyCount'] == 1528
old = {r['path']: r['sha256'] for r in base['files']}
overrides = set(plan['overrides']); additions = set(plan['additions'])
assert len(overrides) == 12 and len(additions) == 18
def inventory(directory):
    assert not directory.is_symlink() and not directory.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
    result = {}
    for child in sorted(directory.iterdir()):
        assert not child.is_symlink() and not child.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
        assert child.resolve().is_relative_to(COPY)
        if child.is_dir():
            assert child.name != '__pycache__'
            result.update(inventory(child))
        else: result[child.relative_to(COPY).as_posix()] = sha(child)
    return result
current = inventory(COPY)
assert len(current) == 1528 and set(current) == set(old)|additions
assert {p for p in old if current[p] != old[p]} == overrides
for folder, key in [('passive-pose-policy-r1', 'policySealSha256'), ('passive-sizing-control-r1', 'controlSealSha256')]:
    packet = HERE.parent/folder
    assert sha(packet/'native-inputs.json') == base[key]
    prior = read(packet/'native-inputs.json')
    for name, digest in prior['gateOwners'].items(): assert sha(packet/name) == digest
    for name, digest in dict(prior['overrides'], **prior['additions']).items(): assert sha(packet/'source'/name) == digest
main_seal = HERE.parent/'silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == base['main1475SealSha256']
main = read(main_seal)['files']; assert len(main) == 1475
for row in main: assert sha(ROOT/row['path']) == row['sha256']
for name, digest in read(HERE/'protocol.json')['baselinesSha256'].items(): assert sha(HERE/name) == digest
owners = ('proposal-initial.json', 'proposal.json', 'scope-review.md', 'prepare.py',
    'prepare_fixtures.py', 'register_and_protocol.py', 'prepare_phase_gates.py',
    'copy-inputs.json', 'accepted15-baselines.json', 'raw-baselines.json', 'new-suites.json',
    'protocol.json', 'unit-suites.json', 'routes-suites.json', 'worker-notes.md', 'reader-notes.md',
    'run_native.ps1', 'check_process_cleanup.ps1', 'sanitize.py', 'preserve_attempt.py',
    'validate_unit.py', 'run_assessment.py', 'assess.py', 'check_assessment.py',
    'assessment-canaries.json', 'assessment-canary-results.json', 'inputs.py',
    'source-plan.json', 'review_source.py', 'source-review.json', 'source-review.diff',
    'review-ack.json', 'retire_copy.py')
assert all((HERE/name).is_file() for name in owners)
archive = HERE/'source'
if mode == 'freeze':
    assert not archive.exists() and not (HERE/'native-inputs.json').exists()
    for name in sorted(overrides|additions):
        target = archive/name; target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((COPY/name).read_bytes())
for name in overrides|additions: assert sha(archive/name) == current[name]
actual = {'baseSha256': sha(HERE/'copy-inputs.json'), 'copyCount': 1528,
    'overrides': {name: current[name] for name in sorted(overrides)},
    'additions': {name: current[name] for name in sorted(additions)},
    'gateOwners': {name: sha(HERE/name) for name in owners}, 'main1475SealSha256': sha(main_seal),
    'reusedPolicySealSha256': base['policySealSha256'], 'reusedControlSealSha256': base['controlSealSha256']}
target = HERE/'native-inputs.json'
if mode == 'freeze':
    target.write_text(json.dumps(actual, indent=2)+'\n')
    print('FROZEN native1528 /30 source deltas /'+str(len(owners))+' gates; main1475 exact')
else:
    same = read(target) == actual
    (HERE/'native-input-audit.json').write_text(json.dumps({
        'status': 'PASS' if same else 'FAIL', 'copyCount': 1528, 'main1475Exact': True,
        'policyAndControlReferencesExact': True}, indent=2)+'\n')
    print('PASS exact native1528/gates/references/main1475' if same else 'FAIL source-sizing seal')
    raise SystemExit(not same)
