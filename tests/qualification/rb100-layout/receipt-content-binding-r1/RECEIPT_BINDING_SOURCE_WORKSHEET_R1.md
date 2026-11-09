# Receipt content binding source worksheet

**Status:** source draft only. No compile, native invocation, canary, export, or
product-data access was performed. Root owns acceptance and every execution
gate.

## API and placement

The standalone ReceiptContentBinding exposes package-scoped
prepare(SetupMeter, long producerStarted, Path receipt, long expectedBytes,
String expectedSha) and instance verifyFinal(SetupMeter, long
producerStarted). The first call binds the caller start to the actual shared
meter's reflected started field, requiring exactly a boxed Long, before
filesystem access. The final call consumes its attempt before authority,
deadline, identity, or content checks. It must run after result readback and
before export finishGroup; it never reads or mutates Meter state. Its
path-free failure is BindingFailure.code.

Pinned sources reviewed:

- CURRENT_FLAT_STUDY.java, SHA-256
  0a7e31b20dabd80a33e5dc5886507817cbc138ce96a37eb20ecc54a6e2ff93e0.
  SetupMeter/field: lines 169–194; deadline helpers/constants: around
  2815–2825; R5WindowsIdentity and R5FileIdentity: lines 2663–2735 and
  2970–2988.
- SHARED_OWNER.java, SHA-256
  4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4.
  reserveCopy: lines 1124–1136; fileDigest: lines 1820–1846;
  hashUnits: lines 1880–1883; actual Meter.started and accounting:
  lines 1963–2079.

The receipt must be canonical absolute op-1.jsonl; expected length is
1–8,388,608 bytes and SHA is 64 lowercase hex characters. Each path entry from
the leaf through the root is retained and checked. The path-entry ceiling is
4096 so each call through actual SetupMeter.copy fits the shared owner's
4,096-entry atomic copy chunk and the largest single final reservation fits
the 32,768-unit step cap. This is a local bound of this component.

## Exact successful-prepare accounting

Let P be path entries including the receipt and root (2–4096), and
B = ceil(expectedBytes / 4096) (1–2048).

Explicit SetupMeter.charge units:

| Operation | Units |
|---|---:|
| Strict actual-meter-start inspection | 1 |
| Expected length, SHA length, 64 SHA characters, and five path-shape checks | 71 |
| Early producer-deadline check before path filesystem access | 1 |
| Path-chain traversal and two array allocations | P + 2 |
| Initial identity construction and its before/after producer checks | P + 2 |
| Initial leaf-size comparison | 1 |
| Initial hash | 9 + 3B |
| Initial SHA comparison | 1 |
| Post-hash identity construction/comparison and deadline checks | 2P + 2 |
| Reserved final retirement, authority/size checks, deadline controls, hash fixed work, and SHA comparison | 17 |
| Reserved final identity sweeps | 4P |
| Reserved final hash blocks and per-block deadline checks | 3B |
| Ticket allocation | 1 |
| **Explicit charge total** | **108 + 8P + 6B** |

SetupMeter.copy is a separate pre-admission path, but the selected shared
reserveCopy also charges each admitted entry to the Meter. The three calls
admit P path references, P initial-identity references, and eight ticket
fields (five references, two longs, one boolean). Therefore:

- copiedEntries increases by 2P + 8, in three copy chunks.
- Those calls add another 2P + 8 to the actual Meter unit counter.
- Successful total Meter unit delta is **116 + 10P + 6B**.
- No receipt-byte or digest-byte copy fee is added.

The final reservation is made during prepare while the metadata group is
open. It covers one retirement, one supplied-authority comparison, one frozen
initial-size assertion, four producer-deadline checks around the final
identity sweeps, final hash fixed work, final SHA comparison, two identity
sweeps at 2P each, and 3B block work. Actual Meter reserve may roll a
group when the current group lacks capacity; this component does not call
finish or open a private Meter.

## Hash and identity loops

Each hash costs 9 + 3B: one hash-start clock sample, SHA-256 initialization,
a 4096-byte buffer allocation, stream open and close, digest finalization, one
bounded EOF probe with exact byte-count check, and two deadline controls for
the EOF boundary; each logical block adds the existing one-per-4096-byte hash
unit plus deadline checks before and after the block. The same clock sample
checks the 90-second producer limit and 5-second per-hash limit. The initial
9 + 3B is charged before opening the first content stream. The entire final
17 + 4P + 3B reservation is charged before returning the ticket.

Lower-level short reads are accumulated into one bounded logical block;
zero-progress reads, truncation, and growth fail closed. One EOF byte is
probed. Streams close on success and failure. The selected
Files.newInputStream(..., READ, NOFOLLOW_LINKS) path is used.

The identity baseline constructs one existing R5FileIdentity for each path.
The initial post-hash sweep costs one new identity plus one comparison per path.
The final method brackets its single content hash with two identity sweeps,
each also one new identity plus one comparison per path. Every path element
checks the Windows identity key and creation time. Only the receipt leaf checks
size and modification time; directory size and modification time are ignored.
The leaf digest catches same-size content changes even when modification time
is restored.

## Failure and proof limits

Preparation codes include METER_STARTED_INVALID,
PREPARE_AUTHORITY_MISMATCH, EXPECTED_LENGTH_OUT_OF_RANGE,
EXPECTED_SHA_INVALID, PRODUCER_DEADLINE, RECEIPT_PATH_INVALID,
INITIAL_LENGTH_MISMATCH, INITIAL_SHA_MISMATCH,
RECEIPT_IDENTITY_MISMATCH, RECEIPT_IO_FAILURE, HASH_DEADLINE, and
METER_REJECTION. Final codes include FINAL_ATTEMPT_REUSED,
FINAL_AUTHORITY_MISMATCH, FINAL_LENGTH_MISMATCH, FINAL_SHA_MISMATCH,
RECEIPT_IDENTITY_MISMATCH, PRODUCER_DEADLINE, HASH_DEADLINE, and
RECEIPT_IO_FAILURE. A failed or foreign final attempt stays retired.

The initial and final snapshots are not an atomic file lock or crash-durability
proof. This component does not enable export or certify the genuine receipt,
producer, normal data, full-D, or held-out/protected inputs. Real selected-host
canaries and all gates remain root-owned and NOT RUN by this worker.
