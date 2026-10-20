"""Recheck the retired finite census, leaving physical counts to actual evidence."""
import hashlib
import json
from pathlib import Path

import analyze
import assess
import check_assessment

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
PROOF = HERE.parent/'routed-passive-move-proof-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount']) == (1467,1470)
assert len(seal['overrides']) == len(seal['additions']) == 3
assert len(seal['gateOwners']) == 29
for group, folder in (('gateOwners',HERE),('overrides',HERE/'source'),
    ('additions',HERE/'source'),('preparationFailureArtifacts',HERE),('protocolRevisionArtifacts',HERE),
    ('preFreezeReviewArtifacts',HERE),('nativeFailureArtifacts',HERE)):
    for name,digest in seal[group].items():
        assert sha(folder/name) == digest,name
main_seal = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == seal['baseMainSealSha256']
main = read(main_seal)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'],row['path']
for folder, key in ((OLD,'publishedCensusSealSha256'),(PROOF,'publishedProofSealSha256')):
    assert sha(folder/'native-inputs.json') == seal[key]
    prior = read(folder/'native-inputs.json')
    for group, subdir in (('gateOwners',folder),('overrides',folder/'source'),('additions',folder/'source')):
        for name,digest in prior[group].items():
            assert sha(subdir/name) == digest,name
    for group in ('preparationFailureArtifacts','nativeFailureArtifacts','nativeRuntimeFailureArtifacts'):
        for name,digest in prior.get(group,{}).items():
            assert sha(folder/name) == digest,name

def stripped(raw,begin,end,count):
    kept = []
    inside = False
    found = 0
    for line in raw.splitlines(keepends=True):
        if line.strip() == begin:
            assert not inside
            inside = True
            found += 1
        elif line.strip() == end:
            assert inside
            inside = False
        elif not inside:
            kept.append(line)
    assert not inside and found == count
    return b''.join(kept)

generator = 'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
helper = 'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
census = 'tests/contracts/PcbRoutedPassiveLegCensusContractTest.java'
for path,begin,end,count in (
    (generator,b'// BEGIN COPY-ONLY successful-route-pool observer.',
     b'// END COPY-ONLY successful-route-pool observer.',2),
    (helper,b'// BEGIN COPY-ONLY accepted15 routed-leg witness.',
     b'// END COPY-ONLY accepted15 routed-leg witness.',1)):
    raw = (HERE/'source'/path).read_bytes()
    assert raw == (OLD/'source'/path).read_bytes()
    assert stripped(raw,begin,end,count) == (ROOT/path).read_bytes()
assert stripped((HERE/'source'/census).read_bytes(),
    b'// BEGIN COPY-ONLY private feasibility census bridge.',
    b'// END COPY-ONLY private feasibility census bridge.',2) == (OLD/'source'/census).read_bytes()
registry = 'scripts/verify-current-contracts.ps1'
raw = (HERE/'source'/registry).read_bytes()
for manifest in ('unit-suites.json','routes-suites.json'):
    for name,marker in read(HERE/manifest):
        line = ("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode()
        assert raw.count(line) == 1
        raw = raw.replace(line,b'')
assert raw == (ROOT/registry).read_bytes()
contract = assess._load_contract()
prior_contract = assess._load_prior_contract(contract)
unit_summary,unit_inputs,unit_manifest_sha = assess._read_phase_files('unit',contract,prior_contract)
unit = assess._unit_validation(contract,unit_summary,unit_inputs,unit_manifest_sha)
assert assess._typed_equal(unit,read(HERE/'unit-validation.json'))
unit_sha = assess._validate_unit_prerequisite((HERE/'unit-validation.json').read_bytes(),unit)
route_summary,route_inputs,route_manifest_sha = assess._read_phase_files('routes',contract,prior_contract)
assessment = assess._routes_assessment(contract,route_summary,route_inputs,route_manifest_sha,unit_sha)
assert assess._typed_equal(assessment,read(HERE/'assessment.json'))
analysis = analyze.result()
assert analysis == read(HERE/'feasibility-analysis.json')
assert analysis['constructionCount'] == 31
canaries = read(HERE/'assessment-canary-results.json')
assert check_assessment._run_canaries() == canaries
ack = read(HERE/'review-ack.json')
assert ack['rootReviewed'] is True and ack['diffSha256'] == sha(HERE/'source-review.diff')
assert canaries['canaryCount'] == ack['syntheticReaderCases'] >= 40
assert canaries['fixtureCounts'] == contract['protocol']['unitFixtureCounts']
assert read(HERE/'suite-transport-canaries.json')['caseCount'] == 12
drivers = 0
for folder,phase in ((HERE/'native-attempt-1','unit'),(HERE,'unit'),(HERE,'routes')):
    cleanup = read(folder/(phase+'-process-cleanup.json'))
    launch = read(folder/(phase+'-process-launch.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers']) == len(cleanup['drivers']) == 2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
failure_dir = HERE/'native-attempt-1'
failure = read(failure_dir/'failure.json')
assert failure['status'] == 'FAIL' and failure['nativeExitCode'] == 2
assert failure['qualifiedUnitReceiptPresent'] is False
assert len(failure['files']) == 42
for name,digest in failure['files'].items():
    assert sha(failure_dir/name) == digest,name
try:
    assess.check_phase((failure_dir/'unit.txt').read_text(encoding='utf-8-sig'),
        '',read(failure_dir/'unit-timing.json'),read(failure_dir/'unit-suites.json'),'unit')
except assess.ReaderError:
    pass
else:
    raise AssertionError('Reader accepted the preserved failed unit phase')
assert drivers == 6
assert read(HERE/'copy-restoration.json') == {
    'status':'PASS','fromCount':1470,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json') == {'status':'PASS','verifiedExactFilesRemoved':1467,
    'privateStateRemoved':True,'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'integrityReviewStatus':'PASS','nativeCensusStatus':'PASS',
    'scope':'31 independent private alternatives; no selected or production change',
    'baseCommit':contract['protocol']['baseCommit'],'main1475Exact':True,
    'publishedCensusAndProofInputsExact':True,'classificationOwnersExact':7,
    'frozenGates':29,'sourceDeltas':6,'acceptedAndPriorOwnerErasureExact':True,
    'syntheticReaderCases':canaries['canaryCount'],'actualTransportCases':12,
    'unitSuites':2,'unitFixtureCounts':canaries['fixtureCounts'],'routeSuites':1,
    'selectedRowsExact42Fields':2,'independentConstructions':31,
    'physicallyValidated':analysis['physicallyValidated'],'physicallyRejected':analysis['physicallyRejected'],
    'sourcePadPairCount':9045,'padPairCountPerValidatedCandidate':9045,
    'reachabilityOracleSha256':'9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7',
    'preservedNativeUnitFailures':1,'nativeFailureReaderRejections':1,
    'recordedDriverBirthsExited':drivers,'copyRestored1467AndRetired':True,
    'boardShrinkage':'NOT_DEMONSTRATED','productionPolicyFitnessChange':'NONE',
    'GwtPlayerSolverFullMatrixAllSeed':'NOT_RUN'}
target = HERE/'root-review.json'
if target.exists():
    assert read(target) == result,'Prior root review differs; preserve it'
else:
    target.write_text(json.dumps(result,indent=2)+'\n')
print('PASS integrated29gates/6deltas/31independent outcomes; unchanged main1475/owned cleanup')
