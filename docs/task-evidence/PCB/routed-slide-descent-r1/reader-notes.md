# Routed slide descent reader notes

This handoff covers the four owned reader artifacts for the bounded
`SELECTED_AFTER_EDGE_TRIM` probe at base `80303258be31da72e238455a05068c2015ee84d4`.
The reader binds the final protocol SHA
`e047c6b87e72538b7b161449b7938b80c8e1ec9c081bea27ba40c26d3c4b73ba`, the pinned
prior compaction reader SHA
`c03cfd51dc2a93612a94438347b1a5fbbe92c9191a472e2d0b00efc749a4476e`, and its
prior protocol SHA `88123f76e526d42dce97bb656eda8ffdf4479ca947052cf819738e8e79aa93cb`.

The offline canary receipt is PASS with 80 cases: 8 positive and 72 negative.
It covers both roots, four old baseline reports, two descent summaries, four
unit suites, one route suite, dynamic census direction order, affected trace
identity, source chaining, first strict acceptance, no improvement, no
structural moves, step and evaluation caps, either compacted control flag,
metrics and identity invariants, all proof fields, timing floors, report ordering, terminal
PASS and cleanup boundaries, fresh unit prerequisites, duplicate JSON keys,
schema/type/hash drift, failure markers, and pinned input changes.

The independent seed-2 pad/net oracle is the literal SHA
`9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7` over 9045
pairs and 175409 canonical UTF-8 bytes. It is recomputed from the sealed target
callback facts rather than obtained from the validator under test. The seed-5
positive fixtures use a synthetic 54-part/131-pad census because the sealed
prior packet has no seed-5 full pad map; the production route reader requires
the runtime native seed-5 census and its independently derived hash.

The final sealed protocol does not contain top-level `physicalRowContract`,
`summaryStatuses`, or `expectedMoves` keys. The reader therefore binds the
physical row adapter to the pinned prior physical reader, derives terminal
status from the current chain and sealed status catalog, and derives each move
order from the current complete structural census. No synthetic field was
added to the sealed protocol.

The reader can validate endpoint incidents, pad/net reachability, counts,
provenance digests, and identity links from the available census. It cannot
reconstruct complete native trace paths or independently establish compactor
ownership; those remain native proof responsibilities. No native, GWT,
browser, solver, or runtime descent was run, and this evidence makes no
fitness, production, global-optimum, or manufacturing claim.

Current owned artifact hashes:

- `assess.py`: `460c5bf29b2a65e4ad8ee83e8726a00afbfc90bedcab75459ab966ec2f2e38aa`
- `check_assessment.py`: `c7d2450dd4622d4bf676c97d0d2b86790d6789f207cc4399b697aafd1e232250`
- `assessment-canary-results.json`: `3754818ac21ebf4674db72b64364e79e8a80860dc6f6cbf017efd795d7c02221`

The Python sources were compiled in memory with `python -X utf8 -B`; no
bytecode cache was created. The canary receipt and CLI artifact writer refuse
to overwrite existing outputs. Native execution and publication remain with
the root worker.
