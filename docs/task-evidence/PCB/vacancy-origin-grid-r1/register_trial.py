"""Register the predeclared eight trial suites in the isolated native copy."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
registry = COPY / 'scripts/verify-current-contracts.ps1'
expected = json.loads((HERE / 'unit-inputs.json').read_text())['overrides']['scripts/verify-current-contracts.ps1']
assert hashlib.sha256(registry.read_bytes()).hexdigest() == expected, 'Registry drift before trial registration'
rows = [('PcbVacancyGridTrialTargetContractTest', 'PCB vacancy grid trial target contracts '),
        ('PcbVacancyGridTrialContractTest', 'PCB vacancy grid trial contracts ')]
rows += [('PcbVacancyGridTrialContractTestGroup' + str(n), 'PCB vacancy grid trial Group' + str(n) + ' ') for n in range(2, 8)]
for name, marker in rows:
    source = COPY / ('tests/contracts/' + name + '.java')
    assert source.is_file() and marker in source.read_text(), 'Missing source/marker ' + name
data = registry.read_bytes()
needle = b"        @{ Name = 'PcbVacancyOriginGridContractTest'; Marker = 'PCB vacancy origin grid contracts: ' },"
assert data.count(needle) == 1
newline = b'\r\n' if b'\r\n' in data else b'\n'
entries = ["        @{ Name = '%s'; Marker = '%s' }," % row for row in rows]
registry.write_bytes(data.replace(needle, needle + newline + newline.join(s.encode() for s in entries)))
print('Registered8 predeclared trial suites; pure3 retained')
