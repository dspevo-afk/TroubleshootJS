# Synthetic composed metadata canary: Source release R1

Status SOURCE ONLY. Only ComposedMetadataCanary.java and this design note were
authored by this leaf. No generation/import/compiler/native execution, tests,
DATA/context JSON/results/classes reads, production/helper changes, stage, commit,
push, mail or delegation occurred. The leaf inspected admitted Source and the
independent pre-code census only. Runtime gates are NOT RUN, not PASS.

Base a221342de31a9e32c450d3430ef2f1061886f99b. Authority is Root's corrected
COMPOSITION_CONTRACT_R1.md SHA256
509c1b72783dd2afa5a5b8fb4120ff65cfcf611eba7f6522975e6b3a60efe8b6.
The prior BC4903 contract is preserved by Root under
source-history-r1/COMPOSITION_CONTRACT_PRE_CODE_R1.md. Static review found that
post-close charge1 makes verifyReady fail R5_METER_CHANGED in unchanged(), before
the later max/group-unit R5_STALE_SNAPSHOT checks. Root corrected the contract
before canary execution; there was no native failure or production repair.

SOURCE_AUDIT_R1.md SHA256
bf5f41545b22f7d09b9fbf7df85743a4af1fd90de4297d71b533309f67f67e79 and
emitted/INDEPENDENT_CENSUS_R1.json SHA256
e1dc853e840e4aaaae3fc7ab51f3258ba403fa8112c8fb19274c55c33787ccd5
were read and hash-checked. Source admission is limited to SOURCE_INPUT_R1.json
79a8f90b1831b611005f6adb17b44b99ec14451089f337355cc0f51d6a205e48 and
SOURCE_INPUT_R2.json
cd2372194f808e90a8a0c8e090541b5bea62ba845e93d47206ceb6f30a865970 named Sources.
The newly emitted synthetic factory Source CohortTerminalTelemetryInputV2.java
c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089 is used by
the actual CurrentV2InputSetup.Factory adapter. No generated context JSON is read.

## Actual owner sequence and independent expectations

Main accepts exactly SMOKE|SUITE and one actual exclusive empty Windows run-root.
It validates canonical absolute path spelling, actual Windows identities and
ancestors, and emptiness before cases and after cleanup. Each case exclusively
creates root/name/op-1.jsonl. Controlled bytes are x characters ending LF; size1
is LF alone. Receipt SHA, bytes, mtime, actual canonical P=nameCount+1,
B=ceil(size/4096), child Maps and Parent resolution are prepared before the
producer clock. Operation timing includes fixture creation as well as the actual
composition; producer time starts only after the controlled fixture is ready.

One actual Parent/SetupMeter/native Meter is zero-unit and closed before actual
CurrentV2InputSetup.read. The canary does not bootstrap18; read performs its own
admission. Actual SetupResult must be10007 units/478 entries/3 chunks/1 group.
The same exact SetupResult is used by Metadata.prepare. The caller prepays4
before the four actual attribution reads forwarded to detached R7. Independent
observer assertions/JSON/MeterImage snapshots are test instrumentation; they do
not become extra product counters or a production work ledger.
Their fixture allocations and introspection are unbilled harness work under the
existing logical helper contracts; wall-clock time remains real throughout, with
no deadline bypass or new primitive-VM accounting claim.

The synthetic prior primitive calls beginOriginalOwner/admitOwnerGroup(0)/
adoptOriginalOwner(1) between groups. It credits one prior group only, without
constructing or executing any OriginalArm, ArmResult, graph, Session or routing.
Missing-credit is a negative control. Setup attribution is not charged again.

The existing codec constructor stages the same exact13-key root, with qualifiedPrior
and work present-null, four disjoint precreated empty child Maps, literal valid
run/source/context headers and fixed owner headers. Constructor debit14/13/1
follows the caller's4. Metadata.prepare borrows prior children into qualifiedPrior
and returns the actual ReceiptContentBinding ticket; debit268+10P+6B units,
2P+21 copies/5 chunks, still open. Input JSON and borrowed prior identities are
checked independently before/after composition.

The existing captureDetachedR7WorkPlan receives the four actual setup scalars,
originalGroups=originalOwnerGroups=1, all other detached scalars0. It installs
work34, validates identity ownership, accounts once, prepays finalization, closes,
captures and freezes. R7 cost18419 is the independent census184 fixed +1099
ownership +17136 accounting; copies106/chunks14. Normal totals are exactly
U=28712+10P+6B, C=618+2P, K23, wrapperG2/prior1/totalG3. No extra DTO, copy
ledger, child deep copy or producer exists in this test.

