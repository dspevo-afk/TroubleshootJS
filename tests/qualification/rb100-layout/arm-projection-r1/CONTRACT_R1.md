# Complete two-arm projection shape contract

Source basis: CurrentResultProjections.arms37-77 (10055B/SHA256
c0398d317419931b7b3b749d3f900ef1a7b27d9c630473412a7bf47595fccd28).
It rejects null ID/Map inputs, creates two ordered5-entry arm maps, then the
ordered2-entry outer arms Map. It borrows nested Maps without validating content.
The prior three-file coverage audit remains in COVERAGE_AUDIT_R1.md.

After all prior correlations including the cohort check, use the existing borrowed
arms/nullArm/observeArm references. Census exact outer keys null,observe; then
null arm keys armRole,armId,freshRun,observation,cleanup; then observe arm keys in
that same order. All three exact key censuses precede the new member reads.
Next read null ID via existing correlationString under its0..256 domain; guard
freshRun then observation with existing correlationRead/correlationMap. Do the
same three member reads for observe. Existing role literals and cleanup Map
guards remain earlier; do not reread them or revalidate nested contents.

New order failures BODY_ARM_PROJECTION_ORDER, ID failures
BODY_ARM_PROJECTION_ID; nested Map type failures retain BODY_CORRELATION_MAP.
Old errors retain precedence: missing outer null/observe still fails its old Map guard;
missing armRole fails BODY_NULL_ROLE/BODY_OBSERVE_ROLE; missing cleanup fails
BODY_CORRELATION_MAP. Missing new fields fail the late key census, not the later
ID/Map reader. Existing cohort errors precede every new check. All new preflight
failures produce zero encoded bytes and preserve tree identities; borrowed
container errors propagate and sink denial precedes controlled work.

Empty IDs, equal IDs, distinct equal-content Strings and empty nested Maps are
supported. There is no ID spelling/hex/nonempty/uniqueness rule or nested run/
observation meaning. ASCII remains owned by the ordinary formatter and may fail
later after output starts. This is not whole DTO, authority, actual ownership/
cleanup, borrowed-lifetime or whole-body zero-output qualification.

The trusted branch uses2 or5 fixed keys; no untrusted loop bound or key array.
Outer census10 hooks plus two inner censuses44 and member reads14 add68 hooks
(4120 total),6 Map gets (65 total); List gets stay1897. Hooks are not native
units. No explicit new Map/List, copy, Meter, group or byte operation. Iterators
follow existing codec pattern. Full export fees are Source inference; actual
same-body R5 count is checked separately. The transform's two insertions are
reversible, preserving all prior bytes/error order and lower writeBody transport.

Only synthetic Map/List/scalars, codec and actual Meter may execute. No actual/
fake arm owner, graph, Session, factory, positive originalDTO/executeArm/R9DTO,
receiptfile, positive prepare/binding, routing, exportV2 or genuine envelope.
All limits/seeds/epochs remain:90s/640/5s/32768/copy1MiB/chunk4096/Source4MiB/
fragment64KiB/opJSON8MiB/result1MiB/depth64/key1m/class10k256MiB/compile-suite60s/
reader180s/wrapper480s/cleanup120s. full30 EXCLUDED; HARD/PSYCHOTIC unavailable/
unrun; oldnormal20 historicalread/notreread; protectedUnread UNCERTIFIED;
playable100=0; beta HELD; genuine UNSPENT_GATED. Production/GWT/Player N/A,
electrical/population NOT RENEWED, CI NOT RUN.
