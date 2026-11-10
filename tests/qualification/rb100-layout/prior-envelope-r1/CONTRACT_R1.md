# Qualified-prior assembly envelope contract

Source basis: CurrentAssemblyMetadata.prepare110 and private priorKey selector
in authenticated12486B/SHA256
49f9930a4e1d8aed297236dd4ee742233c73cffcded9f495fd5815f58d11687b.
It creates a LinkedHashMap, puts schema/context then copies ten prior fields in
fixed order after shape/receipt tuple checks. Its caller owns validated unchanged
setup; copied values' types/authority/receipt contents are not proved here.
SOURCE_SELECTION_R1.md preserves the pre-implementation Source finding.

After ALL existing correlations, including cleanup shape, reuse the existing
qualified-prior Map. Census exactly twelve keys: schema,inputContextSha256,
sourceParent,manifestSha256,sourceManifestSha256,readerSha256,resultSha256,
operation1Sha256,parentReceiptSha256,priorRunId,priorOriginalReceipt,trialDisposition.
Use trusted12/switch with the existing prepaid iterator/fixed-string pattern.
After validating the first key schema, prepay entry.getValue and check against
CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA. Reuse that one authoritative
constant: tsj-cohort-terminal-qualified-prior-v2. No new key array or Map/List get.

New failures: BODY_PRIOR_ENVELOPE_ORDER for key shape and
BODY_PRIOR_ENVELOPE_SCHEMA for schema value. All earlier errors retain precedence.
Missing inputContextSha256 -> BODY_PRIOR_CONTEXT; missing trialDisposition ->
BODY_CORRELATION_MAP in cohort validation. Missing other keys reaches the late
census. Missing schema -> ORDER, not SCHEMA. Present bad schema is checked after
its key but before later-key shape, so bad schema plus a later bad key -> SCHEMA.
No schema value read happens for a bad first key. All new failures precede bytes;
ordinary borrowed container errors propagate, with sink denial before observation.

This qualifies only the outer envelope and fixed wrapper tag. Unused copied
values may be null in synthetic fixtures under this bounded codec contract;
that does not qualify actual setup, receipts, field types, SHA/ID shapes or
authority. Preserve existing context/trial objects and all other fixture values.
No nested prior/receipt semantics, one-field validator loop or full DTO rewrite.

Census costs iterator1 +12*4 +terminal1 =50 hooks, plus schema entry value1 and
fixedString1 =52 new hooks/pass,156 over three. Accepted N28 baseline4162/65/1897
implies4214 hooks/65 Map gets/1897 List gets, first pair admission195 unchanged.
Fresh independent Source/native gates must confirm. Hooks are not native units;
no explicit new collection/copy/Meter/group/byte/mutation. Iterator pattern stays.
Exact inverse of two pinned insertions preserves all prior bytes/errors/lower
transport. Actual same-body R5 count is separate; full export/write/compare/
publication NOT RUN and complete export fees remain Source inference.

Migrate current higher fixture envelopes/Java literals/Python expectation directly,
preserving work padding/two blocks, existing cases/assertions and lower1177/6370
oracles. No producer-generated golden or stale pre-fix PASS. Synthetic Map/List/
scalars, codec and actual Meter only. No actual/fake arm owner, graph, Session,
factory, positive originalDTO/R9DTO, genuine receiptfile, prepare/binding, routing/
exportV2 or genuine envelope. No actual cleanup truth, capture mapping, authority,
borrowed lifetime, full DTO or whole-body zero-output; inner/ASCII may fail later.
All90s/640/5s/32768/copy1MiB/chunk4096/Source4MiB/fragment64KiB/opJSON8MiB/
result1MiB/depth64/key1m/class10k256MiB/compile-suite60s/reader180s/wrapper480s/
cleanup120s bounds and exact seeds/epochs unchanged. full30 EXCLUDED; HARD/
PSYCHOTIC unavailable/unrun; protectedUnread UNCERTIFIED; oldnormal20 not reread;
playable100=0; beta HELD; genuine UNSPENT_GATED. Production/GWT/Player N/A,
electrical/population NOT RENEWED, CI NOT RUN.
