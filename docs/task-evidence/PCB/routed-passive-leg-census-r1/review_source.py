"""Reconstruct accepted owners by erasing only read-only census adapters."""
import difflib
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
COPY = Path(read(HERE/'local-state.json')['scratch'])
generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
helper = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
registry = 'scripts/verify-current-contracts.ps1'

def strip(raw, begin, end, count):
    result = []
    inside = False
    found = 0
    for line in raw.splitlines(keepends=True):
        text = line.strip()
        if text == begin:
            assert not inside
            inside = True
            found += 1
        elif text == end:
            assert inside
            inside = False
        elif not inside:
            result.append(line)
    assert not inside and found == count
    return b''.join(result)

assert strip((COPY/generator).read_bytes(),
    b'// BEGIN COPY-ONLY successful-route-pool observer.',
    b'// END COPY-ONLY successful-route-pool observer.',2) == (ROOT/generator).read_bytes()
assert strip((COPY/helper).read_bytes(),
    b'// BEGIN COPY-ONLY accepted15 routed-leg witness.',
    b'// END COPY-ONLY accepted15 routed-leg witness.',1) == (ROOT/helper).read_bytes()
raw = (COPY/registry).read_bytes()
names = []
for manifest in ('unit-suites.json','routes-suites.json'):
    for name,marker in read(HERE/manifest):
        row = ("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
        assert raw.count(row) == 1,name
        raw = raw.replace(row,b'')
        names.append(name)
assert len(names) == 2 and raw == (ROOT/registry).read_bytes()
diff = []
for name in (generator,helper,registry)+tuple('tests/contracts/'+n+'.java' for n in names):
    before = (ROOT/name).read_bytes() if name in (generator,helper,registry) else b''
    after = (COPY/name).read_bytes()
    # Zero-context diff avoids introducing whitespace-only context prefix lines.
    diff.extend(difflib.unified_diff(before.decode('utf-8').splitlines(keepends=True),
        after.decode('utf-8').splitlines(keepends=True),fromfile='accepted/'+name,
        tofile='census/'+name,n=0))
(HERE/'source-review.diff').write_text(''.join(diff),encoding='utf-8',newline='')
result = {'status':'PASS','acceptedGeneratorErasureExact':True,
    'acceptedPhysicalWitnessErasureExact':True,'verifierCoreExact':True,
    'registeredNewSuites':names,'diffSha256':sha(HERE/'source-review.diff')}
(HERE/'source-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS accepted generator/physical witness/verifier erasure; no rejected overlay')
