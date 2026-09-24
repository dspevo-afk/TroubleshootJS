# Q30 two-layer physical investigation

**Q30-P1 continuation:** The original 288-row result below remains the
pre-floorplanning baseline. Its two pilot/failed-net annotation patch hunks
were subsequently applied to the isolated Q30 worktree to run a matched
after-placement corpus; they do not alter router search or cost. The historical
`run-comparison.ps1` applies that patch to a pre-P1 source copy and therefore
must be run against the recorded source snapshot, not again against the
already-patched P1 worktree. `run-p1-corpus.ps1` compiles the current worktree
without patching it, and `summarize-p1-corpus.py` verifies route quartets
and emits machine-readable before/after results. This note does not revise the
original accepted baseline or claim Q30 admission.

This is a developer-only comparison of the **current root-plan-equivalent
33-package Q30 pilot manifest**, not a generated CircuitJS instance or a
normal-player admission. The original baseline was collected with a scratch-only
patch. The current Q30-P1 worktree has its two pilot/annotation hunks applied;
the current `EXACT_ROOT` pilot now reads the actual `Rb30Plan` board and seeds.

## Q30-P1 matched floorplanning and selected-policy results

The stable floorplanner comparison is in
[`floorplanning/before-after-comparison.json`](../floorplanning/before-after-comparison.json).
The 12 signed seeds and six candidate ordinals are unchanged. The Q30-P1
matched routing file [`p1-matched-before-after.json`](p1-matched-before-after.json)
checks all 288 before/after route keys and equal topology, layout/routing seed,
package/pad/net counts and maximum net degree for each key. Each placement
still receives the same four physical policies. P05, P07 one-layer and P07
restricted two-layer do not route the population. P07 fuller two-layer rises
from **6/72 on five seeds** to **54/72 on all 12 seeds**, including all six
held-out seeds. All accepted routes validate real two-face connectivity and
physical clearance; no factory links or budget/cost changes were used.

| Matched P07 fuller metric | BEFORE | AFTER |
|---|---:|---:|
| Accepted placement routes | 6/72 | 54/72 |
| Seeds with a success | 5/12 | 12/12 |
| Successful via range; median | 36–46; 38 | 17–41; 31 |
| Successful unique copper median | 34,290 | 28,810 |
| Successful outline area median | 5,115,000 | 7,839,000 |
| All-attempt expansion median; p95 | 636,015; 1,000,000 | 399,480; 829,803 |
| All-attempt route time median; p95 | 1,477; 2,592 ms | 834; 1,622 ms |

The larger outline is a material tradeoff. The stable placement comparison
measures a 17.8% lower net MST proxy, 45.0% fewer estimated ratsnest crossings,
35.1% fewer net-box overlaps and 72.2% shorter median connector-to-nearest-net
pad distance. These are placement proxies, not copper or workbench usability
measurements.

The actual provider-selected `MEDIUM_BOARD@1` result is separate from the
four-policy placement matrix. [`p1-policy-final-summary.json`](p1-policy-final-summary.json)
and [`p1-policy-final-raw.txt`](p1-policy-final-raw.txt) record six generated
placements, ranked score components, the best three one-face attempts and up
to three fuller two-layer attempts for each seed. The selected result accepts
**12/12 seeds and 6/6 held-out seeds**, with both copper faces and 17–34 vias
(median 29). Median selected outline area is 6,454,500; median total routing
work is 4,069,854 expansions and median elapsed time is 3,080 ms. All 12
selected routes use P07 because P05 exhausted on this Q30 population. The
policy retains a successful P05 route when an optional P07 probe fails or is
not materially better; its 10% plus 5,000-unit quality rule is part of the
versioned policy. The route comparison and the selected-policy run are
structural candidates; normal Q30 admission and player diagnosis remain
unqualified.

