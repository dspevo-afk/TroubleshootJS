"""Declare selected-frame matched compaction before any native outcome."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-passive-feasibility-census-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
prior=read(OLD/'protocol.json')
assert sha(OLD/'protocol.json')=='6982e4e88969f599c3c859ed8bb9fc2e67946f24c9f5b0453c7e705231102d30'
assert sha(OLD/'native-inputs.json')=='44243c64f1d494760d89c00598895858e17c409b30abc0ad898e01e49fc3d0bf'
paths=['docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json']
paths+=['docs/task-evidence/PCB/routed-passive-feasibility-census-r1/'+n for n in
    ('protocol.json','native-inputs.json','root-review.json','routes.txt','assessment.json','feasibility-analysis.json','assess.py')]
published={p:sha(ROOT/p) for p in paths}
baseline=read(ROOT/paths[0]);assert len(baseline['rows'])==14
selected={r['seed']:r for r in baseline['rows'] if r['seed'] in ('5','2')}
assert set(selected)=={'5','2'}
protocol={
 'schema':1,'baseCommit':'39edb4823ec9658b7f60f63c9b5fc7eb71de5db6',
 'scope':'All31 independent selected-frame seed2 slides with matched unchanged compactor; seed5 control only; no selection or production change',
 'roots':['5','2'],'version':15,'density':14,'phase':'SELECTED_AFTER_EDGE_TRIM',
 'summaryPrefix':'PCB_ROUTED_SLIDE_COMPACTION_CASE ',
 'reportOrder':['PCB_WHITESPACE_CASE ','PCB_ROUTED_LEG_CASE ','PCB_ROUTED_SLIDE_COMPACTION_CASE '],
 'summaryFields':['seed','label','version','phase','status','selectedAttempt','selectedRoute',
    'newRouteRequests','selectedMutation','source','sourceMetrics','control','rows','aggregate','elapsedMillis'],
 'summaryStatuses':{'5':'CONTROL_ONLY','2':'PROBE_COMPLETE'},
 'sourceFields':prior['sourceFields'],
 'metricsFields':['area','copperLength','vias','largestProtectedEmptyRectangleArea','geometrySha256',
    'stableIdentitySha256','labelIdentitySha256','generationMetadata'],
 'sourceGenerationMetadata':'ACCEPTED_SELECTED',
 'privateGenerationMetadata':'UNASSIGNED_PRIVATE_CANDIDATE',
 'controlFields':['compacted','metrics','proof','sourceImmutable'],
 'rowFields':['feasibility','compaction'],
 'compactionFields':['compacted','metrics','proof','sourceImmutable','strictImprovement'],
 'aggregateFields':['structuralDirections','validatedMoves','rejectedMoves','compactions','areaReductions','strictImprovements'],
 'proofFields':['placementValidated','geometryValidated','routingValidated','routeQualityValidated',
    'traceClearanceValidated','twoLayerValidated','padPairCount','sourceReachabilityHash','candidateReachabilityHash',
    'sourceRouteSourceCount','candidateRouteSourceCount','sourceTraceProvenanceHash','candidateTraceProvenanceHash',
    'stableIdentityVerified','labelIdentityVerified','conductorReachabilityVerified','traceProvenanceVerified','sourceImmutable'],
 'feasibilityProofFields':prior['proofFields'],'expectedMoves':prior['expectedMoves'],
 'selectedBaselines':selected,
 'physicalRowContract':{'readerPath':'docs/task-evidence/PCB/routed-passive-feasibility-census-r1/assess.py',
    'readerSha256':sha(OLD/'assess.py'),'protocolPath':'docs/task-evidence/PCB/routed-passive-feasibility-census-r1/protocol.json',
    'protocolSha256':sha(OLD/'protocol.json'),'adapter':'Use pinned _validate_row with selected source facts and published original structural source summary; do not apply preselection summary/source/first-message checks'},
 'improvementRule':'Area strictly below BOTH original source and no-slide compacted control; copper/vias/largest protected empty rectangle no greater than either',
 'areaReductionRule':'Area strictly below both source and control, irrespective of other metrics',
 'compactedRule':'Unchanged compactRouted returned a different layout owner before private normalization; no per-axis causal claim',
 'sourceRule':'All31 moves and control independently start from identical selected source; moves rederived canonically and compared to published cohort',
 'labelRule':'Every legal moved candidate validates with rigid original labels before compaction; control and compaction inputs are fresh unlabeled private copies, output always fresh before same generator silkscreen owner; semantic inventory and styles/targets identical',
 'proofRule':'All physical validators including explicit2layer/traceclearance, source-net independent pad pairs, complete stable route provenance and identity on source/control/every compacted result; unknown/compactor/proof/cancel/budget errors propagate',
 'quietWhitespaceRule':'Original full GRID10 sampler; wrapper suppresses only its stdout with finally-restored flag; published selected whitespace reports remain unchanged',
 'unitFixtureCounts':dict(prior['unitFixtureCounts'],PcbRoutedSlideCompactionContractTest=5),
 'unitEvaluators':dict(prior['unitEvaluators'],PcbRoutedSlideCompactionContractTest='selectedRoutedSlideCompaction'),
 'unitFixtureNames':['selectedPlanFrameAndMetadata','freshPrivateLayoutAndStableIdentity','strictImprovementComparator','selectedObserverOwnershipAndFailureCleanup','quietWhitespaceOwnerCleanup'],
 'routePassDetail':'roots=2 rows=2 evaluator=selectedRoutedSlideCompaction',
 'timingScope':'Native selected routed slide compaction probe; unchanged per-JVM/search/physical limits',
 'maxPhaseElapsedSeconds':36000,'maxCaseMillis':60000,'maxFileBytes':67108864,
 'publishedInputs':published,'classificationOwnerSha256':prior['classificationOwnerSha256'],
 'suiteManifestSha256':{n:sha(HERE/n) for n in ('unit-suites.json','routes-suites.json')},
 'limits':['Informed selected two-root private diagnostic only; no global optimum, policy or board shrinkage acceptance',
    'No extra route, selected mutation, chaining or winner selection; source outline retained for each slide before compaction',
    'All31 reported; full post-compaction proof only for physically admitted moves; rejected rows have null compaction',
    'No per-axis compactor cause inferred from no cut; labels regenerated equally through existing owner',
    'No GWT/player/solver/repair/full matrix/unseen/manufacturing/general timing claim']}
assert not (HERE/'protocol.json').exists()
(HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
print('Declared selected-frame diagnostic protocol',sha(HERE/'protocol.json'))
