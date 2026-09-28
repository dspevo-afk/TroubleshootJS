# Q30 LU zero-solve trial

**Decision: REJECTED after the predeclared first-pair early stop.** The trial did not produce useful savings: the optimized candidate's single cold seed-7 run took 94,810 ms versus 89,794 ms for the control (+5,016 ms); diagnostic proof time was 66,837 ms versus 62,441 ms (+4,396 ms). The optimization is not integrated into production.

This is one matched pair, so it does **not** establish a population-wide performance regression. The declared plan allowed stopping when the first pair showed no useful initial improvement; the remaining six planned runs are recorded as NOT RUN. The two completed runs passed strict report and negative-canary validation, with exact request and owner/physical/proof/work/cache parity except timing, and both cleanups passed.

The work uncovered two distinct issues before the timing decision. The first native attempt failed because the pivoted test matrix did not retain exact-zero coefficients; the assertion was kept and the fixture was replaced with a pivoted 2x2 block plus an independent diagonal block, with the expected pivot checked using the unchanged independent factorization oracle. Independent review then found a real arithmetic-semantic regression: skipping `0 * Infinity` changed the historical NaN propagation for an inverse with a finite subnormal final pivot. The final scratch candidate keeps the optimization only for finite referenced RHS values and executes the original multiply/subtract whenever the referenced RHS is nonfinite. Independent solve-oracle fixtures cover the subnormal-pivot inverse column, finite-input overflow, and a nonfinite RHS, including explicit NaN and positive-infinity classification checks.

## Gates

- Initial focused native gate: **FAIL**, exit 2, 50.9247395 s. A07 stopped at the false-zero fixture assertion; three remaining suites were NOT RUN.
- Corrected-fixture native gate: **PASS**, exit 0, 47.7924081 s. This preceded the inverse-propagation review finding.
- Corrected-fixture GWT build: **PASS**, five permutations, 88.778205 s. This preceded the inverse-propagation review finding.
- Repaired-source native gate: **PASS**, exit 0, 48.5587746 s: A07 271,837 assertions; temporal work 83; generation measurement budget 25; normal execution policy 325.
- Repaired-source JDK 8/GWT build: **PASS**, five permutations, 85.5935064 s.
- Repaired-source compiled A07: **PASS**, 1.283089 s; strict report reader and three negative corruption canaries passed.
- Timing pair validation: **PASS**, two strict readers and 37 negative canaries; exact request and complete owner/physical/proof/work/cache parity excluding timing. Cold work was 468 units, including 390 hypothesis units, in both arms. Candidate/control cleanup passed in 1.023774 s / 0.8219468 s.

## Evidence map

- `finalization.json` is the rejected first-pair decision and enumerates all six NOT RUN runs.
- `plans/` holds the original and repaired pre-comparison declarations. The plan was updated before the repaired-source timing run to retain nonfinite multiplication.
- `failures/` preserves the initial false-zero fixture failure and corrected-fixture gate receipts.
- `inverse/` preserves the independent inverse-propagation finding, pre-repair candidate snapshot/patch, repaired source patch, source audit, and source-revision receipt.
- `gates/` contains repaired-source native, GWT and compiled-A07 receipts.
- `runs/Z7/` and `runs/F7/` contain the two sanitized raw reports, strict wrappers, input manifests, provenance and cleanup records. `runs/A07/` contains the compiled-A07 report and its run provenance/input audit/cleanup evidence.
- `artifact-manifest.json` records source hashes, sanitized-content hashes and compressed-artifact hashes. Absolute local paths were replaced before storage; browser profiles and caches were excluded.

## Numeric contract

The accepted equivalence contract is numeric-value equality for finite results, including signed-zero equivalence; no bitwise signed-zero identity is claimed. Nonfinite propagation is part of the failure semantics: NaN and signed infinity classifications must match the historical independent `originalSolve` oracle. The final scratch candidate was correctness-checked before timing; the timing rejection means it was not promoted.