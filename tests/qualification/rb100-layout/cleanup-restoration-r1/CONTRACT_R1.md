# Cleanup restoration preflight: finite current contract

Extend the current codec's validateCorrelations, after context/prior, fixed-role
and cleanup/work-duration checks, before writeBodyValue. Preserve writeBody and
the existing writeCorrelatedBody API; do not add another wrapper entry point.

Read in this order, using the existing nullArm, observeArm and cleanup Map locals:

1. body.originalCapture -> Map, then its contextRestored -> Boolean.
2. nullArm.cleanup -> Map, then its contextRestored -> Boolean.
3. observeArm.cleanup -> Map, then its contextRestored -> Boolean.
4. cleanup.originalCaptureContextRestored -> Boolean.
5. cleanup.contextRestored -> Boolean.

All five leaves must be Boolean; missing, null or other types fail
BODY_RESTORATION_BOOLEAN. Missing/wrong nested Maps use existing
BODY_CORRELATION_MAP. Header/root-container errors and all previous correlation
errors retain precedence. Read and type-check all five leaves before comparing
the summaries; the first bad leaf in the listed order wins.

Compute two Boolean conjunctions explicitly. Then require the copied capture
summary to equal the source capture flag, else BODY_RESTORATION_CAPTURE. Require
the total summary to equal capture AND null-arm AND observe-arm flags, else
BODY_RESTORATION_TOTAL. A capture mismatch takes precedence over a total mismatch.
If a summary type is bad and the copied capture value also mismatches, the type
failure wins because all reads/type controls precede comparison.

Independent truth table (capture,null,observe -> copied capture,total):
FFF/FFT/FTF/FTT -> F,F; TFF/TFT/TTF -> T,F; TTT -> T,T. False combinations remain
valid diagnostic metadata. Agreement does not establish actual restoration.

Call sink.beforeDataOperation before each Map.get, Map guard, Boolean type test,
Boolean unbox, conjunction and equality control. The successful extension adds
8 gets,3 Map guards,5 types,5 unboxes,2 conjunctions,2 equality controls:25 hooks
per pass,75 across count/write/compare. These are not native Meter units. Header
validation already admits block zero at position zero; no new Meter/group/copy/
byte operation is added. Preserve existing fee/copy equations for the same valid
body; reject before encoding any bytes. Exclusive stable borrowing is unchanged.

Migrate the owned higher-level ordinary-map Java/Python fixtures to include the
existing producer fields. Base fixtures use true/true/true and true/true summaries,
with disjoint capture/arm-cleanup/summary Maps. Maintain prior60 cases/210
assertions; update the guard's12 gets to20 (12+8). Derive literal expectations
independently, never from serializer output. Preserve the exact lower transport
1177/6370 literal oracles and all63 lower codec cases unchanged. Old higher
fixtures remain historical evidence, not a current compatibility obligation.

The pure current-only Source transform accepts only23469-byte codec SHA256
e67dccf469fe8c8fe01812dcd28fd47463f0d9e2e4e1c451b9c65497d43c13a5 plus the
reviewed fragments. It inserts the extension and one private Boolean reader,
conserving all other bytes. It owns no filesystem/CLI/class-loading/producer
interface. Required gates: pinned Source/inverse/negative tests, independent
truth-table and migration review, final-source JDK8target7, migrated60 cases,
all8 combinations/tampering/missing/null/wrong-type/precedence/Map/admission/
zero-byte/no-mutation checks, real R5 count equivalence, retained63 lower codec
and113 receipt-tuple cases, independent Python literals, exact public/index
bytes and selected receipts. Full exportV2/write/compare/publication NOT RUN.

Only synthetic Map/List/scalars, codec and actual Meter may execute; no receipt
files, positive prepare/binding, factory, graph, Session, actual/fake arm owner,
positive original DTO, routing or genuine envelope. No electrical/population
renewal, GWT/Player N/A, CI NOT RUN. All budgets, exact seeds/epochs and data
exclusions remain: full30 EXCLUDED, HARD/PSYCHOTIC unavailable/unrun, old normal20
not reread, protectedUnread UNCERTIFIED, playable100=0, beta HELD and genuine
envelope UNSPENT_GATED. Whole-result/authority/lifetime are separate obligations.
