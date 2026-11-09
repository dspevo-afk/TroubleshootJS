# Synthetic constructor / metadata / R7 / ticket contract

Base a221342de31a9e32c450d3430ef2f1061886f99b; Source-only audit
SOURCE_AUDIT_R1.md applies. No production/helper changes. One test-only canary
composes the existing current owners. Factory is freshly generated from the
single AST-extracted _synthetic_context function; no old DATA/test-module import.
Fresh factory c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089,
context ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f.
These are synthetic projections, never a genuine graph or producer receipt.

Independent pre-code census emitted/INDEPENDENT_CENSUS_R1.json is frozen before
canary implementation. It uses a literal expected root/work shape and Python
fixture, not runtime Java counters or extracted Java implementation arithmetic.
M9,L2,Em72,El62,scalar strings85/numbers37/booleans2. Keys1101 ASCII characters,
values1435, total2536. Guard1099 units/11 copies/chunks. Account minimum364 and
traversal16772, total17136. R7 fixed184, total18419 units/106copies/14chunks.
Root reviewed/corrected literal work-key spellings before first script execution;
the unevaluated earlier draft is retained in source-history-r1. Source audit's
provisional formulas now match the independently emitted census.

Per case, controlled file fixture and P/B/expected digest/path/mtime/cutoff oracle
preparation occur before producer clock. Receipt contents are 'x' bytes endingLF;
size1 is LF only. Windows actual empty run-root, canonical absolute unreparsed
paths; one exact child directory per case and op-1.jsonl therein. P is derived
independently before the clock from actual canonical receipt path name count+1;
B=(size+4095)/4096. Three positive sizes1/4096/4097 cover B1/1/2.

Normal sequence:
1. Real Parent and zero-unit closed Meter at started. Actual InputSetup.read
   charges its own18 admission; no bootstrap. Exact SetupResult and native Meter
   identity stay stable. Expected setup10007units/478copies/3chunks/1closedgroup.
2. Synthetic prior-credit primitive begin/admit0/adopt1 between groups; no
   OriginalArm, ArmResult, owner execution, Session or routing. originalGroups=1,
   originalOwnerGroups=1; all other detached owner/NULL/OBSERVE/cleanup values0.
3. Prepay4 units before actual four SetupResult attribution scalar reads, then
   codec.createBody(actual context hash, literal valid run/source headers and
   four disjoint precreated empty child maps). 14/13/1; work/prior stillnull.
   Header RUN=00000000-0000-0000-0000-000000000001, SOURCE='1' repeated64.
4. Metadata.prepare with actual SetupResult/current context/synthetic receipt;
   268+10P+6B units,2P+21copies,5chunks. Same root/nullprior filled. No finish.
5. Existing captureDetachedR7WorkPlan with actual setup attribution and above
   scalar fixture. Work34/plan/guard/account/snapshot/freeze/finalizer prepayment
   closes metadata exactly once. Four generic maps remain semantically empty.
6. Verify pre.verifyReady and plan.verifyBound, exact34 placeholder keys/zeros.
   finalizeWork at bounded positive cutoff (for example started+1ms) fills all34
   preexisting numeric slots only. Assert all34 independent equations and no
   changes to real Meter units/copies/chunks/groups/open/maxima/measure identity.
7. Ticket.verifyFinal(same meter,started,receipt) once; no live-Meter debit or
   reopening. Reverify snapshot. This is standalone ticket verification after
   synthetic R7 readback, not actual export or full-result acceptance.

Expected normal meter totals: U=28712+10P+6B, C=618+2P, K=23, wrapperG=2,
prior=1,totalG=3. Metadata residual units=U-10007 (NOT U+setup); groups1;
copies=C-478; chunks20. Setup4 caller reads count in metadata, not setup again.
Both normal groups fit32768; normal maxGroupUnits=max(10007,U-10007).
MaxGroupNanos is observed independently from actual group measures, not frozen
clock values. All work values other than bounded dynamic cutoff/maxNanos are
literal equations: setup10007/1/478/3; originalGroups1; other owner/arm fields0;
wrapper2/total3; meter/units U; metadata U-10007/1/C-478/20; copied C/chunks23;
copyChunkLimit4096/copiedEntryLimit1048576. No extra copy ledger/DTO/deep copy.

