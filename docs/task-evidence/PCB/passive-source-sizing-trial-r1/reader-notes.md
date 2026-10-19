# Source-sized passive-pose reader

Candidate `e464dbfd0bffc052e7e233ec59e012e86c16f8c2` on `codex/q60-faulted-profile-counts`.

Status: **PASS — pre-runtime synthetic canaries only.** Native runtime evidence is **NOT RUN**. The canary report does not establish generation, routing, physical-whitespace, or trial fitness behavior.

The reader validates the frozen 8 unit suites and 14 route suites, with report records in maintained order. Unit coverage is 14 trial18 source bindings, 15 raw comparisons, 14 saved15 source rows, and 14 unchanged trial16 source bindings. Route coverage is 28 saved15/trial18 pairs, each with its whitespace record before its pose census and routed row. A complete strict result is PASS only when all 14 trial18 rows avoid regression across area, protected-empty-rectangle area, copper length, and vias, at least one metric improves, and at least one actual TOP90 passive is selected. Saved15 comparison preserves all 42 non-time fields.

`check_assessment.py` passed 52 independent synthetic canaries: 4 positive and 48 negative. The positive set covers both phase shapes, a complete improving, turned trial, and exact native rectangle-object format with a deliberately off-grid trial18 outline origin whose rectangle is grid-aligned relative to that origin. The negative set covers report order and completeness, unknown/failure/timeout/cleanup/terminal and receipt differences, source digest/graph/signed-seed/population/field-set drift, policy/density/admission drift, raw 18-to-15 and sealed-baseline equality, saved15 mutations, route status/caps, whitespace type/fraction/relative-grid/rectangle/population/case errors, and complete regression/no-op/no-TOP90 results. The complete strict failure cases return `FAIL` rather than raising or passing.

Synthetic trial18 source digests are independently derived from each sealed `sourceCanonical15` by replacing its unique `placement-interpretation=15;` marker with `placement-interpretation=18;`. The prior `unit.txt` contributes only its 14 `PCB_PASSIVE_POLICY_INPUT` canonical source fixture records. The prior reader hash is recorded below; its module and prior runtime results are not imported or reused.

The inputs below were SHA-256 checked for the canary run. These receipts bind the reader, proposal/protocol, manifests, sealed baselines, and permitted prior source fixture.

| Input | SHA-256 |
| --- | --- |
| `assess.py` | `291777ab7bc2d96c146ddda5af4d7ad1f9b9cc0aa6ccb990171e46afbb443493` |
| `check_assessment.py` | `eddb180572c3fcc9d8b59fbfbe210d7a87399ddc4f19806cef110e63d6683e21` |
| `assessment-canaries.json` | `b83c4f6f0a153683120a63ae2e54dfac093d6ee11f7876556c4490ebcbfb322` |
| `proposal.json` | `660278926345ca2a707ac8bce84f6ad1ba16fcbcbe909b55bc4dd4b5c92a98de` |
| `protocol.json` | `48b16c668aba3a5606748fd3985175941740b17ac034f3c365f816a6c926761e` |
| `unit-suites.json` | `8521298e51c302b65ab7ceaa66d6d405c5ee33722d2a2b2a7ed028a1d90b000b` |
| `routes-suites.json` | `0f48566d9b26530e7919e6230eb9c0dec081d7d33848db47073fc1638ee0e0a0` |
| `accepted15-baselines.json` | `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816` |
| `raw-baselines.json` | `c896f2ee0cb553220f5e7a4814e6ae8b7aecc21a2f7c787803db42e8a76f1790` |
| `passive-pose-policy-r1/assess.py` | `c9908a3d0439e64b01fa806f353fa7d6e024e8dc6050179a10bf8bef31ff1f26` |
| `passive-pose-policy-r1/unit.txt` | `e58c5e72aea79e65efcb427a643310545742a5161d83072b54fccdb0397f9570` |

The next evidence needed is the frozen native unit and route phase output, receipt, and timing. Run the reader against those artifacts only after the trial outputs are supplied; keep the synthetic PASS separate from native status.
