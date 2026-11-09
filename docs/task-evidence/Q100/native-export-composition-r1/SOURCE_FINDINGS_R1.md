# Current-result export composition Source findings

SOURCE AUDIT COMPLETE, no API/order blocker found for a bounded synthetic test.
All15 Source pins match SOURCE_INPUT_R1 D623430C. Leaf performed read-only Source
mapping; Root inspected current Study2794-2804 and3238-3341 to confirm the actual
API, same-Meter export start, one-shot calls, fee and returned scalar facts.
No implementation/import/generation/fixture/build/native/DATA/class/producer/
Original/Session/routing was used in this Q scope.

The current existing entry point is CohortTerminalTelemetryStudy.exportV2(
runId,sourceSha,wireSha,contextSha,outputDirectory,result,meter,started,plan.pre,
plan,ticket,hook). Positive hook=null. The actual R7WorkPlan can be passed
directly; no new production adapter/API or substitute finalizer is needed.
Legacy exportR5 overloads remain disabled and are not a migration target here.

CRITICAL change from the published composition test: start a FRESH fixture and
stop after captureDetachedR7WorkPlan closes/freezes metadata. Do NOT pre-call
plan.finalizeWork or ticket.verifyFinal: exportV2 owns both one-shot operations.
Pass the exact actual unfinalized plan, its same snapshot, Meter, started, body
and prepared ticket. Ticket path must equal outputDirectory.resolve("op-1.jsonl").
Canonical non-reparse directory contains only that controlled synthetic receipt;
result.json must not exist. Existing generic composition fixture shape fits.

Source order:
1. Snapshot.verifyReady before opening one export group; exportStarted is read
   from that actual native Meter.groupStarted, not a synthetic cutoff.
2. Fixed header/output admission, compare caller IDs with body, validate R5Output
   ancestors/identities/preclosure and exact34 work shape.
3. Actual plan.verifyBound then finalizeWork(exportStarted) once; prove unchanged
   Meter/snapshot except the one fixed open-group unit, validate all work fields.
4. Current codec count pass; R/C/fee/copy/chunk admission before output allocation.
5. Streaming CREATE_NEW/WRITE/NOFOLLOW result; same-codec streaming read/compare+
   SHA/EOF/output-closure. Optional test hook runs between write and readback.
6. Deadline, ticket.verifyFinal on same Meter/start/exact operation path once,
   deadline, copy32 digest admission, fixed marker unit, exact fee/snapshot checks.
7. Close exactly one group, capture its real measure, create publication token.
8. Typed14-key marker count to exact M<=656; one-shot exact M admission after
   close changes only copies/chunks. Exact marker buffer write, stdout error/
   write/flush/error checks, token.afterPublication, return actual metrics.
No finally-finish is present: failed export leaves partial output/Meter for caller
discard; stream resources use try-with-resources. Failure or failed cleanup is
never publication success.

Before export, preserve the already-accounted baseline, not another setup/ticket
debit: U0=28712+10P+6B_receipt, copies0=618+2P, chunks0=23, wrappergroups2/prior1.
For finalized result bytes R including LF, C=ceil(R/4096), exact marker bytes M:
export units=5C+3, copies delta=2R+32+M, chunks delta=2C+2, wrapper delta1.
Synthetic original unit offset0, so marker totalUnits=U0+5C+3 and totalGroups4.
Marker M admission adds only M copies/one chunk after close; no reopen/charge.
All old caps remain. R/C/digest/M are NOT frozen or inferred from zero work slots.

Required pre-code oracle work: independently encode expected finalized body from
literal synthetic input/root shape and all34 independently calculated equations.
Use actual native groupStarted-started for R7 elapsed cutoff, and actual native
group measures for maxima/duration; do not trust serialized work as its oracle.
Study exportStarted equals native groupStarted (3246), retained through close/
token checks. Returned R5ExportMetrics carries elapsedNanos (the actual closed
prefix timestamp), finalExportNanos, totals/maxima and result/marker/copy metrics.
Treat only bounded clock observations as runtime facts; independently derive
structural counts, unit/group/copy formulas, R/SHA/M and compare returned metrics.
Freeze timestamp observation/bounds and independent fourteen-key marker encoding
before code. No codec-generated expected golden or guessed byte/digest count.

Smallest useful next scope is one integrated positive using real setup through
actual R7 and exportV2 result/readback/marker/output closure. One integrated
same-length/restored-mtime receipt mutation via the after-write hook can prove
ticket failure blocks close/publication; existing standalone Source packet covers
other lower-level boundaries for its own recorded candidate, not a new rerun here.
For this optional negative, derive exact failed-phase counters before code;
discard failed Meter as documented and prove owned stream/file cleanup separately.
System.out is hardwired by the API: capture in a bounded test PrintStream and
restore in finally on success/error/cancellation. This is test transport only,
not normal-host stdout/process/lifecycle certification. Add no production hook.

Next: explicitly admit only necessary current pure fixture/generator/oracle and
driver Source references, freeze the exact synthetic contract/oracles/cases, then
Source implementation and one final-source JDK8/native gate sequence. No new
actual producer envelope is authorized by this Source audit. Generic children and
synthetic prior credit prove neither semantic/full-D content nor original receipt
authority. Normal assembly/actual host/lifecycle remain separate. Full30excluded,
HARD/PSYCHOTIC unavailable/unrun, protected unreadUNCERTIFIED, playable100=0,
betaHELD; genuine original then exact NULL/OBSERVE remains UNSPENT_GATED.
