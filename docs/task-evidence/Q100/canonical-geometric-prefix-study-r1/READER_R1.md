# P8 BASE strict-reader contract R1

FROZEN input/native semantic consumer contract before implementation. Exact
host field schemas come from the final HOST_R1.md and must exist before its
outer-host parser is implemented or any reader runs. PLAN_R1 controls scope;
NATIVE_TRANSPORT_R1 and CONTROL_CASES_R1 fix actual wire fields and fixtures.
The focused CANARY is a prerequisite, not a BASE observation.

## Inputs and invocation

Proposed selected-reader invocation, one invocation per completed host bundle:

```text
<pinned-python.exe> -I -B <read_geometric_prefix.py> <pins-path> <pins-sha256> <output-root> <parent-receipt-path>
```

Require the actual Windows process image to equal the pinned Python executable;
`sys.executable` and App Execution Alias are not identity evidence. Require
isolated mode and bytecode suppression before any other import. Authenticate
process helper bytes from its authenticated `SOURCE_ROW` in the source inventory (not from `files`), then `compile`/`exec` those same bytes
in memory and cache the modules; no sibling imports, path search, or
post-authentication reopen is allowed.

Proposed pins schema is `tsj-q100-geometric-prefix-study-inputs-v1`, with exact
top-level fields `schema,repositoryRoot,parent,canaryPrerequisite,p6Context,
sourceInventory,roles,files,toolPaths`. `sourceInventory={path,sha256}` names the
canonical JSONL inventory; its file is also an exact `files` row. `sourceRows`
are not duplicated inline. Roles include the new native study/inspector,
reader/host, and input maker/auditor, plus the process helper. The helper resolves to exactly one source-inventory `SOURCE_ROW`; every other extra role, prerequisite, and tool path resolves to exactly one bounded `files` row. No globs or directory rows.
Reject duplicate JSON keys, unknown keys, bool-as-int, nonfinite numbers,
noncanonical hashes/paths, reparse ancestors, and changed files. The host
creation preflight rejects an existing or source-overlapping output root before
creation. The consumer reads the genuine existing host-created root and checks
its containment, non-reparse identity, and exact census.

Authenticate the immutable CANARY R9 pins and qualification by their accepted
hashes; authenticate the original P6 pins and context without changing their
root or presenting BASE data as a P6 run. The source inventory is canonical
JSONL with schema `tsj-q100-geometric-prefix-source-v1`, parent
`0891474cadc4b9e00d7911e81c124721a11efb87`, 1084 sorted source rows and a
1084-row footer. Independently compare every path/role/byte-count/hash with the
accepted CANARY R9 `sourceRows`, reread all 1084 files from the BASE repository,
and require exactly 850 production files and zero changed rows. Inventory hash
is passed to the six-argument BASE study and bound separately from the BASE
pins digest. No source changes or production-renewal claim is accepted.

## Host, process, and output envelope

The host runs only the pinned Windows PowerShell 5.1 script with the exact argv
frozen in its host contract. Its parent receipt is a three-field object
`{schema,elapsedMs,process}`; the raw maintained process receipt is preserved.
Bind selected-host PID/start ticks, actual executable image and hash, script
path/hash, pins, output root, and argument vector. Require actual exit 0,
proven termination, empty stderr, and the exact success marker. An initial
marker or provisional `host-result.json` is never acceptance.

Require exact host-result keys and exact indexed output census from the later
frozen host/index schema. Bind every process role to one raw bounded-process
receipt and exact copied stdout/stderr bytes. Preserve receipts unchanged and
check PID/start ticks, executable, arguments, exit status, termination proof,
process-root/log cleanup, and role-specific wall limit. Successful roles must
exit zero; the two intentional callback-throw invocations must instead have the
frozen nonzero/marker/stream result and proven termination. Auditor/compiler /
source-check roles and native mode roles must be complete, unique, ordered, and
have no unlisted children. Do not synthesize a process receipt when the bounded
API fails to return one.

