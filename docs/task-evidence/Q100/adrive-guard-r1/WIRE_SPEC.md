# A_DRIVE guard witness wire draft R2

Status: final wire proposal incorporating root review; code lock released after this
draft. R1 remains preserved beside this file.
Plan input: docs/task-evidence/Q100/adrive-guard-r1/PLAN.md,
SHA-256 68c73dda6af9f94ac07f1a576b16d6de74f15d26504d11a9109eec683cbb4d1b.
This contract binds that plan and the accepted predecessor. It does not preassign
A_DRIVE's later branch ordinal, endpoint, path, length, bend count, direct
distance, or predicate result.

## Mode and successful stdout

The new native switch is -Rb100Load12Cost240GuardReplay; the Java argument is
--cost240-guard. It is mutually exclusive with every other RB100 mode and
requires exactly Rb100Load12DetachedBaselineContractTest. Existing mode names,
outputs, and the frozen f11 base validator remain unchanged.

A successful invocation emits exactly three UTF-8 stdout records, each
terminated by one newline:

1. The existing RB100_LOAD12_COST240_REMAINDER_EVENT  prefix followed by its
   existing JSON payload, with no field/schema/meaning changes.
2. RB100_LOAD12_ADRIVE_GUARD_EVENT  followed by the closed witness JSON below.
3. PASS: RB100 LOAD12 detached cost240 guard contracts assertions=<decimal>.

The witness base.runId equals the first record's runId. Its
base.eventSha256 is lowercase SHA-256 over the exact UTF-8 bytes of the
first record's JSON payload only: exclude marker prefix and line ending; do not
parse/re-serialize the base event before hashing. The independent consumer hashes
the raw first payload, calls frozen f11 _validate_event(base), and
cross-binds the witness to raw base source, copy, selector,
remainder, and work objects. Native command, source-v5 provenance,
stdout shape and receipt framing remain a separate wrapper boundary.

No PASS record is emitted for an incomplete witness, unexpected exception,
observer/stale-owner failure, bound or budget exhaustion, mismatch, or failed
cleanup. Preserve existing failure progress/event and process failure; a failure
is never normalized to a routing rejection. The successful event schema below is
closed and has no optional or unknown keys.

## Closed witness record

Top-level keys, all required:

- protocolVersion: integer 1.
- schema: string rb100-load12-adrive-guard-witness-r1.
- status: string PASS.
- runId: same UUID string as the base event.
- operationId: string QUALITY_PROBE.
- scope: string ACTUAL_LOAD12_COST240_COPIED_ORDER_ADRIVE_PRE_GUARD.
- base: object exactly {runId, eventSha256}.
- inputs: object exactly {rootSeed, placementSeed, routingSeed,
  routeRequestIndex, selectedPlacementIndex, selectedPlacementAttempt,
  sourcePass, copyMaximumVias, copyViaCost}. Seeds are exact signed decimal
  strings; other fields are JSON integers. Frozen values are
  9007199254740993, -6587958367974952701, 8445301674744508279,
  0, 0, 4, source pass=1, copied max vias 48, copied VIA
  cost 240.
- lineage: object exactly {source, copy}. source projects the raw
  base source fields routeSessionIdentity, searchIdentity,
  branchIdentity, layoutIdentity, digests
  (seeds, netOrder, prefixRows, geometry) and preserved.
  copy projects raw base copy fields ownerIdentity,
  searchIdentity, branchIdentity, layoutIdentity, digests
  (seeds, netOrder, prefixRows, initialGeometry,
  postGeometry, publication), requested
  (maximumVias, viaCost), and continuationHandoff
  (identity, ownerIdentity). Preserve the LOAD12 predecessor
  branchIdentity as the raw base value; it is not the later A_DRIVE
  branch. IDs are process-local signed identity hashes compared only within
  this run. Validate live object references as well as their reported IDs.
