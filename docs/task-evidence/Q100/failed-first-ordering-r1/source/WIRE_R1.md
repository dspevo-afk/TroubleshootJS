# Failed-first native study wire R1

Status: native-only diagnostic contract. The wire file is an input to the
authenticated build manifest; it does not contain its own SHA. The launcher
supplies the SHA-256 of these exact UTF-8, no-BOM, LF bytes.

## Invocation and files

The Java entry point is `com.lushprojects.circuitjs1.client.FailedFirstRoutingStudy`.

- Canary: `--canary` (one argument).
- Authored diagnostic: `OBSERVE UUID FINAL_RUN_MANIFEST_SHA256 WIRE_CONTRACT_SHA256 EMPTY_ABSOLUTE_DATA_DIR` (five arguments).
- `UUID` is lowercase canonical hyphenated hexadecimal. Both hashes are lowercase 64-character SHA-256.
- The data path must be an existing empty absolute real directory. Reject symbolic links, junctions/reparse paths, nonempty directories, malformed arguments, and hashes before invoking the old ArmRun or creating files.
- Expected preflight rejection prints exactly `FAILED_FIRST_INPUT_REJECTED:<CODE>\n` to stderr and exits 1, without a stack trace or file mutation. Codes are `ARGS`, `UUID`, `MANIFEST_SHA`, `WIRE_SHA`, `DATA_PATH`, and `DATA_NONEMPTY`.
- A completed authored run creates only `op-1.jsonl` and `result.json`. `op-1.jsonl` is emitted by a fresh unchanged `ActualGeneratorBranchOrderStudy.ArmRun` operation 1 under the supplied run ID, manifest SHA, and wire SHA. It is never copied from an older run or re-authored.
- `op-1.jsonl` is byte-preserved. It has the original P07 JSONL schema and must be independently checked against the operation-1 summary below. The producer retains the original operation-1 line/file bounds. `result.json` is compact UTF-8 JSON plus one LF, no BOM, a single JSON line no longer than 1 MiB, and at most 8 MiB as a file. No absolute paths or private workspace paths are serialized.

The original operation-1 bounds are65536 bytes per JSONL line and8MiB for
that receipt; the old32MiB bound was aggregate study size and is not used here.
- Success writes exactly one stdout JSON line with keys `schema,status,
  resultSha256,resultBytes,elapsedNanos,finalExportNanos,totalGroups,totalUnits,
  maxGroupNanos,maximumUnitsPerGroup`. The marker schema is
  `tsj-failed-first-marker-v1`; status is `COMPLETED_DIAGNOSTIC`. SHA/bytes bind
  the closed `result.json`. `elapsedNanos` is process elapsed through final
  result reread and marker preflight; `finalExportNanos` times final JSON
  serialization, write/flush, reread/hash, and marker preflight. The final
  export is a guarded complete group and must be at most 5 seconds. No success
  marker is printed before all files and guards close.
- Runtime, owner, validator, work-bound, cleanup, or serialization failures are not expected-negative preflight. They exit nonzero, preserve the original failure, and do not print a success marker.

## Result schema

The top-level object has exactly these keys and types:

```json
{
  "schema":"tsj-failed-first-routing-v1",
  "mode":"OBSERVE",
  "runId":"lowercase UUID",
  "finalRunManifestSha256":"lowercase SHA-256",
  "wireContractSha256":"lowercase SHA-256",
  "operation1":{},
  "reference":{},
  "source":{},
  "capturedOrder":{},
  "control":{},
  "failFirst":null,
  "orderChange":"MOVED",
  "work":{}
}
```

`failFirst` is null only when the actual failed net was already first in the
captured vector; in that case `orderChange` is `NO_ORDER_CHANGE` and no duplicate
trial is executed. Otherwise `orderChange` is `MOVED` and `failFirst` has the
same run schema as `control`. The producer emits no successful result when the
selected actual capture, owner state, vector identity, cleanup, or bounds cannot
be verified.

### Original operation 1 and source proof

`operation1` is the unchanged `CopperFreeBranchStudy.armJson` object with
exact keys `operation,mode,runId,sourceManifestSha256,wireSha256,receiptPath,
receiptBytes,receiptSha256,elapsedToCloseNanos,maxFullGroupNanos,cleanupNanos,
ownersReleased,contextRestored,groupCount,p07OwnerSteps,callbackUnits,
requestIndex,selectedAttempt,rootSeed,placementSeed,routingSeed`. `operation`
is 1 and `mode` is `AUTHORED`; seed values are decimal strings to preserve signed
longs. Its SHA/bytes describe this invocation's `op-1.jsonl`.

