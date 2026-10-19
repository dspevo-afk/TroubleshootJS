# Local passive pose-choice reader

Candidate `7838523bb45fdc733c388eb631a1695fd23365ac` on
`codex/q60-faulted-profile-counts`.

Status: **PASS — pre-runtime synthetic reader canaries only.** Native runtime
evidence is **NOT RUN**. No local-pose trial output or control-runtime output was
read. The prior source-sized v18 packet and its strict rejection were left
unchanged; its runtime result is not reused as evidence for v19.

`assess.py` keeps the prior reader entry points: `check_phase`, `check_sources`,
`check_raw`, and `assess_routes`. The frozen unit phase has nine suites and 57
ordered report rows: fourteen v19 source bindings, fifteen raw comparisons,
fourteen sealed v15 source rows, then fourteen unchanged v16 source bindings.
The first actual-loop suite must emit only its exact `PASS` marker. The policy
and admission suites also emit marker-only passes. The route phase has fourteen
suites and 28 ordered v15/v19 cases, each with whitespace, pose census, and route
records in that order.

The source reader binds each v19 canonical input to the sealed v15 plan and
independently derives the v16 and v19 interpretation digests. It binds graph,
intent, signed-long layout/routing seeds, population, and eligible passive IDs to
the sealed v15 source rows, then requires density14, pose policy4, and
`RB56_BOARD_NORMAL@5`. The raw reader preserves all fifteen sealed v15 geometry,
evaluation, demand, and courtyard values exactly. Routed v15 rows retain all 42
non-time fields. Each v19 row is source-bound, has the exact saved-row schema,
fits within the unchanged 60,000-millisecond row cap, aggregate placement
evaluation cap of six times 60,000, and 3+3 route-attempt limits. Pose censuses
bind every eligible passive exactly once to TOP0 or TOP90 under the versioned
admission identity.

Every whitespace record uses the declared full package-access scope. Protected
rectangles must be the exact five-field object, with positive grid-aligned
dimensions and coordinates aligned relative to the corresponding outline
origin. Strict fitness fails if any v19 root regresses in area, protected empty
rectangle area, copper length, or vias; it also requires a strict improvement
and at least one selected TOP90 passive overall. Incomplete evidence is rejected
separately from a complete strict-fitness `FAIL`.

The frozen manifest records **59 passing synthetic canaries: 4 positive and 55
negative**. Positive cases cover both phase shapes, a complete improving trial
with a selected TOP90 passive, and an off-grid route origin whose rectangle is
aligned relative to that origin. Negative cases cover receipt/report lifecycle
and ordering, exact v19/v16/v15 source fields and bindings, signed-long
boundaries, all raw equality fields, exact v15 route fidelity, missing route
cases, each of the four strict metric regressions, no-op fitness, no selected
TOP90, whitespace shape/type/fraction/relative-grid cases, route failures/caps,
and pose admission mismatch. Complete metric regressions are reported as
`FAIL`, while malformed or incomplete records are rejected.

The only prior source fixture is `passive-pose-policy-r1/unit.txt`, pinned to the
hash below; it supplies the fourteen unchanged v16 `sourceCanonical15` rows.
The canaries construct all v19 source, raw, pose, whitespace, and route rows
synthetically from the sealed baselines and that prior fixture. The actual-loop
marker check validates only the phase receipt shape; it does not establish
native loop behavior, generation, routing, physical fit, trial fitness, or
player behavior. The canary result records native runtime evidence as **NOT
RUN**.

| Input consumed by the canary run | SHA-256 |
| --- | --- |
| `assess.py` | `39b0ef1749c9ffeb71d248990f9083d379f908e4a823d1600e0b784b96bcfc94` |
| `check_assessment.py` | `d0d5a339ea1c5af8948dbd324cbc44446ff60cdd939bc1a602e6859bc47c6a92` |
| `assessment-canaries.json` | `c065f55b557005587943ddd5372ce2973be4251283efb231aac7eef0fc9da902` |
| `proposal.json` | `10850274f03ee80cf38d1e76e5422344b2e2c6ea6cbeecbb57bbb4549ab8449e` |
| `protocol.json` | `9996bade9539e2eccf6d98e434fe98e11f5b8202a534b4d2bdf97b9d326abf64` |
| `unit-suites.json` | `341e382135719d352c86be281d72c8b3368d5f4aaee9d0263e088a819ec2edb7` |
| `routes-suites.json` | `09809a0e2317bab47b614105bba45eef42da5f186409e971bd2efdbb22e88956` |
| `accepted15-baselines.json` | `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816` |
| `raw-baselines.json` | `c896f2ee0cb553220f5e7a4814e6ae8b7aecc21a2f7c787803db42e8a76f1790` |
| `passive-pose-policy-r1/assess.py` | `c9908a3d0439e64b01fa806f353fa7d6e024e8dc6050179a10bf8bef31ff1f26` |
| `passive-pose-policy-r1/unit.txt` | `e58c5e72aea79e65efcb427a643310545742a5161d83072b54fccdb0397f9570` |

Run the checker with the configured private Python runtime using UTF-8 mode and
bytecode writes disabled. Its output is recorded in
`assessment-canary-results.json`; fresh unit9 and route14 native phases remain
necessary before any real trial assessment.

The final canary command was the configured private runtime with `-X utf8 -B`
on `check_assessment.py`; it returned **PASS: 59 pre-runtime synthetic reader
canaries** in 0.9 seconds. No persistent process or bytecode output remained.
Two earlier development attempts are retained here: the first stopped before
scenarios because the proposal SHA constant had a typo; the second reached the
synthetic route check and rejected its fixture's stale v19 `@4` pose identity.
The reader pin and synthetic fixture were corrected to the frozen proposal and
v19 `@5` identity before the successful run recorded above.