Before finalization, the canary checks independent literal34 key order, exact
Long zeros in every slot, snapshot.verifyReady and plan.verifyBound. It finalizes
at started+1ms and checks all34 equations individually, including all zero owner/
observer/cleanup values. Setup10007/1/478/3; originalGroups1; wrapper2/total3;
meter/units U; metadata U-10007/1/C-478/20; copiedC/chunks23; limits4096/1048576.
For split, wrapper3/total4/metadataGroups2. Cutoff is exactly1ms and bounded,
not a claim to real elapsed export time. There is no export.

Real group measure elements are independently inspected: count, exact unit
segments, nonnegative <=5s durations, summed units and computed maxima must match
the live native counters. Normal segments=[10007,U-10007]. Split segments=
[10007,4+14+(268+10P+6B),18419]. Group640's639 wrapper measures consist of
setup10007,637 actual zero-unit measures and the final metadata segment.
Maximum units is the maximum of these actual expected segments, not a two-group
assumption. Max nanos comes from the independently scanned actual measures.

MeterImage observes every native Meter scalar/flag, native identity, measure-list
identity, every measure element identity/units/nanos. The finalizer and ticket
must leave all these unchanged and keep the same snapshot/body/work identities.
The same real ticket verifies against same meter/start/path once after standalone
synthetic R7 finalization. This is receipt content verification, not original-host
authentication, normal result acceptance or real readback/export qualification.

## Frozen names and PASS state at operation boundary

SMOKE runs all three positive sizes first. SUITE runs exactly the16 distinct
controls below. Names and order are literal Source constants agreed with driver.

| Mode | Name | Required behavior | groups/prior | work/finalized slots | open |
| --- | --- | --- | --- | --- | --- |
| SMOKE | composition_receipt_1_byte | Full actual sequence; B1 | 2/1 | 34/34 | false |
| SMOKE | composition_receipt_4096_bytes | Full actual sequence; B1 | 2/1 | 34/34 | false |
| SMOKE | composition_receipt_4097_bytes | Full actual sequence; B2 | 2/1 | 34/34 | false |
| SUITE | ownership_sibling_alias | Repeated empty sibling Map; BODY_OWNERSHIP_ALIAS before account/close, zeros remain | 1/1 | 34/0 | true |
| SUITE | ownership_prior_alias | Borrowed input prior receipt reused as child; same exact rejection/zeros | 1/1 | 34/0 | true |
| SUITE | metadata_repeat_slot | METADATA_BODY_SLOT after exactly4 further units, no copies/close/work allocation | 1/1 | 0/0 | true |
| SUITE | work_slot_nonempty | Occupied empty work Map preserved; R7_WORK_SLOT_NOT_EMPTY after exactly2 lookups | 1/1 | 0/0 | true |
| SUITE | r7_wrong_start | R5_METADATA_SNAPSHOT after actual close; work stillzero | 2/1 | 34/0 | false |
| SUITE | r7_without_owner_credit | Same snapshot rejection with no prior primitive; no forged counters | 2/0 | 34/0 | false |
| SUITE | receipt_changed_after_close | Same length/mtime restored; FINAL_SHA_MISMATCH on changed bytes | 2/1 | 34/34 | false |
| SUITE | ticket_wrong_meter | Foreign real zero/closed meter; FINAL_AUTHORITY_MISMATCH then FINAL_ATTEMPT_REUSED; both meters unchanged | 2/1 | 34/34 | false |
| SUITE | ticket_wrong_start | Same authority/reuse pair | 2/1 | 34/34 | false |
| SUITE | ticket_wrong_path | Same authority/reuse pair; nonexistent wrong path creates no file | 2/1 | 34/34 | false |
| SUITE | ticket_reuse | First succeeds; second FINAL_ATTEMPT_REUSED | 2/1 | 34/34 | false |
| SUITE | finalizer_reuse | Second finalizer R7_FINALIZER_REUSED, all values/real meter unchanged | 2/1 | 34/34 | false |
| SUITE | snapshot_rejects_later_meter_work | After final ticket, actual charge1 opens next group; verifyReady R5_METER_CHANGED; unitsU+1 | 2/1 | 34/34 | true |
| SUITE | final_copy_cap | Set actual allowance C-1 after metadata; last copy34 rejects before finish/snapshot/finalizer; unitsU-34/copiesC-34/chunks22 | 1/1 | 34/0 | true |
| SUITE | group640_snapshot_reject | 637 real zero-unit groups; actual close reaches639wrapper+1prior; strict snapshot bound rejects | 639/1 | 34/0 | false |
| SUITE | intentional_metadata_split_accounted | Intentional actual finish after prepare; three group segments and34 equations checked | 3/1 | 34/34 | false |

