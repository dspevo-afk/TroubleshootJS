# Copy-effects summary and fixed scope preflight

The current writeCorrelatedBody entry now checks the recorded copy-effects
summary against its two source strings and requires ORIGINAL_CAPTURE_COPY_ONLY
scope, after geometry validation and before encoding. Matching false is valid.
This proves metadata agreement, not actual external effects or playable routing.

The existing private String reader now takes an error code; its old signature is
retired, while all six geometry calls preserve BODY_GEOMETRY_STRING. Both new
fields reuse BODY_STRING_LIMIT256; Boolean and fixedString owners are unchanged.
Two String reads6 +Boolean3 +scopeget/fixed2 +two comparisons2 add13 sink hooks
and4 gets/pass,39 over three; total152hooks/46gets. Hooks are not native units.
No Meter/copy/group/byte/mutation operation is added. Inverse recovery conserves
all other Source bytes, including the lower writeBody suffix.

Matching empty/short/256-character printable strings can pass. No SHA-format
rule is imposed. The unchanged formatter validates ASCII afterward and may
reject matching invalid strings after output starts. Zero-output claims cover
specific new type/length/scope/summary failures, not whole-body preflight.

| Gate | Result and scope |
| --- | --- |
| Source API | PASS:12 private and12 final public tests; pins, incompatible inputs, single reader/old errors, inverse and lower conservation |
| Independent literals | PASS:3 private and3 public tests; directly authored fields, two-block body and exact lower oracle identities |
| Initial JDK8 attempt | FAIL, preserved: new test canary called a missing removeTemplate helper;924.98ms operation,1176.30ms wrapper, termination/root absence proven |
| Final-source JDK8 R2 | PASS:7 Sources,71 classes/193892 bytes,2556 dependencies/12265717 bytes, all major51, no shadows |
| New native R2 | PASS:54 cases/161 assertions; equality/flips, malformed leaves, String bounds, Boolean/scope cases, precedence, hook/admission/count |
| Migrated native R2 | PASS:60/210,69/253,209/566,125/344 cases/assertions, preserving coverage with46 gets/152 hooks |
| Retained native R2 | PASS:63 lower-codec cases/94 assertions and113 receipt-tuple cases/679 assertions |
| JVM argument R2 | Exact CANARY_ARGUMENTS exit1; expected rejection, not positive PASS |
| Review | Root integrated Source/fixture/canary review and separate bounded transform/contract review; reviewers ran no tests |
| Final receipt/input audit | PASS:14 receipts reauthenticated:12 exit0,1 exact argument rejection and1 preserved initial compile failure |

The failure was corrected by replacing the one missing test helper call with
existing child-map removal. No assertion, case, codec, budget or oracle changed.
Original29891-byte canary/SHA2566d149a319b1edffe47a75065b7ae32294adea46cfab36e335a426529ed2d0c12
and failed request/output receipts remain private. Final29920-byte Source/SHA256
56644b0b73268919a71b30177994ef12d973fed387169700b529eb08b81620b2 is bound by
FINAL_SOURCE_FREEZE_R2.json and the final public manifest. All seven Sources were
rebuilt in a separate output directory; all native suites then ran fresh. The
initial failure remains FAIL, not an expected rejection or PASS. Source/literal
evidence is reused only for audited unchanged inputs; public checks ran fresh.

Unique native total:693 cases/2307 assertions. Root independently counted54/161
before execution; the correction leaves this ledger intact. Explicit equal and
distinct synthetic strings establish true/false outcomes independently of the
implementation. New failures assert exact codes, zero bytes and unchanged tree
identities. Six higher fixture Sources add existing producer fields directly to
maps, manual minimal/bodyJson/wide literals and independent Python expectations.
Four old canaries change only get/hook expectations and labels. Disjoint maps,
padding and independent lower1177/6370 oracles remain; no serializer golden was
blessed. Historical snapshots and all prior failures remain preserved.

Final compilation used pinned JDK8 `javac -source 7 -target 7 -encoding UTF-8
-Xlint:-options -classpath <frozen-current-closure> -sourcepath <empty> -d <output>`
with the emitted codec, migrated BodyCorrelationOracle, four existing current
canaries and final CopyEffectsSummaryCanary. Those five canaries plus lower codec
and receipt tuple canaries ran without arguments; the rejection targeted the new
canary. Python commands are in component READMEs. Native replay needs the exact
retained closure; the private host is not a standalone clean-clone entrypoint.

Operation and wrapper elapsed times are separate in the proof. Selected child
termination and task-root/log absence, including the failed compile, are proven;
isolated cleanup duration is unmeasured. No persistent process/listener was
introduced. Existing uncertain resources, hardlinks, scratch and failures remain.

Actual same-body R5 count metrics pass. Full exportV2/write/compare/publication
is NOT RUN; complete export fee/copy equations are Source inference. No receipt
files, positive prepare/binding, factory, actual/fake arm owner, graph, Session,
positiveoriginalDTO, executeArm/R9DTO, routing or genuine envelope. Actual effects,
SHA shape, full semantics, authority and stable exclusive borrowed lifetime remain
unqualified. Production/GWT/Player N/A, electrical/population NOT RENEWED, CI NOT
RUN. All90s/640/5s/32768/copy1MiB/chunk4096/Source4MiB/opJSON8MiB/result1MiB/
depth64/key1m/class10k256MiB/compile-suite60s/reader180s/wrapper480s/cleanup120s
limits, exact seeds/epochs and exclusions remain: full30 EXCLUDED, HARD/PSYCHOTIC
unavailable/unrun, oldnormal20 not reread, protectedUnread UNCERTIFIED,
playable100=0, beta HELD, genuine UNSPENT_GATED.

Proof SHA256:d7076dfa9ec8cf8fe7e6b4776a7b1711905a0cd506d4b84697f7f7507fb612eb.
Next unstarted implementation: nested cleanup schema discriminator, after its
producer-backed contract is frozen. Publication/notification recorded in PUBLICATION_R1.json.
