# Nested cleanup schema discriminator

Current higher fixtures use the [qualified-prior envelope check](../prior-envelope-r1/README.md)
with65 Map gets/4214 hooks and a twelve-key wrapper. Synthetic unused nulls do
not qualify actual setup/receipt data. Earlier frozen acceptance stays attached
to its candidate; independent lower transport oracles remain unchanged.

Current higher fixtures use the [complete cleanup projection check](../cleanup-projection-r1/README.md)
with65 Map gets/4162 hooks and producer cleanup order. Earlier frozen acceptance
stays attached to its candidate; independent lower transport oracles are unchanged.

Current higher fixtures use the [complete arm projection check](../arm-projection-r1/README.md)
with65 Map gets/4120 hooks. Earlier frozen acceptance stays attached to its
recorded candidate; independent lower transport oracles are unchanged.

Current higher fixtures use the [cohort projection check](../cohort-projection-r1/README.md)
with59 Map gets/4052 hooks. Earlier frozen acceptance remains attached to its
recorded candidate; lower transport oracles are unchanged.

The existing writeCorrelatedBody entry now requires cleanup.schema to equal the
producer's tsj-cohort-terminal-cleanup-v1 after all previous correlations, before
encoding. A distinct String object with identical content passes. Missing/null/
non-String/other tags fail BODY_CLEANUP_SCHEMA without output or mutation.

The change reuses cleanup, correlationRead and requireFixedString. One get plus
one combined fixed-string control adds2 sink hooks/pass,6 over three; total154
hooks/47gets. Hooks are not native units. No new helper/API/Map guard, Meter/copy/
group/byte/mutation work or limit is added. The pinned pure transform inserts
only the check; inverse recovery conserves every other byte, including prior
error order and lower writeBody transport. It owns no I/O/CLI/producer interface.

| Gate | Result and scope |
| --- | --- |
| Source API | PASS:8 private and8 final public focused tests: pins/insertion, inverse/prefix/lower conservation, changed/truncated/type/cap/already-derived inputs and purity |
| Independent literals | PASS:3 private and3 public tests; directly authored nested tag/order, two-block body and exact lower oracle identities |
| Final-source JDK8 | PASS:8 Sources,75 classes/205351 bytes,2556 dependencies/12265717 bytes, all major51, no shadows |
| New native | PASS:21 cases/63 assertions:1 fresh equal-content tag,9 invalid tags,11 representative earlier-phase precedence cases including all4 effects failures |
| Migrated native | PASS:60/210,69/253,209/566,125/344,54/161 cases/assertions, preserving coverage with47 gets/154 hooks |
| Retained native | PASS:63 lower-codec cases/94 assertions and113 receipt-tuple cases/679 assertions |
| Shared harness paths | PASS in five freshly run migrated suites:154 hooks/47 fresh gets, admission denial and actual wide R5 same-body count equivalence |
| JVM argument | Exact CANARY_ARGUMENTS exit1; expected rejection, not positive PASS |
| Review | Root integration and separate bounded transform/contract review; reviewers ran no tests |
| Final receipt/input audit | PASS:14 receipts reauthenticated:13 exit0 and1 exact argument rejection |

Unique native total:714 cases/2370 assertions. The new small canary focuses on the
new discriminator and late precedence; it does not duplicate the old Meter/
admission/count harness. Root independently counted21/63 before execution. Every
negative case requires exact error, zero bytes and unchanged tree identities.
Seven fixture Sources place cleanup.schema first in maps, directly authored
minimal/bodyJson/wide Java literals and independent Python expectations. Five old
canaries change only get/hook targets and labels. Other fields/order, disjoint
maps, padding and independent lower1177/6370 oracles remain. No serializer output
was blessed. The final corrected predecessor canary56644b0b... was the input;
its old missing-helper Source and failed compile remain preserved at that
predecessor, with no reclassification. No new runtime failure occurred here.

Compilation used pinned JDK8 `javac -source 7 -target 7 -encoding UTF-8
-Xlint:-options -classpath <frozen-current-closure> -sourcepath <empty> -d <output>`
with emitted codec, migrated BodyCorrelationOracle, five existing current canaries
and new CleanupSchemaCanary. Those six canaries plus lower codec and receipt
tuple canaries ran without arguments; rejection targeted CleanupSchemaCanary.
Python commands are in component READMEs. Native replay requires the exact
retained closure; the private host is not a standalone clean-clone entrypoint.

Operation and wrapper elapsed times are separate in the proof. Selected child
termination and task-root/log absence are proven; isolated cleanup duration is
unmeasured. No persistent process/listener was introduced. Existing uncertain
resources, hardlinks, scratch and failures remain preserved.

This is a nested version discriminator only: full nested shape/cardinality and
whole-result validity remain unqualified. The unchanged ASCII formatter may
reject other fields later after output begins. Actual effects/geometry/SHA shape,
authority and exclusive borrowed lifetime are unqualified. Actual same-body R5
count metrics pass; full exportV2/write/compare/publication NOT RUN and complete
export fee/copy equations Source inference. No receiptfiles, positive prepare/
binding, factory, actual/fake arm owner, graph, Session, positiveoriginalDTO,
executeArm/R9DTO, routing or genuine envelope. Production/GWT/Player N/A,
electrical/population NOT RENEWED, CI NOT RUN. All90s/640/5s/32768/copy1MiB/
chunk4096/Source4MiB/opJSON8MiB/result1MiB/depth64/key1m/class10k256MiB/
compile-suite60s/reader180s/wrapper480s/cleanup120s limits, exact seeds/epochs and
exclusions unchanged: full30 EXCLUDED, HARD/PSYCHOTIC unavailable/unrun,
oldnormal20 not reread, protectedUnread UNCERTIFIED, playable100=0, beta HELD,
genuine UNSPENT_GATED.

Proof SHA256:238f9dbe97cf221c8d5c281f66778bc1a3dc932078e8599055fc67fdb6355a75.
Next: freeze the next complete producer-backed projection contract before any
further implementation. Publication/notification recorded in PUBLICATION_R1.json.
