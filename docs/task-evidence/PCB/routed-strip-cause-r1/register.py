"""Register only the two native cause suites in the exact owned copy."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
registry = COPY / 'scripts/verify-current-contracts.ps1'
base = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
assert hashlib.sha256(registry.read_bytes()).hexdigest() == base['scripts/verify-current-contracts.ps1']
rows = [('PcbRoutedStripCauseContractTest', 'PCB routed strip cause contracts '),
        ('PcbRoutedStripCauseContractTestGroup2', 'PCB routed strip cause Group2 ')]
for name, marker in rows:
    assert marker in (COPY / ('tests/contracts/' + name + '.java')).read_text()
data = registry.read_bytes()
needle = b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest'; Marker = 'PCB post-selection edge trim ' },"
assert data.count(needle) == 1
newline = b'\r\n' if b'\r\n' in data else b'\n'
entries = ["        @{ Name = '%s'; Marker = '%s' }," % row for row in rows]
registry.write_bytes(data.replace(needle, needle + newline + newline.join(s.encode() for s in entries)))
print('Registered2 read-only current15 cause suites')
