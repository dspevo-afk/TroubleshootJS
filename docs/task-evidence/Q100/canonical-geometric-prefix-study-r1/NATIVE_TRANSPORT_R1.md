# P8 native transport contract R1

FROZEN before native producer implementation. This is a source/transport
contract; producer, compiler and BASE runtime gates remain NOT RUN.

## Bound source and invocation

The native entry point is
`com.lushprojects.circuitjs1.client.CanonicalGeometricPrefixStudy`, implemented
by the new `source/CanonicalGeometricPrefixStudy.java` and its new
`source/CanonicalGeometricReasonInspector.java`. Keep P5/P6 sources immutable.
The P8 study composes the genuine operation-1 callback in order P5, P6, P8;
`afterOwnersClosed` attempts all three owners even after an earlier failure and
preserves the first failure with later failures suppressed. P8 owns its own
bounded live/pristine/query views. Do not borrow P6 private query references or
create a second predicate evaluation.

The Study accepts exactly six positional arguments, in this order:

| Index | Meaning | Binding |
|---|---|---|
| 0 | mode | exactly `NULL`, `OBSERVE`, or `REASON_THROW` |
| 1 | runId | canonical lowercase UUID string |
| 2 | inputSha256 | SHA-256 of the authenticated BASE input-pins bytes |
| 3 | wireSha256 | qualified P6 wire SHA-256 carried by the BASE pins |
| 4 | sourceSha256 | SHA-256 of `native-source-inventory.jsonl` |
| 5 | outputDirectory | absolute path to an existing empty directory |

No `--canary` mode is added to this Study. The host separately runs the
unchanged P6 Study `--canary` and the frozen P8 literal canary. The old P6
`CALLBACK_THROW` remains the original P6 Study's five-argument invocation and
its original `FIRST_ENDPOINT_CALLBACK` receipt/exception contract; it emits no
P8 reason sidecar. Do not emulate that interrupted path with a P8 context.

`sourceSha256` is not the BASE input-pins hash, source-parent commit, or wire
hash. `inputSha256` is argv[2]; `sourceSha256` is argv[4]; `wireSha256` is
argv[3], the genuine qualified P6 wire digest retained for P5/P6 compatibility;
do not invent a new P8 wire digest. The source inventory is UTF-8 without BOM, LF-terminated including
the final LF, compact JSON with keys sorted on each line, lowercase hashes,
no absolute paths, and no self-hash. Its exact header is
`{kind:"header",schema:"tsj-q100-geometric-prefix-source-v1",sourceParent:"0891474cadc4b9e00d7911e81c124721a11efb87",rowCount:1084,productionCount:850}`;
then 1084 strictly path-sorted rows with exact keys
`kind="source",path,bytes,sha256,role`; then
`{kind:"footer",rowCount:1084}`. The host authenticates those rows against
BASE pins and current source bytes. Native code validates the digest syntax
and binds argv[4] into its output; it does not treat that digest as source
validation. Inventory maximum is 1 MiB.

The P8 study continues the approved BASE/SEPARATE identity: root
`9007199254740993`, placement `-6587958367974952701`, routing
`8445301674744508279`, request 0, attempt 4, pass 2, net `A_COIL_LOW`, QA.C to
DA.A, receipt ordinal 307 (global zero-based), lineage ordinal 77
(one-based-per-pass), H48. Prefix SHA-256 is
`6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb`;
graph SHA-256 is
`ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a`.
Execute exactly the 14 selected original full queries, parent ordinals
`[0,1,2,3,4,5,6,7,8,9,11,15,17,20]`, in that order. Their compact P8
ordinals are 0..13. No new seed, candidate, policy, or frontier is introduced.

## Files and canonical bytes

Each successful P8 operation adds exactly these two P8-owned files alongside
the unchanged P5/P6 parent artifacts:

1. `prefix-geometric-reasons.jsonl`
2. `canonical-geometric-prefix-index.json`

Both use create-new semantics, UTF-8 without BOM, compact JSON with unique
keys, lowercase SHA-256, and LF line endings. The JSONL has exactly 16 lines
for a completed operation: header, 14 queries, footer. Every line including
LF is at most 65,536 bytes. The sidecar and that operation's unchanged parent
artifacts, index, and captured streams together are at most 8 MiB. The outer
four-operation suite keeps each native operation independently bounded at
8 MiB; its separately bounded transport/metadata envelope is 8 MiB and the
derived suite aggregate is at most 40 MiB. These suite bounds never enlarge
the per-operation limit.

### Sidecar header

Exact keys and types:

