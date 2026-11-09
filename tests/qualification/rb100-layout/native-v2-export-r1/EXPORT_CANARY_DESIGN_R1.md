# Native V2 export canary design R1

## Source status

Authoring remained source-only: the canary author did not compile or execute the Java source. Root owns source freeze and runtime gates.

## Scope and invocation

`CurrentV2NativeExportCanary` is a Java 7 source canary for the derived
`CohortTerminalTelemetryStudy.exportV2` entry. It accepts exactly
`--smoke <existing-canonical-empty-fixture-root>` or
`--suite <existing-canonical-empty-fixture-root>`. It creates only named
children of that root, retains every success and failure artifact, and performs
no recursive cleanup. Smoke contains an actual selected-export positive and a
same-size operation-receipt content mutation with the original modification
time restored. Suite includes those cases plus a two-block positive and the
identity, stale-snapshot, finalizer, receipt, result, closure, reuse, marker-
output, and post-publication fatal-guard rejections listed below.

Every positive case has a fresh output directory. The only output-directory
members after a successful export are sibling `op-1.jsonl` and `result.json`.
The marker is captured in memory during the export and is written to a sibling
audit directory only after all canary assertions. `positive-reports.json` is
also outside every output closure and follows the root driver's
`synthetic-native-v2-export-audit-r1` shape.

## Synthetic owner and meter fixture

Each scenario creates one real `Parent` and `SetupMeter`, then uses the current
meter owner lifecycle to adopt one explicitly synthetic owner group. Setup,
synthetic NULL, synthetic OBSERVE, and receipt-preparation demand are measured
on that same meter. Receipt preparation happens while the metadata group is
open and before `captureDetachedR7WorkPlan` captures its closed snapshot. The
common detached work plan receives the exact 25 scalar values used by the
accepted flat-work canary. No producer arm, result DTO, session, route,
original capture, or electrical claim is created.

`op-1.jsonl` contains a short ASCII synthetic canary receipt. It is written
with `CREATE_NEW`, independently SHA-256 hashed, and bound by the current
receipt ticket to the exact operation path, meter, and producer start. The
current pinned wire-contract SHA is used for both body and marker identities.
All other identities are fixed, valid synthetic UUID/SHA values.

## Positive evidence and independent accounting

The ordinary positive exercises one result block; the suite adds a body with
20 independently bounded 240-character ASCII strings in a nested list so its
encoded result crosses one 4096-byte boundary without exceeding the codec's
ordinary string limit.
For each success, the canary checks the exact two-file closure, preserved
operation receipt, independently formatted canonical result bytes, one marker
line and its independently formatted canonical bytes, result digest, closed
export group, and before/after meter snapshot.

Independent accounting uses `C = ceil(resultBytes / 4096)` and verifies the
derived contract values `exportUnits = 5*C + 3`,
`copiedEntriesDelta = 2*resultBytes + 32 + markerBytes`,
`copyChunksDelta = 2*C + 2`, and one added group. Dynamic elapsed and maximum
group values are measured from the real selected implementation; the canary
checks their bounds rather than relying on a fixed clock. The JSON audit
report records actual pre/post meter counters and returned metrics. Root's
separate standard-library auditor checks the saved result and marker bytes,
hashes, and equations again.

## Negative cases

Suite uses separate task-owned output directories for identity mismatch,
stale Meter snapshot, foreign finalizer, wrapper-group mismatch, post-write
body mutation, same-size result-file mutation with restored modification time,
same-size receipt mutation with restored modification time, a pre-existing
extra output member, a receipt ticket bound to a different operation path,
consumed-ticket reuse, and marker-output failure. Each rejection must use its
exact expected code and retain its files. The post-publication guard case
captures exactly one complete marker, changes one actual Meter counter during
the write, then requires `R5_METER_CHANGED` and no successful metrics; that
failure marker is saved outside the output closure after all checks. Other
rejections emit no marker. The body and operation fixtures remain synthetic
and do not claim normal Full-D semantics.

## Output and limitations

Canary stdout is one compact
`SYNTHETIC_NATIVE_V2_EXPORT_CANARY_ONLY mode=... cases=... assertions=...;
normalHostAcceptance=NONE` line after successful completion. The audit report
and saved marker are written under the supplied root, outside export output
directories. This proves only the selected current export component and
synthetic Windows file/marker transport. It does not prove normal host
acceptance, full-D semantics, native/original behavior, Session behavior,
population, or any protected/held-out qualification. No protected/held-out DATA read by this component; historical protected unread boundary is UNCERTIFIED.
The genuine original plus
NULL/OBSERVE phases remain unspent and gated. Full30 remains excluded,
HARD-PSYCHOTIC unavailable/unrun, playable100=0, and beta remains held.
