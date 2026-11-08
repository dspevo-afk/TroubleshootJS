# Native canary text policy

This checked native-only overlay fixes serialization of valid component
geometry text in the small owner-canary fixtures. It derives one exact external
development source. The dependency closure is required private evidence input,
not vendored here; this is not a standalone checkout native test.

The parent must be named CohortTerminalTelemetryStudy.java, SHA-256
4ea0f0950c31d05e86b6339c918381d7bc0dd0d17999b499a7729f0ffd9e7750.

```text
python -I -B generate_canary_text_overlay.py --source <exact-parent-source> --output-dir <existing-empty-owned-temp-directory>
```

The destination must be a proper descendant of the OS temporary directory.
The generator checks exact source/fragment hashes, one-time replacement anchors,
bounded regular-file reads, reparse-free path components and empty output.
It exclusively creates the216099-byte derived Study and overlay-proof.json,
then checks readback/closure. The Study SHA-256 must be
dacca39996a54ac3c65e8507367cf06401e8b289922b207a3ee0c4e6e22def98.
It never overwrites or removes failed output. These identity-at-check guards
are not a security sandbox against concurrent filesystem mutation.

The four adjacent fragments are byte-pinned. Preserve their raw bytes; only
CRLF-to-LF normalization inside those fragments is used for integration.
The old canonical formatter is replaced once; canary metadata gains the text
validator, its A/B calls gain the exact-path formatter, and the validator is
appended. Authenticated result/marker calls retain their default formatter.
The generated main remains fail-closed.

Only a String at the exact root-relative path
(complete|blocked|nonBranchQuality).freshRun.layout.components[index].geometryCanonical
may contain up to4096 printable-ASCII characters. Index is an actual List entry.
Required map/list types and an absorbing ordinary-path state prevent a similar
field elsewhere from gaining the exception. Keys, identifiers and other values
remain256. Null/Boolean/Integer/Long and Map/List retain their bounded domain;
depth starts at0 and allows64, with at most1048576 value visits.

Metadata uses the formatter's shared path/type helpers and prepays4/1/3/1 units
for map/list/type/limit dispatch plus each node/key/iterator/character operation.
That work is separate from3C/R/C count/emit/write accounting, R including LF and
C=ceil(R/4096). The90s/640-group/5s/32768-unit, copy1MiB and4096-entry segment
bounds remain. No whole-body encoding buffer or extra copy ledger is added.

Caller ownership is required through validation/count/emit. A/B reject invalid
path/type/text changes but do not detect every still-valid same-length mutation.
The checkpoint binds object/Meter identity and is not a deep freeze or digest.

Compile with selected JDK8 source/target1.7, UTF-8, empty sourcepath and the exact
external generated input59474332 plus U92/F75/N4139 authenticated classes/GWT
dependencies. Counts alone do not prove identity. Compile the generated Study,
CanonicalJsonHolder.java and the four canaries. Run with assertions:

- CanonicalJsonCanary:26 established formatter cases and17 rejections.
- R9TextPolicyCanary:synthetic exact-path/type/length/escape/mutation and native
  metadata/publication checks; its stdout is a vector plus scalar summary.
- R9TextPolicyLimitsCanary:actual depth64/65 and1048576/1048577 value visits,
  separate real Meter for each case; metadata data-copy deltas must remain zero.
- R9PreservedFixtureSerializationCanary:<absolute-new-metrics-file> argument;
  stdout must equal the published preserved-fixture-body.json exactly. Its
  identity/throw/source placeholders are explicit and do not prove owner success.

All classes use package com.lushprojects.circuitjs1.client. Test-only fixture
buffers and varargs construction are outside the production allocation proof.
The independent policy tests are directly runnable with Python-I-B:

```text
python -I -B test_r9_canary_text_policy_r1.py
```

See [actual validation and scope](../../../../docs/task-evidence/Q100/native-canary-text-r2/README.md).
This component runs no new Session/capture and grants no typed main/host/consumer,
1092-source closure, full D comparison, routing/electrical or Player acceptance.
Full30 excluded; HARD/PSYCHOTIC unavailable/unrun; playable100=0; beta HELD.
