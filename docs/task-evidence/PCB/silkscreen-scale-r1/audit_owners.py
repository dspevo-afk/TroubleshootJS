"""Prove the bounded production change against all prior accepted inputs."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
prior = HERE.parent / 'post-selection-edge-trim-r1/final-inputs.json'
before = {r['path']: r['sha256'] for r in json.loads(prior.read_text())['files']}
after = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in before}
changed = [p for p in sorted(before) if before[p] != after[p]]
allowed = ['scripts/verify-current-contracts.ps1',
           'src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java']
paths = set(before)
for directory in ('src', 'scripts', 'tests/contracts'):
    for p in (ROOT / directory).rglob('*'):
        if p.is_file() and '__pycache__' not in p.parts and p.suffix != '.pyc':
            paths.add(p.relative_to(ROOT).as_posix())
paths.update(p.relative_to(ROOT).as_posix() for p in (ROOT / '.tools/gwt-2.7.0').glob('*.jar'))
added = sorted(paths - set(before))
expected_added = ['tests/contracts/PcbSilkscreenScaleContractTest.java']
passed = changed == allowed and added == expected_added
result = {'status': 'PASS' if passed else 'FAIL', 'priorInputCount': len(before),
          'unchangedPriorInputs': len(before) - len(changed), 'changedPaths': changed,
          'newSourceContractPaths': added,
          'changedOwnerHashes': [{'path': p, 'oldSha256': before[p], 'newSha256': after[p]} for p in changed],
          'scope': 'All remaining generation, graph, geometry, routing, replay, instrument/input and prior gate-owner bytes exact; this is an input-boundary audit, not new all-seed runtime proof'}
(HERE / 'unchanged-owner-audit.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
raise SystemExit(not passed)