```json
{"kind":"header","schema":"tsj-q100-geometric-reasons-v1","runId":"<uuid>","inputSha256":"<64 lowercase hex>","sourceSha256":"<64 lowercase hex>","mode":"NULL|OBSERVE|REASON_THROW","parentPrefixSha256":"<64 lowercase hex>","parentGraphSha256":"<64 lowercase hex>","selectedQueryOrdinals":[0,1,2,3,4,5,6,7,8,9,11,15,17,20]}
```

The schema and immutable fine-event record are the frozen P8 API
`API_SCHEMA_R1.md` (SHA-256
`915583D2594A74135BD3269C74A8C190CFB1D47C9D0AA28062B24578E17DE7FF`). The
header has no wireSha field; the P8 index and completion record bind it.

### Completed query rows

Every query object has exactly these top-level keys:
`kind="query",ordinal,parentOrdinal,identity,allowed,fineEvents,coarseEvents,cleanup,work`.
`ordinal` is 0..13; `parentOrdinal` is its original P6 ordinal. `allowed` is
a JSON boolean for a returned query and null only for an interrupted
REASON_THROW query. `fineEvents` contains only immutable P8 reason-record
fields; no synthesized cause, geometry, or second predicate result is added.
`coarseEvents` contains P6 event fields in observed order:
`ordinal,netId,startPadId,endpointPadId,fromGridX,fromGridY,toGridX,toGridY,direction,stage`.

`identity` is the exact parent P6 semantic query identity with keys:
`layer,fromState,toState,bit,selected,fromGridX,fromGridY,toGridX,toGridY,fromX,fromY,toX,toY,direction,netId,startPadId,rootPadId,reachedPthPadIds`.
Types are P6-compatible: integral coordinates/state fields, booleans for
`selected`, strings for identity IDs/layer, and a string array for
`reachedPthPadIds` (empty is valid). The row must correlate to the matching
P5 cut query, actual operation context, and coarse event. P5 cutQueries remain
identity authority; emitted Java rows/events are not an oracle.

`cleanup` has exactly these keys, all booleans except the six lowercase
64-hex SHA-256 strings:
`sourcePreserved,observersDetached,referencesDropped,ownersReleased,contextRestored,liveToPristineBeforeEqual,queryToPristineBeforeEqual,liveToPristineAfterEqual,queryToPristineAfterEqual,queryEffectsBeforeSha256,queryEffectsAfterSha256,sourceEffectsBeforeSha256,sourceEffectsAfterSha256,layoutGeometryBeforeSha256,layoutGeometryAfterSha256`.
The query-effect hashes come from actual `fixedGraphObservationEffects`,
including counters/conflicts. A row is serialized only after all observers
are detached and all query/source references are dropped; its cleanup booleans
are final projections, never optimistic flags set before `finally` completes.
Every successful row requires all booleans true, equal before/after
source-effect and layout-geometry hashes, and the same query-effect
before/after projection as its NULL/OBSERVE counterpart. Query-effect hashes
are also compared across those modes; source effects must remain unchanged.

`work` has exactly these non-negative integral counters:
`copyEntries,comparisonEntries,copyUnits,comparisonUnits,queryUnits,metadataUnits,copyNanos,comparisonNanos,queryNanos,metadataNanos,maximumUnitNanos,groupCount`.
Each selected query charges one `queryUnit`, including an interrupted query.
Callback time is included in `queryNanos`, never charged again as a nested
unit. A query row contains only work attributable to that query. Shared face
clone/compare and setup/export work is recorded exactly once in the footer's
`outsideQueryWork` object below. Metadata time covers measured work outside
query callbacks. Row/index/final-output gaps are bounded groups/units; no work
may be omitted or charged twice, and no unit may overlap a measured query.

NULL rows have empty `fineEvents`; OBSERVE rows have exactly one actual fine
reject and one matching coarse reject per selected query. Both modes make the
same full Router calls and preserve the same allowed/result/counters/source
effects/layout. Coarse witnesses may be attached to NULL for same-call parity.
No event is reconstructed by re-running a predicate.

### Completed footer

The final JSONL object has exactly:
`kind,status,complete,runId,inputSha256,sourceSha256,mode,parentPrefixSha256,parentGraphSha256,selectedQueryCount,queryCount,returnedQueryCount,fineEventCount,coarseEventCount,priorObservationUnits,p8ObservationUnits,totalObservationUnits,copyEntries,comparisonEntries,copyUnits,comparisonUnits,queryUnits,metadataUnits,setupUnits,exportUnits,copyNanos,comparisonNanos,queryNanos,metadataNanos,setupNanos,exportNanos,workNanos,maximumUnitNanos,groupCount,outsideQueryWork,cleanupKind,cleanupComplete,sourcePreserved,observersDetached,referencesDropped,ownersReleased,contextRestored`.

