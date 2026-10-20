"""Reproduce the exact published/accepted boundary and inspect only scoped deltas."""
import difflib,hashlib,json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-slide-compaction-probe-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sys.argv[1:] in ([],['--archive'])
COPY=HERE/'source' if sys.argv[1:] else Path(read(HERE/'local-state.json')['scratch'])
prior=read(OLD/'native-inputs.json')
def erase(raw,begin,end,count):
    kept=[];inside=False;found=0
    for line in raw.splitlines(keepends=True):
        if line.strip()==begin:assert not inside;inside=True;found+=1
        elif line.strip()==end:assert inside;inside=False
        elif not inside:kept.append(line)
    assert not inside and found==count
    return b''.join(kept)
reversal=read(HERE/'adapter-reversal.json')
assert set(reversal)=={'files'}
expected_adapters={'tests/contracts/PcbRoutedPassiveLegCensusContractTest.java','tests/contracts/PcbRoutedSlideCompactionContractTest.java'}
assert set(reversal['files'])==expected_adapters
for name,operations in reversal['files'].items():
    raw=(COPY/name).read_bytes()
    for operation in operations:
        if operation['kind']=='erase':
            raw=erase(raw,operation['begin'].encode(),operation['end'].encode(),operation['count'])
        else:
            assert operation['kind']=='replace'
            old=operation['old'].encode();new=operation['new'].encode()
            assert raw.count(old)==operation['count'];raw=raw.replace(old,new)
    assert raw==(OLD/'source'/name).read_bytes(),name
registry='scripts/verify-current-contracts.ps1'
for n in set(prior['overrides'])|set(prior['additions']):
    if n not in expected_adapters|{registry}:assert (COPY/n).read_bytes()==(OLD/'source'/n).read_bytes(),n
raw=(COPY/registry).read_bytes()
rows=read(HERE/'unit-suites.json')+read(OLD/'routes-suites.json')+read(HERE/'routes-suites.json')
assert len(rows)==6 and len({n for n,m in rows})==6
for name,marker in rows:
    row=("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
    assert raw.count(row)==1;raw=raw.replace(row,b'')
assert raw==(ROOT/registry).read_bytes()
gen='src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
physical='tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
functional='tests/contracts/PcbFunctionalGroupingContractTest.java'
raw=(COPY/gen).read_bytes()
for marker,count in (('selected routed slide observer',2),('successful-route-pool observer',2)):
    raw=erase(raw,('// BEGIN COPY-ONLY '+marker+'.').encode(),('// END COPY-ONLY '+marker+'.').encode(),count)
assert raw==(ROOT/gen).read_bytes()
for name,marker,count in ((physical,'accepted15 routed-leg witness',1),(functional,'selected slide quiet whitespace',2)):
    assert erase((COPY/name).read_bytes(),('// BEGIN COPY-ONLY '+marker+'.').encode(),('// END COPY-ONLY '+marker+'.').encode(),count)==(ROOT/name).read_bytes()
new_names={'tests/contracts/PcbRoutedSlideDescentContractTest.java','tests/contracts/PcbRoutedSlideDescentTrialContractTest.java'}
names=sorted(set(prior['overrides'])|set(prior['additions'])|new_names);assert len(names)==10
diff=[];changed=[]
for name in names:
    before=(OLD/'source'/name).read_bytes() if (OLD/'source'/name).is_file() else b''
    after=(COPY/name).read_bytes()
    if before!=after:changed.append(name)
    diff.extend(difflib.unified_diff(before.decode().splitlines(keepends=True),after.decode().splitlines(keepends=True),
        fromfile='published-probe/'+name,tofile='private-descent/'+name,n=1))
    assert not after.endswith(b'\n\n'),name
    assert all(line.rstrip(b'\r')==line.rstrip(b'\r').rstrip(b' \t') for line in after.split(b'\n')),name
assert set(changed)==expected_adapters|{registry}|new_names
(HERE/'source-review.diff').write_text(''.join(diff),encoding='utf-8',newline='')
(HERE/'source-review.json').write_text(json.dumps({'status':'PASS','acceptedOwnerErasureExact':True,
    'publishedProbeReversalExact':True,'sourceDeltasFromMain':10,'changedFromPublished':changed,
    'registeredCopyRows':6,'newRowsBeyondPublished':2,'diffSha256':sha(HERE/'source-review.diff')},indent=2)+'\n')
print('PASS10source deltas/5changedfrompublished; scoped adapters reverse exactly; main owners exact')
