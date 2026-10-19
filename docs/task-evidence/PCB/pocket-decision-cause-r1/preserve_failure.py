"""Preserve a failed attempt before owned source or input records are replaced."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
target = HERE / ('attempt' + str(int(sys.argv[1])))
target.mkdir(exist_ok=False)
receipts = []
names = ('focused-native.txt', 'focused-timing.json', 'focused-receipt.txt',
         'focused-inputs.json', 'focused-input-audit.json', 'diagnostic-source.json',
         'process-launch.json', 'PcbPocketDecisionDiagnosticContractTest.java',
         'assess_decisions.py', 'check_assessor.py')
for name in names:
    source = HERE / name
    if not source.exists():
        continue
    raw = source.read_bytes()
    safe = raw
    substitutions = 0
    if name.endswith('.txt'):
        for prefix in (str(ROOT), ROOT.as_posix()):
            token = prefix.encode('utf-8')
            substitutions += safe.count(token)
            safe = safe.replace(token, b'<repo>')
    (target / name).write_bytes(safe)
    receipts.append({'file': name, 'rawSha256': hashlib.sha256(raw).hexdigest(),
                     'archivedSha256': hashlib.sha256(safe).hexdigest(),
                     'privateRepoPrefixSubstitutions': substitutions})
(target / 'preservation.json').write_text(json.dumps({'status': 'FAILED_ATTEMPT_PRESERVED',
    'files': receipts, 'scope': 'Exact bytes except documented repository-prefix sanitization in text logs'}, indent=2) + '\n')
print('PRESERVED failed attempt' + sys.argv[1])