`outsideQueryWork` has exactly
`copyEntries,comparisonEntries,copyUnits,comparisonUnits,queryUnits,metadataUnits,setupUnits,exportUnits,copyNanos,comparisonNanos,queryNanos,metadataNanos,setupNanos,exportNanos,maximumUnitNanos,groupCount`;
all values are non-negative integers and its `queryUnits` is zero. Each shared
clone/comparison, setup, and export unit/entry/nanosecond is assigned once
either to one query row or to `outsideQueryWork`. For every shared field also
present in the footer, the footer equals the sum of all query rows plus
`outsideQueryWork`; no work is unassigned. `p8ObservationUnits` is the sum of
all copy, comparison, query, metadata, setup, and export units from those same
allocations. `workNanos` equals the non-overlapping P8 observer timing-category sum.
It covers only this inspector's measured work, not preceding P07 generation or
P5/P6 work. It cannot include the external host's later post-source audit.
The native wrapper separately measures all operation/preparation/export/output
work, including final sidecar/footer serialization and file/stream flushes as
defined in the authoritative accounting boundary below.

For NULL, `status="COMPLETE_NULL_CONTROL_ONLY"`, `complete=true`, selected,
query, and returned counts are 14; `fineEventCount=0`. For OBSERVE,
`status="COMPLETE_REASON_OBSERVATIONS_ONLY"`, `complete=true`, those counts
are 14; fine and coarse counts are the actual emitted counts (expected 14
each). In either mode, cleanup/source/owner/context booleans are true,
`cleanupKind="FULL_NATIVE_WALL_PLUS_POST_SOURCE"`, `queryUnits=14`, and
`totalObservationUnits=priorObservationUnits+p8ObservationUnits`. The P6
prior count already includes P5 and is added once. Footer totals must reconcile
to rows and work units. These observations do not establish physical
feasibility or unique trace ownership.

## Expected reason interruption

`REASON_THROW` runs the same actual P5/P6/P8 observer chain and selected query
order, but the P8 fine sink records the first reason and throws the exact
sentinel `FIRST_GEOMETRIC_REASON_CALLBACK` before a coarse event is emitted
for that query. The single partial query has `fineEvents` length 1,
`coarseEvents` length 0, `allowed:null`, `queryUnits=1`, and `returnedQueryCount`
does not count it. Preserve exact throwable identity, finish owner/context and
observer cleanup, compare source/effects/layout proofs, then classify it as
expected. Any cleanup failure or different throwable remains a failure.

The interrupted sidecar has exactly header, that first query, and footer.
Footer keys are the completed-footer keys above; values use
`status="EXPECTED_REASON_THROW_ONLY"`, `complete=false`, selected and query
counts 1, returned count 0, fine count 1, coarse count 0, `queryUnits=1`,
and the verified cleanup/source/owner/context booleans. It never qualifies as a completed
14-query observation. There is no normal index or completion trailer. The
single stderr marker is:

```text
CANONICAL_GEOMETRIC_PREFIX_EXPECTED_REASON_THROW <compact JSON>
```

Its JSON has exactly
`schema="tsj-canonical-geometric-prefix-exception-v1",runId,mode,inputSha256,sourceSha256,wireSha256,queryOrdinal,parentOrdinal,sentinel,exceptionClass,exceptionMessage,suppressedCount,cleanupKind,workNanos,cleanupComplete,sourcePreserved,observersDetached,referencesDropped,ownersReleased,contextRestored,partialSidecar,partialOperationReceipt,parentBlockers,parentStages`.
Exact expected values are `mode="REASON_THROW"`, `queryOrdinal=0`,
`parentOrdinal=0`, `sentinel="FIRST_GEOMETRIC_REASON_CALLBACK"`,
`exceptionClass="java.lang.IllegalStateException"`,
`exceptionMessage="FIRST_GEOMETRIC_REASON_CALLBACK"`, and
`suppressedCount=0`. `cleanupKind` is
`FULL_NATIVE_WALL_PLUS_POST_SOURCE`; `workNanos` is the inclusive non-negative
whole-wrapper monotonic elapsed value at marker preparation (not the P8-only footer work). All cleanup/source/owner/context
booleans are true only after their actual checks pass. Each file reference is
exactly `{path,bytes,sha256}` and binds the flushed file: `partialSidecar` is
`prefix-geometric-reasons.jsonl`, `partialOperationReceipt` is the actual
`op-1.jsonl`, `parentBlockers` is the completed `prefix-blockers.json`, and
`parentStages` is the completed `prefix-stages.json`. The reader validates
those bytes and the completed parent P5/P6 proofs before accepting the expected
throw: P5 blockers and P6 stages must each have their normal complete 22-query
projection, and the actual operation-1 receipt must match the P5/P6 operation
identity and receipt evidence. This is verified reader parity, not a status
claim copied from the marker. No success marker is written. Unexpected errors
retain their ordinary failure/stack trace and can never satisfy this
expected-exception record.

