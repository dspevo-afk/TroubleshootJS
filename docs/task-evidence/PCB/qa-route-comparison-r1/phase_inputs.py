"""Freeze the isolated route implementation and exact unchanged main boundary."""
import hashlib
import json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
tests = ('PcbQaRouteComparisonContractTest.java', 'PcbQaRouteOriginalContractTest.java', 'PcbQaRouteTranslatedContractTest.java')

def collect():
    current = {}
    for p in sorted(COPY.rglob('*')):
        if p.is_file():
            assert not p.is_symlink() and '__pycache__' not in p.parts
            current[p.relative_to(COPY).as_posix()] = sha(p)
    accepted = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
    additions = {'tests/contracts/' + n for n in tests}
    allowed = {'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java', 'scripts/verify-current-contracts.ps1'}
    assert set(current) == set(accepted) | additions, 'Unexpected copied inventory'
    assert {p for p in accepted if current[p] != accepted[p]} == allowed, 'Unexpected copied owner change'
    for name in tests:
        assert sha(HERE / name) == current['tests/contracts/' + name], 'Fixture archive mismatch'
    assert sha(HERE / 'generator-adapter.java') == current['src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java']
    rows = [{'path': 'copy/' + p, 'sha256': current[p]} for p in sorted(current)]
    names = ('README.md', 'proposal.json', 'prepare.py', 'prepare_binding.py', 'copy-inputs.json',
             'accepted-case.json', 'seed-copy-correction.json', 'register.py', 'run_focused.ps1',
             'phase_inputs.py', 'check_routes.py', 'check_reader.py', 'reader-canaries.json',
             'sanitize_logs.py', 'retire_copy.py', 'check_process_cleanup.ps1', 'source-review.md',
             'worker-notes.md', 'source-feasibility.md', 'pre-runtime-clarification.json', 'generator-adapter.java',
             'preserve_failure.py', 'repair_registration.py', 'registration-repair.json') + tests
    rows += [{'path': 'packet/' + name, 'sha256': sha(HERE / name)} for name in names]
    renderer = HERE.parent / 'silkscreen-scale-r1/final-inputs.json'
    for row in json.loads(renderer.read_text())['files']:
        assert sha(ROOT / row['path']) == row['sha256'], 'Main frozen owner changed: ' + row['path']
    rows.append({'path': 'main/silkscreen-final-inputs.json', 'sha256': sha(renderer)})
    return rows

actual = collect()
target = HERE / 'native-inputs.json'
if sys.argv[1] == 'freeze':
    assert not target.exists(), 'Already frozen'
    target.write_text(json.dumps({'files': actual}, indent=2) + '\n')
    print('FROZEN ' + str(len(actual)) + ' copy/gate records; main1475 exact')
elif sys.argv[1] == 'audit':
    frozen = json.loads(target.read_text())['files']
    before = {r['path']: r['sha256'] for r in frozen}
    after = {r['path']: r['sha256'] for r in actual}
    changed = [p for p in sorted(set(before) | set(after)) if before.get(p) != after.get(p)]
    result = {'status': 'FAIL' if changed else 'PASS', 'changedPaths': changed,
              'frozenCount': len(before), 'actualCount': len(after), 'mainRenderer1475Exact': True}
    (HERE / 'native-input-audit.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    raise SystemExit(bool(changed))
else:
    raise ValueError('Expected freeze/audit')
