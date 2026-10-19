"""Register only the fixed native experiment in the owned source copy."""
import hashlib
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
UNIT=[
 ('PcbPassivePoseFoundationContractTest','PCB passive pose foundation contracts '),
 ('PcbPassivePoseSourceContractTest','PCB passive pose source bindings '),
 ('PcbPassivePosePolicyContractTest','PCB passive pose policy contracts '),
 ('PcbPassivePoseAdmissionContractTest','PCB passive pose admission contracts '),
 ('PcbPassivePoseTrialSourceContractTest','PCB passive pose trial source contracts '),
 ('PcbPassivePoseRawContractTest','PCB passive pose raw42 contracts '),
 ('PcbPassivePoseRawContractTestGroup2','PCB passive pose raw-1022 contracts '),
 ('PcbPassivePoseRawContractTestGroup3','PCB passive pose raw4 contracts ')]
ROUTES=json.loads((HERE/'route-suites.json').read_text())
assert len(ROUTES)==28
base={r['path']:r['sha256'] for r in json.loads((HERE/'copy-inputs.json').read_text())['files']}
registry=COPY/'scripts/verify-current-contracts.ps1'
assert hashlib.sha256(registry.read_bytes()).hexdigest()==base['scripts/verify-current-contracts.ps1']
for name,marker in UNIT+ROUTES:
    assert marker in (COPY/('tests/contracts/'+name+'.java')).read_text(),name
data=registry.read_bytes()
needle=b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest'; Marker = 'PCB post-selection edge trim ' },"
assert data.count(needle)==1
newline=b'\r\n' if b'\r\n' in data else b'\n'
entries=["        @{ Name = '%s'; Marker = '%s' },"%tuple(row) for row in UNIT+ROUTES]
registry.write_bytes(data.replace(needle,needle+newline+newline.join(s.encode() for s in entries)))
(HERE/'unit-suites.json').write_text(json.dumps(UNIT,indent=2)+'\n')
print('Registered unit8 and routed28 native-only policy suites')
