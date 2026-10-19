"""Preserve the completed Java7 fixture compile failure, then repair one declaration."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert json.loads((HERE / 'unit-timing.json').read_text(encoding='utf-8-sig'))['status'] == 'FAIL'
assert json.loads((HERE / 'unit-process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'
prior = HERE / 'unit-attempt1'
assert not prior.exists()
prior.mkdir()
rows = []
for source in sorted(HERE.iterdir()):
    if not source.is_file() or source.name == 'local-state.json':
        continue
    target = prior / source.name
    target.write_bytes(source.read_bytes())
    assert sha(target) == sha(source)
    rows.append({'path': source.name, 'sha256': sha(target)})
registry = COPY / 'scripts/verify-current-contracts.ps1'
(prior / 'verify-current-contracts.ps1').write_bytes(registry.read_bytes())
rows.append({'path': 'verify-current-contracts.ps1', 'sha256': sha(registry)})
(prior / 'preserved-files.json').write_text(json.dumps({'status': 'PASS', 'files': rows}, indent=2) + '\n')
for name in ('unit-process-launch.json', 'unit-timing.json', 'unit-native.txt',
             'unit-inputs.json', 'unit-input-audit.json', 'unit-process-cleanup.json',
             'unit-log-sanitization.json'):
    source = HERE / name
    assert sha(source) == sha(prior / name)
    source.unlink()
assert not (HERE / 'unit-native-receipt.txt').exists(), 'Compile failed before receipt emission'
test = COPY / 'tests/contracts/PcbVacancyOriginGridContractTest.java'
before = test.read_bytes()
old = b'        Vector<Rectangle> nullObstacle = new Vector<Rectangle>();'
new = b'        final Vector<Rectangle> nullObstacle = new Vector<Rectangle>();'
assert before.count(old) == 1
test.write_bytes(before.replace(old, new))
(HERE / 'PcbVacancyOriginGridContractTest.java').write_bytes(test.read_bytes())
(HERE / 'unit-repair.json').write_text(json.dumps({
    'scope': 'One test local declaration made explicitly final for maintained -source7; no assertion, helper, driver, budget or oracle change',
    'originalSha256': hashlib.sha256(before).hexdigest(),
    'repairedSha256': sha(test), 'priorAttempt': 'unit-attempt1',
    'priorStatus': 'FAIL', 'priorElapsedSeconds': 35.8669925,
    'priorCleanup': 'PASS; maintained terminal cleanup and both recorded drivers exited'
}, indent=2) + '\n')
print('Preserved completed attempt1; repaired only explicit-final test declaration')
