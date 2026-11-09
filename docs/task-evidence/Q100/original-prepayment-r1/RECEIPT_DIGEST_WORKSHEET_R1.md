# Original receipt digest component — Source freeze R1

Status: SOURCE AUTHORED; emitter, Python controls, compilation, Windows smoke and
native stream suite NOT RUN by this worker. This is a bounded source component,
not qualification of the original caller, full host, assembly or any producer.

## Ownership and input

Only these five task-root files are authored: `ReceiptDigestMembers.java.fragment`,
`generate_receipt_digest.py`, `OriginalReceiptDigestCanary.java`,
`test_receipt_digest_source.py` and this worksheet. Source snapshots are read-only.
No emitted Java, classes, output journals, DATA, producer implementation or old
classes were read or generated. Repository changes were preserved. No staging,
commit, push, notification, persistent process or new delegation occurred.

The exact SharedOwner input is 182535 bytes, SHA-256
`4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4`.
The `Parent` / `SetupMeter` API is read from current Study pin
`802d051413bdd0c5dde25bf327f29cbe18a633f82fa4d2f29d1c15996c1c1847`.
The fragment pin is
`528a8b5f3cc8130cf20c04538fbaf6afeb1c982d7dc3516a9c73c6514b9e8c72`.
The root contract is `COMPONENT_CONTRACT_R1.md`; it remains unchanged.

The pinned `fileDigest(Path,Meter)` at lines 1820–1848 charges after read/update.
Its `buildOriginalArm` caller at lines 386–387 opens the Meter group before it.
This source observation identifies the missing receipt byte fee. It is not an
execution of that caller or proof of genuine original attribution.

## Replacement and accounting

The emitter replaces only the byte span beginning at the exact private metered
method signature and ending before the following `hashString` method. The replacement
contains that method and one private `fileDigestRead(InputStream,long,Meter)` helper.
All prefix and suffix bytes, including the existing unmetered overload, Digest DTO,
8 MiB limit, budgets, other hashes and downstream I/O, remain byte-identical.

For a regular non-symlink file, read bounded expected size first. Charge
`(expectedSize+4095)/4096` exactly once, including a zero charge for an empty file,
before stream open, digest creation, buffer allocation or body read. Direct
`Meter.charge` requires an already open group and cannot reserve or roll it.
Maximum debit is 2048 for exactly 8 MiB; 32768 group capacity stays unchanged.
There are no refunds and no per-read fees, including on failed reads.

This deliberately corrects short-read rounding. Two reads of 2048 bytes for a
4096-byte file cost one logical hash unit; the former per-read accounting would
cost two. Full-block fees are retained. This is a bounded logical byte accounting
correction; it does not change a global cap, schema/model epoch or host-I/O census.

The prepaid helper consumes exactly the bounded expected bytes with an 8192-byte
buffer and bounded read lengths, rejecting zero progress, over-return and early
EOF. A separate one-byte EOF probe rejects extra data. Deadline checks bracket
read, update, digest finalization and file metadata, with checks around stream
ownership exit. Final count, size, regular-file and non-symlink checks remain.
The wrapper owns and closes the real stream on success and error. The helper owns
no caller stream and introduces no prepayment token, alternate DTO or authority.

## Root-run Source controls and emitter

From this task directory, run the selected Source tests only when root opens
execution: `python -m unittest -v test_receipt_digest_source.py`.
Eight named unittest methods cover exact prefix/suffix parity, source and fragment
pin negatives, invalid CLI inputs before any output/directory creation, exclusive
output preservation, prepayment ordering, read/deadline guards and frozen actual
owner canary inventory. No assertions or counts are presented here as observed.

Then `python generate_receipt_digest.py` emits only the future default
`emitted/FailureCohortRoutingStudy.java` and prints strict JSON containing input,
replacement, old-span, output and unchanged prefix/suffix hashes and byte ranges.
The generator reads at most 4 MiB per input. It validates both byte pins before
creating output directories or files and refuses to overwrite an existing release.
The output is an exact splice, independently checked by Source tests. `--source`,
`--fragment` and `--output` permit root-controlled fresh fixture paths. Any
post-release fix must preserve the prior released artifacts before replacement.

Root compiles the actual changed SharedOwner and current emitted Study in its
fresh 859/860 Source set with the producer implementation absent. This worker does
not compile or run a surrogate owner. The canary uses actual current `Parent`,
`SetupMeter` and the private owner methods via `p.call`, including `Path.class`
and `p.meterType`. `NativeFixture` opens its group using `m.charge(0)`.

## Frozen SMOKE inventory and independent expected debits

CLI: `OriginalReceiptDigestCanary SMOKE <existing-empty-task-owned-directory>`.
Windows is required. Root supplies the unique empty directory. The Java canary
uses ordinary real files with `CREATE_NEW`, deletes only the exact file it created
in `finally`, and leaves the supplied directory. No persistent process is started.

