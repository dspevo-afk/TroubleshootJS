"""Declare the finite private-move proof before native execution."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
POOL = HERE.parent/'successful-route-pool-r1'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
assert not (HERE/'protocol.json').exists()
prior = read(OLD/'native-inputs.json')
assert sha(OLD/'assess.py') == prior['gateOwners']['assess.py']
protocol = {
    'schema':1, 'baseCommit':'a816fdf186e2c89316bdd57baf60393299795a02',
    'scope':'One private endpoint-only passive move; no selected-layout, route, policy or fitness change',
    'roots':['5','2'], 'version':15, 'density':14,
    'phase':'FINALIZED_ROUTE_CANDIDATE', 'targetStrategy':'P07_FULLER_TWO_LAYER',
    'targetAttempt':2, 'constructionLimitPerTargetCallback':1,
    'selection':'First canonical census part/direction structurally eligible with analyticCopperDelta<=0',
    'proofPrefix':'PCB_ROUTED_PASSIVE_MOVE_PROOF ',
    'reportOrder':['PCB_WHITESPACE_CASE ', 'PCB_ROUTED_LEG_CENSUS ',
        'PCB_ROUTED_LEG_CASE ', 'PCB_ROUTED_PASSIVE_MOVE_PROOF '],
    'proofFields':['seed','label','version','phase','status','constructionCount',
        'newRouteRequests','selectedMutation','move','source','candidate','proof','physicalFeasibility'],
    'moveFields':['componentId','dx','dy','analyticCopperDelta','actualCopperDelta'],
    'sourceFields':['liveGraphBefore','liveGraphAfter','geometryBefore','geometryAfter','copperLength','area'],
    'candidateFields':['geometryHash','componentCount','padCount','traceCount',
        'affectedTraceCount','movedPadCount','copperLength','area','generationMetadata'],
    'validationFields':['placementValidated','geometryValidated','twoLayerValidated',
        'padPairCount','sourceReachabilityHash','candidateReachabilityHash',
        'sourceRouteSourceCount','candidateRouteSourceCount','sourceTraceProvenanceHash',
        'candidateTraceProvenanceHash','traceEndpointOnlyVerified','unrelatedGeometryVerified',
        'conductorReachabilityVerified','traceProvenanceVerified'],
    'expectedStatus':{'5':'NO_TARGET','2':'PRIVATE_VALIDATED'},
    'expectedMove':{'componentId':'C5','dx':0,'dy':-10,'analyticCopperDelta':-10,
        'actualCopperDelta':-10},
    'expectedSource':{'area':9313352,'copperLength':51860,'traceCount':145,
        'geometryHash':'e5242445527c50145b0848e79efb045518ef56e0a3f559ddeeb9ba798efd6c6e'},
    'expectedCandidate':{'componentCount':56,'padCount':135,'traceCount':145,
        'affectedTraceCount':3,'movedPadCount':2,'copperLength':51850,'area':9313352,
        'generationMetadata':'NOT_ASSIGNED_PRIVATE_CANDIDATE'},
    'expectedPadPairCount':9045,
    'reachabilityCanonical':"R1\\n then sorted pad pairs i<j: field(a)+field(b)+bit+'\\n'; field=UTF16-code-unit-length+':'+ID; bit=1 iff same source net; UTF8 SHA256",
    'unitFixtureCounts':{'PcbRoutedPassiveLegCensusContractTest':6,
        'PcbRoutedPassiveMoveProofContractTest':4},
    'unitEvaluators':{'PcbRoutedPassiveLegCensusContractTest':'endpointLegCensus',
        'PcbRoutedPassiveMoveProofContractTest':'singlePrivateMove'},
    'routePassDetail':'roots=2 rows=2 evaluator=singlePrivateMove',
    'timingScope':'Native single private passive move proof; unchanged per-JVM/search/physical limits',
    'maxPhaseElapsedSeconds':36000, 'maxCaseMillis':60000, 'maxFileBytes':67108864,
    'publishedInputs':{p.relative_to(ROOT).as_posix():sha(p) for p in (
        OLD/'native-inputs.json',OLD/'root-review.json',OLD/'protocol.json',OLD/'routes.txt',
        POOL/'native-inputs.json',POOL/'routes.txt')},
    'priorCensusReader':{'path':(OLD/'assess.py').relative_to(ROOT).as_posix(),
        'sha256':sha(OLD/'assess.py')},
    'suiteManifestSha256':{n:sha(HERE/n) for n in ('unit-suites.json','routes-suites.json')},
    'limits':[
        'One informed private geometry trial, not a production or fitness acceptance',
        'No extra route call, alternative move, original mutation or selection change',
        'Fixed outline/tray/holes; no area or protected-void shrinkage claim',
        'Finalized Plan and absent generation seeds are not reconstructed or invented',
        'Full validation/reachability/provenance errors and cancellation propagate',
        'No GWT/player/solver/repair/full-matrix/unseen/all-seed/manufacturing/performance claim'],
}
(HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
print('Declared one-private-move proof2roots,2unit/1route,9045 independent pad pairs; native NOT_RUN')
