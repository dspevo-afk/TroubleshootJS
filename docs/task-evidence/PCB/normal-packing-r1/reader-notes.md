# Normal-packing offline reader

`assess_normal.py` is a bounded, read-only Python reader for the frozen normal
packing packet.  It loads only `protocol.json`, `scope.md`, and the two suite
manifests before phase files exist.  The CLI accepts `baseline`, `unit`, or
`routes`; it refuses to overwrite assessment artifacts.  All Python checks use
the pinned task Python with `-X utf8 -B`.

The reader pins protocol SHA-256
`1456fb21aec5ee02af3c779cd14cae8d0cd8a280095e9e34b0f44671b34269cc`, source
helper SHA-256
`3b43e98367873b54b2606427d68f6287bac49ab39d08eec3501e6290e0966ff0`, and the
unit/routes manifest hashes recorded in the canary receipt.  It derives the
ordered 44-case matrix from the protocol: groups contain 12, 12, 12, 4, 1, 1,
1, and 1 cases.  Route markers are checked against the eight Java collector
names (`base-families-0-2`, `base-families-3-5`, `base-families-6-8`,
`controlled-indicator-all-roots`, and the four RB30 roots); the printed full
marker and its JSON short name are validated separately.

JSON parsing rejects duplicate keys, non-finite constants, invalid UTF-8, and
unexpected fields.  Case, whitespace, timing, suite, receipt, terminal, and
cleanup records use closed field/order checks with strict bool/int/string/null
types, signed-long seed canonicalization, digest and numeric bounds, graph
before/after equality, logical/layout population equality, outline area
recomputation, GRID10 adjacency, and accepted/rejected shape rules.  Both
stdout and child receipt report/suite lines must match exactly, with all group
reports before the matching suite marker and terminal/cleanup output after the
complete census.

The comparison keeps every baseline case, requires accepted source and mapping
identity fields to match, preserves each typed rejection phase/type, reports
new admissions separately, rejects area/copper/via regressions, rejects
protected-whitespace regressions, and requires at least one strict area gain.
Routes assessment records native/integrity status separately from fitness and
returns exit code 2 for a fitness failure.  Baseline and candidate input seals
are accepted in the exact eight-field schema (`schema`, `phase`, `baseCommit`,
`protocolSha256`, `baseMainSealSha256`, `sourceCount`, `files`, `gateOwners`)
with canonical sorted relative file paths and SHA-256 values.

`check_normal_assessment.py` builds an independent literal 44-case fixture
corpus (15 accepted and 29 typed rejections) and exercises positive baseline,
unit, candidate, input-seal, and all-eight-collector checks.  Its 71 canaries
passed: 7 positive and 64 rejected.  Negative coverage includes duplicate and
unexpected keys, NaN and bool/int confusion, family/root/signed-seed/order
drift, missing and foreign records, whitespace adjacency/schema/metric drift,
graph and population changes, outline/geometry/layout/timing bounds, route
cross-group and short-name framing, stdout/receipt divergence, skips/timeouts,
terminal/cleanup placement, unit marker leakage, input-seal errors, typed
rejection substitution, comparison identity and metric regressions, no strict
gain, and stale unit prerequisites.  Unexpected exceptions are not converted
to valid negative canaries.

The canary receipt records the exact consumed hashes and is the only generated
artifact from this offline check.  No baseline, candidate, unit, or route
outcome artifact was read while implementing the reader.  The reader does not
run native/JVM/GWT code, inspect compiled solver behavior, certify visible
normal-player flow, or claim all-seed/optimal-packing coverage; those remain
root-owned gates.
