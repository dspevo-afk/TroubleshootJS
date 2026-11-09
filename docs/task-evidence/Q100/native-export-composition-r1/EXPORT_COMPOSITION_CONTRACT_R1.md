# Actual R7 to current V2 export: synthetic composition contract

Base2ab3e1cf60b24c5ee12459ff39ca2b3c0e0d8182. Source audit737A3B2B and
admission577707E6 apply. Source manifests R1D623430C/15 and R299BD8F9B/7 admit
only their named Sources. No old DATA/classes/producer/OriginalArm/ArmResult/
graph/Session/routing. Helpers and production bytes stay unchanged; tests only.

Small finish line: actual InputSetup -> current constructor -> metadata/ticket ->
actual detached R7 close/freeze -> existing exportV2 with actual UNFINALIZED plan
and UNUSED prepared ticket. Export owns both one-shot operations. Do not pre-call
finalizeWork or verifyFinal and do not replace the real R7 plan with a test plan.
Four distinct empty child maps and synthetic prior credit1 are explicit fixtures.
Receipt is4096 'x' bytes with final LF. All fixture/path/hash preparation precedes
the real producer clock. Fresh actual zero/closed Meter; helper charges18 itself.
Same setup/Meter/nativeMeter/start/body/plan/snapshot/ticket identities throughout.

Before export: actual setup10007units/478copies/3chunks/1closedgroup; caller
prepays4 attribution reads; constructor14/13/1; metadata268+10P+6B/2P+21/5;
R7 including guard/account18419/106/14. B_receipt=1, P=canonical path entries+1.
Thus U0=28712+10P+6, copied0=618+2P, chunks0=23, wrapper2/prior1. R7 real plan
gets actual setup4 attribution scalars; all owner/fresh/cleanup values0 except
originalGroups=originalOwnerGroups=1. Original wrapper/hash/capture paths absent.

The canonical/reparse-free output directory initially has only op-1.jsonl and no
result.json. exportV2(run,source,wire,context,dir,body,meter,start,plan.pre,plan,
ticket,hook) opens one export group, gets actual native groupStarted, binds and
finalizes actual plan, counts/writes/readbacks current body, consumes final receipt
attempt, closes, admits exact marker and verifies publication. No new helper/API.
Run=00000000-0000-0000-0000-000000000001/source='1'*64/current contextCA2D5B88;
wire1ABECDF5. Exclusive stable body/borrowed child ownership is caller precondition.

For finalized result R bytes including LF, C=ceil(R/4096), exact marker M bytes:
positive adds5C+3units/2R+32+M copies/2C+2chunks/one closed wrapper group.
Final wrappers3/prior1/marker totalGroups4. Marker M admission after close changes
only copies/chunks; no reopen/charge. Globalresult1MiB/marker656/copy1MiB/chunk4096
and all90s/640groups/5s/32768 limits unchanged. This test's small transported result
is bounded32768bytes (hex65536chars), markerhex1312chars; report1MiB unchanged.
The fixture bound makes no general capacity/performance claim.

Exact cases: SMOKE1 current_export_composition (hook=null); SUITE1
receipt_changed_before_export_close. A fresh fixture is used each time. In SUITE,
existing after-result-write hook changes receipt first byte x->y, preserves size,
restores exact mtime and checks them. Actual ticket must fail FINAL_SHA_MISMATCH
after result readback, before digest32/final marker fees, close or publication.
Expected negative counters: U0+5C+1,copied0+2R,chunks0+2C,closedwrappers2/prior1,
open=true,groupUnits5C+1; no third measure/returned metrics/marker. Actual real R7
work is finalized34. Failed export Meter is explicitly discarded WITHOUT finish;
it owns no OS resource. Prove stream/file cleanup separately. Do not call failure
a successful closed export group. One expected exception alone does not pass.

Transport is test-only: bounded ByteArrayOutputStream/PrintStream captures the
existing hardwired System.out marker; restore original System.out in finally on
success and error before writing case summary. No production hook/host dispatch.
Capture actual result bytes with a bounded, identity-checked small fixture read,
then lowercase hex; capture actual marker bytes as lowercase hex. Oracle uses
decoded bytes as ACTUAL only. Never emit result/body/marker through current codec
to construct EXPECTED values. Do not publish a marker on the negative path.

Metrics are OPERATION_BOUNDARY before cleanup. operationNanos includes fixture
and exercise; cleanupNanos covers snapshot/read-only fact capture, restoration,
identity checks and owned leaf removal. Cleanup failure forces FAIL. Delete only
known owned result/receipt/case-directory with identity/containment checks, no
recursive delete. Task run root is exclusive empty before and after all cases.
Positive cleanupDisposition=ALREADY_CLOSED_AND_REMOVED; negative disposition=
EXPECTED_PARTIAL_EXPORT_METER_DISCARDED_AND_REMOVED. No positive finally-finish.

