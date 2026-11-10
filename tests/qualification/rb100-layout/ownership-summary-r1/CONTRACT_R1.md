# Ownership summary preflight: finite current contract

Append to the current validateCorrelations after its restoration checks, before
encoding. Reuse capture, nullCleanup, observeCleanup and cleanup Map locals.
Preserve the current higher entry point and lower writeBody bytes. These checks
prove metadata agreement, not actual owner release or absence of aliases.

Read/type-check/unbox these14 strict Boolean leaves in this order:

1. capture.ownersReleased
2. cleanup.originalCaptureOwnersReleased
3. nullCleanup.adapterSessionAliasDropped
4. nullCleanup.adapterSearchAliasDropped
5. nullCleanup.adapterCandidateAliasDropped
6. nullCleanup.adapterRouteResultAliasDropped
7. nullCleanup.adapterBoardTemplateAliasesDropped
8. cleanup.nullArmAliasesDropped
9. observeCleanup.adapterSessionAliasDropped
10. observeCleanup.adapterSearchAliasDropped
11. observeCleanup.adapterCandidateAliasDropped
12. observeCleanup.adapterRouteResultAliasDropped
13. observeCleanup.adapterBoardTemplateAliasesDropped
14. cleanup.observeArmAliasesDropped

Missing/null/other types fail BODY_OWNERSHIP_BOOLEAN at the first bad leaf. Read
all14 before summary comparison. Reduce each five-flag group with four eager
conjunctions; then compare capture copy, null aggregate and observe aggregate in
that order. Mismatches fail BODY_OWNERSHIP_CAPTURE, BODY_OWNERSHIP_NULL and
BODY_OWNERSHIP_OBSERVE respectively. Type failures preempt all new comparisons;
capture mismatch preempts null, which preempts observe. Previous header/context/
role/duration/restoration checks retain precedence. No requirement that flags
are true: matching false diagnostic metadata is valid.

Parameterize the existing private Boolean reader with a code constant. Its five
restoration calls pass BODY_RESTORATION_BOOLEAN; new calls pass
BODY_OWNERSHIP_BOOLEAN. Retire the old private signature; no duplicate reader or
wrapper API. Sink admission still precedes each read/type/unbox/control.
Additional14gets+14types+14unboxes+8AND+3equal=53 hooks per successful pass,
159 across three passes; total correlations106 hooks/34 gets. Hooks are not
native Meter units. Existing block-zero admission, fees, copies, groups and
exclusive stable borrowing remain; no bytes or mutation before validation.

Migrate current higher fixtures with existing producer fields: base capture
ownersReleased true; both arm-cleanup five flags true after contextRestored;
summary ownership fields true after existing restoration fields. Keep all maps
disjoint and literals independently explicit. Body canary changes only20->34
gets/label, retaining60cases/210assertions. Restoration canary changes only20->34
gets/label and53->106 hooks/label, retaining69cases/253assertions and old errors.
Python expectations migrate; its3 tests and exact lower1177/6370 Source pins
remain. Preserve original snapshots and all prior failures.

Independent cases: both capture ownership values with matching copy and flipped
copy rejection; all32 input masks for each arm separately, only31 yields true,
plus flipped-summary rejection. Equations are orthogonal, so no2048 cross-product
is required. Include14leaf missing/null/String/Integer cases, type-before-new-
comparison and comparison precedence, prior errors, zero bytes/tree mutation,
fresh admissions,106 hook count,34 gets, and actual R5 count equivalence.

Pure Source transformation pins exact25156-byte codec SHA256
344223e4f3d12bc9c1bb219bca80a8c711ce99aed8fe4b309c1043da6b5f4dc4 and reviewed
fragments. Change only the reader and its5 old calls plus appended checks; inverse
recovery proves conservation, including lower transport suffix. No filesystem,
CLI, class-loading or producer interface. Final-source JDK8target7, private/public
Source and independent literal gates, migrated60+69, retained63+113 native,
exact public/index bytes and selected process/input receipts are required.

Only synthetic Map/List/scalars, codec and actual Meter execute. No receipt
files, positive prepare/binding, factory, actual/fake arm owner, graph, Session,
positive original DTO, routing, exportV2 or genuine envelope. Full export fee/
copy equations are Source inference; real count equivalence is separately tested.
Whole-result, actual release, authority and lifetime remain unqualified. All
budgets, exact seeds/epochs and exclusions remain: full30 EXCLUDED, HARD/PSYCHOTIC
unavailable/unrun, old normal20 not reread, protectedUnread UNCERTIFIED,
playable100=0, beta HELD, genuine UNSPENT_GATED. GWT/Player N/A, electrical/
population NOT RENEWED, CI NOT RUN.
