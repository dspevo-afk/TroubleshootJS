# Qualified receipt tuple: finite metadata contract

This is a metadata consistency prerequisite, not factory/SOURCE/file authority,
borrowed-lifetime or genuine-envelope qualification. Only ordinary synthetic
Map/scalar values and the actual current Meter may execute in its new tests.
No positive prepare/binding, receipt file, factory, graph, Session, actual/fake
arm owner, original DTO, routing or genuine run is authorized by this component.

CurrentAssemblyMetadata.prepare calls validateReceiptTuple immediately after
priorShape and before copy(12), receipt preparation or body replacement. Existing
argument/header/body-slot/outer-shape precedence stays intact. The helper's
non-null outer Map is normally already validated by priorShape; direct fixtures
exercise the helper alone. Null Meter fails METADATA_ARGUMENTS without a charge.

All listed operations cost one actual SetupMeter unit before observation. Error
codes have the prefix METADATA_RECEIPT_. The successful helper costs81 units,
zero copied entries/chunks and no explicit group transition:

| Unit | Observation/control | Failure suffix |
| --- | --- | --- |
| 1 | prior is non-null | PRIOR |
| 2 | prior.get(priorOriginalReceipt) | propagated lookup failure |
| 3 | nested receipt is exactly LinkedHashMap | MAP |
| 4 | receipt.get(receiptPath) | propagated lookup failure |
| 5 | String op-1.jsonl equality | PATH |
| 6 | receipt.get(receiptBytes) | propagated lookup failure |
| 7 | Integer type | BYTES |
| 8 | Integer.intValue | BYTES |
| 9 | inclusive range1..8388608 | BYTES |
| 10 | numeric equality with supplied long expectedBytes | BYTES |
| 11 | receipt.get(receiptSha256) | propagated lookup failure |
| 12 | String type | SHA |
| 13 | String.length | SHA |
| 14 | length64 | SHA |
| 15..78 | charAt(0..63) and lowercase-hex control | SHA |
| 79 | equals expectedSha | SHA |
| 80 | prior.get(operation1Sha256) | propagated lookup failure |
| 81 | same SHA equality (String.equals enforces type) | OPERATION |

An invalid character at indexi fails after15+i units. Missing/wrong nested maps
fail at3; path at5; wrong count type at7; out-of-range count at9; unequal expected
count at10; wrong SHA type at12; wrong length at14; expected SHA mismatch at79;
operation SHA mismatch at81. Each failure preserves the borrowed tree and causes
no application copy or live mutation. Meter admission failure propagates before
the corresponding observation. No new budget or second ledger is introduced.
The helper intentionally does not duplicate full schema/ordinal/outer validation.

The existing prepare null-expectedSha argument guard still runs before the helper.
For direct helper fixtures, null expectedSha rejects at79; malformed expected
counts reject through equality at10. Bounds come from the binding's existing
8MiB cap, not the input positiveInt contract. Exact LinkedHashMap policy comes
from the current input kind/shape contract. Stable exclusive borrowing remains
the caller's responsibility; equal metadata is not source authenticity.

The current-only pure Source transform pins the exact9970-byte metadata owner,
SHA256a98ea9290f8fcdb86be4377698acbe012292972c56016383a9f2684bd40b9c7c,
inserts one helper fragment and one call, and conserves all remaining bytes.
It owns no filesystem, CLI, class-loading or producer interface. Its caller must
use existing admitted Source I/O; incompatible inputs reject before any return.

Required gates: independent Source/debit review; pinned pure Source positive and
negative/inverse tests; final-source JDK8target7 compile; synthetic per-field,
type/range/hex-index/error-precedence/debit/no-copy/no-mutation cases; admitted
outer reads at2/80 and denied admission before reading; relevant retained gates
or audited unchanged evidence. Publish exact tested bytes and truthful scope.
Production/GWT/Player N/A, electrical/population NOT RENEWED, CI NOT RUN.
Keep all limits, exact seeds/epochs and independent oracles. Full30 EXCLUDED,
HARD/PSYCHOTIC unavailable/unrun, old normal20 not reread, protectedUnread
UNCERTIFIED, playable100=0 and beta HELD. Genuine envelope remains UNSPENT_GATED.
