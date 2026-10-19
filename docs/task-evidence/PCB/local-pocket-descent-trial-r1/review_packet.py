"""Root integrated review after native evaluation and verified owned COPY retirement."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

import assess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
seal = read(HERE/'native-inputs.json')
assert (seal['baseCount'],seal['copyCount']) == (1467,1475)
assert len(seal['overrides']) == 7 and len(seal['additions']) == 8
assert len(seal['gateOwners']) == 28
for name,digest in seal['gateOwners'].items():
    assert sha(HERE/name) == digest,name
for name,digest in dict(seal['overrides'],**seal['additions']).items():
    assert sha(HERE/'source'/name) == digest,name
failure = read(HERE/'attempt1/archive-sha256.json')
assert failure['status'] == 'PASS' and failure['artifactCount'] == 50
assert failure['gateOwners'] == 26 and failure['sourceDeltas'] == 15
assert failure['nativeStatus'] == 'FAIL' and failure['elapsedSeconds'] == 48.4835479
for name,digest in failure['files'].items():
    assert sha(HERE/'attempt1'/name) == digest,name
preserved = read(HERE/'attempt1-preservation.json')
assert preserved['status'] == 'PASS' and preserved['archiveManifestSha256'] == sha(HERE/'attempt1/archive-sha256.json')
prior = read(HERE/'attempt1/native-inputs.json')
old_sources = dict(prior['overrides'],**prior['additions'])
new_sources = dict(seal['overrides'],**seal['additions'])
assert set(old_sources) == set(new_sources)
assert {n for n in old_sources if old_sources[n] != new_sources[n]} == {
    'tests/contracts/PcbLocalPocketDescentContractTest.java'}
main_seal = ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(main_seal) == seal['baseMainSealSha256']
main = read(main_seal)['files']
assert len(main) == 1475
for row in main:
    assert sha(ROOT/row['path']) == row['sha256'],row['path']
for name,digest in read(HERE/'source-audit.json')['files'].items():
    assert sha(ROOT/name) == digest,name
canaries = read(HERE/'assessment-canary-results.json')
assert canaries['status'] == 'PASS' and canaries['caseCount'] == 45
for name,digest in canaries['consumedSha256'].items():
    path = ROOT/name if name.startswith('docs/') else HERE/name
    assert sha(path) == digest,name
transport = read(HERE/'suite-transport-canaries.json')
assert transport['status'] == 'PASS' and transport['caseCount'] == 12
for name,digest in transport['consumedSha256'].items():
    assert sha(HERE/name) == digest,name
comparison = subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'compare_selection.py')],
    capture_output=True,text=True)
if comparison.returncode != 0:
    raise RuntimeError(comparison.stdout+comparison.stderr)
independent = read(HERE/'independent-review.json')
assert independent['status'] == 'NO_BLOCKING_FINDING' and independent['runtimeOutputsRead'] is False
for name,digest in independent['reviewedSha256'].items():
    assert sha(HERE/name) == digest,name
_proposal,protocol,baseline_data,_doc,baseline_rows,_origin = assess._load_contract()
unit = assess._unit_validation(protocol,baseline_data)
routes = assess._routes_assessment(protocol,baseline_data)
assert assess._typed_equal(unit,read(HERE/'unit-validation.json'))
assert assess._typed_equal(routes,read(HERE/'assessment.json'))
prefix = 'PCB_POCKET_DESCENT_CASE '
rows = [json.loads(line[len(prefix):]) for line in (HERE/'routes.txt').read_text().splitlines()
    if line.startswith(prefix)]
assert len(rows) == 28
assert [(r['seed'],r['version']) for r in rows] == [(s,v) for s in protocol['roots'] for v in (15,21)]
metrics = ('area','largestProtectedEmptyRectangleArea','copperManhattanLength','vias')
deltas = []
violations = []
improvement = False
for i,old in enumerate(baseline_rows):
    saved,trial = rows[i*2:i*2+2]
    assert {k:v for k,v in saved.items() if k != 'elapsedMillis'} == {
        k:v for k,v in old.items() if k != 'elapsedMillis'}
    delta = {'seed':old['seed']}
    for metric in metrics:
        assert type(trial[metric]) is int and type(old[metric]) is int
        value = trial[metric]-old[metric]
        delta[metric] = value
        if value > 0:
            violations.append({'seed':old['seed'],'metric':metric,'baseline':old[metric],
                'trial':trial[metric],'delta':value})
    improvement |= delta['area'] < 0 or delta['largestProtectedEmptyRectangleArea'] < 0
    deltas.append(delta)
fitness = 'PASS' if improvement and not violations else 'FAIL'
assert deltas == routes['strictFitness']['deltas']
assert violations == routes['strictFitness']['violations']
assert fitness == routes['strictFitness']['status']
drivers = 0
for folder,phase in ((HERE/'attempt1','unit'),(HERE,'unit'),(HERE,'routes')):
    cleanup = read(folder/(phase+'-process-cleanup.json'))
    launch = read(folder/(phase+'-process-launch.json'))
    assert cleanup['status'] == 'PASS' and cleanup['terminationAttempted'] is False
    assert len(launch['drivers']) == 2 and len(cleanup['drivers']) == 2
    for before,after in zip(launch['drivers'],cleanup['drivers']):
        assert all(before[k] == after[k] for k in ('pid','startTicks','executable'))
        assert after['outcome'] == 'EXITED' and after['sameCreationStillRunning'] is False
        drivers += 1
assert read(HERE/'copy-restoration.json') == {
    'status':'PASS','fromCount':1475,'restoredCount':1467,'main1475Exact':True}
assert read(HERE/'copy-cleanup.json') == {'status':'PASS','verifiedExactFilesRemoved':1467,
    'privateStateRemoved':True,'symlinkOrReparseBoundariesCrossed':False,'sharedResourcesTouched':False}
assert not (HERE/'local-state.json').exists()
result = {'status':'PASS','scope':'Integrated native/fitness/source/cleanup review; no production promotion',
    'baseCommit':protocol['baseCommit'],'main1475Exact':True,'frozenGates':28,'sourceDeltas':15,
    'failedAttempt1Artifacts':50,'attempt1CorrectionFixtureOnly':True,
    'selectedAndGlobalScoreComparisonsRecomputed':True,
    'independentSourceReview':'NO_BLOCKING_FINDING; coverage/responsiveness limits recorded',
    'syntheticReaderCases':45,'actualTransportCases':12,'unitSuites':3,'routeSuites':7,
    'cases':28,'saved15ExactRows':14,'trial21AdmittedRows':14,'strictFitness':fitness,
    'independentMetricDeltas':deltas,'violations':violations,'recordedDriverBirthsExited':drivers,
    'copyRestored1467AndRetired':True,'productionBuildPlayerSolverAllSeed':'NOT_RUN'}
(HERE/'root-review.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS integrated local-pocket28gates/15deltas/50failedartifacts/native28cases; strict fitness '+fitness+'; exactmain1475/ownedcleanup')
