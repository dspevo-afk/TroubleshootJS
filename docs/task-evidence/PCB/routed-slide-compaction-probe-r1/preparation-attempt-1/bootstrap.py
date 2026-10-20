"""Install only erasable selected-layout diagnostics and bounded suite rows."""
import hashlib
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-passive-feasibility-census-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
COPY=Path(read(HERE/'local-state.json')['scratch'])
prior=read(OLD/'native-inputs.json')
generator='src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
leg='tests/contracts/PcbRoutedPassiveLegCensusContractTest.java'
registry='scripts/verify-current-contracts.ps1'
wrapper='tests/contracts/PcbRoutedSlideCompactionTrialContractTest.java'
for name in (generator,leg):
    assert sha(COPY/name)==dict(prior['overrides'],**prior['additions'])[name]
assert not (COPY/wrapper).exists() and not (HERE/'bootstrap-audit.json').exists()
units=[['PcbRoutedPassiveLegCensusContractTest','PCB routed passive leg census contracts '],
    ['PcbRoutedPassiveFeasibilityCensusContractTest','PCB routed passive feasibility census contracts '],
    ['PcbRoutedSlideCompactionContractTest','PCB routed slide compaction contracts ']]
routes=[['PcbRoutedSlideCompactionTrialContractTest','PCB routed slide compaction trial contracts ']]
bridge=r"""    // BEGIN COPY-ONLY selected routed slide observer.
    interface SelectedSlideObserver {
        void onSelected(TroubleshootBoard board, PcbBoardLayout layout,
            PcbPlacementPlanner.Plan plan, String route, int attempt);
    }
    private static SelectedSlideObserver selectedSlideObserver;
    static SelectedSlideObserver setSelectedSlideObserver(SelectedSlideObserver observer) {
        SelectedSlideObserver previous=selectedSlideObserver;
        selectedSlideObserver=observer;
        return previous;
    }
    static PcbPlacementPlanner.Plan reframeSelectedSlidePlan(
            PcbPlacementPlanner.Plan plan, PcbBoardLayout layout) {
        return new PcbPlacementPlanner.Plan(layout.getBoardOutline(),
            translatedPlanFootprints(plan,layout,0,0),layout.getRegions(),
            plan.courtyardArea,plan.demandArea,plan.candidate,plan.evaluations);
    }
    static void dispatchSelectedSlide(TroubleshootBoard board,PcbBoardLayout layout,
            PcbPlacementPlanner.Plan plan,String route,int attempt) {
        SelectedSlideObserver observer=selectedSlideObserver;
        if(observer==null)return;
        GenerationWorkScope.check();
        observer.onSelected(board,layout,reframeSelectedSlidePlan(plan,layout),route,attempt);
    }
    static void restoreSelectedSlideSilkscreen(TroubleshootBoard board,PcbBoardLayout layout) {
        if(!layout.getSilkscreenLabels().isEmpty())
            throw new IllegalArgumentException("Private relabel requires an empty label owner");
        new SeededPcbLayoutGenerator().placeSilkscreen(layout,board,layout.getBoardOutline());
    }
    // END COPY-ONLY selected routed slide observer.
"""
raw=(COPY/generator).read_bytes()
anchor=b'    private final int layoutAlgorithmVersion;\r\n'
assert raw.count(anchor)==1
raw=raw.replace(anchor,anchor+bridge.replace('\n','\r\n').encode())
anchor=b'        session.mediumPolicyResult = new MediumBoardPhysicalPolicy.Result(\r\n'
assert raw.count(anchor)==1
call=r"""        // BEGIN COPY-ONLY selected routed slide observer.
        if(selectedSlideObserver!=null) {
            PcbPlacementPlanner.Plan selectedPlan=null;
            for(MediumBoardPhysicalPolicy.PlacementCandidate candidate:session.mediumPlacements)
                if(candidate.attempt==session.mediumSelectedPlacementAttempt) {
                    if(selectedPlan!=null)throw new IllegalStateException("Duplicate selected slide plan");
                    selectedPlan=candidate.plan;
                }
            if(selectedPlan==null)throw new IllegalStateException("Missing selected slide plan");
            dispatchSelectedSlide(session.board,session.bestLayout,selectedPlan,
                session.mediumSelectedRoute,session.mediumSelectedPlacementAttempt);
        }
        // END COPY-ONLY selected routed slide observer.
"""
raw=raw.replace(anchor,call.replace('\n','\r\n').encode()+anchor)
legraw=(COPY/leg).read_bytes()
anchor=b'    private PcbRoutedPassiveLegCensusContractTest() { }\n'
assert legraw.count(anchor)==1
movebridge=r"""    // BEGIN COPY-ONLY selected routed slide census adapter.
    static Vector<MoveIntent> selectedSlideMoves(TroubleshootBoard board,PcbBoardLayout layout) {
        Vector<MoveIntent> moves=new Vector<MoveIntent>();
        for(PartResult part:Recorder.analyzeTarget(board,layout))
            for(DirectionResult direction:part.directions)
                if(direction.eligible && direction.copperDelta.longValue()<=0L)
                    moves.add(new MoveIntent(part.componentId,direction.dx,direction.dy,
                        direction.copperDelta.longValue()));
        return moves;
    }
    // END COPY-ONLY selected routed slide census adapter.
"""
legraw=legraw.replace(anchor,movebridge.encode()+anchor)
reg=(ROOT/registry).read_bytes()
anchor=b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest';"
assert reg.count(anchor)==1
rows=b''.join(("        @{ Name = '"+name+"'; Marker = '"+marker+"' },\r\n").encode() for name,marker in units+routes)
reg=reg.replace(anchor,rows+anchor)
wrap=b"""package com.lushprojects.circuitjs1.client;

/** Fixed informed native diagnostic only; actual selected output remains untouched. */
public final class PcbRoutedSlideCompactionTrialContractTest {
    public static void main(String[] args) {
        for(long seed:new long[]{5L,2L})
            PcbRoutedSlideCompactionContractTest.observe(seed,"seed"+seed);
        System.out.println("PASS: PCB routed slide compaction trial contracts "+
            "roots=2 rows=2 evaluator=selectedRoutedSlideCompaction");
    }
}
"""
# All preflights above precede the first mutation.
for name,data in ((generator,raw),(leg,legraw),(registry,reg),(wrapper,wrap)):
    (COPY/name).write_bytes(data)
for name,value in (('unit-suites.json',units),('routes-suites.json',routes)):
    assert not (HERE/name).exists()
    (HERE/name).write_text(json.dumps(value,indent=2)+'\n')
(HERE/'bootstrap-audit.json').write_text(json.dumps({'status':'PASS',
    'overrides':[generator,'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java',registry],
    'addedRootSources':[leg,'tests/contracts/PcbRoutedPassiveFeasibilityCensusContractTest.java',wrapper],
    'plannedWorkerSource':'tests/contracts/PcbRoutedSlideCompactionContractTest.java',
    'bridgeBoundary':'After selected trim and generation statistics; before result completion',
    'defaultObserver':'NULL; no inspection','nativeExecution':'NOT_RUN'},indent=2)+'\n')
print('Prepared COPY1470 adapters/wrapper;1471 after worker probe source; no native execution')
