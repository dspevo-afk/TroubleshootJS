# Two-phase capture/cohort association contract

One current CurrentAssemblyMetadata owner prepares a capture-bound raw Cohort
before arms, provides a paid borrow for both future exact arm calls, then projects
once and stages a body after completed arms. This retires captureBoundBody in
current Source. Preserve cohortBeforeBody as independent component with264/125/64
fees and pendingCleanupBody14/13/1; factor only their shared raw derivation into a
private helper retaining its original input/prior/error/charge order.

Association is package-visible static nested final CaptureCohort with private mutable fields: exact
SetupMeter, capture Map, raw Cohort and completed boolean. Private constructor
sets all four explicitly; it is metadata association, not authority/ticket/DTO.
No association or Meter/owner object enters the wire graph. Normal main/assemble
remain closed. Caller exclusively owns stable input Maps/lists and all borrowed
references through both arms and preparation/export, dropping raw borrows after
arms. External aliases/adversarial mutation/lifetime authority are not qualified.

prepareCaptureCohort(meter,setup,capture): null3 args ->METADATA_ARGUMENTS before
any charge/read. Pay1 before capture.get(netIds), List guard shares it; bad value
->METADATA_CAPTURE_NET_IDS. Borrow IDs, call private raw setup derivation once
(old cohort264 minus projector5 =259units/123copies/63chunks). Pay copy(4), then
charge(1) before association allocation/four constructor stores. Return it OPEN.
Expected prepare265units/127copies/64chunks. Derivation errors retain order.

captureCohortForArms(meter,association): null2args ->ARGUMENTS. Pay1 before reading
completed; completed ->METADATA_CAPTURE_COMPLETED. Pay1 before exact SetupMeter
identity read/guard; mismatch ->METADATA_CAPTURE_METER. Pay1 before borrowing
the raw Cohort. Thus successful borrow3units/0copies/0chunks; completed1unit;
mismatch2units debited to requesting Meter only. No implicit role calls or copies.
One borrow may be used for both exact arms by a future caller. Tests may borrow
twice to prove identity, priced twice; no fake arm objects/calls are permitted.

finishCaptureBody(meter,association,runId,sourceSha,contextSha,arms): null6args
->ARGUMENTS before observations/charges. Use same open/meter/raw borrow3; pay1
before association.capture read. Project the same raw Cohort once (5/2/1), stage
via pendingCleanupBody with same capture/arms (14/13/1). Then prepay copy(4) AND
charge(4) before any association mutation: clear meter/capture/cohort and set
completed=true. Return body only after all four stores. Expected finish31units/
19copies/3chunks; no body returned/association mutation if admission fails.
Successful completion is one-use; second borrow/finish rejected completed-first,
even with another nonnull Meter. Failed finish preserves OPEN association for
inspection, but the partial failed Meter is not successful/reopened. Abort drops
caller-owned local association/borrows; it owns no live simulation/OS resources.
Reset means a fresh metadata association, not reviving a completed one.

Chosen synthetic roundtrip with ONE pre-arm borrow:265+3+31=299units,
127+0+19=146copies,64+0+3=67chunks. TWO borrows ->302units/146copies/67chunks.
These are proposed finite API fees for actual native proof, not whole-export
or owner accounting. Prices include holder allocation/stores/guard reads/clears;
no budget/oracle change, unpriced derivation, mutable placeholder or extra arm.

Deep admission controls must use actual selected Meter, not mocks:640 closed
groups refuses first read; allocation gate at639closed then32768-264 units lets
prepare264 before final allocation charge fails. Finish gate: prepare, close its
group, close638 more one-unit groups, charge32741; finish's first27 units close
group640, then final4-unit prepayment refuses before clearing. Check exact
inherited IOException with message "native wrapper exceeded 640 measured groups", no returned
association/body, unchanged input trees and retained OPEN refs on failed finish.
Preserve failed Meter counters; do not manufacture reset or successful cleanup.

Native coverage migrates all N31 18/71 case families (identity/pending slots,
literal60-ID order/detachment, null/type/vector/disposition/throw/group refusal)
and adds both phases, exact Meter identity, one-use, alias clearing, failure
preservation and independently hand-counted phase/aggregate fees. Source tests
prove two exact reversible substitutions, unrelated bytes unchanged, retired
API absent, one raw derivation per prepare, projector only in finish, paid stores
and purity/pins/caps. No producer-generated golden or missing required family.

Only synthetic Map/List/scalar/SetupResult/Cohort/association metadata +actual
Meter. No actual/fake OriginalArm/ArmResult, graph, Session, factory, positive
originalDTO/prepare(receipt)/binding(receipt)/executeArm/R9DTO, genuine receiptfile,
routing/exportV2/genuine. Actual normal producer, work installer, full DTO,
receipt/authority/borrowed lifetime/physical/electrical/whole-output proof remain
separate. No target code ran for this contract freeze. All gates initially NOT RUN.
All90s/640/5s/32768/copy1MiB/chunk4096/Source4MiB/fragment64KiB/opJSON8MiB/
result1MiB/depth64/key1m/class10k256MiB/compile-suite60s/reader180s/wrapper480s/
cleanup120s, independent oracles, exact seeds/epochs unchanged. HARD/PSYCHOTIC
unavailable/unrun; full30EXCLUDED;oldnormal20notreread;protectedUnreadUNCERTIFIED;
playable100=0,betaHELD,genuineUNSPENT_GATED. Production/GWT/Player N/A;
electrical/population NOT RENEWED; CI NOT RUN, self-hosted prerequisite retained.

Static nested means no implicit outer-instance field. Exactly four explicit
instance fields/stores are priced; primitive VM accessors/allocations are not
claimed. Constructor/fields are private; no direct unmetered setter/accessor.
Only the owner methods expose the handle/raw value with their stated admission.
One-use means one successful finish, not a counted arm borrow. Extra metadata
borrows are allowed and priced3 each; the future caller still owns exact arm
count/order and drops external raw aliases. This contract does not enforce or
qualify that future caller, adversarial mutation or exclusive borrowed lifetime.
Source/design review by receipt_tuple_ledger agrees the fees/order, with these
static/type/one-use clarifications. Actual native gates remain pending.
