"""Register only the new pure suite in the owned source copy."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
assert COPY.name.startswith('tsj-vacancy-grid-')
relative = 'scripts/verify-current-contracts.ps1'
target = COPY / relative
base = {r['path']: r['sha256'] for r in json.loads((HERE / 'copy-inputs.json').read_text())['files']}
assert hashlib.sha256(target.read_bytes()).hexdigest() == base[relative], 'Registry already changed'
data = target.read_bytes()
needle = b"        @{ Name = 'PcbVacancyTargetsContractTest'; Marker = 'PCB vacancy targets contracts ' },"
assert data.count(needle) == 1 and b"Name = 'PcbVacancyOriginGridContractTest'" not in data
newline = b'\r\n' if b'\r\n' in data else b'\n'
entry = b"        @{ Name = 'PcbVacancyOriginGridContractTest'; Marker = 'PCB vacancy origin grid contracts: ' },"
target.write_bytes(data.replace(needle, needle + newline + entry))
for source, archive in ((COPY / 'src/com/lushprojects/circuitjs1/client/PcbVacancyTargets.java',
                         HERE / 'PcbVacancyTargets.phase1.java'),
                        (COPY / 'tests/contracts/PcbVacancyOriginGridContractTest.java',
                         HERE / 'PcbVacancyOriginGridContractTest.java')):
    assert source.is_file() and not archive.exists()
    archive.write_bytes(source.read_bytes())
print('Registered one pure suite; exact implementation/test archives saved')
