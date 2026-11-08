# Current authored context contract

Normal-only input contract; no DATA contents have been read. The exact20-role
allowlist derives from pinned builder271db07/helper2cebc source and remains
gated by PLAN_R1's reader/source checks. Old D descriptor/result/generated-input DATA
are denied. Prior manifests/inventory are metadata only: never follow their
paths recursively. The prior observation projection is a cross-check, not an
oracle. Seven qualification receipt sizes are bounded512KiB with exact source
SHA pins; their exact contents are defined by SHA, not an initial data snapshot.

Canonical context is compact ASCII JSON plus LF, at most1048576bytes, with exact
ordered keys schema,nativeProjection,physicalContext. Schema is
tsj-cohort-terminal-input-context-v2. It contains no current runId, current
producer manifest SHA or its own SHA. RuntimeIdentity supplies these separately.
Paths stay in trusted root configuration, not canonical semantic context.

nativeProjection has exact ordered keys prior,capture,ownerSettings,seedValues,
sourceIdentity. It is the native factory's data view; only that view is emitted
into generated DATA Java. capture/ownerSettings/sourceIdentity preserve the
existing frozen native data-projection field schemas and validations from the
pinned helper source, ported mechanically without the old descriptor/loader API.
No version resolver or opaque N context object is accepted. All declared keys,
types, ASCII/length/range/alias bounds and immutable source identities validate.

prior is an ordered historical projection with10 keys:
sourceParent,manifestSha256,sourceManifestSha256,readerSha256,resultSha256,
operation1Sha256,parentReceiptSha256,priorRunId,priorOriginalReceipt,trialDisposition.
Historical identifiers remain historical. priorOriginalReceipt has the existing
five keys; path=op-1.jsonl, ordinal307/lineage77 and the pinned original hash/size.
trialDisposition retains the authenticated two-ID cohort and stable requested
ordering. Current result qualifiedPrior is a fresh12-key map adding schema
tsj-cohort-qualified-prior-v2 and runtime inputContextSha256 before these10 fields.
It is never canonical context content, avoiding self-hash or manifest cycles.

seedValues are exact canonical signed-long decimal strings:
rootSeed=9007199254740993; placementSeed=-6587958367974952701;
routingSeed=8445301674744508279. These source-verified literals never pass through
floating point or JavaScript numeric serialization. Preserve request0/attempt4,
pass2,60net IDs and246PAD endpoints. Unknown prior vector/grid/physical values
come only from authenticated pinned oracle parsing, never from returned data.

physicalContext has gridWidth,gridHeight,padLands,qualifiedP07Pads in that order.
PAD rows retain their existing N/P07 schemas from the pinned pure parsers,246
each. Their physical identities agree under the existing independent projection;
net membership/order and grid bounds retain current observation/P07 semantics.
This deliberate pair preserves independent oracle representations from pinned
prior result and operation receipt. It is not a duplicate live electrical graph.

The current normalizer reads only allowlisted roles through existing bounded,
canonical/reparse/identity-aware I/O. Verify exact role SHA and known lengths
before decoding; allow zero-length streams only for pinned receipt roles. Reject
extra/missing closure entries, changed inputs, malformed schemas, bad source/run/
receipt/qualification bindings and failed cleanup. Preserve both original and
current source identities. Use source-extracted pure N/P07 semantics and helper
projection logic; do not execute an old builder, CLI or recursive context loader.

Current generated class CohortTerminalTelemetryInputV2 exposes contextSha256,
dataEntries,dataJson and a fixed context schema tag. Generated Java DATA is at
most1048576bytes, included as DATA in compile/source/class provenance. Its pure
factory returns a fresh nativeProjection with exact admitted entry count. Input
schema/identity/seeds/cohort/owner/static source validation precedes any original
capture. Actual preflight copies/counters belong to the setup snapshot and copy
allowance; source/template/PAD proof later uses the real owner graph.

Hash flow is acyclic: contracts -> canonical context -> contextSHA -> generated
input DATA -> classes/producer manifest -> independent runtime/pre-call identity
-> actual one-original/two-arm producer -> external raw capture. The normal host
authenticates this entire chain before semantic admission and exclusive output.
Synthetic unit/context/transport fixtures cannot qualify genuine OriginalArm or
normal acceptance. Data access and native execution remain gated by PLAN_R1.
