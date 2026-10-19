"""Root structural audit of scope, reused body, catalog and sealed literal fixtures."""
import hashlib
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
FOUNDATION=HERE.parent/'passive-pose-foundation-r1'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
foundation=json.loads((FOUNDATION/'native-inputs.json').read_text())
sealed=dict(foundation['overrides'],**foundation['additions'])
for relative in ('src/com/lushprojects/circuitjs1/client/PhysicalPackages.java',
 'tests/contracts/PcbPassivePoseFoundationContractTest.java','tests/contracts/PcbPassivePoseSourceContractTest.java'):
    assert sha(COPY/relative)==sealed[relative],'Reused prerequisite changed'
adapter='tests/contracts/PcbPostSelectionEdgeTrimContractTest.java'
old=(FOUNDATION/'source'/adapter).read_text()
new=(COPY/adapter).read_text()
# Normalize solely the new bounded DTO/adapter, extraction and observed identity field.
start=old.index('    private static Observation routeAndObserve(')
old_tail=old[start:]
new_tail=new[new.index('    private static Observation routeAndObserve('):]
extract_start=new_tail.index('        return routeAndObserveCore(seed, version, plan, board, sourceIntentSha,')
extract_end=new_tail.index('        check(board.getPlacementConstraints().requiresTwoLayerRouting,',extract_start)
normalized=new_tail[:extract_start]+new_tail[extract_end:]
normalized=normalized.replace('area, copperLength, vias, json, admission.identity());','area, copperLength, vias, json);')
normalized=normalized.replace('final String json, admissionIdentity;','final String json;')
normalized=normalized.replace('int vias, String json,\n                String admissionIdentity) {','int vias, String json) {')
normalized=normalized.replace('            this.admissionIdentity = admissionIdentity;\n','')
assert normalized==old_tail,'Physical oracle body or helpers changed'
before='    /** Bounded package adapter for current15 routed-cause observations. */'
assert old[:old.index(before)]==new[:new.index('    static String savedIntentShaForCurrent15(')]
planner_old=(ROOT/'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java').read_text()
planner=(COPY/'src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java').read_text()
for literal in ('MEDIUM_MAX_EVALUATIONS=60000','MAX_AREA=64000000L'):
    assert literal.replace(' ','') in planner.replace(' ','') and literal.replace(' ','') in planner_old.replace(' ','')
for name in ('PcbRoutingWork.java','MediumBoardPhysicalPolicy.java','PcbPlacementCompactor.java',
 'SeededPcbLayoutGenerator.java','PcbPhysicalTransform.java','PcbFootprint.java'):
    relative='src/com/lushprojects/circuitjs1/client/'+name
    assert sha(COPY/relative)==sha(ROOT/relative),'Unscoped physics/routing/finish owner edit'
old_admission=(ROOT/'src/com/lushprojects/circuitjs1/client/MediumBoardNormalAdmission.java').read_text()
new_admission=(COPY/'src/com/lushprojects/circuitjs1/client/MediumBoardNormalAdmission.java').read_text()
field_start='            field(value, "rb56-physical-contract", '
field_end='manufacturing-rating=NONE");'
def physical_field(text):
    start=text.index(field_start)
    return text[start:text.index(field_end,start)+len(field_end)]
assert physical_field(old_admission)==physical_field(new_admission)
base={r['path']:r['sha256'] for r in json.loads((HERE/'copy-inputs.json').read_text())['files']}
changed=[p for p,h in base.items() if sha(COPY/p)!=h]
expected={'PhysicalPackages.java','Rb56Placement.java','PcbPlacementConstraints.java','PcbPlacementPlanner.java',
 'MediumBoardNormalAdmission.java','PcbTwoLayerRules.java','Rb56Generator.java','Rb56PlayerFamilyCapability.java',
 'GenerationDependencyContext.java','GenerationRequest.java','PcbPostSelectionEdgeTrimContractTest.java','verify-current-contracts.ps1'}
assert {Path(p).name for p in changed}==expected and len(changed)==12
(HERE/'source-review.json').write_text(json.dumps({'status':'PASS',
 'authoritativePhysicalOracleBodyAndHelpersExactAfterAdapterNormalization':True,
 'reusedMechanicalAndOldSourceBindingsExact':True,'oldRb56PhysicalContractFieldExact':True,
 'unchangedRoutingPhysicsFinishingOwners':True,'changedBaseOwners':sorted(changed),
 'rootReview':'Actual helper/planner/constraints/admission/consumers/tests diff reviewed; conservative real-envelope sizing; one template per existing visit; canonical package identity and separate pose/admission namespaces',
 'deferred':'DifficultyAssessment consumer, turned-owner lifecycle/request replay and used-pose visuals required only after strict14 fitness; no promotion from native prerequisite'},indent=2)+'\n')
print('PASS scoped12 owners, exact physical core/catalog prerequisite and unchanged bounded owners')