The final policy rerun follows the one-face fallback correction, a generic
pre-mutation compact-outline access check, and the exact selected-geometry
exporter. The compact check recovered held-out seed 83 after direct P07
routing had succeeded but final cropping removed a required escape channel;
the previously valid planner outline bounds its deterministic margin fallback.
Earlier `p1-policy-raw.txt` and `p1-policy-interim-pre-edge-raw.txt` (with their
summaries) are intermediate receipts. The direct-route `p1-geometry-seed0-c4.txt`
and `p1-geometry-seed37-c1.txt` are also intermediate pre-final-crop routes;
`p1-selected-geometry-seed0.txt` and `p1-selected-geometry-seed37.txt` contain
the exact selected final layouts. Their area, via count and total routing
work match the final policy receipt. Face renderings from these files are
structural views, not production workbench screenshots.

## Matched setup

- Recorded exact signed-long seeds: representative `0, 1, 3, 17, 42, -1` and
  held-out `11, 23, 37, 59, 83, -23`.
- For every seed, compare all six `PcbPlacementPlanner` candidate outlines.
  Each quartet routes the **same materialized placement object**: identical
  33-package inventory, 82 pads, 24 or 25 nets, topology, pad/part geometry,
  positions, outline, access and placement seed. The routing seed is recorded
  and passed to P05; P07's deterministic A* API has no routing-seed parameter.
  Its algorithms and orderings therefore differ from P05. The input identity
  for each quartet is also checked by `placementSignature`, outline area and
  topology in `matched-288.json`.
- `P05_SINGLE_FACE` is the actual P05/P09 bottom-face route request and its
  normal 1,000,000-expansion bound. `P07_ONE_LAYER`,
  `P07_RESTRICTED_TWO_LAYER` and `P07_FULLER_TWO_LAYER` use the accepted
  developer prototype with unchanged 10-unit grid, one-million total and
  100,000 per-branch expansion bounds. Restricted permits two transitions per
  branch and eight vias per board; fuller permits four and 48. Both keep
  original package courtyards and the shared trace width 9 / visible
  clearance 6 predicates. No factory links or invented via geometry are used.
- An accepted route is reported only after the real board layout validates
  physical connectivity, shorts, clearance and route quality. Genuine two
  layer success also requires nonzero top and bottom trace length and plated
  via count. Failure rows carry the bounded reason and failed net; zero
  route metrics mean no **complete accepted** route, not that the search never
  drew partial copper.

## Results

The 12-seed first-placement control contains 48 rows: all four policies
reject candidate 0 for every seed. The full matched corpus contains 72
placements and 288 route rows. P05 single face, P07 one layer and P07
restricted two layer each accept 0/72; P07 fuller accepts 6/72 on five seeds.
All six accepted fuller routes have copper on both faces and 36–46 real vias.

| Seed:candidate | Topology | Top/bottom segments | Top/bottom length | Vias | Area | Unique copper | Quality score | Expansions | Route ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0:1 | Separate direct, BJT/BJT | 37 / 113 | 5,970 / 30,510 | 37 | 5,610,000 | 36,480 | 184,760.19 | 500,236 | 1,006.78 |
| 23:4 | Separate direct, NMOS/NMOS | 33 / 134 | 4,660 / 29,630 | 42 | 5,292,000 | 34,290 | 175,728.11 | 852,884 | 2,231.99 |
| 23:5 | Separate direct, NMOS/NMOS | 46 / 125 | 7,860 / 32,090 | 38 | 5,115,000 | 39,950 | 181,815.44 | 986,289 | 1,874.93 |
| 37:4 | Shared hysteretic, BJT/BJT | 42 / 122 | 5,650 / 31,310 | 40 | 4,658,000 | 36,960 | 169,259.07 | 917,447 | 1,690.30 |
| 59:2 | Shared hysteretic, BJT/NMOS | 30 / 120 | 3,890 / 27,960 | 36 | 5,115,600 | 31,850 | 175,806.70 | 711,048 | 1,302.63 |
| 83:5 | Separate direct, BJT/NMOS | 36 / 136 | 6,580 / 27,590 | 46 | 4,834,800 | 34,170 | 165,082.00 | 513,914 | 1,353.81 |

`summary.json` gives each policy's exact failure distribution; `matched-288.json`
contains topology, placement signature, dimensions/area, per-face trace and
segment counts/lengths, via count, unique copper, score, expansions, failed
net and elapsed route time for every matched row. `first-placement-48.json`
preserves the independently completed first pass. Both raw stdout receipts
are retained beside them.

