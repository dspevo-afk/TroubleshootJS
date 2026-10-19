"""Prove observer erasure and all inherited22/source-corpus boundaries."""
import difflib
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'global-pocket-descent-trial-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
COPY = Path(read(HERE/'local-state.json')['scratch'])
overlay = read(HERE/'overlay-audit.json')['files']
generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
for name,digest in overlay.items():
    assert sha(OLD/'source'/name) == digest,name
    if name != generator:
        assert sha(COPY/name) == digest,name
original = (OLD/'source'/generator).read_bytes()
trial = (COPY/generator).read_bytes()
kept = []
active = None
blocks = []
for line in trial.splitlines(keepends=True):
    text = line.strip()
    if text == b'// BEGIN COPY-ONLY successful-route-pool observer.':
        assert active is None,'Nested observer erasure block'
        active = text.replace(b'BEGIN',b'END',1)
        blocks.append(text.decode())
    elif text == b'// END COPY-ONLY successful-route-pool observer.':
        assert active == text,'Unmatched observer erasure block'
        active = None
    elif active is None:
        kept.append(line)
assert active is None and blocks,'Missing observer erasure markers'
assert b''.join(kept) == original,'Observer erasure differs from exact archived22 generator'
registry = 'scripts/verify-current-contracts.ps1'
registry_bytes = (COPY/registry).read_bytes()
names = []
for manifest in ('unit-suites.json','routes-suites.json'):
    for name,marker in read(HERE/manifest):
        row = ("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
        assert registry_bytes.count(row) == 1,name
        registry_bytes = registry_bytes.replace(row,b'')
        names.append(name)
assert len(names) == 3 and registry_bytes == (ROOT/registry).read_bytes()
diff = []
for name in (generator,registry)+tuple('tests/contracts/'+n+'.java' for n in names):
    base = (OLD/'source'/name).read_bytes() if name == generator else (
        (ROOT/name).read_bytes() if name == registry else b'')
    candidate = (COPY/name).read_bytes()
    diff.extend(difflib.unified_diff(base.decode('utf-8').splitlines(keepends=True),
        candidate.decode('utf-8').splitlines(keepends=True),
        fromfile='accepted22/'+name if name == generator else 'accepted/'+name,
        tofile='observer/'+name,n=2))
(HERE/'source-review.diff').write_text(''.join(diff),encoding='utf-8',newline='')
result = {'status':'PASS','observerErasureExact22':True,'observerBlocks':blocks,
    'sixOther22OverridesExact':True,'verifierExactOutsideThreeRegistrations':True,
    'generatorBeforeSha256':hashlib.sha256(original).hexdigest(),
    'generatorAfterSha256':hashlib.sha256(trial).hexdigest(),
    'diffSha256':sha(HERE/'source-review.diff')}
(HERE/'source-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS observer erasure exact22; six other overrides exact22; verifier core exact')