Every expected negative requires the exact selected exception text/code AND the
specified state/identity/count assertions. Merely throwing never passes. Alias
guard partial units are bounded before accounting and close, not fabricated as
a guessed exact debit; the full successful guard/account costs remain the frozen
independent census. No additional broad test matrix is implied.

## Protocol, operation boundary and cleanup

Output is one compact ASCII JSON line with top-level mode,status,names,cases.
Each case has exactly22 uniform scalar fields:

name,status,phase,reason,operationNanos,cleanupNanos,cleanupDisposition,error,
cleanupError,units,copies,chunks,groups,priorGroups,pathEntries,hashBlocks,
normalUnitsOracle,normalCopiesOracle,normalChunksOracle,workSlots,finalizedSlots,
metadataOpen.

name/status/phase/reason/cleanupDisposition/error/cleanupError are strings;
operationNanos/cleanupNanos and all numeric counts/oracles are nonnegative Java
Longs emitted as JSON integers; metadataOpen is boolean. PASS phase=complete,
reason=OK, error/cleanupError empty. normal*Oracle always means the full normal
sequence U/C/K reference for the current P/B, including early negative records;
it does NOT assert partial negative counts equal full normal totals. All other
counts are actual observations. No assertion-count metric is invented.

OPERATION_BOUNDARY semantics: units/copies/chunks/groups/priorGroups/workSlots/
finalizedSlots/metadataOpen are captured BEFORE cleanup closes a meter. groups
counts actually closed wrapper groups only. This deliberately preserves the six
expected open cases and their partial work in the record. Cleanup is separate:
all six open cases (two aliases, repeat metadata, occupied work, later charge,
final copy cap) call real SetupMeter.finish and prove open=false. Their PASS
cleanupDisposition is CLOSED_AND_REMOVED. All others have
ALREADY_CLOSED_AND_REMOVED; the foreign ticket meter is independently proved
unchanged and closed. No expected-expiry discard exists in this suite.

operationNanos includes fixture preparation/composition and ends before metric
capture. cleanupNanos includes read-only operation-boundary metric capture,
actual meter closure where needed, file identity checks and removal. Cleanup
errors never disappear into a PASS: any failure sets statusFAIL, phasecleanup,
reasonCLEANUP_FAILED, cleanupDispositionCLEANUP_FAILED and bounded cleanupError.
Operation failures keep their bounded error string; combined cleanup failures
retain suppressed counts. Any failing record makes the process exit1.

Only the exact successfully created receipt and its exact case directory can be
deleted. Before each nonrecursive delete, validate Root/child containment and
the actual Windows identity/creation-time of the known owned object. Receipt
content changes are allowed only within the owned file; identity replacement is
never allowed for cleanup. No link traversal/recursive delete/process/browser/
listener cleanup exists. Missing identity proof retains resources and fails
cleanup. File/directory deletion is attempted even if meter finish fails. Root
must still be the same actual empty directory after all cases.

## Release and limitations

The Source schema/names were coordinated with the strict driver before release.
Hash/size release is sent separately to avoid self-hashing this note. Root owns
final Source freeze, native Source closure863, final JDK8 target7/major51 build,
fresh classes/two pinned GWT jars, actual tiny smoke-before-suite execution,
selected CLI positive/negative controls and acceptance. No unreviewed Source
changes may reuse this release hash. Raw released revisions must be preserved
before later fixes; this note makes no claim to a full draft-edit history.

All existing limits remain90s/640groups/5s/32768units/copy1MiB/chunk4096 and codec
depth64/visits1m; Source/class/process caps unchanged. Genuine original/NULL/
OBSERVE remains UNSPENT_GATED. Four generic empty children cannot establish arm/
cohort/cleanup semantics. Full30 excluded, HARD/PSYCHOTIC unavailable-unrun,
protected-unread UNCERTIFIED, playable100=0, betaHELD. Production/GWT/player/
electrical/population renewal is NOT APPLICABLE here. Export/full-D/normal-host/
lifecycle qualification remains the next unstarted scope.
