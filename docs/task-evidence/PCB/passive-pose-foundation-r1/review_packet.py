"""Final publication audit after verified source-copy retirement."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert not (HERE / 'local-state.json').exists()
seal = json.loads((HERE / 'native-inputs.json').read_text())
for name, digest in seal['gateOwners'].items():
    assert sha(HERE / name) == digest, 'Final gate changed: ' + name
for name, digest in dict(seal['overrides'], **seal['additions']).items():
    assert sha(HERE / 'source' / name) == digest, 'Final source archive changed'
attempt = HERE / 'attempt1'
old_archive = json.loads((attempt / 'archive-sha256.json').read_text())
for name, digest in old_archive['files'].items():
    assert sha(attempt / name) == digest, 'Failed attempt changed: ' + name
old_seal = json.loads((attempt / 'native-inputs.json').read_text())
for name in seal['overrides']:
    assert old_seal['overrides'][name] == seal['overrides'][name]
changed_tests = [name for name in seal['additions'] if old_seal['additions'][name] != seal['additions'][name]]
assert changed_tests == ['tests/contracts/PcbPassivePoseFoundationContractTest.java']
main = HERE.parent / 'silkscreen-scale-r1/final-inputs.json'
assert sha(main) == seal['main1475SealSha256']
rows = json.loads(main.read_text())['files']
assert len(rows) == 1475
for row in rows:
    assert sha(ROOT / row['path']) == row['sha256'], 'Accepted main input changed'
for name in ('copy-restoration.json', 'copy-cleanup.json', 'reproduction.json', 'native-input-audit.json'):
    assert json.loads((HERE / name).read_text())['status'] == 'PASS'
cleanup = (HERE / 'retire_copy.py').read_text()
assert "COPY.name.startswith('tsj-passive-pose-')" in cleanup
# Preserve an inherited receipt wording error instead of rewriting consumed evidence.
assert json.loads((HERE / 'copy-cleanup.json').read_text())['ownership'] == 'Unique tsj-routed-strip OS-temp copy'
result = {'status': 'PASS', 'main1475Exact': True, 'finalNativeInputs': seal['copyCount'],
    'finalGateOwnersExact': len(seal['gateOwners']), 'failedAttemptFilesExact': len(old_archive['files']),
    'productionPatchBetweenAttemptsExact': True, 'onlyChangedFixture': changed_tests[0],
    'integratedRootReview': 'Four declarations/narrow helpers; seven literal local geometries; independent affine/corner expectations and12 turned spots; real typed stock/registry/binding; exact original routing body; explicit source and cache boundaries',
    'optionalLeafReview': 'Bounded implementation and separate read-only future planner/admission review; root owns runtime and acceptance',
    'cleanupReceiptLabelErratum': 'Inherited ownership wording says routed-strip; actual frozen guard checks unique tsj-passive-pose OS-temp prefix. Restoration/containment/reparse/retirement checks PASS; raw receipt retained unchanged',
    'limits': 'No enabled pose policy, main production capability, packing improvement, full matrix, GWT/player, whole-cache or solver-mutation proof'}
(HERE / 'root-review.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS final/failed archives and frozen gates exact; accepted main1475 exact; cleanup label erratum explicit')
