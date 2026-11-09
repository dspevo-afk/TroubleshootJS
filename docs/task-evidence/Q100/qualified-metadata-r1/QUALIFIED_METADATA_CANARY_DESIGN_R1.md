# Qualified metadata native canary R1

Status: SOURCE ONLY. No generation, compile, native invocation, tests, factory
invocation, producer/Original/Session/routing execution, DATA/journal access,
staging, commit, push or notification was performed by this author. Root owns
source acceptance, paired Python consumer evidence and any later execution.

The only authored files are `QualifiedMetadataCanary.java` and this design.
Existing repository edits and all other component/driver sources are preserved.
The admitted source inventory is the fifteen rows of `SOURCE_INPUT_R1.json`;
`COMPONENT_CONTRACT_R1.md` supplies the component contract. The relevant reviewed
implementation owners are CURRENT_STUDY, SHARED_OWNER, CURRENT_RECEIPT_BINDING,
CurrentV2InputSetup, CurrentV2CanonicalCodec, context_codec and the synthetic
fixture definition in test_context_codec. No replacement owner or product codec
is introduced.

## Deferred native protocol

Class: `com.lushprojects.circuitjs1.client.QualifiedMetadataCanary`.
Arguments: exactly `SMOKE <empty-directory>` or `SUITE <empty-directory>`.
Root must supply a unique existing canonical empty Windows directory owned by
this invocation, and compile only its separately reviewed synthetic factory.
The canary refuses non-Windows execution and uses the existing R5 file identity
implementation for the root chain. This is a component canary, not a whole-host
authentication or FileShare qualification gate.

Each invocation emits exactly one compact JSON object followed by one platform
newline on stdout. Its root keys, in order, are `mode`, `status`, `names`, `cases`.
`mode` is SMOKE or SUITE; status is PASS or FAIL. Names are the frozen ordered
source names below. `cases` contains one ordered record per case. Every record
field is scalar; no assertion count is declared or reconstructed. A protocol
failure yields a scalar `protocol` FAIL record and exit 1. Any case/cleanup
failure produces FAIL and exit 1. The runner must reject missing/extra names,
records, malformed output or a mismatching exit status independently.

Common record fields: `name`, `status`, `phase`, `operationNanos`, `cleanupNanos`,
`reason`, `suppressedFailures`. The last field is an integer count, never an array.
`operationNanos` includes fixture setup, native setup, component assertions,
caller close/final verification, and optional fixture serialization. Fixture I/O
is excluded from the actual producer clock: receipt creation, actual file
read/hash, path/block oracle, mtime observation and Parent construction precede
`started = System.nanoTime()`. Cleanup has a separate timer and failure outcome.
Additional observations are scalar: expectedFailure, pathEntries, hashBlocks,
deltaUnits, deltaCopiedEntries, deltaCopyChunks, metadataOpen,
qualifiedPriorBytes, qualifiedPriorSha256.

SMOKE names, in order (7):

1. receipt_1_byte
2. receipt_4096_bytes
3. receipt_4097_bytes
4. receipt_sha_mismatch
5. receipt_size_mismatch
6. wrong_started
7. repeat_slot_rejected

SUITE names, in order (17):

1. null_arguments
2. body_wrong_class
3. body_missing_slot
4. body_nonnull_slot
5. context_short
6. context_uppercase
7. context_nonhex
8. input_data_null
9. input_data_wrong_class
10. prior_null
11. prior_wrong_class
12. prior_wrong_order
13. prior_nonstring_key
14. prior_missing_key
15. prior_extra_key
16. receipt_changed_after_close
17. different_valid_context_is_caller_precondition

## Independent expectations

The factory is Root's future synthetic `CohortTerminalTelemetryInputV2`, with
literal context SHA
`ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f`.
The canary does not generate, embed, read or infer actual DATA. An adapter forwards
the four static factory APIs directly to actual CurrentV2InputSetup.read. Native
setup uses actual Parent and SetupMeter; it must return 10007 units, 478 copied
entries, 3 copy chunks, one actual closed group. These expectations come from the
specified synthetic fixture/helper contract, not from the observed subject.

Every receipt is a real task-created file named `op-1.jsonl`, initially filled
with ASCII x and ending with LF. Empty receipt success is deliberately absent:
the existing receipt owner requires expectedBytes >= 1. Before the component
call, `P = receipt.toRealPath().getNameCount() + 1` is computed independently,
including the Windows root. `B = (size + 4095) / 4096` uses the independently
selected fixture size. Neither oracle reads private ticket paths or counters.

For every successful prepare, actual meter delta must be:

- units: `268 + 10P + 6B`;
- copied entries: `2P + 21`;
- copy chunks: `5`.

