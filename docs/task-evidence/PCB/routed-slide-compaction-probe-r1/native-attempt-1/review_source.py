"""Erase only selected-slide adapters against pinned prior and accepted owners."""
import difflib,hashlib,json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-passive-feasibility-census-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sys.argv[1:] in ([],['--archive'])
COPY=HERE/'source' if sys.argv[1:] else Path(read(HERE/'local-state.json')['scratch'])
def erase(raw,begin,end,count):
    kept=[];inside=False;found=0
    for line in raw.splitlines(keepends=True):
        if line.strip()==begin:
            assert not inside;inside=True;found+=1
        elif line.strip()==end:
            assert inside;inside=False
        elif not inside:kept.append(line)
    assert not inside and found==count
    return b''.join(kept)
gen='src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
physical='tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
functional='tests/contracts/PcbFunctionalGroupingContractTest.java'
leg='tests/contracts/PcbRoutedPassiveLegCensusContractTest.java'
proof='tests/contracts/PcbRoutedPassiveFeasibilityCensusContractTest.java'
registry='scripts/verify-current-contracts.ps1'
for name,marker,count in ((gen,'selected routed slide observer',2),
    (leg,'selected routed slide census adapter',1),(proof,'selected routed slide proof adapter',1)):
    raw=erase((COPY/name).read_bytes(),('// BEGIN COPY-ONLY '+marker+'.').encode(),
        ('// END COPY-ONLY '+marker+'.').encode(),count)
    assert raw==(OLD/'source'/name).read_bytes(),name
assert (COPY/physical).read_bytes()==(OLD/'source'/physical).read_bytes()
assert erase((OLD/'source'/gen).read_bytes(),
    b'// BEGIN COPY-ONLY successful-route-pool observer.',
    b'// END COPY-ONLY successful-route-pool observer.',2)==(ROOT/gen).read_bytes()
assert erase((COPY/physical).read_bytes(),b'// BEGIN COPY-ONLY accepted15 routed-leg witness.',
    b'// END COPY-ONLY accepted15 routed-leg witness.',1)==(ROOT/physical).read_bytes()
assert erase((COPY/functional).read_bytes(),b'// BEGIN COPY-ONLY selected slide quiet whitespace.',
    b'// END COPY-ONLY selected slide quiet whitespace.',2)==(ROOT/functional).read_bytes()
raw=(COPY/registry).read_bytes();names=[]
for manifest in ('unit-suites.json','routes-suites.json'):
    for name,marker in read(HERE/manifest):
        row=("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
        assert raw.count(row)==1,name;raw=raw.replace(row,b'');names.append(name)
assert len(names)==4 and raw==(ROOT/registry).read_bytes()
src=(COPY/gen).read_text()
assert src.index('trimSelectedMediumBorder(session);') < src.index('session.bestLayout.setGenerationStatistics(') < src.index('if(selectedSlideObserver!=null)') < src.index('session.mediumPolicyResult = new MediumBoardPhysicalPolicy.Result(')
diff=[]
for name in (gen,physical,functional,registry)+tuple('tests/contracts/'+n+'.java' for n in names):
    before=(OLD/'source'/name).read_bytes() if name in (leg,proof) else (ROOT/name).read_bytes() if (ROOT/name).is_file() else b''
    after=(COPY/name).read_bytes()
    diff.extend(difflib.unified_diff(before.decode('utf-8').splitlines(keepends=True),
        after.decode('utf-8').splitlines(keepends=True),fromfile=('published-prior/' if name in (leg,proof) else 'accepted-main/')+name,
        tofile='private-probe/'+name,n=1))
(HERE/'source-review.diff').write_text(''.join(diff),encoding='utf-8',newline='')
(HERE/'source-review.json').write_text(json.dumps({'status':'PASS','acceptedOwnerErasureExact':True,
    'priorLegAndPhysicalErasureExact':True,'sourceDeltas':8,'registeredNewSuites':names,
    'selectedHookAfterTrimAndGenerationStatistics':True,'diffSha256':sha(HERE/'source-review.diff')},indent=2)+'\n')
print('PASS8source deltas, accepted/prior owner erasure, selected hook after trim+statistics')
