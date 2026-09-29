# Q30 accepted-source two-lane profile and routing-map experiment

2026-09-28. **Q30 BLOCKED / NOT ACCEPTED; no optimization integrated.**
The branch-local primitive map preserved proof but did not demonstrate a material
repeatable cold improvement above the predeclared noise threshold. Stop this
experiment; do not integrate its prototype or expand its timing campaign.

Source base: `3a877723f366f0dfa51666ab4121ea82f0e82838`, branch
`codex/q30-multirail-qualification`. Clean Git exports contain the committed
plan-3 path (33/35/37 packages); all measured rows here request **seed7 / 33 parts**.
No dirty scale implementation or prior support-tracking/LU prototype was imported.
Normal Q30 remains disabled; **90,000 ms / 640 work / 5,000 ms active** are unchanged.
The private coordinator's 300-second measurement allowance is not normal admission.

## Measurements and decision

Each row used a fresh maintained browser host/profile and the same compiled
coordinator request. Cold and warm phases belong to one row. Runs were sequential,
with no concurrent task build/native campaign. Uncontrolled OS background activity
is a remaining host limitation. Both candidate runs use identical frozen inputs.

| Row | Arm | Cold s | Routing s | Proof s | Warm s | Host cleanup s |
|---|---|---:|---:|---:|---:|---:|
| 01 | control | 79.020 | 16.661 | 57.262 | 21.763 | 1.328 |
| 02 | diagnostic profile | 79.433 | 16.647 | 57.670 | 21.462 | 1.266 |
| 03 | control | 76.978 | 16.363 | 55.610 | 21.260 | 0.922 |
| 04 | control | 79.989 | 17.726 | 56.928 | 21.439 | 1.282 |
| 05 | candidate | 77.308 | 15.223 | 57.098 | 20.379 | 1.047 |
| 06 | candidate | 78.350 | 16.125 | 57.154 | 20.095 | 1.203 |
| 07 | control | 78.817 | 16.993 | 56.650 | 22.481 | 1.250 |

The declared candidate sequence was **B/C/C/B**, rows04–07. Both paired cold gains
had to exceed `max(1,000ms, 3 × surrounding control range)`:

- Control range: **1,172 ms**; required gain: **3,516 ms**.
- Paired cold gains: **2,681 ms and 467 ms — FAIL**.
- All four controls span **3,011 ms**; wider three-range sensitivity: 9,033 ms.
- Paired routing gains: 2,503 / 868 ms; proof changes: 170 / 504 ms slower.
- Warm gains: 1,060 / 2,386 ms. These do not establish cold acceptance.

Candidate logical work did not decrease: cold468 total/390 hypothesis work,
warm79/1; canonical routing work/order and proof context matched controls.
Changing the lookup representation therefore has no claimed causal reduction in
search expansions. Seeds13/64 were conditional on a material seed7 result and
were **NOT RUN**. Neither this seed nor these private measurements establish the
full accepted-size corpus, normal-player margin, or 20–40 scale coverage.

## Fresh lane A: solver

The owned duration-advance operations consumed **55.145 s**, plus UI execution
0.124 s and analysis1.413 s at their operation boundaries. Actual work was
617,983 accepted simulation steps, zero retries/convergence failures,
1,350,575 nonlinear trials, 732,592 nonlinear factorizations and solves,
502 analyses/matrix stamps, and 3.089915 simulated seconds. There were
75,122,038 element `doStep` calls and 6,900,056,544 copied matrix/RHS slots.
The largest observed matrix had88 rows and the largest graph247 elements.

Nested inner timing: factor21.856 s, publication8.805 s, solve5.948 s,
wire refresh5.855 s, copy5.841 s, element `doStep`4.453 s, stampCircuit0.144 s.
**Do not add these to enclosing operation totals.** Stamping may include linear
factorization; all observed factors were nonlinear and occurred in solver runs.
Maximum owned duration advance was904.6 ms; maximum individual factor5.7 ms.

The main purpose totals were healthy settling13.597 s, repair-status settling
11.210 s, customer retest11.192 s, and input observations10.122 s. Those are
overlapping views of solver work, not additional cost. Five hypothesis graphs
were freshly constructed; six distinct owner/graph identity-hash pairs were
observed across11 bindings. Hashes describe ownership, not electrical equivalence.
Ten control-revision invalidations and96 sensor-input commands were observed;
none of those commands repeated its immediate prior input value. No safely
removable settling, factorization, or electrically equivalent-state reuse was
established. No solver algorithm or support-tracking prototype was integrated.

## Fresh lane B: routing

Six accepted placements required24,951 planner evaluations; placement planning
cost0.830 s. P05 made3 candidate attempts,13 rejected passes,164 net attempts,
488 branches and3,000,000 expansions. Its A* search cost0.827 s inside1.543 s of
branch-route time. P07 made3 candidates (2 successful,1 rejected),7 passes
(2 successful,5 rejected),270 branches and1,448,229 expansions.

P07 A* work cost **14.351 s**, branch setup0.667 s; total pass/slice work was
about15.080 s. It made8,027,604 best-map gets,2,538,101 puts,6,353,497 node offers
and225,873 stale polls. This measured opportunity motivated testing a branch-local
integer-key map; it did not establish that map operations dominated A* cost.
Net expansion leaders were RAIL5(557,458), B_COIL_LOW(370,458), LOAD12(126,208),
and A_COIL_LOW(117,522). Net timing was not separately isolated.

The canonical route selects **P07_FULLER_TWO_LAYER, placement2**, second in
`[5,2,3,4,1,0]`. Per-pass work partitions as follows:

| Pass outcome | Expansions | Pass work s |
|---|---:|---:|
| Five rejected passes | 875,841 | 8.8110 |
| Successful but unselected placement5 pass1 | 261,310 | 2.9310 |
| Selected placement2 pass1 | 311,078 | 3.3375 |

These timings include setup/control/publishing around search. Discarded work is
not automatically avoidable work. Original placement access is already checked
before search; later compact-outline access depends on actual routed bounds.
No exact early rejection preserving deterministic selection was established.
Physical committed-state validation cost0.086 s; final placement validation
0.043 s, compact-margin validation0.034 s and geometry validation0.030 s.

## Validation, interpretation limits and failures

- **PASS:** all seven raw cold/warm reports, source/web input immutability and host
  cleanup. Each cold proof has5 hypotheses ×37 observations,10 hypothesis pairs;
  the maintained reader rejects37 corruptions per run. All declared cross-run
  proof projections match, including canonical physical/routing context.
- **PASS:** final private-profile native seven suites: request446, measurement
  budget25, normal policy325, physical policy27, solver profile12, stage profile76,
  routing resumption2,203 assertions. Candidate native: P07(1,087) and routing
  resumption(2,203). These are focused gates, not the full matrix.
- **PASS:** actual `scripts/build.ps1` JDK8/GWT five-permutation control/profile/
  candidate builds. Final compile/link seconds:84.329/1.444,82.842/1.447,
  87.434/1.369 respectively. Candidate and profiling code were isolated exports.
- **PASS:** compiled A07 for all three arms plus maintained reader/three negative
  mutations each; profile scope-loss/13 negatives; injected profile-capture
  failure (both captures failed, mask3, cleanup/restoration complete, profilers
  cleared, warm NOT_RUN). Expected negative reports are not qualification PASS.
- **PASS:** profile metadata/nine corruption negatives and independent source
  reviews of18 instrumentation production files and the two candidate files.
  Root reviewed integration and evidence. Source review is not timing evidence.
- **NOT RUN:** full acceptance, conditional seeds13/64, full routing corpus,
  visible player acceptance, scale integration and later milestones. The separate
  compiled normal-disabled verifier belongs to dirty scale source; native normal
  policy was checked without importing it. No visible UI change was integrated.

Known profiler limitation: raw `P07.discarded-expansions=1,217,703` doublecounts
341,862 failed-candidate expansions. The table uses unique per-placement/pass
counts instead. P05 rejected-pass and partial-net counts also overlap. Rank-zero
counter values may be omitted; canonical route metadata supplies actual ranking.
P07 pass3 terminal overhead is not a fourth search. P07 A* timing includes loop
control, not pure map time. Nested timers and browser clock quantization preclude
intrinsic-cost precision; there is no overhead correction. Profile02 was0.413 /
2.455 s slower than surrounding controls, which cannot isolate profiler overhead.

Earlier failures are retained: a native campaign exit2 from a duplicated output
marker (preceding assertions passed), first profile build exit2 because GWT2.7
lacks `System.nanoTime`, and initial relative-output host preflight rejection
before launch. Final source uses browser `performance.now` and native fallback
`currentTimeMillis`; final gates ran after repairs. Original worker handoff notes
predate these root fixes. A snapshot-cleanup review finding was repaired and then
tested with the injected failure. One validator rewrite transiently produced an
empty helper; it was restored, syntax checked and all final comparisons rerun.

The initial profile/control parity reader failed on `cold.owner.ownerIdentity`
(2014 versus27583). Source assigns `System.identityHashCode(owner)`; after each
raw report passes integer/freshness/cold-versus-warm identity and collision-negative
checks, exactly the two opaque per-run owner hashes are excluded from cross-run
parity. The original failure and normalization amendment are retained. All other
non-timing proof data remains compared; raw reports are unchanged.

## Evidence and reproducibility

`evidence.zip` preserves raw reports, host/process/cleanup receipts, frozen input
maps, initial plans, timing analysis, final readers, sanitized build/native logs,
and **diagnostic/candidate overlays only as evidence**. It contains no prior
tracking/LU prototype. `archive-manifest.json` records original and sanitized SHA256
values for every entry. Personal/task absolute paths are replaced with placeholders;
raw proof reports and overlay source bytes are unchanged. `archive-verification.json`
records hash verification and fresh strict-reader replays after extraction.

Extract to a fresh task-owned directory. Examples (bundled Python or Python3):

```text
python -B records/validate_run.py 02-two-lane-profile-seed7 --compare 01-control-seed7
python -B records/validate_run.py 05-candidate-seed7 --compare 04-control-seed7
python -B records/validate_run.py 07-control-seed7 --compare 06-candidate-seed7
python -B records/validate_run.py --scope-loss
python -B records/analyze_timings.py
```

Fresh host command was `records/measure.py LABEL ARM 7`; it invokes the unchanged
maintained `tests/browser/compiled_attribute_acceptance.py` with explicit specs.
Native/build commands and scoped reviews are captured in records and logs.
Rebuilding requires a clean export of the stated base, the chosen evidence overlay,
JDK8 and the repository's GWT dependencies; it is not required to reread the proof.

All172 pre-existing changed/untracked Q30 files and96 desktop files were rehashed
unchanged. The26 scale tracked edits and all existing untracked work remain
unstaged. Resource closeout is recorded separately in `resource-cleanup.json`.
No push, email, normal enablement, scale publication or later milestone occurred.
Next: use these measurements to investigate another causally defensible reduction;
do not extend either the support-tracking or primitive-map campaign on this evidence.