The shared-hysteretic Q30 incidence graph has a prior nonplanarity witness in
the Q30 pilot evidence. This comparison finds physically valid two-face routes
for that topology on seeds 37 and 59, resolving that structural barrier for
those exact placements. Separate-direct routes also occur on seeds 0, 23 and
83. The six results show a one-face bottleneck under the current bounded
algorithms. They do not prove every single-face placement impossible or that
the restricted policy cannot be improved. Fuller acceptance requires many
vias and 31,850–39,950 unique copper units; inspectability, service, customer
play and broader seed yield remain unqualified. Every Q30 placement also
exceeds P09's 16-package/40-pad/16-net/degree-10/area-2,250,000 envelope, and
P09 admits no vias. None of these prototype routes enters normal generation.

## Reproduction

Run the Q30-P1 matched placement matrix and selected policy from this worktree
with JDK 8 and the existing GWT JAR bundle:

```powershell
.\docs\task-evidence\Q30\two-layer-investigation\run-p1-corpus.ps1 `
  -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' `
  -Mode two-layer -Seed all -CandidateCount 6 `
  -OutputPath 'docs/task-evidence/Q30/two-layer-investigation/p1-matched-raw.txt'
python -B docs/task-evidence/Q30/two-layer-investigation/summarize-p1-corpus.py `
  --raw docs/task-evidence/Q30/two-layer-investigation/p1-matched-raw.txt `
  --before docs/task-evidence/Q30/two-layer-investigation/matched-288.json `
  --out docs/task-evidence/Q30/two-layer-investigation/p1-matched-before-after.json
.\docs\task-evidence\Q30\two-layer-investigation\run-p1-corpus.ps1 `
  -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' `
  -Mode p1-policy -Seed all `
  -OutputPath 'docs/task-evidence/Q30/two-layer-investigation/p1-policy-final-raw.txt'
python -B docs/task-evidence/Q30/two-layer-investigation/summarize-p1-policy.py `
  --raw docs/task-evidence/Q30/two-layer-investigation/p1-policy-final-raw.txt `
  --out docs/task-evidence/Q30/two-layer-investigation/p1-policy-final-summary.json
```

Each invocation compiles a source snapshot in a unique OS-temporary directory.
The matched direct-route result can be reused after the edge-trim fix because
that fix changes only `SeededPcbLayoutGenerator` post-route finalization; the
frozen planner, `Q30PhysicalPilot`, direct P05/P07 routers, plan, seeds and
matched route inputs did not change. The selected-policy corpus was rerun from
the final compaction source.

The original pre-floorplanning baseline is reproduced separately:

From the Q30 worktree, with JDK 8 and the existing GWT JAR bundle:

```powershell
.\docs\task-evidence\Q30\two-layer-investigation\run-comparison.ps1 `
  -SourceRoot '<Q30_WORKTREE>' -JavaHome '<JDK8_HOME>' `
  -GwtHome '<GWT_HOME>' -Seed all -CandidateCount 6
```

The script creates a unique OS-temporary source copy, applies
`two-layer-investigation.UNAPPLIED.patch` **only there**, compiles that copy
with JDK 8, then saves its stdout as `routing.txt` in the printed temporary
directory. `-CandidateCount 1` reproduces the 48-row first-placement control.
The patch changes only the developer Q30 pilot and a generic P07 failure-net
annotation. It does not change routing decisions, budgets, via predicates or
production P09 policy. `git apply --check` and application of the patch to
fresh copies of those two original files passed; final-source JDK 8 native
compilation and the full 288-row run passed. The run is a structural physical
probe, not a JDK8/GWT production build or compiled player-flow test. A separate
`-Seed 0 -CandidateCount 1` invocation of this exact reproduction script also
passed JDK 8 compilation and reproduced all four candidate-0 outcomes and
expansion counts. The task-owned temporary source copies and raw logs remain
available for review; the Q30 worktree was not edited by this investigation.