## Success index and process line

The index has exact schema `tsj-canonical-geometric-prefix-index-v1` and exact
keys:
`schema,mode,runId,inputSha256,sourceSha256,wireSha256,parentPrefixSha256,parentGraphSha256,selectedQueryOrdinals,parentStageIndex,reasonSidecar,queryCount,returnedQueryCount,fineEventCount,coarseEventCount,aggregateBytes,priorObservationUnits,p8ObservationUnits,totalObservationUnits,cleanupKind,cleanupComplete`.
`parentStageIndex` and `reasonSidecar` are file-reference objects with exactly
`path,bytes,sha256`; paths are the fixed relative names
`canonical-stage-index.json` and `prefix-geometric-reasons.jsonl`. Hashes cover
the exact flushed bytes. Index `aggregateBytes` is exactly the sum of this
operation's parent artifacts and reason sidecar; it excludes the index itself
and captured stdout/stderr, so it is non-circular. Parent artifacts include
`op-1.jsonl`, `fixed-prefix.jsonl`, `prefix-blockers.json`,
`prefix-stages.json`, and
`canonical-stage-index.json`. Index/source hashes do not
self-reference the index. The source inventory file is an authenticated input,
not a generated artifact.

On successful NULL/OBSERVE completion, after flushing and hashing all files,
stdout contains exactly one terminal marker line:

```text
CANONICAL_GEOMETRIC_PREFIX_COMPLETE <compact JSON>
```

The JSON has exactly `schema="tsj-canonical-geometric-prefix-complete-v1",mode,runId,inputSha256,sourceSha256,wireSha256,index,aggregateBytes,workNanos`;
`index` is `{path,bytes,sha256}` for the exact index bytes. Here
`aggregateBytes` is the total size of all native output files for this
operation, including the index and excluding captured stdout/stderr. After
this marker is flushed, the actual host/reader checks
`aggregateBytes + capturedStdoutBytes + capturedStderrBytes <= 8 MiB`; its
output guard measures after emission, so the marker itself cannot escape the
limit. No success line is allowed before final file flush/size/hash checks. A
normal run must finish all 14 calls and include the complete footer before
this marker.

## CLI negative controls and accounting

Preflight checks finish before generation or file mutation. For a deterministic
expected rejection, exit nonzero, stdout is empty, and stderr has exactly one
line:
`P8_GEOMETRIC_PREFIX_PREFLIGHT_REJECT <compact JSON>`.
That JSON has exactly
`schema="tsj-canonical-geometric-prefix-preflight-v1",phase="PREFLIGHT",code,argIndex`.
Codes are `ARGUMENT_COUNT` (argIndex null), `INVALID_MODE` (0),
`INVALID_RUN_UUID` (1), `INVALID_INPUT_SHA256` (2), `INVALID_WIRE_SHA256` (3),
`INVALID_SOURCE_SHA256` (4), `INVALID_OUTPUT_DIRECTORY` (5), and
`OUTPUT_DIRECTORY_NOT_EMPTY` (5). SHA arguments require exactly 64 lowercase
hex characters. A nonempty output directory remains byte-for-byte unchanged;
all preflight rejections create no output. An unexpected exception is not a
preflight rejection and retains its ordinary diagnostics.

Keep the established caps unchanged: 90-second operation, 640 actual/wrapper
groups, 5-second maximum complete group/unit, 32,768 P07-slice/combined
observation units, 1,000,000 expansions/clones, 48 holes, four flips, 4,096
clone entries per chunk, 8 MiB per operation, 64 KiB per JSONL line/index,
and 120-second cleanup. Compile/native/reader/outer limits remain
60/480/180/1,800 seconds. Include receipt, stream, flush, hash, and final-output
work in the existing accounting; a throw does not erase work. Old P6
CALLBACK_THROW retains its pinned P6 accounting semantics.

## Pinned source evidence and validation status

