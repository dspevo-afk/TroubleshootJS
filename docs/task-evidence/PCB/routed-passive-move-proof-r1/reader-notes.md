# Private routed passive move proof reader

This leaf owns the strict offline reader and its in-memory canary receipt for
the one private endpoint-only move packet. It does not implement the Java
construction, bridge, wrapper, native harness, or physical verifier.

`assess.py` pins protocol SHA-256
`6bdde6eed11b1b441e8440843d0130eecf70079043017472c1e8cf7ce214568d`, the
prior census reader SHA-256
`f9b79e21875864aecde86e41d1b49d955d6f9201585cebebf462ffd87fb82229`, both
current suite manifests, and all six published input hashes from
`protocol.json`. The prior reader is composed only for strict JSON parsing,
typed equality, whitespace validation, and the old three-row census route
oracle. Its old phase suite and artifact assessment are not reused.

The routes reader requires the exact per-root order
`WHITESPACE`, `CENSUS`, `CASE`, `MOVE` for roots 5 then 2. It passes the
filtered first three rows to the pinned census reader, then validates the
closed move schema. It derives the first eligible direction with analytic
copper delta at most zero from the target callback, requiring the protocol
expectation `C5,(0,-10),-10,-10`. Seed 5 must be `NO_TARGET` with construction
count zero and all nested fields null. Seed 2 must be
`PRIVATE_VALIDATED` with one construction, zero new route requests, unchanged
selected mutation, the pinned source/candidate metrics, changed candidate
geometry, and all proof flags true.

The reader independently recomputes the reachability digest from all 135
published target pads. It sorts stable pad IDs, emits UTF-16-code-unit-length
fields for every pair, uses source net equality for each bit, and hashes the
UTF-8 `R1` stream. The canary has a separate literal Python oracle over the
sealed published census facts: 9,045 pairs and 175,409 canonical UTF-8 bytes.
Its digest is
`9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7`. Both
reported graph-derived reachability hashes must equal this digest. Current
census summaries must also remain typed-equal to their sealed published rows;
a canary changes one published pad net (and its incident net) and recomputes
both foreign proof hashes to show that matching a runtime-mutated oracle is
still rejected. Source and
candidate trace provenance hashes must be valid, equal, and accompanied by the
native verifier flags; the offline reader cannot reconstruct full trace paths
or independently recompute that provenance hash from absent candidate geometry.

The synthetic canary run used the pinned accepted15 census rows and a literal
changed candidate geometry. It produced `assessment-canary-results.json` with
status `PASS`, 36 total cases, 4 positive cases, 32 rejection cases, and unit
fixture counts 6 for `PcbRoutedPassiveLegCensusContractTest` and 4 for
`PcbRoutedPassiveMoveProofContractTest`. Rejections cover foreign equal graph
anchors, a coherently altered current census net with matching foreign hashes,
candidate counts, selector/move/filter/order/hash drift,
source/candidate geometry, strict booleans, area and copper deltas, the 9,045
pair count, provenance mismatch, foreign reachability, route-source counts,
physical flags, null-shape rules, missing reports, report-before-PASS order,
native failure/timing/exit output, duplicate keys, stale unit prerequisite,
protocol pin, and published-input pin.

Validation command, using the pinned Python runtime from the task pointer:

```text
python -X utf8 -B docs/task-evidence/PCB/routed-passive-move-proof-r1/check_assessment.py
```

Result: `PASS assessment canaries; cases=36 rejected=32`.

Actual native unit/private-move execution is `NOT RUN` here. Java/GWT/player,
full-matrix, unseen-root, all-seed, solver, repair, manufacturing, and fitness
claims remain outside this reader leaf. The root agent owns native execution,
the phase artifacts, integrated review, and publication.
