"""Declare the finite physical census before implementation and native execution."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'routed-passive-leg-census-r1'
PROOF = HERE.parent/'routed-passive-move-proof-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
old = read(OLD/'protocol.json')
proof = read(PROOF/'protocol.json')
summaries = [json.loads(line[len(old['summaryPrefix']):])
    for line in (OLD/'routes.txt').read_text().splitlines() if line.startswith(old['summaryPrefix'])]
target = next(c for r in summaries if r['seed'] == '2'
    for c in r['successfulCallbacks'] if c['target'])
moves = [{'componentId':p['componentId'], 'dx':d['dx'], 'dy':d['dy'],
    'analyticCopperDelta':d['copperDelta'], 'actualCopperDelta':d['copperDelta']}
    for p in target['parts'] if p['eligiblePackage'] for d in p['directions']
    if d['eligible'] and d['copperDelta'] <= 0]
assert len(moves) == 31 and moves[0] == proof['expectedMove']
geometry = {
    'COMPONENT_BODY_OVERLAP':'PCB component bodies overlap: ',
    'COMPONENT_COURTYARD_OVERLAP':'PCB component routing courtyards overlap: ',
    'PAD_OVERLAP':'PCB pads overlap: ',
    'TRACE_OUTSIDE_BOARD':'PCB trace leaves board: ',
    'TRACE_COMPONENT_COURTYARD':'PCB trace passes through component routing courtyard: ',
    'TRACE_SELF_OVERLAP':'PCB trace has repeated or overlapping segments: ',
    'TRACE_SELF_INTERSECTION':'PCB trace self-intersects: ',
    'LABEL_COMPONENT_OVERLAP':'Silkscreen label overlaps component: ',
    'LABEL_PAD_OVERLAP':'Silkscreen label overlaps pad: ',
    'LABEL_COPPER_OVERLAP':'Silkscreen label overlaps copper: ',
    'LABEL_LABEL_OVERLAP':'PCB silkscreen labels overlap: ',
    'FOREIGN_NET_CONTACT':'Unrelated PCB conductors share copper: ',
    'FOREIGN_COPPER_CLEARANCE':'PCB traces violate copper clearance: ',
    'PHYSICAL_NET_DISCONNECTED':'PCB net is electrically disconnected: ',
}
two_layer = {
    'INSULATING_BODY_BARRIER':'Insulating body crosses an unrelated barrier',
    'INSULATING_SPAN':'Package body crosses outside its declared insulating span',
    'LAND_DOMAIN_BARRIER':'Component land crosses a physical domain barrier',
    'LEAD_DOMAIN_BARRIER':'Component lead crosses a physical domain barrier',
    'COPPER_DOMAIN_BARRIER':'P07 copper crosses a physical domain barrier',
    'DRILL_DOMAIN_BARRIER':'Drill or land crosses a physical domain barrier',
    'VIA_DOMAIN_OR_NET':'P07 via crosses a domain barrier or has an unknown net',
    'VIA_PACKAGE_COURTYARD':'P07 via drills through a package courtyard',
    'VIA_PAD_CLEARANCE':'P07 via overlaps a component pad or its clearance',
    'VIA_COPPER_CLEARANCE':'P07 via violates foreign copper clearance',
    'DRILL_LAND_CLEARANCE':'P07 overlapping drill/land clearance',
}
owners = ['PcbBoardLayout.java','PcbTwoLayerRules.java','PcbPlacementPlanner.java',
    'PcbAccessPlanner.java','PcbConductorBuilder.java','PcbConductorGraph.java','PcbTraceClearance.java']
owner_paths = ['src/com/lushprojects/circuitjs1/client/'+n for n in owners]
owner_text = '\n'.join((ROOT/n).read_text() for n in owner_paths)
assert all(text in owner_text for text in list(geometry.values())+list(two_layer.values()))
published = dict(proof['publishedInputs'])
for name in ('native-inputs.json','root-review.json','protocol.json','routes.txt'):
    published[(PROOF/name).relative_to(ROOT).as_posix()] = sha(PROOF/name)
protocol = {
    'schema':1, 'baseCommit':'8ddbd8af545a8e66bf9bf52985b2486a0a734481',
    'scope':'Independent physical census of all31 nonincreasing structural directions; no selection or production change',
    'roots':['5','2'], 'version':15, 'density':14, 'phase':'FINALIZED_ROUTE_CANDIDATE',
    'targetStrategy':'P07_FULLER_TWO_LAYER', 'targetAttempt':2,
    'constructionLimitPerTargetCallback':31,
    'sourceRule':'Every candidate starts from the identical original target, never another candidate',
    'summaryPrefix':'PCB_ROUTED_PASSIVE_FEASIBILITY_CENSUS ',
    'reportOrder':proof['reportOrder'][:3]+['PCB_ROUTED_PASSIVE_FEASIBILITY_CENSUS '],
    'summaryFields':['seed','label','version','phase','status','newRouteRequests','selectedMutation',
        'constructionCount','source','rows','aggregate','elapsedMillis'],
    'sourceFields':['liveGraphBefore','liveGraphAfter','geometryBefore','geometryAfter',
        'copperLength','area','componentCount','padCount','traceCount','traceProvenanceHash',
        'sourceRouteSourceCount','reachabilityHash','padPairCount'],
    'rowFields':['move','candidate','disposition','stage','reason','message','checks','proof','timing'],
    'moveFields':proof['moveFields'], 'candidateFields':proof['candidateFields'],
    'checkFields':['traceEndpointOnlyVerified','unrelatedGeometryVerified','sourceImmutable',
        'placementValidated','geometryValidated','twoLayerValidated'],
    'proofFields':proof['validationFields'],
    'timingFields':['constructionMillis','placementMillis','geometryMillis','twoLayerMillis','proofMillis','totalMillis'],
    'aggregateFields':['structuralDirections','constructionCount','physicallyValidated',
        'physicallyRejected','placementRejected','geometryRejected','twoLayerRejected'],
    'expectedMoves':moves, 'expectedSource':dict(proof['expectedSource'],componentCount=56,
        padCount=135,traceCount=145,sourceRouteSourceCount=145,padPairCount=9045,
        reachabilityHash='9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7'),
    'expectedFirstRejection':{'stage':'GEOMETRY','reason':'TRACE_COMPONENT_COURTYARD',
        'message':'PCB trace passes through component routing courtyard: CTRL_RETURN / C5 segment 3256,1094 -> 3226,1094 keepOut=Rect(3171,1094,80,90) startPad=U1.RETURN endPad=C5.2'},
    'placementReasons':['ENVELOPE_ACCESS_OR_DOMAIN','STRANDED_ESCAPE','DOMAIN_CORRIDOR','DISCONNECTED_ESCAPE_CHANNEL'],
    'geometryPrefixReasons':geometry, 'twoLayerExactReasons':two_layer,
    'qualityReasons':{'ROUTE_QUALITY_BENDS':'PCB trace has excessive bends: ',
        'ROUTE_QUALITY_DETOUR':'PCB trace has excessive detour: '},
    'rejectionRule':'Only at the actual named validator call; unknown errors, budgets, malformed construction, proof mismatches and cancellation propagate',
    'unitFixtureCounts':{'PcbRoutedPassiveLegCensusContractTest':6,
        'PcbRoutedPassiveFeasibilityCensusContractTest':6},
    'unitEvaluators':{'PcbRoutedPassiveLegCensusContractTest':'endpointLegCensus',
        'PcbRoutedPassiveFeasibilityCensusContractTest':'physicalFeasibilityCensus'},
    'routePassDetail':'roots=2 rows=2 evaluator=physicalFeasibilityCensus',
    'timingScope':'Native routed passive physical census; unchanged per-JVM/search/physical limits',
    'maxPhaseElapsedSeconds':36000, 'maxCaseMillis':60000, 'maxFileBytes':67108864,
    'publishedInputs':published,
    'priorCensusReader':proof['priorCensusReader'],
    'classificationOwnerSha256':{n:sha(ROOT/n) for n in owner_paths},
    'suiteManifestSha256':{n:sha(HERE/n) for n in ('unit-suites.json','routes-suites.json')},
    'limits':['Informed two-root private census; no policy/fitness/production or board shrinkage acceptance',
        'No extra route call, original mutation, candidate chaining, alternate-move selection or version change',
        'Full physical/net/provenance proof only for validated rows; rejected rows have null proof',
        'Fixed outline/tray/holes and unassigned private generation metadata',
        'Finite known physical rejection catalog; all unknown errors/cancellation/budgets abort',
        'No GWT/player/solver/repair/full-matrix/unseen/all-seed/manufacturing/performance claim']}
assert not (HERE/'protocol.json').exists()
(HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
print('Declared31 independent moves/6new fixture groups/closed stage taxonomy; native NOT_RUN')
