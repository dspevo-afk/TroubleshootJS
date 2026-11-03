# Actual LOAD12/73 detached baseline48 — evidence receipt

**Evidence status:** the final native and reader gates and root final-source audit pass for the recorded candidate. Publication remains pending. This receipt is not a routing or playable-board qualification.

See [PLAN.md](PLAN.md) for the bounded implementation contract and retained limits. The machine receipts and raw logs remain in the task-owned external bundle `load12-baseline-r1`; they were not copied into this repository.

## Candidate and request identity

- Parent/HEAD: `8093e2a989e690344c9913ce65ee326735f60348`; branch: `codex/q100-load12-baseline-20261103`.
- Native source snapshot revision 20: 1,079 pinned inputs, 86,838,457 bytes, manifest SHA-256 `02bb0b1512fc67a371fa24b249cc0dcfd451666592882d20e8c0378025a47975`. The r3 native receipt audited inputs before and after; process exit was 0, termination was proven, and cleanup status was PASS.
- Layout/model interpretation stayed at layout 26 and dependency epoch `tsj-generation-dependencies-v28`. The exact seed tuple is root `9007199254740993`, placement `-6587958367974952701`, routing `8445301674744508279`.
- The comparison is request 0, selected placement index 0 / attempt 4, pass 1, LOAD12 net position 3 / next fetch 4, branch 73 of 73, BOTTOM layer, from `JLOAD.1` to `CSTORE_E.1`. The grid is 478 × 309 (147,702 cells), origin (30,30), pitch 10. The initial prefix contains 17 vias and 88 traces. The replay retains the existing VIA cap of 48.

## Final native and supporting gates

| Gate | Result | Recorded evidence |
| --- | --- | --- |
| Actual query-baseline r3 | **PASS** | Parent elapsed 66.6657706 s. NULL: 15,464 ms, 300 units, max unit 1,317 ms, cleanup PASS / 1 ms. PROBE: 20,855 ms, 631 units, max unit 672 ms, cleanup PASS / 0 ms. The wrapper reports aggregate cleanup status PASS but does not export a separate aggregate cleanup duration (`null`). |
| Focused native six-suite set | **PASS** | 33.4961802 s; 106,568 assertions total. Detached replay 89,591; shortcut query 24; zero-VIA graph 57; prefix allocation 48; two-layer 13,112; accepted-branch capture 3,736. |
| Original D_SENSE graph query | **PASS** | 67.9075259 s parent. NULL: 15,697 ms / 300 units / 1,429 ms max / cleanup PASS 1 ms. PROBE: 22,087 ms / 634 units / 715 ms max / cleanup PASS 0 ms. |
| Original LOAD12 graph query | **PASS** | 67.8524283 s parent. NULL: 15,722 ms / 300 units / 1,294 ms max / cleanup PASS 1 ms. PROBE: 22,497 ms / 628 units / 647 ms max / cleanup PASS 0 ms. |
| Composite-reader positives and stale-count rejection | **PASS** | D_SENSE and LOAD12 positive receipts pass; stale 1,078-input metadata is rejected with the expected identity error. The selected reader source stayed stable. |
| Baseline Python reader tests r2 | **PASS with declared skip** | 19 passed, 1 file-symlink privilege case skipped, 20 methods total; 0.8688835 s. |
| Existing Python reader tests r2 | **PASS with declared skip** | 51 passed, 1 file-symlink privilege case skipped, 52 methods total; consumed source inputs stayed unchanged. |
| Baseline CLI positive and six artifact-bound negatives | **PASS** | Negative controls reject stale 1,078-input metadata, rounded seed, clone post-hash mismatch, duplicate event, over-640 work, and impossible entry-call/work data. Expected exit-2 rejections are recorded as successful negative controls. |
| Actual Windows directory-junction negative | **PASS** | Junction was rejected and removed using exact nonrecursive cleanup; target reader remained stable and target mutations were NONE. |
| Production/build reuse audit | **PASS — reuse boundary only** | 29.9876062 s. All 1,202 Player and Developer compiler inputs per variant matched; 357 preserved Player outputs and 357 archived Developer outputs matched. Consumed inputs for E06State10, Current9, Player privacy, and normal3 were unchanged. These are reused results, not fresh builds or gates. |
| Root final-source audit r1 | **PASS** | 14.8851226 s; sanitized qualification receipt recorded. |

The focused native receipt and the three actual diagnostic receipts each report successful input audits and cleanup. The native suite inventory is 1,079 inputs / 86,838,457 bytes at manifest `02bb0b1512fc67a371fa24b249cc0dcfd451666592882d20e8c0378025a47975`.

## Detached replay comparison

