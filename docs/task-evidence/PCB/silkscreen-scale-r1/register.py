"""Insert exactly one permanent native contract after root source review."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
name = 'PcbSilkscreenScaleContractTest'
registry = ROOT / 'scripts/verify-current-contracts.ps1'
original = json.loads((HERE / 'original-source.json').read_text())['files'][1]
assert hashlib.sha256(registry.read_bytes()).hexdigest() == original['sha256']
test = ROOT / 'tests/contracts' / (name + '.java')
assert test.is_file() and 'PASS: PCB silkscreen scale contracts: ' in test.read_text()
data = registry.read_bytes()
anchor = b"        @{ Name = 'PcbScreenLabelLayoutContractTest';"
assert data.count(anchor) == 1 and name.encode() not in data
eol = b'\r\n' if b'\r\n' in data else b'\n'
row = ("        @{ Name = '" + name + "'; Marker = 'PCB silkscreen scale contracts: ' },").encode() + eol
registry.write_bytes(data.replace(anchor, row + anchor))
print('REGISTERED one permanent physical silkscreen projection suite')