`reference` is the unchanged `CopperFreeBranchStudy.referenceJson` object with
exact keys `receiptOrdinal,groupOrdinal,lineageOrdinal,pass,netId,startPadId,
rootPadId,terminalReason,prefixGeometrySha256,prefixGeometryBytes,
prefixHoleCount,prefixViaCount,reachedPadIds,reachedPthPadIds,reachedPadCount,
reachedPthPadCount`. It retains global receipt ordinal 307 versus local lineage
ordinal 77, pass 2, QA.C to DA.A, A_COIL_LOW, the NO_PATH prefix SHA, 48 holes/
VIA, and the root-only reached set. `source` is the unchanged
`CopperFreeBranchStudy.sourceJson` object with exact keys
`templateGeometryBeforeSha256,templateGeometryAfterSha256,
templateEffectsBeforeSha256,templateEffectsAfterSha256,detachedGeometrySha256,
detachedGeometryAfterSha256,templateTraceCount,templateHoleCount,padLands,
sourceUnchanged`. It retains the original template geometry/effect hashes,
pad-land rows, and zero-trace/zero-hole detached template proof. The callback
decorator forwards both terminal-prefix and owner-closed callbacks to that
genuine old Capture and preserves these objects without rewriting the original
receipt.

### `capturedOrder`

This object has exactly keys `pass,failedNetId,priorPromotedNetId,netIds,
liveSearchIndex,owner`:

- `pass` integer 2;
- `failedNetId` string, copied from the selected terminal snapshot and required
  to equal the live `Search.currentNet`;
