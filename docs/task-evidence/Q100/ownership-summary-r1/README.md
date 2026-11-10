# Ownership summary preflight and current fixture migration

The current writeCorrelatedBody preflight now checks ownership summaries against
their source flags. The copied capture owner-release flag must match
originalCapture.ownersReleased. Each null/observe alias summary must equal the
conjunction of its five adapter alias-drop flags. All14 leaves are strict
Booleans; matching false values are valid. Existing restoration checks precede
these new checks, and no bytes are encoded before validation. This proves
metadata agreement, not actual owner release or absence of aliases.

The pure pinned transform changes only the existing private Boolean reader and
its five old calls, then appends checks. The old private reader signature is
retired; restoration errors keep precedence. Inverse recovery conserves all
other baseline bytes, including lower writeBody transport. Fourteen reads,
types and unboxes,8 eager conjunctions and3 comparisons add53 sink hooks per
pass (159 across three passes). Total correlations106 hooks/34 gets. Hooks are
not native units; no Meter/group/copy/byte operation is added.

| Gate | Result and scope |
| --- | --- |
| Source API | PASS:12 private and12 final public tests, including pinned negative admission, inverse and lower-transport conservation |
| Independent Python literals | PASS:3 private and3 public tests of explicit migrated fields, two-block body and unchanged lower-oracle identities |
| Final-source JDK8 | PASS:5 Sources,45 classes/123711 bytes,2556 dependencies/12265717 bytes, all major51, no shadows |
| New native | PASS:209 cases/566 assertions; capture2 and each arm32 independent truth rows with flipped-summary rejection, malformed leaves, precedence, zero output/tree preservation,106 hooks/34 admitted gets and actual wide R5 count |
| Migrated native | PASS:60 cases/210 assertions and69 cases/253 assertions; original coverage retained with34 get/106 hook expectations |
| Retained native | PASS:63 lower-codec cases/94 assertions and113 receipt-tuple cases/679 assertions |
| JVM argument | Exact CANARY_ARGUMENTS exit1; expected rejection, not positive PASS |
| Source review | PASS:root integration and separate bounded read-only transform/contract review; reviewers ran no tests |
| Final receipt/input audit | PASS:11 selected receipts reauthenticated;10 exit0 and1 exact argument rejection |

Total unique native coverage is514 cases/1802 assertions. The independent arm
oracle uses mask==31, not the implementation's eager conjunction chain. The
independent equations require no2048-row cross-product. The higher fixtures now
contain existing producer ownership fields in disjoint capture/arm-cleanup/
summary maps. Explicit minimal/bodyJson/wide literals and Python expectations
were migrated directly. Current body canary changes only20->34 gets; restoration
canary changes only20->34 gets and53->106 hooks, with labels. Independent lower
1177/6370 transport oracles remain unchanged; no serializer output was blessed.
Prior snapshots and all failures remain preserved.

The selected compile took917.63ms; new native381.52ms, restoration355.54ms,
body365.29ms, lower codec468.00ms and receipt378.71ms. Operation and wrapper
totals are separate in the proof. Selected termination and task-root/log absence
are proven; isolated cleanup duration is unmeasured. No persistent process or
listener was introduced. Existing uncertain resources and private scratch remain.

The actual final-source command used pinned JDK8 `javac -source 7 -target 7
-encoding UTF-8 -Xlint:-options -classpath <frozen-current-closure> -sourcepath
<empty> -d <output>` with the emitted codec, migrated BodyCorrelationOracle,
CurrentBodyCorrelationsCanary, CleanupRestorationCanary and new
OwnershipSummaryCanary. Native classes were those three canaries plus
CurrentV2CanonicalCodecCanary and QualifiedReceiptTupleCanary, without arguments;
the rejection targeted OwnershipSummaryCanary. Python commands are in component
READMEs. Native replay requires the exact retained closure; the private host is
not a standalone clean-clone build entrypoint. Tested public bytes match private
inputs exactly and are bound by SOURCE_MANIFEST_R1.json.

Actual same-body R5 count fees/copies/groups are qualified. Full exportV2/write/
compare/publication is NOT RUN; full export fee/copy equations are Source inference.
Full-result semantics, actual release, caller authority and stable exclusive
borrowed lifetime remain unqualified. No receipt files, positive prepare/binding,
factory, actual/fake arm owner, graph, Session, positive original DTO, routing or
genuine envelope ran. Production/GWT/Player N/A, electrical/population NOT RENEWED,
CI NOT RUN. All90s/640groups/5s/32768units/copy1MiB/chunk4096/Source4MiB/opJSON8MiB/
result1MiB/depth64/key1m/class10k256MiB/compile-suite60s/reader180s/wrapper480s/
cleanup120s limits, exact seeds/epochs and exclusions remain: full30 EXCLUDED,
HARD/PSYCHOTIC unavailable/unrun, old normal20 not reread, protectedUnread
UNCERTIFIED, playable100=0, beta HELD, genuine UNSPENT_GATED.

Proof SHA256:05ff6c5d258cf41150064fa04135609e0380042d6a69e5a2c4e472c32330afdd.
Next unstarted implementation: source-geometry summary agreement at the same
preflight, subject to a frozen producer-backed contract. Genuine execution is
still gated. Publication/notification receipt will be recorded after verified push.
