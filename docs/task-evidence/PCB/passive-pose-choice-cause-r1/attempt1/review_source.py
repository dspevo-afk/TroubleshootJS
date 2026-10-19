"""Root-reviewed hook normalization and unchanged owned search boundaries."""
import hashlib
import json
import re
from pathlib import Path
HERE=Path(__file__).resolve().parent
POLICY=HERE.parent/'passive-pose-policy-r1'
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
planner='src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java'
old=(POLICY/'source'/planner).read_text()
new=(COPY/planner).read_text()
normalization=json.loads((HERE/'hook-normalization.json').read_text())
assert normalization['rootReviewed'] is True
assert normalization['basePlannerSha256']==sha(POLICY/'source'/planner)
assert normalization['observedPlannerSha256']==sha(COPY/planner)
restored=new
for row in normalization['replacements']:
    assert row['reason'] and row['observed']!=row['policy']
    assert restored.count(row['observed'])==1,'Ambiguous hook normalization'
    restored=restored.replace(row['observed'],row['policy'],1)
assert restored==old,'Non-observer planner change after reviewed normalization'
for call in ('fits','mediumPlacementScore','mediumGridOrigin','mediumGridBound'):
    assert len(re.findall(r'\b'+call+r'\s*\(',new))==len(re.findall(r'\b'+call+r'\s*\(',old)),call
observer=(COPY/'src/com/lushprojects/circuitjs1/client/PcbPassiveChoiceObserver.java').read_text()
for forbidden in ('new Random(','.fromPhysicalPackage(','.translated(','.setPlacementConstraints(',
                  '.generateWithPolicyResult(','.mediumPlacementScore(','.mediumGridOrigin('):
    assert forbidden not in observer,'Observer constructs or evaluates new search work'
base={r['path']:r['sha256'] for r in json.loads((HERE/'copy-inputs.json').read_text())['files']}
changed={p for p,h in base.items() if sha(COPY/p)!=h}
assert changed=={planner,'scripts/verify-current-contracts.ps1'}
(HERE/'source-review.json').write_text(json.dumps({'status':'PASS',
 'scope':'Root actual hook/helper/protocol/fixture diff review plus literal text normalization; runtime fidelity remains separate',
 'reviewedHookReplacements':len(normalization['replacements']),
 'plannerOutsideReviewedObservationHooksExact':True,'sameFitScoreClipCallCounts':True,
 'unchangedOtherPolicyInputs':1502,'mainProductionMutated':False,
 'limits':'Static call counts are not a behavioral oracle; four complete native row reproductions and independent capture checks required'},indent=2)+'\n')
print('PASS reviewed hook normalization and unchanged fit/score/grid call sites/other policy owners')
