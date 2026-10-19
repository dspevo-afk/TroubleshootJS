"""Register the four bounded foundation suites only in the exact owned copy."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
registry = COPY / 'scripts/verify-current-contracts.ps1'
base = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
assert hashlib.sha256(registry.read_bytes()).hexdigest() == base['scripts/verify-current-contracts.ps1']
rows = [('PcbPassivePoseFoundationContractTest', 'PCB passive pose foundation contracts '),
    ('PcbPassivePoseSourceContractTest', 'PCB passive pose source bindings '),
    ('PcbPassivePoseCurrent15ContractTest', 'PCB passive pose current15 contracts '),
    ('PcbPassivePoseCurrent15ContractTestGroup2', 'PCB passive pose current15 Group2 ')]
for name, marker in rows:
    assert marker in (COPY / ('tests/contracts/' + name + '.java')).read_text()
data = registry.read_bytes()
needle = b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest'; Marker = 'PCB post-selection edge trim ' },"
assert data.count(needle) == 1
newline = b'\r\n' if b'\r\n' in data else b'\n'
entries = ["        @{ Name = '%s'; Marker = '%s' }," % row for row in rows]
registry.write_bytes(data.replace(needle, needle + newline + newline.join(s.encode() for s in entries)))
print('Registered4 native-only passive foundation suites')
