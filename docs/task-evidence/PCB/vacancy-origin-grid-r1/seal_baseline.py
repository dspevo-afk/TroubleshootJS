"""Pin complete accepted15 routed exports before a consumed policy trial."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def strict_pairs(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, 'Duplicate JSON field: ' + key
        result[key] = value
    return result

target = HERE / 'accepted15-baselines.json'
assert not target.exists(), 'Baseline already sealed'
source = HERE.parent / 'post-selection-edge-trim-r1/final-native.txt'
rows = []
for line in source.read_text(encoding='utf-8-sig').splitlines():
    if line.startswith('PCB_EDGE_TRIM_CASE '):
        row = json.loads(line.split(' ', 1)[1], object_pairs_hook=strict_pairs)
        if row['version'] == 15:
            assert row['status'] == 'PASS' and row['density'] == 14
            rows.append(row)
roots = json.loads((HERE / 'proposal.json').read_text())['roots']
assert len(rows) == len(roots) == 14 and len({r['seed'] for r in rows}) == 14
assert {r['seed'] for r in rows} == set(roots)
lookup = {r['seed']: r for r in rows}
rows = [lookup[seed] for seed in roots]
prior = json.loads((source.parent / 'final-inputs.json').read_text())['files']
changed = [r['path'] for r in prior if sha(ROOT / r['path']) != r['sha256']]
assert set(changed) == {'src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java',
                       'scripts/verify-current-contracts.ps1'}
target.write_text(json.dumps({'status': 'PINNED', 'scope': 'All14 informed accepted15 regressions; not unseen',
    'source': source.relative_to(ROOT).as_posix(), 'sourceSha256': sha(source),
    'rows': rows, 'reuseBoundary': {'prior1466Inputs': len(prior), 'exactPriorInputs': len(prior) - len(changed),
    'changedPriorOwners': changed,
    'explanation': 'Renderer-only projection and one added native suite registration are not consumed by physical generation/metric export; all generation/model/geometry/routing/source/metric fixtures stay byte-exact. Their separate final-source renderer native/GWT/player gates have passed.'}}, indent=2) + '\n')
(HERE / 'baseline-seal.json').write_text(json.dumps({'status': 'PASS', 'roots': roots,
    'acceptedBaselineSha256': sha(target), 'sourceLogSha256': sha(source),
    'stage': 'Before phase2 version/planner hook or routed trial; pure helper is unused'}, indent=2) + '\n')
print('PASS exact14 accepted15 baselines sealed; 1464 prior owners exact, renderer/registry boundary explicit')
