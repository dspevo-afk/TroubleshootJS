# Revision17 API and closed wire

Keep first12 scalar/path/graph records in existing order with sourceRevision17.
Append RB100_PREFIX_ALLOCATION_EVENT then PASS: RB100 prefix allocation contracts.

Native helper API:
Context(PcbBoardLayout layout,PcbBoardHole[] prefixHoles,String[] netOrder,
int searchNetIndex,String currentNet,int currentBranchOrdinal,int pass,
int maximumVias).
static Cursor begin(Context); advance(int maximumWork) boolean; lastWork();
isComplete(); bodyJson() immutable onlycomplete; result().totalHoles finalint.
Private immutable row codec callable from native fixture:
Row.snapshot(PcbBoardHole hole,int index,String[] netOrder) static on Row
Row fields are closed row fields below. snapshot parses creation identity but
does not apply current-prefix prior-ordinal restriction; Context/Cursor does.
Large row/match/totals loops resumable, one comparison debit each. Metadata refs
copied under exclusive owner. row work counters mean precisely the fields below.

Event exactkeys protocolVersion1,sourceRevision17,runId,
operationIdQUALITY_PROBE,scopeACTUAL_FIXED_PREFIX_CREATION_ALLOCATION_ONLY,
binding,diagnostic,chronologyProoffalse,routeAdoptionfalse,layoutQualificationfalse.
binding exactkeys branchIdentityVerifiedtrue,sourcePublisherUnchangedtrue,
pass,netId,startPadId,rootPadId,prefixHoleCount,maximumVias,
templateHoleCount0,searchNetIndex,currentNetPosition,currentBranchOrdinal,
netOrder. CurrentNetPosition=searchNetIndex-1; netOrder[currentNetPosition]=netId.
Source net vector order retained, max256 unique valid IDs, each<=4096 UTF16 chars.
No lexical sorting of netOrder; it is actual Search ordering.

diagnostic exactkeys stability,work,totals,rows.
stability exactkeys prefixGeometrySha256,liveBeforeVerifiedtrue,
liveAfterVerifiedtrue,holeIdentitiesVerifiedtrue,lineageVerifiedtrue.
work exactkeys advanceCalls,workSteps,rowsScanned,netComparisons,
netTotalsWritten,activeMillis.
rowsScanned=prefixHoleCount, netComparisons=prefixHoleCount*netOrder.length,
netTotalsWritten=netOrder.length. workSteps>=sum of those, <=advanceCalls*32768
and <=640*32768; natural counters. Timing summednanos includes assembly, excludes
constantResultwrapper/laterJSON, actualouterclocksincludeall.
totals exactkeys totalHoles,byKind,byNet.
byKind exactkeys VIA,PLATED,NON_PLATED; P07 scope VIA=totalHoles, others0.
byNet array in source netOrder, exactkeys netPosition,netId,holeCount; zero totals
included and sum totalHoles. Rows are canonical Java String ID order.

row exactkeys index,id,netId,kind,x,y,drillRadius,landRadius,exposure,fingerprint,
creatorNetPosition,creatorBranchOrdinal,piece.
index is exact source-array/canonical-ID position. kindVIA/drill2/land6/
exposureEXPOSED. Integer coords use existing board coordinate bound. Match
p07-via/<known net>/<positive canonical decimal ordinal>/<nonnegative canonical
decimal piece>; IDs may contain slash. Unique full known-net parse, encoded
net equals netId. creatorNetPosition<=currentNetPosition; ordinal strictly less
than currentBranchOrdinal and>0; piece>=0. Whole ID length<=4096 UTF16 chars,
no control U+0000..001F/U+007F. Fingerprint recomputed using JavaUTF16 lengths.

Summary exactkeys protocolVersion1,sourceRevision17,statusPASS,runId,
operationIdQUALITY_PROBE,events1,scopeACTUAL_FIXED_PREFIX_CREATION_ALLOCATION_ONLY,
totalHoles,chronologyProoffalse,routeAdoptionfalse,layoutQualificationfalse.

Coordinated schemas: p07-quality-zero-via-input-v2,
q100-quality-zero-via-native-run-v2, q100-quality-zero-via-reader-result-v2.
Main/suite remains Rb100QualityZeroViaGraphContractTest; native1074=850+218+6.
Current frozen path/scalar helpers only explicit private metadata updates, no
function monkeypatch/module leakage. Reader current2 files migrate; other28 frozen.
Totalclosedrecordcount14. Report adds prefixAllocationObservation (closed
binding/stability/work/totals/creation rows) while retaining unchanged graph
analysis and false global acceptance flags. Actual totals/owner distribution
UNKNOWN before real gate; no predicted goldens.
Pre-gate lineage clarification: Search uses a single increasing global branch
ordinal while netIndex advances monotonically. All rows sharing a creator
ordinal must name the same net/position. As distinct creator ordinals increase,
their net positions must be nondecreasing. An inventory is partial (branches
without new holes are absent), so this necessary consistency check does not
establish a chronological insertion log. Pair comparisons are bounded48² and
are debited in workSteps; no new wire field or budget is introduced.
Work clarification before gates: inspect each unordered pair of rows once for
the global creator-ordinal/net-position consistency above, debiting one step per
pair. Therefore workSteps must also be at least rowsScanned+netComparisons+
netTotalsWritten+N*(N-1)/2. For48 rows this adds1128 comparisons. Extra preparation
steps may increase the debit; the same32768 quantum and640-unit cap apply.Timing clarification before native gates: sum capture/advance work, Result creation,
complete body template assembly and a complete valid placeholder JSON String.
Then insert actual elapsed milliseconds in a reserved numeric slot. Only that
self-referential timestamp correction/final String wrapper and later outer JSON
are excluded. Outer value-unit/case clocks include all work. Root source review
identified and corrected the earlier omission before any native gate.