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
The actual-loop suite requires the exact summary form
`assertions=N fixtures=5 evaluator=mediumPlace`, with `1 <= N <= 1000000`,
in that order and no extra text. Policy markers retain the prior exact
`geometries=7 sequence=63/63 visits=126` requirements; admission markers retain
the bounded `N assertions` form. The route phase has fourteen suites and 28
ordered v15/v19 cases, each with whitespace, pose census, and route records in
that order. Synthetic unit markers use 37 loop assertions, the archived policy
summary shape, and `31 assertions` for admission, independent of any runtime
assertion count.

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

The frozen manifest records **72 passing synthetic canaries: 4 positive and 68
negative**. Positive cases cover both phase shapes, accepted actual-loop,
policy, and admission summary formats, a complete improving trial with a
selected TOP90 passive, and an off-grid route origin whose rectangle is aligned
relative to that origin. Negative cases cover strict actual-loop summary bounds
and grammar (zero/overbound/missing assertions, wrong fixtures/evaluator,
trailing text, duplicate and reordered fields), policy geometry/sequence/visit
requirements, bounded admission counts, receipt/report lifecycle and ordering,
exact v19/v16/v15 source fields and bindings, signed-long boundaries, all raw
equality fields, exact v15 route fidelity, missing route cases, each of the four
strict metric regressions, no-op fitness, no selected TOP90, whitespace
shape/type/fraction/relative-grid cases, route failures/caps, and pose admission
mismatch. Complete metric regressions are reported as `FAIL`, while malformed
or incomplete records are rejected.

The only prior source fixture is `passive-pose-policy-r1/unit.txt`, pinned to the
hash below; it supplies the fourteen unchanged v16 `sourceCanonical15` rows.
The canaries construct all v19 source, raw, pose, whitespace, and route rows
synthetically from the sealed baselines and that prior fixture. The actual-loop
marker check validates only the phase receipt shape; it does not establish
native loop behavior, generation, routing, physical fit, trial fitness, or
player behavior. The canary result records native runtime evidence as **NOT
RUN**.

For this parser correction, root reported that native attempt 1 failed because
of a missing connector and that the three-line fixture fix followed. Root then
reported native unit9 attempt 2 passed in 54.1480115 seconds, including 162
actual-loop assertions, but the reader rejected its summary. This worker did
not read either native output, and attempt 2 is not reused after the parser
change. Root will freeze the corrected reader and run fresh unit9/routes14
phases. The canary result below remains synthetic-only.

| Input consumed by the canary run | SHA-256 |
| --- | --- |
| `assess.py` | `485038f44a3583f922416f298ca9f7cb6a9c12b70b1555f0fc7311a4a6cf4e79` |
| `check_assessment.py` | `467bcf45b2db537e57c07578502f7a935c68c58b6495603d808849b8f3a609b1` |
| `assessment-canaries.json` | `5c2ccfbf1d0a46cea6a393d13f4102c468cf3783a74ee27bfefcf14ff8569ce4` |
| `proposal.json` | `10850274f03ee80cf38d1e76e5422344b2e2c6ea6cbeecbb57bbb4549ab8449e` |
| `protocol.json` | `243c8b4464840c247aec7609ee42f4297e37f9328c26f6be16e014b4cb6f02b6` |
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

The final correction canary command was the configured private runtime with
`-X utf8 -B` on `check_assessment.py`; it returned **PASS: 72 pre-runtime
synthetic reader canaries** in 1.0 seconds. No persistent process or bytecode
output remained. This correction restores the archived policy/admission marker
grammar and adds strict parsing for the actual-loop summary; it does not change
source, raw, physical, route, or fitness oracles.
Two earlier development attempts are retained here: the first stopped before
scenarios because the proposal SHA constant had a typo; the second reached the
synthetic route check and rejected its fixture's stale v19 `@4` pose identity.
The reader pin and synthetic fixture were corrected to the frozen proposal and
v19 `@5` identity before the successful run recorded above.
