# Frozen revision16 API and wire

Native Context:
Context(PcbBoardLayout layout,PcbNetRouter.Router[] faces,String net,
PcbPadPlacement start,PcbPadPlacement root,PcbPadPlacement[] reached,
boolean[] actualGoalMask,int width,int height,int originX,int originY,int pitch,
int initialLayerMask,int[][] pathPhysical /* x,y,layer */).
Context keeps private source references under the stated exclusive-owner
precondition; any large copy/scan/encoding work is resumable in Cursor.
static Cursor begin(Context); Cursor.advance(int maximumWork) bool;
lastWork(); isComplete(); bodyJson() immutable onlycomplete;
Cursor.result().ordinaryQueries package-visible finalint.
No native shortest analysis. Result immutable. Goal masks are copied/encoded
incrementally; output strings private/final and source state checked.

Keep first10 scalar/path records in exact prior order with sourceRevision16.
Replace the last2 shortcut records with:
RB100_ZERO_VIA_GRAPH_EVENT and PASS: RB100 quality zero-via graph contracts.
Top event exact keys protocolVersion1,sourceRevision16,runId,
operationIdQUALITY_PROBE,scopeACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY,
binding,diagnostic,feasibilityUNKNOWN,legalRouteProoffalse,
layoutQualificationfalse,routeAdoptionfalse.
binding exactkeys branchIdentityVerifiedtrue,sourcePublisherUnchangedtrue,
pass,netId,startPadId,rootPadId,reachedPthPadIds,foundTerminalPadId,
endCell,endLayer,goalMaskLength,policy,primaryLayer,initialLayerMask,
maximumVias,prefixHoleCount. Actualpolicy FULLER_TWO_LAYER, primary1,mask3.
Do NOT assert prefix saturation or absent via alternatives.

diagnostic exactkeys stability,grid,work,masks.
stability exactkeys prefixGeometrySha256,liveBeforeVerifiedtrue,
liveAfterVerifiedtrue,liveEffectsUnchangedtrue,goalVerifiedtrue,
originalPathVerifiedtrue.
grid exactkeys width,height,cells,originX,originY,pitch,
startCell,initialLayerMask,endCell,endLayer.
work exactkeys advanceCalls,workSteps,copyEntries,comparisonEntries,
ordinaryQueries,goalCells,encodedCells,activeMillis.
ordinaryQueries=2*(4*cells-2*width-2*height), goalCells=cells,
encodedCells=3*cells. Natural counters, copy/compare counts actual task entries,
workSteps conservativefullnestedallowance+boundedprimitivework,
activeMillis summednanos thenflooronce; includespayloadassembly, excludesfinal
constant-time Resultwrapper/laterJSONserialization. Outerclockincludesall.
masks exactkeys topStepsHex,bottomStepsHex,goalFacesHex; eachlengthcells lowercase
hex canonical, goalnibbles0..3. GridNDESWboundarybits0, no wrap.
Graph includes no via edges.

Summary exactkeys protocolVersion1,sourceRevision16,statusPASS,runId,
operationIdQUALITY_PROBE,events1,scopeACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY,
ordinaryQueries,feasibilityUNKNOWN,legalRouteProoffalse,
layoutQualificationfalse,routeAdoptionfalse.

New input p07-quality-zero-via-input-v1, native
q100-quality-zero-via-native-run-v1, result
q100-quality-zero-via-reader-result-v1. SuiteRb100QualityZeroViaGraphContractTest,
native1072=850+216+6/rev16. Focused independent existingP07ShortcutQueryContractTest
and newP07ZeroViaGraphContractTest in one maintained compile/source gate.

Reader report names/analysis representation are reader-owned but closed and
test-bound: seedmask3 actual, per-face reachablegoals/shortestanygoal/
distancecapturedend nullable, count lengthEligibleGoals with per-goal threshold
direct>0 anddistance<=3*direct, minimumeligiblelength nullable, geometricRelaxation
claimonly. No layout/routing adoption allowed. Bind original source records and
parseall12 closedrecords; no unknown-event filtering.