The original PROBE operation supplied fresh source Pre/Post captures. A typed owner copy was made and drained, then its actual detached replay was compared to the original branch. Source and clone agree on the path, goal, metrics, seeds, net order, prefix rows, Pre geometry, Post geometry, publication, and Post rows. The source was preserved and resumed. No graph receipt was fabricated.

| Compared property | Source and clone result |
| --- | --- |
| Path tuple SHA-256 | `1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d` — equal |
| Goal SHA-256 | `cc48299ed82e39f1bac10269e7c41afc6468f1e53fc96baa36a95d5c60d548d2` — equal |
| Path metrics | 222 nodes, planar length 2,170, 2 bends, 4 flips — equal |
| Pre geometry SHA-256 | `7c963bb9ba10f7419d2e6abaecff4cfc1504ba2744914d6874ffd02ca475c447` — equal |
| Post geometry SHA-256 | `9ea3f27c084608f5dd1ed14a5720229fb3a4a6697d632fb8a84212835bf1b297` — equal |
| Publication SHA-256 | `20c2e5579af0b95ac0f29422ef071ee1c8b8f85820bbe7af78f55d13b783300a` — equal |
| Physical rows | Pre: 17 holes / 17 vias / 88 traces. Post: 21 holes / 21 vias / 93 traces; publication adds 4 vias and 5 traces — equal. |

The charged work is recorded separately from operation elapsed time:

| Phase | Calls | Work / entries |
| --- | ---: | ---: |
| Owner copy | 247 | 8,089,655 work; 2,665,509 copied entries |
| Detached replay through Pre | 571,334 single-step calls | 571,334 work |
| Detached replay through Post | 1 | 472 work |
| Clone Pre / Post cursors | 26 / 4 | 829,511 / 117,262 work |
| Original source Pre / Post cursors | 26 / 4 | 829,508 / 117,262 work |

The recorded run stayed under the existing 90 s operation, 640-unit, 5 s per-unit, 120 s cleanup, 32,768 quantum, and 48-VIA limits. The receipt explicitly records `graphReceiptCreated=false`, `routeAdoption=false`, `layoutQualification=false`, and `playable100=false`.

## Preserved development failures

| Gate | Result and measured limits | Correction recorded for r3 |
| --- | --- | --- |
| Actual query-baseline r1 | **FAIL**, parent 53.5666379 s; NULL 15,619 ms / 300 units; PROBE 6,540 ms / 309 units / 647 ms max; case cleanup PASS / 1 ms and 0 ms. | The adapter stopped at newly installed Publisher stage 0 instead of continuing collection. |
| Actual query-baseline r2 | **FAIL**, parent 52.2255731 s; NULL 15,205 ms / 300 units; PROBE 6,697 ms / 309 units / 721 ms max; case cleanup PASS / 1 ms and 0 ms. | Stage 1 alone did not reach the existing capture boundary; the metric walk had to finish before the quality decision. |
| Baseline Python r1 | **FAIL**, 20 methods with 3 fixture errors and 1 declared file-symlink privilege skip. | Corrected fixture scalar-subset records and artifact paths; r2 passed without weakening validators. |
| Current-census Python r1 | **FAIL**, required-input cardinality 13 versus actual 14. | Metadata/test correction is covered by current-census r2 PASS (51 passed plus 1 declared skip). |

The r3 adapter follows Publisher stage 0 collection and stops at the unchanged quiescent stage 1 metric-walk boundary. It also follows the real stage 5-to-2 cycle between publication pieces and completes at stage 6. The correction changes no routing behavior, oracle, work quantum, or budget. Earlier failure bundles remain preserved and receive no pass credit.

## Limits and pending acceptance

This evidence covers one authored request/placement and actual LOAD12/73 branch under the recorded candidate. It does not establish cap17, VIA avoidability, route adoption, an accepted layout, electrical solvability, or playable100 qualification. No production or visible Player flow changed. No production schema/model epoch or budget was changed.

Fresh GWT/Player, full33, electrical62, human Player, CI, and full30 gates were **NOT RUN** for this native diagnostic scope; reused build and regression evidence is identified above and is not presented as fresh. Visible Player/browser evidence is **NOT APPLICABLE** because no visible Player flow changed. Protected HARD was unread/unrun in this scope; the historical unread boundary remains **UNCERTIFIED**. HARD and PSYCHOTIC remain unavailable; full30 remains owner-excluded. Playable100 remains 0 and beta remains held.

Root's final-source audit r1 passed in 14.8851226 s and the sanitized qualification receipt was recorded. Root updated the roadmap, architecture, and task report; no implementation or test blocker remains. Publication and remote verification remain pending. No commit, push, or milestone completion email is claimed here. The previous charged-owner email was already sent once and must not be duplicated.
