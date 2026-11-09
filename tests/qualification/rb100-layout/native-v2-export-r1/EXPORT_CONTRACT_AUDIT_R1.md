# Native V2 export contract audit R1

**Status: source review complete for R2.** The composed export and receipt
logic matches the frozen contract by source inspection. R1's study output had
one extra terminal LF. R2 removes exactly that byte: the source inheritance
audit records `newStudy == retainedR1raw.rstrip(LF) + LF` and unchanged logic
regions. The receipt helper bytes are unchanged. This resolves the source
format issue; it is not a runtime or native result.

## Reviewed pins and boundary

- `PLAN_R1.md`: `44011d73e4e7b160d5599024881e6c980ad6cbc3c8c9eec3d653a755de5a033a`.
- `SOURCE_INPUT_R1.json`: `8370f4400e5ffa083b2c9d32681c7617910df2c08223a39e94116d918923b9e3`.
- `derive_native_v2_export.py`: `389022a6ba18c429d21c6794371b3e99ab5f81bba74146d625b452a3c5fe885b`.
- `EXPORT_SOURCE_WORKSHEET_R1.md`: `9089df9622400b141b88a1b803428c14bed25aef8ae56d786b12a5aefdf28f8b`.
- `EXPORT_CANARY_DESIGN_R1.md`: `0d9000d84b4084f57763afa103c32845cd53e52eb25f627ce6735910900638fa`.
- Emitted `CohortTerminalTelemetryStudy.java`: `802d051413bdd0c5dde25bf327f29cbe18a633f82fa4d2f29d1c15996c1c1847` (234,150 bytes; one final LF).
- Emitted `ReceiptContentBinding.java`: `1df7e06085165d025ff79bdd0cdaa7f603c110cc26b6cc6f280d73f52e8d2e41`.
- `NATIVE_V2_EXPORT_SOURCE_PROOF_R1.json`: `817d7a24f86cf7ffe1fc1ec4515ca3b0d4a7675f95848f2d7faa725ff4a4e32c`.
- `CurrentV2NativeExportCanary.java`: `11b33317533a4599c14e3052bd68c1ec2807933455d49136546ae6eba50d2e27`.
- Root's synthetic file auditor source: `audit_export_files_r1.py`,
  `298dee901686815379fd386c56fd5707de78af19e03a45cbd216388a85438402`.
- `SOURCE_INHERITANCE_AUDIT_R3.json`:
  `46149ebc99e4262c8ab0658972d6745c45803665e17c4eb8cf065cea4cdd1a2e`.

The derived source proof pins the 0a current-flat source, R `88986781…`, W
`1ae31054…`, receipt helper `7f271ed2…`, and codec `76fb7bbc…`. Its status is
`DERIVED_SOURCE_ONLY_NOT_EXECUTED`; it records the five formulas below and
keeps main, assemble, and both generic export overloads unchanged. This audit
did not compile or execute any Java/Python source, run the canary or auditor,
read DATA/journals/classes, or invoke a producer/native path.

The R2 proof hash is `817d7a24f86cf7ffe1fc1ec4515ca3b0d4a7675f95848f2d7faa725ff4a4e32c`.
Root reports the five source CLI gates passed (two byte-identical derivations
and three expected exit-1 rejection cases); that does not establish a build or
runtime result. The prior generator raw bytes were unavailable for an
independent before/after comparison, so this audit pins the released generator
hash without claiming its raw history was preserved.

## Composed export flow

The emitted API is package-private `exportV2(...)` at
`CohortTerminalTelemetryStudy.java:3238-3339`. It takes caller-supplied run,
source, wire, and context identities, output path, result map, shared meter,
closed snapshot, finalizer, prepared receipt ticket, and optional test hook.
It checks those identities against the result header inside the first fixed
schema/output operation; the codec then checks the exact V2 body schema and
frozen wire hash. This component checks equality, not independent producer or
host authority.

The flow checks the adopted snapshot before opening one export group. It
requires the initial output directory to contain exactly `op-1.jsonl`, then
requires exactly that receipt and sibling `result.json` after writing.
`R5Output` retains path, type, Windows identity, size, and timestamp checks at
`CohortTerminalTelemetryStudy.java:2994-3035`. The receipt ticket supplements
those metadata checks with a final content hash.

The exporter checks the 34-slot work map, calls
`finalizer.verifyBound(meter, pre, result, work)` immediately before
`finalizeWork`, then runs `r5ValidateWork`; the derived validator additionally
requires `wrapperGroupsBeforeExport == pre.groups` at lines 2947-2968. The
detached plan's `verifyBound` checks meter, snapshot, body, and work identities
at lines 3559-3563.