| Exact case name | Bytes | Additional digest debit | Expected result |
| --- | ---: | ---: | --- |
| file_empty | 0 | 0 | Correct empty SHA/count; group stays open |
| file_1 | 1 | 1 | Correct SHA/count |
| file_4096 | 4096 | 1 | Correct SHA/count |
| file_4097 | 4097 | 2 | Correct SHA/count |
| file_8192 | 8192 | 2 | Correct SHA/count |
| file_8mib | 8388608 | 2048 | Correct SHA/count at unchanged cap |
| file_overcap_9mib | 9437184 | 0 | IOException before payment/body |
| file_expired_before_body | 4097 | 0 | Actual Meter group deadline rejection |
| file_capacity_before_body | 4097 | 0 | Actual Meter capacity rejection; existing 32768 units retained |
| file_empty_closed_meter | 0 | 0 | Empty charge rejects closed actual Meter; remains closed |
| file_unmetered_4097 | 4097 | 0 | Existing unmetered overload, correct SHA/count |

Each successful digest is compared with an independent direct SHA-256 of the
fixture byte array and the fixture length. Metered cases check actual units and
groupUnits, zero copiedEntries/copyChunks, zero finished groups and an open group.
The deliberately closed case checks the corresponding closed state and unchanged
copy counters. Unmetered case checks its separate actual fixture remains unchanged.

## Frozen SUITE inventory and independent expected debits

CLI: `OriginalReceiptDigestCanary SUITE`. The streams below are explicitly
synthetic fixtures for the private prepaid read helper, never a fake original arm.
The actual Meter is prepaid once by the fixture before the helper invocation.

| Exact case name | Expected bytes | Debit retained | Fixture / expected result |
| --- | ---: | ---: | --- |
| short_one_byte_4097 | 4097 | 2 | One-byte chunks; correct SHA/count |
| short_2048_4096 | 4096 | 1 | Two 2048 reads; rounding correction |
| short_2048_4097 | 4097 | 2 | 2048 chunks and final bounded length 1 |
| chunk_4096_8192 | 8192 | 2 | Two 4096 chunks; correct SHA/count |
| chunk_8192_8192 | 8192 | 2 | One full buffer; correct SHA/count |
| empty_eof_probe | 0 | 0 | No body read; one EOF probe |
| zero_progress | 4096 | 1 | Zero-return rejection |
| early_eof | 4096 | 1 | Only 2048 bytes available; rejection |
| extra_byte | 4096 | 1 | 4097 available; EOF probe rejects |
| overreturn | 4096 | 1 | Returns requested length plus one; rejection |
| read_error | 4096 | 1 | First body read throws; same IOException propagates |
| error_after_prefix | 4096 | 1 | Throws after first 2048-byte prefix |
| expiry_during_read | 4096 | 1 | Read expires actual Meter; immediate post-read check rejects before successor read |
| expiry_during_eof | 0 | 0 | EOF probe expires actual Meter; post-read check rejects |
| bounded_final_read | 8193 | 3 | Full buffer then exactly one-byte request |
| eof_read_error | 0 | 0 | EOF probe throws; same IOException propagates |

All helper cases verify actual prepaid fee retention, untouched copy counters and
open group; they also verify the helper does not close the caller-owned stream.
Success cases verify one EOF probe and the independent byte SHA/count. The stream
asserts every body request is positive and at most 8192. Output is one strict JSON
object with `mode`, `status`, explicit ordered `names` and per-case `name`/`status`
plus observed debit/read counts or a failure. No manual assertion-count oracle is
invented. Any failed case yields overall FAIL and exit 1. Root independently checks
the exact frozen names; a missing case is not success.

## Limits and current gate ledger

| Gate / boundary | Current status |
| --- | --- |
| Input SharedOwner byte hash | Observed match via Get-FileHash |
| Source and canary author review | Performed; no execution proof inferred |
| Python Source/CLI controls | NOT RUN |
| Default emitter / parity output | NOT RUN; no emitted source yet |
| Fresh JDK8 target7 compile | NOT RUN |
| Real Windows file smoke | NOT RUN; required early root gate |
| Actual-Meter synthetic stream suite | NOT RUN; follows Windows smoke |
| Locked Windows file read failure | NOT RUN; requires root-supplied FileShare.None holder |
| GWT / visible player flow | NOT APPLICABLE to Source-only component |
| Genuine original / producer / assembly / host / full-D / H1 | UNSPENT_GATED / unqualified |

Capacity/expiry-before-body ordering is a pinned Source proof plus actual Meter
rejection case; ordinary smoke files are not a locked-file proof. Synthetic read
errors do not certify Windows host failures. Real-file resource closure is checked
through exact-file deletion in the canary; no host-wide lifecycle guarantee follows.
Same-size content changes and VM internals retain their existing census. Later
`readOriginalGroups` I/O is outside this correction. Root retains native/compile
60-second limits and all existing output/class/reader/wrapper/cleanup limits.
Playable100 remains 0 / beta HELD; no previously gated scope is opened here.

Next action: root audits this Source freeze, emits into a fresh candidate only when
authorized, runs focused Source/CLI controls, compiles the actual owners, runs real
Windows SMOKE early, then opens the broader SUITE. Preserve all failures and
released artifacts before fixing, and distinguish timeout/cleanup failure from PASS.