Frozen CANARY API schema SHA-256:
`915583D2594A74135BD3269C74A8C190CFB1D47C9D0AA28062B24578E17DE7FF`.
Source-only semantic seam memo SHA-256:
`CBDEE663AD41013FCF2ED8BB37D3F91542A2DE43390F8F1C0207E9D821666965`.
Original P6 Study SHA-256:
`9936A10CB7F6FC90BDFA2B3DDD99F318264D3D25A6954A169F8C91702C2C830B`.
Original P6 Inspector SHA-256:
`054979FC0BE665319C4C4CDA0ECEC39FF5FFD10509487F537F243CB86A417803`.
P8 qualified Router/rules overlays are
`3bc847e015e1b379dcafcb1e39eb4964e65e7a57d6ef06ed2c710cca1ff48f2f` /
`9e59ae5969b593617042bb2a571aab4037235a13f06e03379d341eaf80ae94d9`.
The active source parent is `0891474cadc4b9e00d7911e81c124721a11efb87`.

This draft was produced by reading the frozen plan/API/seam contracts and
source-only findings. No native code was changed, no source was compiled, no
runtime, actual capture, test, or protected data was read or run. Status:
transport contract draft only; producer and all runtime gates NOT RUN.

## Authoritative accounting and file boundary

This section resolves the draft's scope ambiguities before code. The unchanged
P6 parent projection emitted by the new P8 study consists exactly of op-1.jsonl,
fixed-prefix.jsonl, prefix-blockers.json, prefix-stages.json and
canonical-stage-index.json. There is no canonical-blocker-index.json. Complete
P8 adds only prefix-geometric-reasons.jsonl and
canonical-geometric-prefix-index.json: exactly seven data files. REASON_THROW
emits exactly four data files: partial op-1.jsonl, complete prefix-blockers.json,
complete prefix-stages.json and partial prefix-geometric-reasons.jsonl. It emits
neither full fixed-prefix transport nor either normal index. Old CALLBACK_THROW
has only its original partial op-1.jsonl, with stages inline in its old marker.

The P8 footer owns observation work only. Row work plus outsideQueryWork
allocates every P8 clone/comparison/query/metadata/setup/row-export unit once.
Category nanos form a non-overlapping partition of that owned measured work;
query callbacks are inside queryNanos. The P5/P6 preceding work is not a P8
setup unit. Footer priorObservationUnits is the actual hierarchical P6 total
(including P5 once); add P8 units once under32768. Non-query metadata and
afterOwnersClosed checks have bounded measured units, not free opaque work.

Footer groupCount counts its own bounded observation groups; these are nested
inside the existing actual selected ADVANCE group, not additional scheduler
advances. The existing640 limit counts actual receipt GROUP rows plus the
three native wrapper groups. Do not add nested observation groups as fictitious
advances, and do not omit the selected callback's full duration from its actual
ADVANCE group. Each observation unit and complete outer group remains <=5s.

The unchanged parent Study wrapper measurement design owns preparation,
graph/artifact export, and post-index/output groups. It includes actual P07
work, preceding P5/P6 callbacks, P8 callback work and owner/context handling.
The native success marker workNanos is its measured whole-wrapper work before
marker output, less only actual ArmResult cleanup, under the existing90s
bound. It is NOT the P8-only footer workNanos. The wrapper checks actual total
work again after final serialization/flush/output, including those operations;
nonzero exit overrides any earlier marker. Its maximum/report scalars cover
completed intervals; source guards plus full native wall charge cover the
interval measured only after its own emission. Do not invent its duration.

Footer construction/final hash/index/stream emission are measured in wrapper
export/post groups and actual process wall, rather than pretended already
known in the footer's self-serialization. The exception marker workNanos is
whole monotonic native elapsed through marker preparation; its output guard
checks the final emission too. Neither throw receives any host work credit.
External source-audit and full final host/receipt/stream cleanup are measured
only by the actual host and parent, not by Java. The parent checks the120s
conservative cleanup upper bound through returned selected-host exit.

Normal aggregateBytes definitions above are unchanged: index excludes itself
and streams; completion includes all seven files, excludes streams. Actual
host/reader add exact streams and enforce8MiB per operation. Exception four
files plus actual stderr marker/stdout also stay under8MiB. No self-hash or
recursive byte-size fixed point is needed.

CONTROL_CASES_R1 fixes the exact early CLI fixtures and semantic controls. The
new Study has exactly the three six-argument modes and no duplicate canary API.
The wire is the genuine P6 qualified wire digest, never a new source digest.
All remaining scope exclusions and actual failure preservation still apply.
