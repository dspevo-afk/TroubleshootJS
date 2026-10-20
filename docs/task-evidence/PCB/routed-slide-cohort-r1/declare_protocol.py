"""Declare informed14 qualification with the published search/caps unchanged."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3];OLD=HERE.parent/'routed-slide-descent-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(OLD/'protocol.json')=='e047c6b87e72538b7b161449b7938b80c8e1ec9c081bea27ba40c26d3c4b73ba'
assert sha(OLD/'native-inputs.json')=='8c1c0a121455a4e02d85253c3df03c456d27dc1ae8044673ee29c764d0928ad5'
prior=read(OLD/'protocol.json');roots=['-1017','-1004','-1022','0','17','42','1','7','5','14','2','3','4','6']
baseline_path='docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json'
raw_path='docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt'
assert sha(ROOT/baseline_path)=='12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
assert sha(ROOT/raw_path)=='db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d'
rows=read(ROOT/baseline_path)['rows'];assert [r['seed'] for r in rows]==roots
assert all('selected=P07_FULLER_TWO_LAYER;' in r['statisticsCanonical'] for r in rows)
paths=[baseline_path,raw_path]+['docs/task-evidence/PCB/routed-slide-descent-r1/'+n for n in
    ('protocol.json','native-inputs.json','root-review.json','routes.txt','assessment.json','assess.py','descent-analysis.json')]
protocol={
 'schema':1,'baseCommit':'1202f18e48bcd3401aa117c3d5fd3b25bc0c21c9',
 'scope':'All14 informed accepted15 regression roots with exactly the published bounded generic private descent; no production change',
 'roots':roots,'partitions':[roots[i:i+2] for i in range(0,len(roots),2)],
 'version':15,'density':14,'phase':'SELECTED_AFTER_EDGE_TRIM',
 'summaryPrefix':'PCB_ROUTED_SLIDE_COHORT_CASE ',
 'reportOrder':['PCB_WHITESPACE_CASE ','PCB_ROUTED_LEG_CASE ','PCB_ROUTED_SLIDE_COHORT_CASE '],
 'selectedRoute':'P07_FULLER_TWO_LAYER',
 **{n:prior[n] for n in ('summaryFields','limits','structuralLimits','statuses','stepFields','structuralFields',
    'sourceFields','metricsFields','controlFields','rowFields','compactionFields','proofFields','feasibilityProofFields',
    'sourceGenerationMetadata','privateGenerationMetadata','aggregateFields','improvementRule','selectionRule',
    'terminationRule','chainRule','proofRule','structuralRule')},
 'selectedBaselines':{r['seed']:r for r in rows},
 'originalBoundary':'Every selected42non-time field and exact current15 whitespace equals the sealed accepted15 corpus; all signed-long seeds remain text',
 'sourceBoundary':'Initial area/copper/vias/void/parts/pads/geometry/live graph from sealed per-root baseline; positive native trace/provenance counts with complete current pad-net census and immutable chain; published roots5/2 full non-time summary additionally exact',
 'matchedRoots':['5','2'],
 'matchedTimePaths':['elapsedMillis','steps[*].rows[*].feasibility.timing.constructionMillis',
    'steps[*].rows[*].feasibility.timing.placementMillis','steps[*].rows[*].feasibility.timing.geometryMillis',
    'steps[*].rows[*].feasibility.timing.twoLayerMillis','steps[*].rows[*].feasibility.timing.proofMillis',
    'steps[*].rows[*].feasibility.timing.totalMillis'],
 'matchedTimeRule':'Only numeric time values may differ; preserve null versus measured stage profiles, all field order/schema and every non-time fact',
 'priorContract':{'path':'docs/task-evidence/PCB/routed-slide-descent-r1/assess.py','sha256':sha(OLD/'assess.py'),
    'protocolPath':'docs/task-evidence/PCB/routed-slide-descent-r1/protocol.json','protocolSha256':sha(OLD/'protocol.json')},
 'unitFixtureCounts':dict(prior['unitFixtureCounts'],PcbRoutedSlideCohortContractTest=3),
 'unitEvaluators':dict(prior['unitEvaluators'],PcbRoutedSlideCohortContractTest='selectedRoutedSlideCohort'),
 'unitFixtureNames':['genericFrameAndMetadata','genericFrameRejectionAndImmutability','cohortObserverCleanup'],
 'routePassDetail':'roots=2 rows=2 evaluator=selectedRoutedSlideCohort',
 'timingScope':'Native informed14 routed slide cohort; unchanged per-JVM/search/physical limits',
 'maxPhaseElapsedSeconds':36000,'maxCaseMillis':60000,'perJvmMillis':60000,'maxFileBytes':67108864,
 'publishedInputs':{p:sha(ROOT/p) for p in paths},
 'classificationOwnerSha256':prior['classificationOwnerSha256'],
 'suiteManifestSha256':{n:sha(HERE/n) for n in ('unit-suites.json','routes-suites.json')},
 'limitsDisclosure':['All14 roots are informed regressions, not unseen; bounded private qualification, no optimum/manufacturing/general performance claim',
    'Shared search/caps/validators/metrics unchanged; original selected results are immutable; no extra route or policy/version change',
    'Full current census derives move order/affected trace counts/net-pair hashes; full native paths and physical proof remain native responsibilities',
    'Seven fixed two-root JVMs retain60second budget; any timeout/missing proof/cleanup is FAIL or incomplete, never PASS',
    'No production/GWT/player/solver/repair/fullmatrix/unseen claim']}
p=HERE/'protocol.json';assert not p.exists();p.write_text(json.dumps(protocol,indent=2)+'\n')
print('Declared informed14 cohort protocol',sha(p))