- target: object exactly
  {ordering, pass, netId, netVectorPosition, searchNetIndex,
  netVectorIdentity, branchOrdinal, searchBranches, ownerIdentity,
  searchIdentity, layoutIdentity, branchIdentity, publisherIdentity,
  foundNodeIdentity, foundTerminalIdentity}. Integers are actual live
  values; netId is A_DRIVE; positions are zero-based; nullable
  foundTerminalIdentity is JSON null only when the actual value is null.
  ordering is the actual one-based current ordering. netVectorPosition
  is the actual zero-based position of currentNet in the live vector;
  searchNetIndex is the live next-fetch index. Capture this relation once
  in the charged checkpoint, not by copying/scanning the vector per microcall.
  Bind copied owner/Search/layout and continuation identities to the base
  event. Do not compare the current A_DRIVE branch identity to the predecessor
  LOAD12 branch identity.
- grid: object exactly
  {width, height, cells, originX, originY, pitch}, copied from live Search.
  Integers satisfy cells=width*height, 1<=cells<=250000,
  pitch=10. originX/originY are Search minX/minY.
- endpoints: object exactly {start, root, end, foundTerminal}.
  start and root are objects {padId, identity, x, y, cell} with
  the actual pad physical coordinates and live mapped cell. end is
  {identity, x, y, cell} from the actual found Node; its x/y are grid
  coordinates, not pad coordinates. foundTerminal is null or
  {padId, identity, x, y, cell} from the retained actual Publisher
  terminal and carries that pad's physical coordinates.
  Production Search.cell(pad) mapping is exactly:
  dx=pad.x-originX, dy=pad.y-originY; require both nonnegative,
  divisible by GRID, and within width/height; then
  cell=(dy/GRID)*width+dx/GRID. Keep the actual physical start.x/y
  even when they differ from the corresponding gridpoint. Direct distance
  uses physical start coordinates and grid end coordinates.
- path: object exactly {encoding, nodeCount, maximumNodes,
  rowsSha256, nodes}. Encoding:
  cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow.
  maximumNodes=4096; nodeCount is 1..4096 and equals array length.
  Each row is exactly 11 JSON integers in that order, start-to-end.
  parentRow=-1 only on row 0; row i>0 has parentRow=i-1.
  rowsSha256 is lowercase SHA-256 of the canonical compact ASCII JSON
  spelling of the node array alone (no spaces/newline; base-10 integers
  without leading zeroes).
- checkpoint: object exactly
  {publisherStage, qualityIndex, reversedSize, storedLength, storedBends,
  storedLastDirection, direct, recomputedLength, recomputedBends,
  transitions, directZero, lengthOverThreeDirect, bendsOver16, rejected}.
  It describes the live pre-guard point: stage is 1 and
  qualityIndex==reversedSize==path.nodeCount. direct is the Manhattan
  distance from actual physical start-pad coordinates to actual found Node
  grid coordinates. Recomputed length is GRID times same-layer edges;
  recomputed bends come from coordinate-derived move directions and reset to
  sentinel 4 at every layer transition. Require stored/recomputed length and
  bends to match. Predicate bits are exactly direct==0,
  storedLength>3*direct, and storedBends>16; rejected is their OR
  and must be true.
- boundary: object exactly
  {status, type, reason, orderings, cumulativeExpansions,
  remainingRequestBudget, sameTypedBoundary, rejectedBranchPublished}.
  It records the actual next unchanged advance(1) result:
  status=ORDERING_BOUNDARY, type=ORDERING_BOUNDARY,
  reason=A_DRIVE:BRANCH_QUALITY_LIMIT, orderings=2,
  sameTypedBoundary=true, and rejectedBranchPublished=false.
  The boundary must cross-equal the base event and accepted predecessor:
  orderings 2, cumulative expansions 774550, remaining request budget
  225450. These are not expected A_DRIVE branch/path metrics.
- prefix: object exactly {before, after, unchanged}. Both snapshots
  have exactly {layoutIdentity, geometrySha256, traceCount, traceRowsSha256,
  holeCount, holeRowsSha256}. Compute once at the charged pre-capture
  checkpoint and once after the unchanged guard call. They must be equal and
  unchanged=true; counts can include geometry already published by LOAD12
  and earlier branches. rejectedBranchPublished=false applies only to the
  rejected A_DRIVE branch. Independently retain/compare the paused source via
  base source.before/after/preserved.
