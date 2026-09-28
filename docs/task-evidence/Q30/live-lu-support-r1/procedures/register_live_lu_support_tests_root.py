from pathlib import Path
s=Path(__file__).resolve().parent
p=s/'live-lu-support-r1-preparation/candidate/scripts/verify-current-contracts.ps1'
b=p.read_bytes();nl=b'\r\n' if b'\r\n' in b else b'\n'
needle=b"        @{ Name = 'Q30LuPivotCollectionContractTest'; Marker = 'Q30 LU pivot collection contracts ' },"
assert b.count(needle)==1 and b'LuStructuralSupportContractTest' not in b
lines=[b"        @{ Name = 'LuStructuralSupportContractTest'; Marker = 'LU structural support contracts assertions=' },",
       b"        @{ Name = 'LuStructuralFactorizationContractTest'; Marker = 'LU structural factorization contracts assertions=' },"]
p.write_bytes(b.replace(needle,needle+nl+nl.join(lines)))
print('Registered two new focused suites; not run.')
