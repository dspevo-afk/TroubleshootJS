"""Preserve completed failure and frozen packet owners before repairs."""
import hashlib
import json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
target = HERE / ('attempt' + str(int(sys.argv[1])))
assert json.loads((HERE / 'native-timing.json').read_text(encoding='utf-8-sig'))['status'] == 'FAIL'
assert json.loads((HERE / 'native-input-audit.json').read_text())['status'] == 'PASS'
assert json.loads((HERE / 'process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'
target.mkdir(exist_ok=False)
frozen = json.loads((HERE / 'native-inputs.json').read_text())['files']
names = {r['path'][7:] for r in frozen if r['path'].startswith('packet/')}
names.update(('native-inputs.json', 'native-input-audit.json', 'native.txt', 'native-timing.json',
              'native-receipt.txt', 'process-launch.json', 'process-cleanup.json'))
receipts = []
for name in sorted(names):
    source = HERE / name
    if not source.exists():
        continue
    raw = source.read_bytes()
    (target / name).write_bytes(raw)
    receipts.append({'file': name, 'sha256': hashlib.sha256(raw).hexdigest()})
(target / 'preservation.json').write_text(json.dumps({'status': 'FAILED_TERMINAL_ATTEMPT_PRESERVED',
    'files': receipts, 'timingFromCompletedDriver': True, 'cleanupCheckedBeforePreservation': True,
    'sanitization': 'None; native log contains no private absolute path'}, indent=2) + '\n')
print('PRESERVED completed failed attempt' + sys.argv[1])