The output index must have canonical framing, unique ordered paths, exact mode
and run identity, byte counts and SHA-256 values, a required footer/EOF, and no
missing, duplicate, unreferenced, or extra files. Recompute every hash from
stable bounded reads; bind all internal artifact references back to the index.
The transport must pass the authenticated P4 decoder (canonical chunk ordering, base64, lengths, hashes, line endings, and EOF).
Keep the existing 8 MiB cap separately for each of the four native operations,
including that operation's payloads, streams, sidecars, and index. Keep host/source
metadata and evidence under a separate 8 MiB cap. Report the exact non-deduplicated
grand total (at most 40 MiB across the four operation budgets plus metadata); do not
raise an individual cap or omit files. Preserve the 64 KiB structured-line/index limit.
Keep raw native/PowerShell streams as captured; any CRLF normalization is only a local parse view and never rewrites a
receipt or indexed artifact. Verify source/handle identity with common
`stat`/`fstat` fields across APIs and ctime stability only within each API.

## BASE semantic validation

For each complete NULL/OBSERVE run, parse the actual operation using P4
`_operation` with the new operation bytes/index entry, actual run ID, and BASE
pins SHA; require the pinned candidate, seeds, target, variant, epochs, and pad
census. Decode actual transport with P4 `_capture_transport`; bind graph/facts
lengths and hashes, operation, source, geometry, H48, and independent zero-VIA
diagnosis. Keep feasibility/admission unknown. Re-derive the frozen P5 cut from
the actual graph/facts with `_validate_cut_document`; its `cutQueries`, not Java
or observed rows, are identity authority. Require the unchanged 14 selected
ordinals `[0,1,2,3,4,5,6,7,8,9,11,15,17,20]` from the original 22 queries.

Use authenticated P5 `_validate_attribution` in its actual CAPTURE context and
P6 `_validate_stage_payload` against the new BASE run ID/pins SHA, actual facts,
operation, P5 attribution/totals, cut, work, and authenticated branch reader.
Retain P4/P5 capture, geometry, route, and graph parity as separate checks; use
P6's NULL/OBSERVE semantic parity only for the fields it normalizes. Do not call
P6 `validate_run` with relabelled pins or forge its historical root/context.
Keep P5/P6 totals hierarchical: P6 already includes P5 prior units, so count
that total once before adding P8 work.

P8 reason rows are a separate new-source validator. Bind the sidecar header to
`inputSha256 == BASE pins SHA` and `sourceSha256 == source-inventory SHA`; bind
its run, mode, wire, graph, prefix, and cut identity to the actual run and
independently rederived cut. Validate exact row keys/types and each field the
frozen Router reason API defines for that site: site, stage, coordinates,
reported owner/pad/net, reservation/default sentinels, domain bounds, escape
flags, and underpass state. Require only fields that API emits for that site;
null/default values stay literal and unobserved owner fields are never enriched.
Use P5-validated geometry/facts/census as independent authority for pad IDs,
nets, courtyards, and array ordinals where its projection preserves them. The
fine record's source ordinal is an actual callback field; do not reconstruct
owner-array order from sorted geometry. If the P5 projection loses order needed
to validate an array index, record that limitation or freeze and validate a
bounded actual ordered descriptor census; do not invent an index oracle. Each
reason row identifies an actual query ordinal in cut order and at most the first
fine rejection observed during that single call. Do not re-evaluate predicates,
infer a reason from coarse stages, collapse null/mixed reservation states, or
invent an event for an allowed/unobserved query.
Require full source/prefix/observer-detach/reference-drop proof after cleanup.

NULL has no fine observer events and is the route/counter/effect control.
OBSERVE contains the completed actual selected query observations; compare its
P4/P5/P6 route and result projections against NULL and retain the exact 14 cut
identities. Do not require a presupposed reason distribution: report only
reasons the native callback emitted. Do not equate a fine first-owner record
with unique ownership or physical feasibility.