- work: object exactly
  {budgetPhase, controllerUnitLimit, ownerStepsPerUnitLimit,
  controllerUnits, subphases, continuationAdvanceApiCalls,
  continuationOwnerSteps, maximumGroupOwnerSteps, operationElapsedNanos,
  phaseElapsedNanos, overheadElapsedNanos, capturedNodeRows,
  verifiedNodeRows, pathRowsUtf8Bytes}. budgetPhase is orderingRemainder.
  controllerUnits is the complete existing orderingRemainder phase
  count, equals the sum of the five subphases.*.controllerUnits and the
  raw base work.phases[orderingRemainder].calls. subphases has exactly
  advance, capture, recompute, serialize, boundaryCheck;
  each is {controllerUnits, elapsedNanos}. The counts partition all
  controller units in that existing phase: all low-level continuation work is
  advance, and actual bounded work uses the other four. Every guard unit
  name begins
  p07-quality-load12-remainder-ordering-remainder-adrive-guard- and so
  remains truthfully inside the existing phase.
  Capture, recompute, row serialization/UTF-8/hash work are owner work zero.
  continuationAdvanceApiCalls counts every attempted advance(1);
  increment before call. In finally, read and add actual lastWork for every
  microcall, including throwing calls. continuationOwnerSteps equals the
  raw base phase's ownerSteps. API calls are distinct from groups/steps and
  need not equal either. maximumGroupOwnerSteps is at most 32768 and at
  most the base event's global maxOwnerStepsInUnit; equality to the latter
  is not required. The one operation cap remains 640 units and the per-unit
  owner-step cap remains 32768.
  capturedNodeRows and verifiedNodeRows both equal path.nodeCount.
  pathRowsUtf8Bytes equals the exact canonical node-array byte length.
  phaseElapsedNanos is the existing monotonic Budget interval for
  orderingRemainder; operationElapsedNanos is the existing exact
  operation interval. overheadElapsedNanos>=0 and
  phaseElapsedNanos=sum(subphases.*.elapsedNanos)+overheadElapsedNanos.
  The phase is within the operation. Integer milliseconds in the base event
  use floor conversion: floor(operationElapsedNanos/1e6) equals base
  work.operationElapsedMs, and because terminal-capture/final-report
  phases are later active phases, base
  work.phases[orderingRemainder].elapsedMs equals
  floor(phaseElapsedNanos/1e6).  Do not equate nanoseconds to rounded
  milliseconds.
- cleanup: object exactly {status, elapsedMs, limitMs}, PASS under
  the unchanged 120000 ms ceiling; status and elapsedMs equal the base cleanup fields.
  Shutdown/termination remains in the native wrapper under its 15000 ms gate,
  not this Java event.
- qualification: object exactly
  {routeAccepted, geometryPublished, layoutQualification, routeAdoption,
  playable100}; all false. geometryPublished=false refers only to the
  rejected A_DRIVE branch; the copied prefix can contain previously published
  geometry.

The complete witness payload, excluding marker/newline, is at most 1 MiB
UTF-8. No 66-node D_SENSE helper is used.

## Path tuple checks for the independent consumer