- `priorPromotedNetId` nullable string, copied from the actual pass-2
  `Search.promotedNet` (the prior pass's promotion owner);
- `netIds` string array, copied from the live `Search.nets` in order;
- `liveSearchIndex` integer, the actual `Search.netIndex` at selected failure;
- `owner` object with exactly `policy,maximumExpansions,expandedAtFailure,
  maximumVias,viaCost,policyTransitions,policyVias,branchOrderStrategy,
  netOrderStrategy,roleWidths,orderingStream`, read from the live owner.
  Authored acceptance requires
  `FULLER_TWO_LAYER` and role widths. Preserve the captured `orderingStream`
  value; fresh attempts use the normal legacy `advanceSlice` Session interface
  (`orderingStream=false`). If those flags differ, disclose the instrumentation
  mode difference and compare every routing-relevant setting, prior promotion,
  and owner-computed pass-2 vector before routing. The per-request expansion
  maximum and current consumed count remain distinct.

The vector must be unique and exactly equal to the board's complete net set.
The original prior promotion is seeded in both fresh Sessions. Each must let
the native owner build its pass-2 Search order and match this captured vector
before any net starts. The control leaves that vector untouched. The trial
stable-moves only `failedNetId` to index 0, preserving all other relative order.
The trial is an explicit new order-vector intervention, not a fabricated
pass-2 NET_ORDER row.

### `control` and `failFirst`

Both run objects have exactly keys `kind,initial,ordering,outcome,finalizers,
layout,work`.

- `kind`: `CAPTURED_ORDER_CONTROL` or `FAIL_FIRST_TRIAL`;
- `initial`: exactly keys `pass,passes,expanded,maximumExpansions,maximumVias,
  priorPromotedNetId,policy,viaCost,policyTransitions,policyVias,
  branchOrderStrategy,netOrderStrategy,roleWidths,capturedOrderingStream,
  sessionOrderingStream,instrumentationModeDifference,
  routingRelevantSettingsMatch,traceCount,holeCount`; `pass` 2, `passes` 0,
  `expanded` 0, captured
  `maximumExpansions`, `maximumVias`, `priorPromotedNetId`, `policy`, `viaCost`,
  `policyTransitions`, `policyVias`, `branchOrderStrategy`, `netOrderStrategy`,
  actual `roleWidths`, captured `orderingStream`, fresh
  `sessionOrderingStream=false`, `instrumentationModeDifference`,
  `routingRelevantSettingsMatch`, and zero `traceCount`/`holeCount`;
- `ordering`: exactly `capturedNetIds,computedNetIds,requestedNetIds,
  traversedNetIds,orderMatchesCapture,searchCount`; `capturedNetIds`, owner-computed `computedNetIds`,
  `requestedNetIds`, actual `traversedNetIds`, `orderMatchesCapture` boolean,
  and `searchCount` (must be 1);
- `outcome`: exactly `disposition,reason,complete,ownerComplete,pass,passes,
  expanded,maximumExpansions,errorClass,errorMessage`; `disposition` (`FOUND`, `NO_PATH`, `CAP_REJECTED`, or
  `QUALITY_REJECTED`), exact nullable `reason`,
  `complete` is true only for `FOUND`; `ownerComplete` is the actual
  `Session.complete` flag (true for every returned owner result), terminal `pass`, terminal `passes`, `expanded`,
  `maximumExpansions`, and nullable `errorClass`/`errorMessage`;
- `finalizers`: exactly `routingGeometry,allNetConnectivity,twoLayerRules,
  routeQuality,viaCap`; values are `PASS`, `NOT_RUN`, or `REJECTED`. For
  `FOUND`, all are `PASS`. For `QUALITY_LIMIT`, the native Session has already
  passed `validateRoutingGeometry` (which includes board/route physical and
  connectivity checks) and `PcbTwoLayerRules.validate`; report those three as
  `PASS`, route quality as `REJECTED`, and via cap as `NOT_RUN`. For
  `net:BRANCH_QUALITY_LIMIT`, `NO_PATH`, or any cap rejection, all five are
  `NOT_RUN`; the owner did not return a completed candidate. Unexpected
  finalizer failures remain process failures and are never serialized.
- `layout`: null unless the native owner returned `FOUND`; otherwise exact
  physical operands and routing output. It contains `geometrySha256`, `width`,
  `height`, `outline`, `partsTray`, `pads`, `components`, `regions`, `labels`,
  `placementConstraints`, `clearanceRules`, `traceCount`, `holeCount`,
  `viaCount`, `traces`, and `holes`, with exactly those keys. Rectangles are
  integer `[x,y,width,height]` arrays; points are `[x,y]` arrays. `outline`
  and `partsTray` are rectangles.

  Pad rows have exactly `padId,netId,componentId,terminalId,x,y,padBounds,
  probeBounds,escapeDirection,escapeLength,attachment,mountingSide,exposure,
  topCopper,bottomCopper`; direction is `[dx,dy]` and copper flags are booleans.
  Component rows have exactly `componentId,packageId,poseFingerprint,x,y,
  width,height,rotation,mountingSide,bounds,bodyBounds,keepOut,
  routingCourtyard,selectionEnvelope,dragEnvelope,geometryContractVersion,
  geometryVariantKey,geometryTransformKey,geometryCanonical,underpass,
  raisedCrossover,isolationBodyCanonical,terminals`. Optional geometry values
  are JSON null only when absent in the actual owner object.
  `poseFingerprint` and `geometryCanonical` are strings: actual pose
  fingerprint and placed `geometryFingerprint()`, respectively. `underpass`
  is null or a rectangle; `raisedCrossover` is null or its actual fingerprint
  string; `isolationBodyCanonical` is null or its actual canonical string.
  Terminal rows have exactly `terminalId,terminalDomain,padPoint,padBounds,
  boardPadProbeCenter,boardPadProbeBounds,connectedLeadEnd,connectedLeadBody,
  connectedLeadBounds,connectedProbeCenter,connectedProbeBounds,liftedLeadEnd,
  liftedLeadBody,liftedLeadBounds,liftedProbeCenter,liftedProbeBounds,
  escapeDirection,escapeLength,attachment`; nullable operands remain null when
  the actual package does not supply them.

  Region rows have exactly `id,label,componentIds,bounds`. Label rows have
  exactly `id,text,bounds,baselineX,baselineY,fontSize,bold,targetPadId`.
  `placementConstraints` has exactly `physicalPolicyId,physicalPolicyVersion,
  placementDensityVersion,placementPackingVersion,requiresTwoLayerRouting,
  routingLayer,traceWidthProfileIdentity,traceWidthProfileCanonical,parts,
  barriers`. Part rows have exactly `componentId,regionId,regionLabel,domainId,
  anchor,accessMargin,terminalDomains,domainCourtyards`; terminal-domain rows
  are `{terminalId,domainId}`, domain-courtyard rows are `{domainId,bounds}`,
  and barrier rows are `{firstDomain,secondDomain,clearance}`.
  `clearanceRules` has exactly `traceWidth,copperHeight,minimumVisibleClearance,
  minimumCenterlineClearance,routingGridClearanceCells,roleWidths,
  maximumTransitions,maximumVias`; the last two are copied from the captured
  policy owner.
  Trace rows have exactly `sourceId,netId,startPadId,endPadId,layer,exposure,
  width,x,y`, where endpoint IDs are nullable and `x`/`y` are ordered integer
  arrays. Hole rows have exactly `id,netId,kind,exposure,x,y,drillRadius,
  landRadius`, with nullable net IDs. All values come from the returned owner
  layout and its actual board/constraints; a geometry hash alone does not
  replace these operands.
  - `work`: exactly `advanceCalls,ownerUnits,wrapperUnits,copyEntries,copyChunks,
  groups,maxGroupNanos,maximumUnitsPerGroup`. `ownerUnits` is the sum of actual
  `Session.lastWork()` values; wrapper units cover measured adapter work that
  is not already counted in owner units.

Both fresh runs use independent Session objects and candidate copies from the
same detached zero-copper/zero-hole template. They use the captured actual
request policy, `maximumExpansions`, `maximumVias`, `viaCost`, and ordering
strategies. Their budgets both start at zero; this is a fair fresh-budget pair,
not original pass-2 cumulative-budget parity. On exhaustion pass 2 becomes pass
3; with `ORDERINGS=3` the following owner unit must finish rejection without
creating another Search. No fallback, retry, or pass-3 search is permitted.
`FOUND` requires the native Session's complete original finalizer sequence and
its returned layout. Exhaustion/quality/cap results have no layout and do not
claim complete-route validity. Any other finalizer exception is
`FINALIZER_ERROR`, not NO_PATH or FOUND.

### `work`

`work` has exactly `elapsedCutoffNanos,cleanupNanos,groupsBeforeExport,
wrapperGroupsBeforeExport,
`maxGroupNanosBeforeExport`, `maximumUnitsPerGroupBeforeExport`,
`unitsBeforeExport`, `originalOwnerSteps`, `originalDiagnosticUnits`,
`originalCaptureUnits`,
`extraTerminalCallbackUnits`, `controlOwnerUnits`, `controlWrapperUnits`,
`trialOwnerUnits`, `trialWrapperUnits`, `metadataUnits`, `copiedEntries`,
`copyChunks`, `copyChunkLimit`, `copiedEntryLimit`, and `cleanup`.

The additive unit formula is
`operation1.p07OwnerSteps + originalDiagnosticUnits + operation1.callbackUnits + extraTerminalCallbackUnits + control.ownerUnits + control.wrapperUnits + (failFirst == null ? 0 : failFirst.ownerUnits + failFirst.wrapperUnits) + metadataUnits`.
`metadataUnits` is the measured new-wrapper Meter total outside the per-run
owner/wrapper scopes: total Meter units minus the control and trial
`ownerUnits + wrapperUnits`. It includes receipt digest/GROUP parsing, source
checks, order-transform work, and result metadata construction. Before export,
an explicit bounded tree walk charges one unit for every JSON value, one for
every map key, and ceil(UTF-8 bytes/4096) extra units for each key/string value
(zero extra for an empty string). Root depth is zero, every child value adds
one depth, maximum depth is64, and visited values are at most1048576. The
consumer independently requires metadataUnits to cover this tree minimum. Each
run-scoped Meter charge is assigned exactly once to that run's owner or wrapper
counter. Wrapper units count explicit field/record work, copied entries, and
UTF-8 hash/serialization work in 4096-byte chunks; native `Session.lastWork()`
is charged only to owner units. Every `advanceSlice` call also charges one
wrapper unit for its fixed, bounded live-state polling/dispatch operation.
Consequently each run's `wrapperUnits` is at least `advanceCalls`; owner work
and adapter bookkeeping are distinct even when the native owner advances one
unit at a time. Copy, fingerprint, order, output and exceptional work retain
their separately charged units.
`originalDiagnosticUnits` is the sum of the actual operation-1 GROUP
`diagnosticUnits` parsed from the genuine receipt, including a measured zero
sum; it is not inferred from callback units. The operation-1 GROUP rows are
parsed from the genuine receipt; their `p07Steps + diagnosticUnits`, old
callback units, and added terminal callback units are reconciled and guarded
as one actual group. New owner calls use
`lastWork()` exactly once. Search expansions remain a distinct per-request
budget counter and are not added a second time as work units.

`copyChunkLimit` is 4096 and `copiedEntryLimit` is 1048576. Before each
synchronous native layout copy, all actual copied categories are counted; a
whole copy must fit one 4096-entry chunk or the run rejects before that copy.
Categories include pads, components, traces, holes, labels, regions, and actual
Search pad/component arrays. `copiedEntries` and `copyChunks` include original
capture additions, both fresh Session templates/candidates, order-vector
copies, and output row copies. No clone/copy is omitted from these totals.

The aggregate ledger is reserved before these copies. Fingerprint calls also
count their component/pad/label/trace vectors, region memberships and both trace
coordinate arrays. A component withT terminals copiesT*T+4*T terminal entries:
the loop-condition getTerminals call occursT+1 times. Legacy pad-land export
copies11 entries per pad across the pad vectors, row list and coordinate
arrays/lists. The wrapper prebounds the selected legacy callback before calling
it; its existing callback-unit contract is retained, and the added census work
is charged as additional terminal-callback work. Per-run output order lists and
the stable move's set/list copies are included. Byte-buffer work uses the
separate4096-byte hash/encoding accounting and is not a geometry-entry count.

`elapsedCutoffNanos` ends immediately before final result serialization. The
final export group is measured through serialization, write/flush, reread/hash,
and marker preflight; its real duration is reported in stdout
`finalExportNanos`, not recursively embedded in `result.json`. The native
process guard measures through that same cutoff plus final export and checks
the complete operation against 90 seconds. `cleanupNanos` includes the original
ArmRun cleanup and both fresh-owner reference-drop intervals. `cleanup` is an
object with exactly `originalOwnersReleased,originalContextRestored,
controlReferencesDropped,trialReferencesDropped,sourceUnchanged,
allReferencesDropped`, all booleans derived from observed close/drop checks.
The marker's `totalGroups` is `groupsBeforeExport + 1`; `totalUnits` is
`unitsBeforeExport + finalExportUnits`, where final export charges five
4096-byte work passes over the final JSON byte length (serialization, UTF-8
encoding, write, reread, and digest) plus three fixed marker/file preflight
units. `maxGroupNanos` and `maximumUnitsPerGroup` are maxima of the pre-export
fields and the final export's measured duration/units.

`wrapperGroupsBeforeExport` is the measured meter-group count excluding the
separately reported `control.work.groups` and `failFirst.work.groups`; it
contains original-receipt processing, source-integrity checks, and result
metadata. The exact census is
`groupsBeforeExport = operation1.groupCount + control.work.groups +
(failFirst == null ? 0 : failFirst.work.groups) + wrapperGroupsBeforeExport`.
The final export is one additional guarded group. The combined old-operation,
fresh-owner, and wrapper groups including final export must be at most 640;
every complete group is at most 5 seconds and 32768 units.
The process maximum is 90 seconds, cleanup maximum 120 seconds, and all original
P07 expansion, 48-hole, and four-flip limits remain unchanged. No post-cutoff
work credit is permitted. Any failed check suppresses the success marker.
Unexpected runtime or required-finalizer errors are hard process failures and
must not be serialized as a completed outcome or receive a success marker.
Only owner-completed NO_PATH, cap, and route-quality rejections are normal
diagnostic dispositions.

## Canary

`--canary` uses only the literal open/blocked fixtures and active-branch
exception fixture. Its single stdout object has exactly keys
`schema,status,openPositive,blockedNoFallback,activeExceptionCleanup,
stableTransform`; schema is `tsj-failed-first-canary-v1`, status is `PASS`. Each
`openPositive` has exactly `{run}`; `run` uses the full run schema above with
`kind=CAPTURED_ORDER_CONTROL`, `outcome.disposition=FOUND`, two traversed net
IDs, role widths true, and actual complete `layout`/finalizer evidence.
`blockedNoFallback` has exactly `{run,noFallback}`; it uses the full run schema,
with `outcome.disposition` `NO_PATH` or `CAP_REJECTED`, `ordering.searchCount=1`,
`outcome.pass=3`, `outcome.passes=1`, `outcome.ownerComplete=true`,
`outcome.complete=false`, `layout=null`, and `noFallback=true`.
`activeExceptionCleanup` has exactly `exceptionClass,message,sameThrowable,
callbackCalls,expandedAtThrow,searchPresent,branchPresent,
ownerDiagnosticFailureSame,sessionReleased,searchReleased,candidateReleased,
layoutReleased,boardReleased,referencesReleased,cleanupNanos`. Its required
literal exception class/message are `java.lang.IllegalStateException` and
`FAILED_FIRST_ACTIVE_BRANCH_CANARY`; `sameThrowable=true`, `callbackCalls=1`,
`expandedAtThrow>=1`, all presence/identity/release booleans are true, and
`cleanupNanos` is a positive integer. `stableTransform` has exactly
`source,moved,alreadyFirst,duplicateRejected,missingRejected,unknownRejected,
unrelatedOrderPreserved`; the first three are string arrays and the rest are
booleans. Its literal source is `["NET_A","NET_B","NET_C","NET_D"]`,
moved is `["NET_C","NET_A","NET_B","NET_D"]`, and alreadyFirst equals
source. All three invalid-transform checks and the stable relative-order check
must be true. No authored IDs or raw production data appear.
Canary success is structural owner evidence only, not evidence about the
authored board.
