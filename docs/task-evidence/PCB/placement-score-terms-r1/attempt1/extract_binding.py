"""Pin existing observations before the one-attempt score replay."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
prior = HERE.parent / 'pocket-decision-cause-r1'
data = (prior / 'focused-native.txt').read_bytes()
assert hashlib.sha256(data).hexdigest() == 'ec3c40456877334d5bcdb4db850bcb456d617fe1445dc4037cf61468d7fe6cbe'
prefix = 'PCB_POCKET_DECISION_CASE '
rows = [json.loads(line[len(prefix):]) for line in data.decode('utf-8-sig').splitlines() if line.startswith(prefix)]
row, = [r for r in rows if r['seed'] == '42']
diagnostic = row['diagnostic']
chosen, = [r for r in diagnostic['chosenComparisons'] if r['componentId'] == 'QA']
digest = 'd88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b'
assert all(diagnostic[k] == digest for k in ('rawPlanSha256', 'returnedPlanSha256', 'selectedPlacementPlanSha256'))
assert row['selectedAttempt'] == 1 and row['layoutSeed'] == '5526715536693200536'
result = {'status': 'PINNED', 'priorLogSha256': hashlib.sha256(data).hexdigest(),
          'root': '42', 'layoutSeed': row['layoutSeed'], 'selectedAttempt': 1,
          'selectedPlanSha256': digest, 'chosen': chosen,
          'alternative': {'componentId': 'QA', 'recordedOriginSequence': 6297,
                          'x': 1390, 'y': 1550, 'rotation': 'DEG_0', 'mountingSide': 'TOP',
                          'effectiveScore': 5948.889212121212},
          'claim': 'Existing observations; prior causal result remains INCONCLUSIVE'}
(HERE / 'plan-binding.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