Frozen names/protocol (Root author and driver must agree before native):
SMOKE3:
composition_receipt_1_byte, composition_receipt_4096_bytes,
composition_receipt_4097_bytes.
SUITE16:
ownership_sibling_alias, ownership_prior_alias, metadata_repeat_slot,
work_slot_nonempty, r7_wrong_start, r7_without_owner_credit,
receipt_changed_after_close, ticket_wrong_meter, ticket_wrong_start,
ticket_wrong_path, ticket_reuse, finalizer_reuse,
snapshot_rejects_later_meter_work, final_copy_cap,
group640_snapshot_reject, intentional_metadata_split_accounted.

Failure expectations:
- Two alias controls use a repeated empty child Map or borrowed input receipt
  already in qualifiedPrior. R7 rejects BODY_OWNERSHIP_ALIAS before account/close;
  work34 installed/zero, setup group only; exclusive intended inputs preserved.
- Repeat metadata rejects METADATA_BODY_SLOT before more input/receipt work;
  exactly4 extra units, no copies, no close. Stop this invalid sequence there.
- Occupiedwork rejects R7_WORK_SLOT_NOT_EMPTY after2 lookups, no new workcopies.
- Wrong R7started=started+1 rejects R5_METADATA_SNAPSHOT after metadata closes;
  missing primitiveprior gate likewise rejects snapshot. Neither means native
  producer success; they are expected synthetic canary rejections.
- Receipt mutation same length, restore exact mtime, expects FINAL_SHA_MISMATCH
  after R7 closure. All authority/reuse cases consume ticket once as defined:
  wrongmeter/start/path -> FINAL_AUTHORITY_MISMATCH then FINAL_ATTEMPT_REUSED;
  successful ticket twice -> FINAL_ATTEMPT_REUSED. Same_meter_wrong_body is not
  an authorized case; body ownership is already covered by existing plan/guard.
- Second finalizer -> R7_FINALIZER_REUSED. Post-close charge1 intentionally
  opens real group; snapshot.verifyReady rejects R5_METER_CHANGED in unchanged()
  before the later maximum/groupUnits R5_STALE_SNAPSHOT checks; cleanup
  actually closes it. Do not call this unchanged closed-meter evidence.
- Final copycap set to expected C-1 after setup/meta admits earlierR7 operations
  but rejects the precharged last34 replacements. No metadata finish; expected
  before failure C-34 copies,22chunks and units U-34 (guard/account/prepay charged).
  Snapshot/finalizer never executes. Cleanup closes actual still-open group.
- After setup/prior gate,637 zero-unit wrapper groups before constructing body
  bring wrapper638+prior1. Metadata may run/close at wrapper639+prior1=640; closed
  snapshot rejects R5_METADATA_SNAPSHOT (strict<640). No extra owner credit or
  budget increase. Existing meter controls, no reflected forged counters.
- Intentional extra finish after metadata adds one real wrappergroup without
  units/copies/chunks. R7 wrapper3/total4, metadataGroups2. Max units are the max
  of actual three group measures with independent Source-derived segment units:
  setup10007; preR7=4+14+(268+10P+6B); R7=18419. Validate equation, not assume
  original2-groupmax. This is an accounting control, not normalcaller policy.

Main takes exactly SMOKE|SUITE and an actual exclusive empty task run root.
Output mode/status/names/cases, exact names/order. Per case records status,phase,
reason, actual counts, expected metrics and operationNanos/cleanupNanos plus
cleanupDisposition/errors. Expected negative cases are PASS only after exact
rejection/state assertions; cleanup failure forces FAIL. No asserted count guessed.
Cases delete only owned known receipt files/case directory with exact containment;
no recursive deletion, links, subprocesses, persistent process/listener/browser.
Operation and cleanup timing separate. Windows path/file identity actually used.
All caps unchanged. Smoke must pass before suite through selected existing owner.

FinalSource inventory863, JDK8 target7/major51 and fresh classes+two pinned GWT
jars only. No actual producer/OriginalArm class execution, genuine arm carriers,
old classpath or old DATA. Existing uninstantiated ArmResult type carrier alone
may compile as dependency. Source/census/real native/gate receipts audited. No
production/GWT/Player/electrical/population renewal. Full30excluded, protected
unreadUNCERTIFIED, HARD/PSYCHOTIC unavailable/unrun, playable100=0,betaHELD;
genuineoriginalNULL/OBSERVE UNSPENT_GATED. Export/fullD/normalhost/lifecycle next.
