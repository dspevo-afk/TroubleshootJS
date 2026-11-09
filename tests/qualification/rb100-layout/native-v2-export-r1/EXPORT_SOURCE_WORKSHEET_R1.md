# Native V2 export source worksheet R1

Status: source draft only. The deriver and emitted Java have not been run,
compiled, or tested. Root owns source audit, derivation, integration, and all
runtime gates.

## Inputs and source boundary

derive_native_v2_export.py reads only these pinned Java snapshots from the
supplied source directory; SOURCE_INPUT_R1.json is supplied separately by
the --manifest argument:

| Source | Bytes | SHA-256 |
| --- | ---: | --- |
| CURRENT_FLAT_STUDY.java | 229,972 | 0a7e31b20dabd80a33e5dc5886507817cbc138ce96a37eb20ecc54a6e2ff93e0 |
| R_STUDY.java | 229,578 | 88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf |
| W_STUDY.java | 239,584 | 1ae31054a430c00bf926ff4deff8091892bae267f05257aa0d869000226f7bd1 |
| CurrentV2CanonicalCodec.java | 18,580 | 76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522 |
| CURRENT_RECEIPT_BINDING.java | 14,372 | 7f271ed213d109ea6e185d8418dafff2791e7809c805922ef0f1de44bedc65b9 |

The source manifest pins and raw bytes are checked before decoding. Each Java
input is capped at 4 MiB; output Java is UTF-8 with LF line endings. After
all source transforms, the deriver applies .rstrip('\n') + '\n' to each
emitted Java file, removing extra trailing LF bytes and leaving exactly one
final LF. The generator does not import or execute project sources. It
requires an existing empty output directory and writes only:

- CohortTerminalTelemetryStudy.java
- ReceiptContentBinding.java
- NATIVE_V2_EXPORT_SOURCE_PROOF_R1.json

The pinned codec is a dependency, not copied or rewritten by this derivation.
The proof records source pins, changed declaration hashes, preserved declaration
hashes, output hashes, and accounting formulas without absolute paths.

## Transformation and preserved code

The current flat source remains the base, retaining its main, assemble,
adopted-only snapshot, flat R7 work plan, owner and Meter adapters. The deriver
checks exact and unique declaration anchors with a bounded brace scanner before
each change. It then:

1. Adds wrapperGroupsBeforeExport == pre.groups to r5ValidateWork.
2. Extends R5Output to require only the pre-existing op-1.jsonl in the
   output directory before writing, and exactly that file plus sibling
   result.json afterward. It retains identity checks for the directory,
   operation file, and result file.
3. Changes the export marker ceiling to 656 and removes the retired generic
   marker serializer.
4. Derives exportV2 from the pinned authored R5 exporter method. It uses the
   pinned current codec for body count, write, and read-compare; checks supplied
   run/source/wire/context identities against the body within the first fixed
   schema/output operation; verifies the plan binding immediately before
   finalizeWork; and verifies the operation receipt after result readback and
   before finishGroup, with the export deadline checked on both sides.
5. Changes the emitted receipt helper API to
   verifyFinal(meter, started, suppliedReceipt). The existing one bounded
   authority predicate also requires receipt.equals(suppliedReceipt); the
   exporter supplies output.operation. It adds no retained field, traversal,
   allocation, Meter charge, or copied entry.

The derivation checks that main, assemble, and both legacy exportR5
overloads have unchanged declaration hashes. main remains fail-closed and
both generic export overloads still throw R5_EXPORT_DISABLED. There is no
normal six-argument entry API.

The exported component API is:

    static R5ExportMetrics exportV2(
        String runId, String sourceSha, String wireSha, String contextSha,
        Path outputDirectory, Map<String,Object> result, SetupMeter meter, long started,
        R5Snapshot pre, R5WorkFinalizer finalizer, ReceiptContentBinding receipt,
        R5AfterResultWrite hook)

The caller must keep the borrowed result tree and marker digest exclusively stable, with no alias mutation, until export returns; all three body passes serialize the same live tree. The marker uses the codec’s
typed 14-field view and an exact-size byte target; no result-tree or digest
clone is introduced. The hook remains optional and test-only.

## Export order and accounting

Let R be canonical result bytes, C = ceil(R / 4096), and M be the exact
marker line length, bounded at 656 bytes.

1. Check the closed adopted snapshot and open one export group.
2. Charge the existing fixed schema/output operation, bind all four supplied
   identities to the result header, then validate the two-file output closure.
3. Read the existing work map, verify its exact shape, call
   finalizer.verifyBound(meter, pre, result, work) immediately before
   finalizer.finalizeWork(exportStarted), and validate the finalized work
   including the wrapper-group count.
4. Use CurrentV2CanonicalCodec.writeBody for count, write to result.json, and
   streamed read-compare. The write/read sinks retain the existing bounded
   identity and output closure checks.
5. Call receipt.verifyFinal(meter, started, output.operation) after successful
   readback and before finishGroup. The two deadline checks bracket its
   actual final file reads. Receipt work is already prepaid in metadata.
6. Close exactly one export group, derive the measured metrics, count the typed
   V2 marker, admit its exact copy against the same Meter, write one marker line
   to stdout, and verify the publication token.

The unchanged export ledger is:

| Quantity | Formula |
| --- | --- |
| Export units | 5*C + 3 |
| Copied entries | 2*R + 32 + M |
| Copy chunks | 2*C + 2 |
| Export groups | +1 |
| Receipt preparation prepayment | 116 + 10*P + 6*B |
| Final receipt authority control | One bounded path equality inside the already prepaid 17 fixed final operations |
| Export-time receipt Meter charges | 0 |

Here P is the receipt path-entry count and B = ceil(receiptBytes / 4096).
The path equality uses the already retained canonical Path and does not
normalize, enumerate, or allocate. Final verification consumes one attempt;
a failed receipt/export attempt is not retried.

## Evidence limits

This worksheet and the generator are source artifacts only. They do not establish
that derivation succeeds, that the Java compiles, or that Windows file identity,
the selected Meter, stdout publication, or the complete two-file operation
passes runtime checks. Root must run the pinned-source derivation and independent
source review before selecting fresh target-7 build/native gates.

No genuine producer or capture was started. This does not qualify a normal
full-D run, an H1/host/semantic/lifecycle path, or any protected/held-out input.
The one original followed by exact NULL/OBSERVE remains UNSPENT and GATED;
HARD/PSYCHOTIC remains unavailable and unrun; full-30 remains excluded;
historical protected unread boundary is UNCERTIFIED;
playable-100 remains
zero and beta remains held.
