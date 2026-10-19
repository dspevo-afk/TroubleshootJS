"""Register only the diagnostic in the owned source copy."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
name = 'PcbPlacementScoreTermsContractTest'
registry = ROOT / 'scripts/verify-current-contracts.ps1'
prior = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
assert hashlib.sha256(registry.read_bytes()).hexdigest() == prior['scripts/verify-current-contracts.ps1']
test = ROOT / 'tests/contracts' / (name + '.java')
assert test.is_file() and 'PASS: PCB placement score terms contracts: ' in test.read_text()
data = registry.read_bytes()
anchor = b"        @{ Name = 'PcbScreenLabelLayoutContractTest';"
assert data.count(anchor) == 1 and name.encode() not in data
eol = b'\r\n' if b'\r\n' in data else b'\n'
addition = ("        @{ Name = '" + name + "'; Marker = 'PCB placement score terms contracts: ' },").encode() + eol
registry.write_bytes(data.replace(anchor, addition + anchor))
(HERE / (name + '.java')).write_bytes(test.read_bytes())
(HERE / 'planner-instrumentation.java').write_bytes((ROOT / 'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java').read_bytes())
print('REGISTERED one suite in isolated copy; archived final instrumentation/test')