Main takes exactly SMOKE|SUITE and TASK_RUN_ROOT. Output exactly mode/status/names/
cases. Exact per-case keys and types (all integers exact nonnegative Java longs):
strings: name,status,phase,reason,cleanupDisposition,error,cleanupError,
expectedFailure,resultHex,markerHex.
booleans: meterOpen,stdoutRestored,metricsReturned,meterDiscarded,receiptMutationApplied.
integers: operationNanos,cleanupNanos,pathEntries,receiptBytes,receiptHashBlocks,
units,copiedEntries,copyChunks,wrapperGroups,priorGroups,groupUnits,maxGroupUnits,
maxGroupNanos,closedGroupMeasures,workFinalizedSlots,exportCutoffNanos,
beforeExportElapsedNanos,afterExportElapsedNanos,setupMeasureUnits,setupMeasureNanos,
metadataMeasureUnits,metadataMeasureNanos,exportMeasureUnits,exportMeasureNanos,
resultBytesObserved,metricsResultBytes,metricsMarkerBytes,metricsExportUnits,
metricsFinalExportNanos,metricsElapsedNanos,metricsTotalUnits,metricsTotalGroups,
metricsMaxGroupNanos,metricsMaximumUnitsPerGroup,metricsCopiedEntriesDelta,
metricsCopyChunksDelta.
PASS record phase=complete/reason=OK/error=cleanupError=''; expectedFailure='' for
positive or FINAL_SHA_MISMATCH for negative. workFinalizedSlots34. stdoutRestored
true always. Positive metricsReturned=true/meterOpen=false/meterDiscarded=false/
receiptMutationApplied=false. Negative flags inverse except stdoutRestored true.
Negative exportMeasureUnits/Nanos and all11 metrics fields0 (no return/close).

Independent oracle: literal synthetic prior/root and all34 work equations; do not
read actual serialized body/marker fields to supply expected values. Work cutoff
is actual native Meter.groupStarted-started, independently observed after export;
beforeExportElapsed<=cutoff<=afterExportElapsed<=90s. Setup/meta measures have
units10007/U0-10007,nanos[0,5s]; exact work maxima derive from those measurements.
Positive third export measure has units5C+3/nanos[0,5s]. Returned elapsedNanos is
the bounded actual closed-prefix clock fact only: cutoff+exportNanos<=elapsed<=
afterExportElapsed. All other returned metrics and native counters derive from
independent result bytes/digest/marker and ledger equations, not metric outputs.
Negative afterElapsed-cutoff<=5s; no fictitious closed measure or marker clock.

Canonical EXPECTED body is independent compact JSON in literal thirteen-key order
with LF and ordinary ASCII string escaping. Qualified prior is twelve ordered
keys using Source fixture prior with two distinct nested containers/lists. All34
work keys/order from the prior independent Source census; calculate values from
above constants and measured clock facts. Independent marker has fourteen ordered
keys: schema,status,runId,sourceManifestSha256,wireContractSha256,inputContextSha256,
resultSha256,resultBytes,elapsedNanos,finalExportNanos,totalGroups,totalUnits,
maxGroupNanos,maximumUnitsPerGroup; compact JSON plus LF. Marker schema/status are
tsj-cohort-terminal-marker-v2/COMPLETED_DIAGNOSTIC. SHA256 uses independent hash
of EXPECTED body bytes. Compare all ACTUAL bytes, lengths, digest, keys/equations,
native/returned counters and marker metrics; no runtime golden blessing.

Before oracle/canary implementation, Root admits/runs only the pinned pure fixture
generation and an independent literal calibration vector: P11/B1/cutoff80000ns,
setup/meta/export measures1000/2000/3000ns, before70000/after100000ns, closedelapsed
90000ns. These are explicitly synthetic Python unit-test values, never injected
into native clocks. Freeze reference body/marker bytes/digests/facts produced by
stdlib JSON from that literal vector BEFORE implementation. Actual native clocks
remain real; exact R/C/M for actual runs are derived per run, never guessed here.
Pure oracle tests include this frozen vector and negative bytes/counters/clocks/
schema controls. Selected real Python CLI positive/rejection controls must bind
authenticated native input and independently supplied P from actual run-root path.
Actual native SMOKE and its independent reader must pass before native SUITE.

Fresh final-source JDK8 target7/major51,863 exact Sources/fresh classes+two pinned
GWT jars only. No actual producer Study/Mode/ArmRun or OriginalArm implementation;
uninstantiated ArmResult dependency carrier alone allowed. Root reviews/freeze
before tests/generation/native. Preserve raw released/failing revisions and caps.
GWT/visible Player N/A, no production/visible change; electrical/population NOT
RENEWED. Full30 EXCLUDED, HARD/PSYCHOTIC unavailable/unrun, protected unread
UNCERTIFIED, playable100=0/betaHELD; genuine original then exact NULL/OBSERVE
UNSPENT_GATED. Full-D semantics/normal assembly/actual host lifecycle remain next.
