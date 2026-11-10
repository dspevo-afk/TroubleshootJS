# Source-geometry summary preflight and current fixture migration

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

Current higher fixtures/canaries now use the [nested cleanup schema check](../cleanup-schema-r1/README.md)
with47 gets/154 hooks. Prior frozen acceptance and pure Source stages remain
attached to their recorded candidates; lower transport oracles are unchanged.

Current higher fixtures/canaries now use the [copy-effects summary preflight](../copy-effects-summary-r1/README.md)
with46 gets/152 hooks. Prior frozen acceptance and pure Source stages remain
attached to their recorded candidates; lower transport oracles are unchanged.

The current writeCorrelatedBody preflight now recomputes sourceGeometryUnchanged
from five exact comparisons among six recorded geometry strings. It runs after
ownership checks, before encoding. Matching false is valid. This proves metadata
agreement, not physical geometry preservation or playable routing.

Reviewed current-only Sources establish string comparisons but do not establish
64-lowerhex format for all six output fields. No such requirement is added.
The private String reader reuses existing BODY_STRING_LIMIT256, with prepaid get,
type and length controls. Empty/short/256-character printable strings may pass
when the summary agrees. The unchanged formatter owns printable ASCII afterward;
matching ASCII-invalid strings may fail there after output has begun. SHA shape,
complete DTO semantics and whole-body zero-output preflight are not qualified.
Only the new map/type/length/summary failures are guaranteed before encoding.

The pure pinned transform inserts only checks and one private reader. Its inverse
recovers every other baseline byte, including the lower writeBody suffix. Eight
gets and controls, five equals, four eager conjunctions and one summary control
add33 sink hooks/pass,99 across three passes; total139hooks/42gets. Hooks are
not native units. No Meter/group/copy/byte/mutation operation is added.

| Gate | Result and scope |
| --- | --- |
| Source API | PASS:12 private and12 final public tests; pins, incompatible-input rejection, inverse and lower-suffix conservation |
| Independent Python literals | PASS:3 private and3 public tests; directly authored migrated fields, two-block body and exact unchanged lower oracles |
| Final-source JDK8 | PASS:6 Sources,58 classes/159060 bytes,2556 dependencies/12265717 bytes, all major51, no shadows |
| New native | PASS:125 cases/344 assertions;32 masks with matching/flipped summaries,30 malformed String leaves,4 accepted bounds,4 template types,4 Boolean types,2 type precedences,12 previous errors, hook/admission/count cases |
| Migrated native | PASS:60 cases/210 assertions,69/253 and209/566, preserving coverage with42 gets/139 hooks |
| Retained native | PASS:63 lower-codec cases/94 assertions and113 receipt-tuple cases/679 assertions |
| JVM argument | Exact CANARY_ARGUMENTS exit1; expected rejection, not positive PASS |
| Review | PASS:root Source/fixture/canary integration and separate bounded Source review; reviewers ran no tests |
| Final receipt/input audit | PASS:12 selected receipts reauthenticated;11 exit0 and1 exact argument rejection |

Unique native total:639 cases/2146 assertions. The independent truth oracle uses
mask==31, not the implementation's eager conjunction chain. Root independently
counted125/344 before execution. The six fields use independent synthetic '4'
and '5' strings. Boundary cases explicitly establish the limited String domain.
The new failures assert exact errors, zero bytes and unchanged object identities.

Current higher Java/Python fixtures now contain existing producer geometry fields
in disjoint template/arm-cleanup/summary maps. Minimal/bodyJson/wide literals and
Python expectations were directly authored, not regenerated from serialized output.
Three old canaries change only get/hook expectations and labels. Lower1177/6370
independent oracles, padding and all prior snapshots/failures are preserved.

The actual final-source command used pinned JDK8 `javac -source 7 -target 7
-encoding UTF-8 -Xlint:-options -classpath <frozen-current-closure> -sourcepath
<empty> -d <output>` with the emitted codec, migrated BodyCorrelationOracle,
CurrentBodyCorrelationsCanary, CleanupRestorationCanary, OwnershipSummaryCanary
and new GeometrySummaryCanary. All five canaries plus QualifiedReceiptTupleCanary
ran without arguments; the rejection targeted GeometrySummaryCanary. Python
commands are in component READMEs. Native replay requires the exact retained
closure; the private host is not a standalone clean-clone build entrypoint.

Operation and wrapper elapsed times are separate in the proof. Selected child
termination and task-root/log absence are proven; isolated cleanup duration is
unmeasured. No persistent process/listener was introduced. Existing uncertain
resources, hardlinks, private scratch and failures remain preserved.

Actual same-body R5 count metrics pass. Full exportV2/write/compare/publication
is NOT RUN; complete export fee/copy equations are Source inference. No receipt
files, positive prepare/binding, factory, actual/fake arm owner, graph, Session,
positive original DTO, executeArm/R9DTO, routing or genuine envelope. Actual
geometry preservation, full-result semantics, authority and stable exclusive
borrowed lifetime remain unqualified. Production/GWT/Player N/A; electrical/
population NOT RENEWED; CI NOT RUN. All90s/640groups/5s/32768units/copy1MiB/
chunk4096/Source4MiB/opJSON8MiB/result1MiB/depth64/key1m/class10k256MiB/
compile-suite60s/reader180s/wrapper480s/cleanup120s limits, exact seeds/epochs and
exclusions remain: full30 EXCLUDED, HARD/PSYCHOTIC unavailable/unrun, oldnormal20
not reread, protectedUnread UNCERTIFIED, playable100=0, beta HELD,
genuine UNSPENT_GATED.

Proof SHA256:4dbaf9712dab7a39eaf7170c988aac654c4c3c13479b0edef974ec20a5ba9e90.
Next unstarted implementation: copy-effects summary/scope agreement, after its
producer-backed contract is frozen. Publication/notification recorded in PUBLICATION_R1.json.
