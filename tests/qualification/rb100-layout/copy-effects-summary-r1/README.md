# Copy-effects summary and fixed scope preflight

Current higher fixtures use the [complete arm projection check](../arm-projection-r1/README.md)
with65 Map gets/4120 hooks. Earlier frozen acceptance stays attached to its
recorded candidate; independent lower transport oracles are unchanged.

Current higher fixtures use the [cohort projection check](../cohort-projection-r1/README.md)
with59 Map gets/4052 hooks. Earlier frozen acceptance remains attached to its
recorded candidate; lower transport oracles are unchanged.

Current higher fixtures/canaries now use the [nested cleanup schema check](../cleanup-schema-r1/README.md)
with47 gets/154 hooks. Prior frozen acceptance and pure Source stages remain
attached to their recorded candidates; lower transport oracles are unchanged.

The existing writeCorrelatedBody entry checks the recorded copy-effects summary
against its two source strings and requires the fixed ORIGINAL_CAPTURE_COPY_ONLY
scope, after geometry validation and before encoding. Matching false is valid.
This checks metadata agreement, not actual copy effects or external state.

The private String reader now takes an error code. Its old signature is retired;
all six geometry calls preserve BODY_GEOMETRY_STRING. The two effects fields use
the same existing BODY_STRING_LIMIT256; scope uses the existing fixedString owner.
Four gets and their controls add13 hooks/pass,39 over three; total152hooks/46gets.
Hooks are not native units. No Meter/group/copy/byte/mutation operation is added.
Lower writeBody and independent1177/6370 transport oracles remain unchanged.

Matching empty/short/256-character printable strings can pass. No new SHA-shape
rule is added. The unchanged formatter owns ASCII validation afterward and may
reject matching invalid strings after output starts. New type/length/scope/
summary failures precede encoding; whole-body preflight is not qualified.

`derive_copy_effects_summary.derive(codec, checks, reader)` accepts only pinned
31250-byte codec/SHA256193b8b3d56aa909a87c825055c6f4fd47502e75107318eb936c5888de4fa1d64
and two reviewed fragments. Inverse recovery conserves all other bytes. The pure
API owns no I/O, CLI, class-loading or producer interface. Existing caller owns
admitted Source I/O. CONTRACT_R1.md defines ordering, errors and limits.

Run12 Source tests:

    python -I -B test_copy_effects_summary_source.py --codec-source <pinned-current-CurrentV2CanonicalCodec.java> -v

Run independent literal tests in current-body-correlations-r1 with its documented
command. Native replay needs the exact retained closure and JDK8target7; the
private host is not a standalone clean-clone build entrypoint. The new
CopyEffectsSummaryCanary accepts no arguments. Fixtures are ordinary synthetic
Map/List/scalars; only codec and actual Meter execute.

No receiptfiles/positiveprepare/binding/factory/actualorfake arm owner/graph/
Session/positiveoriginalDTO/executeArm/R9DTO/routing/exportV2/genuine envelope.
Full export fees remain Source inference; actual same-body count is separate.
Actual effects/full-result semantics/authority/lifetime remain unqualified.
GWT/Player N/A, electrical/population NOT RENEWED, CI NOT RUN. Budgets, exact
seeds/epochs and exclusions unchanged: full30 EXCLUDED, HARD/PSYCHOTIC
unavailable/unrun, oldnormal20 not reread, protectedUnread UNCERTIFIED,
playable100=0, beta HELD, genuine UNSPENT_GATED.
