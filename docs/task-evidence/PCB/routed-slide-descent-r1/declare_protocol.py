"""Declare bounded generic descent before any native outcome is observed."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-slide-compaction-probe-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
prior=read(OLD/'protocol.json')
assert sha(OLD/'protocol.json')=='88123f76e526d42dce97bb656eda8ffdf4479ca947052cf819738e8e79aa93cb'
paths=['docs/task-evidence/PCB/routed-slide-compaction-probe-r1/'+n for n in
    ('protocol.json','native-inputs.json','root-review.json','routes.txt','assessment.json','assess.py','compaction-analysis.json')]
protocol={
 'schema':1,'baseCommit':'80303258be31da72e238455a05068c2015ee84d4',
 'scope':'Bounded generic repeated private slide/compaction for both informed roots; no production change',
 'roots':['5','2'],'version':15,'density':14,'phase':'SELECTED_AFTER_EDGE_TRIM',
 'summaryPrefix':'PCB_ROUTED_SLIDE_DESCENT_CASE ',
 'reportOrder':['PCB_WHITESPACE_CASE ','PCB_ROUTED_LEG_CASE ','PCB_ROUTED_SLIDE_DESCENT_CASE '],
 'summaryFields':['seed','label','version','phase','status','selectedAttempt','selectedRoute','newRouteRequests','selectedMutation',
    'limits','source','sourceMetrics','initialControl','steps','finalMetrics','aggregate','elapsedMillis'],
 'limits':{'maxAcceptedSteps':4,'maxEvaluatedMoves':128},
 'structuralLimits':{'maxParts':128,'maxDirections':512,'directions':[[-10,0],[10,0],[0,-10],[0,10]]},
 'statuses':['STEP_CAP','EVALUATION_CAP','NO_STRUCTURAL_MOVES','NO_STRICT_IMPROVEMENT'],
 'stepFields':['index','source','sourceMetrics','control','structural','rows','acceptedRowIndex'],
 'structuralFields':['geometryHashBefore','geometryHashAfter','parts'],
 'sourceFields':prior['sourceFields'],'metricsFields':prior['metricsFields'],
 'controlFields':prior['controlFields'],'rowFields':prior['rowFields'],
 'compactionFields':prior['compactionFields'],'proofFields':prior['proofFields'],
 'feasibilityProofFields':prior['feasibilityProofFields'],
 'sourceGenerationMetadata':prior['sourceGenerationMetadata'],'privateGenerationMetadata':prior['privateGenerationMetadata'],
 'aggregateFields':['steps','acceptedSteps','evaluatedMoves','validatedMoves','rejectedMoves','compactions'],
 'improvementRule':'Area strictly below all four current-source/current-control/initial-source/initial-control; copper/vias/protected-void no greater than all four',
 'selectionRule':'First strict result in canonical nonincreasing ten-unit move order; each attempt independently from same current source; restart census after accepted private update',
 'terminationRule':'Before each step check accepted4 then evaluated128; an accepted step reaching4 wins STEP_CAP; otherwise evaluated128 wins EVALUATION_CAP; zero derived moves NO_STRUCTURAL_MOVES; exhausted nonempty cohort without acceptance NO_STRICT_IMPROVEMENT',
 'chainRule':'First source is original accepted selected source; subsequent source geometry/metrics equals preceding accepted compaction, generation explicitly private; final equals last accepted or original if none; every original/current source immutable',
 'proofRule':prior['proofRule'],
 'structuralRule':'Complete generic current-source census using old analyzeTarget and appendPart; derive all eligible nonpositive directions and unique affected trace indices independently; no fixed31 after first source',
 'selectedBaselines':prior['selectedBaselines'],
 'priorContract':{'path':'docs/task-evidence/PCB/routed-slide-compaction-probe-r1/assess.py','sha256':sha(OLD/'assess.py'),
    'protocolPath':'docs/task-evidence/PCB/routed-slide-compaction-probe-r1/protocol.json','protocolSha256':sha(OLD/'protocol.json')},
 'unitFixtureCounts':dict(prior['unitFixtureCounts'],PcbRoutedSlideDescentContractTest=6),
 'unitEvaluators':dict(prior['unitEvaluators'],PcbRoutedSlideDescentContractTest='selectedRoutedSlideDescent'),
 'unitFixtureNames':['canonicalFirstImprovementAndTransitions','strictDescentAndInitialGuards','exhaustionAndEmptyCohort',
    'acceptedStepAndEvaluationCaps','failureAndCancellationPropagation','selectedObserverOwnershipAndAdapterIsolation'],
 'routePassDetail':'roots=2 rows=2 evaluator=selectedRoutedSlideDescent',
 'timingScope':'Native selected routed slide descent; unchanged per-JVM/search/physical limits',
 'maxPhaseElapsedSeconds':36000,'maxCaseMillis':60000,'maxFileBytes':67108864,
 'publishedInputs':dict(prior['publishedInputs'],**{p:sha(ROOT/p) for p in paths}),
 'classificationOwnerSha256':prior['classificationOwnerSha256'],
 'suiteManifestSha256':{n:sha(HERE/n) for n in ('unit-suites.json','routes-suites.json')},
 'limitsDisclosure':['Two informed roots only; bounded private search, no global optimum or general performance/manufacturing claim',
    'No extra route request, actual selected mutation, policy/version or production change',
    'Rows are canonical attempted prefixes; unattempted census moves remain explicitly unevaluated',
    'No inference of compactor per-axis rejection cause; unchanged common label and whitespace owners',
    'No GWT/player/solver/repair/full matrix/unseen claim']}
assert not (HERE/'protocol.json').exists()
(HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
print('Declared bounded descent protocol',sha(HERE/'protocol.json'))
