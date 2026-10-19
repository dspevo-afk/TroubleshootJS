"""Audit the prior qualification boundary without normalizing source bytes."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
PACKET = Path(__file__).resolve().parent
PRIOR = ROOT / 'docs/task-evidence/PCB/pad-cost-vacancy-r1/restored13-requalification-r1'
baseline = json.loads((PRIOR / 'final-inputs.json').read_text(encoding='utf-8'))
changed, missing = [], []
for item in baseline['files']:
    path = ROOT / item['path']
    if not path.is_file():
        missing.append(item['path'])
    elif hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
        changed.append(item['path'])
expected_changes = ['scripts/verify-current-contracts.ps1']
result = {
    'status': 'PASS' if sorted(changed) == expected_changes and not missing else 'FAIL',
    'priorInputs': len(baseline['files']), 'changed': changed, 'missing': missing,
    'allowedChange': 'Native-only diagnostic suite registration; inspected separately',
    'boundary': 'Every other frozen input is byte-identical, including production sources. Compiled web is checked separately in compiled-web-boundary-audit.json. The new native test is outside the prior manifest and receives fresh focused validation.',
    'gwtAndPlayerFlow': 'NOT RUN for diagnostic-only change; prior production evidence retained only at audited unchanged inputs',
}
(PACKET / 'production-boundary-audit.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))
if result['status'] != 'PASS':
    raise SystemExit(1)