This combines native helper overhead 152 units/13 entries/2 chunks with existing
receipt staging 116+10P+6B units/2P+8 entries/3 chunks. The component leaves actual
metadata open, groups still 1, groupUnits equal the formula, setup max-group
counters unchanged, actual groupMeasures still the one setup group, unchanged
copy allowance, zero prior/in-flight/constructor counts, false Original owner
flags and exact meter start. The test reads actual meter fields and actual group
measures; it creates no producer ledger or fabricated owner/arm/ticket.

The body starts with ordered keys before/qualifiedPrior/after and sentinel
values. Prepare must replace only the null slot while preserving key order.
The new qualified map must have exactly these twelve keys in order:
schema,inputContextSha256,sourceParent,manifestSha256,sourceManifestSha256,
readerSha256,resultSha256,operation1Sha256,parentReceiptSha256,priorRunId,
priorOriginalReceipt,trialDisposition. Schema is independently fixed to
`tsj-cohort-terminal-qualified-prior-v2`. All ten input prior values preserve
object identity, explicitly including the receipt and trial nested maps. The
input remains ten keys in exact order, and its entire serialized projection
must remain unchanged. The qualified top map must be a new exact LinkedHashMap.

Only the harness calls Meter.finish, verifies the resulting second actual group
and consumes the returned actual ReceiptContentBinding. Final verification may
not charge, copy, reopen metadata or change counters. The repeat-slot case first
proves ordinary success, then expects METADATA_BODY_SLOT, unchanged body and four
additional actual units; it retains/consumes the original ticket.

SHA/size/start mismatch cases require the actual BindingFailure type and exact
INITIAL_SHA_MISMATCH / INITIAL_LENGTH_MISMATCH / PREPARE_AUTHORITY_MISMATCH code.
Body snapshots preserve order, value identity and nested serialized content on
every rejection. Null argument guards exercise all six nullable parameters and
require no work or metadata opening. Other guards expect exact METADATA_BODY_SLOT,
METADATA_CONTEXT_SHA, METADATA_INPUT_DATA or METADATA_PRIOR_SHAPE codes.

Two malformed SetupResult wrappers and malformed shallow-copied input maps are
negative harness probes only. The genuine validated setup is retained unchanged
and still comes from actual InputSetup. These invalid wrappers never count as a
successful validated input. No fabricated OriginalArm, ArmResult, graph or
product result is constructed. Nested prior semantic invalidity is deliberately
left to the pure consumer contract; the native component assumes an unmutated
validated setup and does not revalidate deep prior semantics.

The valid-but-different lowercase context SHA case deliberately succeeds with
64 zeroes in the staged map. The component is a syntax/compatibility seam; current
context equality is a caller precondition, and the paired pure consumer must
reject a differing expected SHA. Claiming native host authentication from this
argument would be incorrect.

The changed-receipt case prepares normally, has the harness close metadata,
changes one byte without changing length, restores the observed original mtime,
then requires the actual ticket's FINAL_SHA_MISMATCH. Restoring mtime intentionally
retains the content-hash challenge beyond the ordinary identity/mtime guard;
the harness does not modify the receipt owner or ticket. Failures remain failures.

## Synthetic paired-consumer fixture and resources

Only successful SMOKE receipt_4096_bytes writes `<root>/qualified-prior.json`.
It contains the new qualified map in exact insertion order, compact ASCII JSON
plus exactly one LF, bounded to 1 MiB. Runtime records report its actual byte
length and SHA-256. Root should parse it read-only and compare exact bytes/content
with the existing pure qualified-prior producer on the same synthetic context.
No product codec is replaced: the canary's serializer is test-only, bounded to
1 MiB and depth 32, rejects cycles/non-ASCII/non-string object keys and unsupported
number types, and exists only for fixture emission and assertions. It is not
whole-result codec, alias, export or publication evidence.

Each case creates exactly `<root>/<case-name>/op-1.jsonl`. Cleanup attempts finish
and each exact successfully created file/directory independently, even after a
failure; there is no recursive deletion or process launch/termination. A clean
SMOKE run leaves only qualified-prior.json; a clean SUITE run leaves its root
empty. Root owns those roots and the retained JSON after invocation. Suppressed
cleanup errors cause FAIL and are counted; they are never treated as PASS.

Limits remain unchanged: 90-second process, 640 groups, 5-second group,
32768 units/group, 1 MiB copied entries, 4096-entry chunks and all admitted source,
class, process and cleanup caps. Normal assembly remains closed. This source
delivers no actual host proof, genuine original capture, NULL/OBSERVE proof,
whole-result proof, FileShare qualification, playable100 or beta qualification.
Full30 remains EXCLUDED, HARD/PSYCHOTIC unavailable/unrun, protected unread
UNCERTIFIED, genuine envelope UNSPENT_GATED. Native checks are NOT RUN here.