The old P6 `CALLBACK_THROW` remains a separate actual P6-study invocation inside
the new BASE run/pins context. Validate its exact original P6 marker, partial
`op-1.jsonl` through branch 307 without a footer, expected throwable, lifecycle,
work, and zero prior observation units. Reuse P6 `_exception_marker` /
`_exception_run` only with the actual BASE `info`/pins SHA, genuine P5/P4
capture context, and a genuinely completed NULL run from this BASE suite. Do not
validate it with the old full P6 host envelope, fabricate historical context,
or expect a P8 reason sidecar on this path.

The new `REASON_THROW` is distinct: require the normal preceding P5/P6 chain,
one first fine callback and zero coarse callbacks, exact throwable identity and
message `FIRST_GEOMETRIC_REASON_CALLBACK`, and verified detach/context restore
and reference release before the process result is accepted. The interrupted
query has no returned boolean and is not a completed observation. Preserve its
actual partial/exception evidence; no missing footer or truncated complete-mode
sidecar can qualify as success.

## Timing, limits, and status

Keep the frozen limits unchanged: 90 s counted work, at most 640 groups and
5 s per complete group, 32768 combined observation units, 1M expansions, 48
holes/four flips, 4096 entries per chunk, 1M entries per clone, and 32768 combined
units. Keep the per-operation and host/source-evidence caps from the output
contract above, with 64 KiB structured-line/index limits. Retain 60 s compile,
480 s per native child, 60 s audit, 180 s reader, 1800 s selected-host wall,
and 120 s cleanup. Throws subtract no work. Callback time inside a measured
query is overlapping diagnostic time, not another unit.

Use monotonic host offsets. Set `nativePhaseStartOffsetMs` immediately before
the first native child. Set `cleanupStartOffsetMs` after the last native child
and its receipt/stream/final native I/O, immediately before post-source audit.
That audit and all later finalization belong to the full-parent tail, not the
native phase. Derive the interval as `cleanupStartOffsetMs -
nativePhaseStartOffsetMs`. Subtract inclusive operation work only for validated
completed normal native calls (NULL/OBSERVE); never subtract work for either
throw, a preflight failure, or an incomplete/missing call. Define
`nativePhaseChargeMs = max(interval - validCompletedNormalWork,
hostReportedCharge, sumRoleWallResiduals)`, with role residuals taken from the
frozen host schema. A role residual uses full wall for throws/failures; subtract
only that role's validated normal inclusive work for a completed normal call.
Require finite, ordered, nonnegative arithmetic and reject a negative interval
residual rather than clamping it. Preserve uncharged gaps conservatively.


The conservative cleanup upper bound is `nativePhaseChargeMs + (fullParentElapsedMs - cleanupStartOffsetMs)`;
require `cleanupStartOffsetMs <= fullParentElapsedMs` and upper bound <=120000
without clamping or tolerance that understates elapsed time. This includes
post-source checks, verified scratch cleanup, final receipt/output writes, and
selected-host return. Record reader/source/output closure time separately. A
failed termination, cleanup, source recheck, or final closure is failure.

Success emits one bounded canonical JSON line with a status limited to
`PASS_Q100_GEOMETRIC_PREFIX_OBSERVATIONS_ONLY`, `layoutQualification=false`,
and all route/layout/electrical/Q100 acceptance flags false. It reports the
actual run IDs, reason counts/rows, P4/P5/P6 comparison results, source/output
closure, process proofs, and timing bounds. Any failure emits one exact LF
stderr frame `P8_PREFIX_READER_REJECT <UPPERCASE_CODE>\n`, nonzero exit, and no
stdout; an unexpected exception is a distinct failure, never remapped to an
expected semantic rejection. No GWT, Player, route benefit, canonical solution,
physical feasibility, or broader population claim follows.

## Finite controls before acceptance

- Preserve the five already-qualified selected-host rejection controls and
  four selected-reader parent-receipt controls as independent evidence; require
  exact codes/frames, actual selected-child exit and termination, and unchanged
  real positive outputs.
- Run one genuine selected-host BASE positive bundle through the independent
  reader before accepting any mode. Record before/after fingerprints of its
  accepted outputs; controls must not mutate them.
- Exercise actual reader CLI pin/transport tampering once and require the
  intended source/pins or indexed-hash rejection, not a startup/import failure.