The same `CurrentV2CanonicalCodec.writeBody` path is used for count, result
write, and streamed read-compare (lines 3274, 3286, 3293). After successful
readback, the exporter checks the export deadline, calls
`receipt.verifyFinal(meter, started, output.operation)`, and checks the
deadline again before `finishGroup` (lines 3298-3311). The ticket compares the
supplied operation path with its retained canonical receipt inside the one
prepaid authority predicate, consumes one attempt, rechecks identities, and
rehashes content. It performs no Meter reads or charges during export.

After one group closes, `R5PublicationToken` verifies the closed meter and
admits the exact marker copy. The V2 codec counts and writes the typed 14-field
marker to one exact-size buffer, capped at 656 bytes. The exporter checks
stdout before and after one write/flush; a failure propagates and cannot retry
publication. Both legacy `exportR5` overloads still throw
`R5_EXPORT_DISABLED` at lines 3231-3234, and `main` still throws its
component-only error at line 28. `assemble` remains unchanged; this component
does not create a normal producer, OriginalArm, Session, or routing run.

## Accounting review

For result bytes `R` including LF, `C = ceil(R / 4096)`, and exact marker bytes
`M`, the source preserves:

| Quantity | Contract | Source result |
| --- | --- | --- |
| Export units | `5*C + 3` | Preserved |
| Copy entries | `2*R + 32 + M` | Preserved |
| Copy chunks | `2*C + 2` | Preserved |
| Export groups | `+1` | Preserved |
| Receipt preparation | `116 + 10*P + 6*B` | In metadata before snapshot |
| Final receipt Meter charges | `0` | Ticket does not access Meter counters |

Here `P` is bounded path entries and `B = ceil(receiptBytes / 4096)`. The
receipt path equality is within the reserved one-of-17 final fixed authority
operations, using the retained canonical `Path` without normalization,
enumeration, or allocation. The helper's initial/final receipt hashing demand
and path copies are prepaid before the metadata snapshot closes; no receipt
byte or digest copy is added to the export formula. Marker bytes are admitted
exactly after the export group closes, not reserved at the 656-byte maximum.

## Synthetic canary coverage

The final canary source has a smoke path with two cases and a suite path with
14 cases: two positives (one body block and a two-block body) plus receipt
content mutation, identity mismatch, stale snapshot, foreign finalizer,
wrapper-group mismatch, body mutation, result-file mutation, extra output
file, foreign receipt path, consumed ticket, stdout failure, and a
post-publication fatal guard. It constructs a real shared `Parent`/`SetupMeter`
but supplies synthetic owner state and the exact 25 detached scalar values;
receipt preparation occurs in an open metadata group before the closed
snapshot is captured. No producer arm, result DTO, session, route, or
electrical claim is created.

The positives independently format result and marker bytes, check the exact
two-file output closure and receipt preservation, compare returned and actual
Meter deltas to the formulas, and assert the actual closed Meter is
`open == false` with `groupUnits == exportUnits`. `invoke` restores `System.out`
in a `finally` block. The post-publication case intentionally emits one
complete marker, mutates a closed Meter counter, and requires fatal
`R5_METER_CHANGED` with no success metrics; it is persisted as a failure
artifact outside the positive report, not as a normal success. Other negative
cases require exact failure codes and no marker.

Root's standard-library auditor source independently checks the saved result
and marker canonical bytes, digest, report bindings, output closure, receipt
hash, and fee/copy/group equations. It does not independently recheck the
canary's live `open` or `groupUnits` values because those fields are not in the
saved `MeterState` report; the Java canary asserts them in-process. This
narrows the saved-report proof without changing the source-level check.

## Limits and remaining gate

Exclusive body/digest stability through finalization, all three body passes,
and publication is a caller precondition. The API borrows the tree; it does
not deep-freeze it or prevent alias mutation. Receipt/output identity checks
and final rehashes are not an atomic file lock or crash/power-loss durability
proof; stdout flush is not durable storage. The synthetic component does not
authenticate pre-call identities, qualify normal-host authority, or prove
full-D semantics. No genuine producer/capture or native export has run here.

The R2 formatting correction is the only Study source delta from R1; the
helper remains hash-identical and the source proof records one final LF for
both Java outputs. This leaf's review does not grant compilation,
selected-Windows runtime, receipt-host, normal Full-D, or export acceptance.
The genuine original plus
exact NULL/OBSERVE remains unspent and gated; Full30 remains excluded;
HARD/PSYCHOTIC remains unavailable/unrun; historical protected input remains
uncertified; playable100 is zero and beta remains held.
