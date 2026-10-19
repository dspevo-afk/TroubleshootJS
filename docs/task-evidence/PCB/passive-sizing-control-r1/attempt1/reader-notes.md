# Passive sizing-control reader notes

**Reader canaries: PASS. Native execution: NOT RUN.** This packet implements the independent data reader and synthetic canaries for the explicit placement17/density14/passivePosePolicy2 control under `RB56_BOARD_NORMAL@3`. It does not change source, defaults, accepted policy, or the strict14 fitness result.

`assess.py` exposes four APIs. `check_phase(text, receipt, timing, suites)` checks exactly eight timing keys against the frozen protocol, phase suite order (8 unit or 2 route suites), terminal and cleanup markers, and report order in both receipt and log. It matches each expected suite marker prefix exactly once and in manifest order, accepts only bounded structured detail tokens (including forms such as `assertions=245 geometries=7 sequence=63/63 visits=126`, `roots=14 assertions=114`, `rows=5 routes=0`, and `roots=1 rows=3`), and requires each complete PASS line to match between terminal and child receipt. `check_sources(unit_text)` returns all14 seed bindings after verifying old source rows, passing the existing16 rows through the SHA-pinned prior reader, and independently deriving the control17 source digest by replacing only `placement-interpretation=15;` with `placement-interpretation=17;`. `check_raw(unit_text, bindings)` returns the 15 JSON-serializable raw records after comparing every 15/17 field to the sealed untouched-candidate baseline. `assess_routes(route_rows, pose_rows, bindings)` returns reader status, the four exact old route reproductions, and two control17 metric-delta records versus both15 and16; those deltas are descriptive and impose no fitness or non-regression gate.

The control17 route reader requires the frozen 43-field row shape, source graph/intent and signed-long identities from source bindings, exact part/pad population, PASS status, existing bounded work counters, and `area == width * height`. Its pose census must list every canonical passive in sorted TOP0 and have empty TOP90 under the exact `@3` admission identity. The four old route and pose rows reproduce their sealed baselines exactly, except elapsed time is required to be positive and at most60,000ms.

The runner used the private workspace `.tools.python` runtime with `-X utf8 -B`:

```text
python -X utf8 -B docs/task-evidence/PCB/passive-sizing-control-r1/check_assessment.py
```

Final synthetic result: **20/20 canaries PASS, 52 explicit expectation assertions, 0 failures.** The positive chain covers all14 source bindings,15 raw rows, four old routes, two in-memory control routes, and passing unit/route phase fixtures. Negative cases reject reordered rows and suite markers, changed policy/admission/density, graph or signed-long drift, a wrong derived17 digest, a changed untouched raw candidate, any old-route metric change, TOP90 membership, missing or duplicate suite PASS prefixes, mismatched full PASS lines across log and receipt, unrecognized free-text suffixes, failure/timeout status, and missing or early cleanup. The suffix fixtures cover published suite-detail formats and protocol-specific raw/route row-count forms; these remain synthetic reader inputs.

Only the SHA-pinned published policy `unit.txt` was used, and only its14 trial16 source rows were retained as `sourceCanonical15` fixtures. No new unit/routes output was read. All new17 source, raw, route, pose, phase and timing records in the canaries are generated in memory. Native phases and physical-solver execution are **NOT RUN**; strict14 fitness is **NOT RUN here**, and its existing rejection remains unchanged. The synthetic PASS verifies reader behavior and input contracts only.

Consumed SHA-256 identities:

| Input | SHA-256 |
|---|---|
| `proposal.json` | `4c42f270f063d931d5180b184f049137cbf6ad0681c776a89783930cbcd08c4d` |
| `protocol.json` | `a1e0859389d3890d813f70ba91f19922893e750cfd334ac1a88c51c2583cb1a6` |
| `unit-suites.json` | `829d8bea0a3fe4b6ecb79bd74f3019b5623b66c7bfd028321786dc00e6f17897` |
| `route-suites.json` | `8498271b32e5a521819ced0ce9288f1d3f6c1c4935bd65c9fa1f3e2fd8f30e59` |
| `accepted15-baselines.json` | `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816` |
| `route-baselines.json` | `aa17f3137be2b929d2bcaf3651640b34a7db511010b5d4a05c1f813799013b19` |
| `raw-baselines.json` | `c896f2ee0cb553220f5e7a4814e6ae8b7aecc21a2f7c787803db42e8a76f1790` |
| `pose-baselines.json` | `377f70b7c888722ac1600b8d446cebb4230f7ab9ba78b1fccf8782cfdfa71ce8` |
| Pinned prior `passive-pose-policy-r1/assess.py` | `c9908a3d0439e64b01fa806f353fa7d6e024e8dc6050179a10bf8bef31ff1f26` |
| Published prior `passive-pose-policy-r1/unit.txt` source fixture | `e58c5e72aea79e65efcb427a643310545742a5161d83072b54fccdb0397f9570` |
| This `assess.py` | `18cfe6a5dc28d278bffc0a4514eee81394db44632e74cf988af042bf62094d5d` |
| `check_assessment.py` | `748d9b166edf9eaf2fe4014f425a1adc733f39f6248c057c820f4ec0fea3680e` |
| `assessment-canaries.json` | `f887125c2f6cd130521e827d24b88cb8fef4e95d35b423c5b12e5e64378cd592` |
| `assessment-canary-results.json` | `7e97a9c766c1488155ff172f9c6880a966519920794fa87e1e48ffcf5c2728f5` |