- With authenticated reader functions and copies of a genuinely parsed reason
  sidecar in memory, require specific semantic rejection for wrong run/source/
  cut identity, query reorder/duplicate/missing ordinal, wrong coordinates,
  invalid owner/default/null/boolean values, malformed footer/EOF, and invalid
  units/arithmetic. These are synthetic semantic fixtures, not host runs or
  forged positive receipts. Freeze each expected code before execution; retain
  failures and raw receipts.

No new seeds/candidates, posthoc predicate probes, production edits, full30, or
HARD/PSYCHOTIC data are part of this contract. The canonical 14 reason
 distribution remains unqualified until the fresh BASE host, independent
 reader, finite controls, and root review all pass.

## Final consumer interfaces and summary

This section and final NATIVE_TRANSPORT_R1 override provisional draft wording.
The footer workNanos is P8-owned observation work only; native marker workNanos
is whole-wrapper work under its defined source guards. Never credit only the
footer as whole-operation work or add overlapping P07/P5/P6 time to P8 units.
The normal data allowlist has exactly seven files; REASON_THROW four; original
P6 CALLBACK_THROW only its original partial op-1 file. There is no extra
canonical-blocker-index. Validate complete preceding P5/P6 sidecars for the
new throw, alongside actual partial receipt parity and owner/context proof.

The reader's one-line success JSON has exact top-level keys:
schema,status,layoutQualification,inputSha256,sourceSha256,sourceParent,mode,
sourceClosure,processes,operations,parity,reasonCounts,reasonRows,cleanup,
outputClosure,limits. schema is tsj-q100-geometric-prefix-reader-v1.
sourceParent is0891474cadc4b9e00d7911e81c124721a11efb87; mode is actual host
CANARY or SUITE. layoutQualification is false. CANARY status is exactly
PASS_Q100_GEOMETRIC_PREFIX_HOST_CANARY_ONLY; SUITE status is exactly
PASS_Q100_GEOMETRIC_PREFIX_OBSERVATIONS_ONLY. No other success statuses.

sourceClosure is the authenticated new auditor summary. processes is an ordered
list with exact fields role,exitCode,terminationProven,wallMs,processRootAbsent;
it projects actual verified raw receipts. operations contains only the four
BASE data modes in SUITE, none in CANARY. Each operation has exact fields
mode,runId,status,queryCount,fineEvents,coarseEvents,workNanos,
priorObservationUnits,p8ObservationUnits,totalObservationUnits. Counts/work are
actual validated values. Old CALLBACK_THROW has fineEvents and p8ObservationUnits
null (no P8 reason sidecar), priorObservationUnits=0 and its actual P6 unit total.

parity has exact keys p4Capture,p5Attribution,p6Stages,p8ResultEffects,
oldThrowPrefix,newThrowPrefix. Values are null for CANARY and true for a
qualified SUITE, only after each separate unchanged predicate/actual new
comparison passes. No source-only parity becomes a runtime boolean.
reasonCounts is the sorted actual fine-site histogram for OBSERVE; reasonRows
is its14 actual query rows with original identity, fine/coarse data and cleanup
proof. CANARY has empty histogram/list. Never put controller/global paths in
that public-safe projection or infer a reason count from the coarse9/5 split.

cleanup has exact keys boundMs,nativePhaseChargeMs,finalHostTailMs,upperBoundMs,
processRootsAbsent,scratchRemoved. boundMs=120000, the last two fields true
only after actual checks. Other fields are the independently computed finite
nonnegative times. outputClosure has exact files,bytes,unchanged fields from
actual closed tree; unchanged=true after verification. limits is the exact
validated final host limits object. Keep the one-line summary <=65536 bytes.

CONTROL_CASES_R1 fixes the selected CLI controls and the source-authenticated
12-case semantic harness. The reader owner implements that harness in
test_read_geometric_prefix.py using its actual parser/validator functions;
internal Python function signatures are not a separate wire API. The normal
CLI prints only the summary, while the harness may retain validated in-memory
contexts for those synthetic controls. No genuine input/output is rewritten.
Direct helper imports execute cached authenticated bytes, as specified above.