For row i, validate 0<=cell<cells, layer in {0,1},
direction in {0,1,2,3,4}, nonnegative cost/heuristic/serial, and
0<=transitions<=4. Recompute grid coordinates as
x=originX+(cell%width)*pitch, y=originY+(cell//width)*pitch, and
recompute Node key as
(((transitions*2+layer)*5+direction)*cells)+cell. Row 0 must use the
mapped start cell, direction sentinel 4, and transitions 0. Each later parent
index points to the previous row. For a same-layer edge, derive direction
from coordinate delta and require the exact production mapping:
0=north (dx=0,dy=-pitch), 1=east (dx=pitch,dy=0),
2=south (dx=0,dy=pitch), 3=west (dx=-pitch,dy=0). Require the row's
stored direction to equal that derived direction and transitions to remain
unchanged. For a layer transition, require same cell and same coordinates,
transition count +1, and direction sentinel 4. Derive bends from coordinate
directions, resetting direction at a via; never trust the stored direction or
reported bend count alone. Require storedLastDirection to equal the final
derived direction (or sentinel 4 after a final via or for a one-node path).
Recompute direct from physical start-pad x/y to
grid end x/y. Recompute length, bends, transition count, and all three exact
guard predicates independently.

A one-node path is valid: with direct distance zero, direct-zero rejects even
when length and bends are zero. The exact 3x length and 16-bend limits are
allowed; only greater-than values reject.

## Accounting and serialization boundary

The probe retains the real BranchSearch reference exactly when a microcall
moves it into Publisher, then binds that BranchSearch to the actual found Node,
nullable found terminal, current Search, copied owner/layout, branch ordinal,
net vector, and pass. O(1) per-microcall checks read only owner/Search/
branch/Publisher references and scalar metadata, using cached read-only
reflection metadata if needed. Never hash geometry, traverse a path, or copy a
net vector on that hot path.

Bulk node-chain capture, primitive-row copy, independent recomputation, compact
node-array JSON/UTF-8 construction, byte count, and row SHA are all performed
inside charged controller units before operation accounting closes. Preserve
the resulting immutable canonical row byte array for output. After operation
measurement and cleanup, bounded report/transport work constructs the base
payload, hashes its exact emitted payload, assembles small guard-envelope
metadata, and writes the prebuilt row bytes verbatim into the JSON stream. It
must not recopy, traverse, or reserialize the large node array after the
operation. This boundary keeps the original operation/cleanup timing meanings
and makes their outside-operation reporting work explicit.

Full identity, seed/order/prefix and geometry snapshots are taken only in
charged capture and post-boundary units while the source remains paused. Each
new unit gets monotonic start/end checkpoints and stays under the existing 5 s
unit limit. The 4096-node bound is independent of routing limits; exceeding it
fails the diagnostic. Existing request 1000000 expansion, branch 100000
expansion, 250000-cell grid, 3 orderings, 48 vias, 4 transitions, and 700000
access-cell limits remain unchanged; cleanup remains 120000 ms and native
shutdown 15000 ms.

## Failure separation and controls

Keep these non-PASS terminal classes distinct:
PRODUCTION_BOUNDARY_MISMATCH, OBSERVER_FAILURE, STALE_OWNER,
INCOMPLETE_CAPTURE, WITNESS_NODE_LIMIT, WITNESS_BYTES_LIMIT,
BUDGET_EXPIRED, OPERATION_EXCEPTION, CLEANUP_FAILED.
The only success is an independently verified bounded witness followed by the
unchanged exact typed A_DRIVE quality boundary, unchanged copied layout/source,
and successful lease/cleanup proof. It establishes no route, finalizer, full
physical/electrical result, adoption, or playable layout.

Keep consumer fixtures primitive and independent: one-node/direct-zero;
length exactly 3x and one unit above; 16 and 17 bends; via direction reset;
corrupt parent/key/coordinates; base run/hash/lineage mismatch; stale owner;
4096 accepted and 4097 rejected nodes; 1 MiB accepted and overflow rejected;
wrong/missing boundary; budget/cleanup failure. These test parsing, arithmetic,
lineage and result classification only. No reflection writes or production
seam. Preserve f11 unchanged.

## Approval boundary and preservation

R2 settles only the wire/accounting contract and releases the lock for the
bounded implementation in the three assigned native files. It changes no
production owner, routing predicate, threshold, cost, geometry, or old mode.
The source owner PcbLayerRoutingPrototype.java, generator, old modes,
independent predecessor readers, failure evidence, raw line-ending changes,
Y checkout, and evidence remain preserved.

Root acceptance: wire R2 approved for implementation. Production Search.cell currently
requires exact grid alignment; nonaligned pads reject. Physical pad coordinates
remain explicit and direct distance uses the physical start and grid end. This
specification establishes no runtime result.

Approved prefix canonicalization: traceRowsSha256 is SHA256 of UTF8 bytes of
production traceGeometryFingerprint(). holeRowsSha256 is SHA256 of UTF8 rows
sorted by hole ID with Java String.compareTo/UTF16 order; each row is
index:id.length:id:fingerprint.length:fingerprint followed by LF. Index is the
zero-based sorted position and lengths are Java UTF16 units. Sorting uses a
fresh reference copy, never live layout vectors. The wire contains digests and
counts, not the trace/hole rows, so the consumer validates shape/equality and
lineage only. These are producer snapshot observations/source assertions and
do not claim independently recomputed full geometry or P08 qualification.
