"""Register two ordered native cases only in the owned accepted-source copy."""
import json
from pathlib import Path
import hashlib
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch'])
accepted = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
registry = COPY / 'scripts/verify-current-contracts.ps1'
assert hashlib.sha256(registry.read_bytes()).hexdigest() == accepted['scripts/verify-current-contracts.ps1']
data = registry.read_bytes()
anchor = b"        @{ Name = 'PcbScreenLabelLayoutContractTest';"
assert data.count(anchor) == 1
eol = b'\r\n' if b'\r\n' in data else b'\n'
rows = []
for suffix, marker in (('Original', 'original'), ('Translated', 'translated')):
    name = 'PcbQaRoute' + suffix + 'ContractTest'
    assert name.encode() not in data and (COPY / 'tests/contracts' / (name + '.java')).is_file()
    rows.append(("        @{ Name = '" + name + "'; Marker = 'PCB QA route " + marker + " contracts: ' },").encode() + eol)
data = data.replace(anchor, b''.join(rows) + anchor)
compile_anchor = b"    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/P03StructuralFixtures.java')"
compile_row = b"    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/PcbQaRouteComparisonContractTest.java')"
assert data.count(compile_anchor) == 1 and compile_row not in data
registry.write_bytes(data.replace(compile_anchor, compile_row + eol + compile_anchor))
for name in ('PcbQaRouteComparisonContractTest.java', 'PcbQaRouteOriginalContractTest.java', 'PcbQaRouteTranslatedContractTest.java'):
    (HERE / name).write_bytes((COPY / 'tests/contracts' / name).read_bytes())
(HERE / 'generator-adapter.java').write_bytes((COPY / 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java').read_bytes())
print('REGISTERED original then translated; archived generator adapter and3 native fixture files')
