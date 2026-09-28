# Finite-predicate focused comparison evidence

**Disposition: accepted intermediate solver trial only. Q30 qualification remains blocked and not accepted.** This is the predeclared eight-run comparison sequence, not the full acceptance corpus and not a normal-player acceptance result. The seed-13 finite-predicate candidate cold run took **91,583 ms**, above the 90,000 ms limit. Normal publication remains disabled.

## Scope and reviewable source

The comparison used baseline `0b29680c7daa62123732b91e3c1157e8ba0d6abd` and a two-file candidate delta:

- `SolverExecutionBoundary.finite` changed from NaN/infinity predicates to inclusive bounds comparisons against `±Double.MAX_VALUE`.
- `A07ExecutionContractVectors` added independent classification cases covering finite extrema, subnormals, signed zero, infinities, NaN, overflow, and positive/negative powers of two.

The GWT implementation of the old infinity predicate also checks NaN, adding
repeated classification work at the heavily used finite guard. Two inclusive
comparisons classify every Java double identically: finite values lie inside
the bounds, infinities lie outside, and comparisons with NaN are false.
Independent source review found no correctness blocker. Every guard call and
all solver steps, settling, hypotheses, admission and cleanup remain in place;
the old predicate remains the native/compiled test oracle. No generation or
replay code changes in this optimization.

[`finite-predicate.patch`](finite-predicate.patch) is the byte-exact patch. [`finite-predicate-semantic.patch`](finite-predicate-semantic.patch) is its LF-normalized review version. [`patch-verification.json`](patch-verification.json) records both reconstructions and source hashes. The exact patch contains line-ending churn from the candidate archive; the normalized patch isolates the semantic changes. [`source/finite-integration.json`](source/finite-integration.json) confirms only those two files were integrated and their normalized contents match the trial. The tests verify finite/nonfinite classification; they do not claim NaN payload preservation through arithmetic.

The adjacent [CDP CPU profile](../cdp-cpu-profile/README.md) ranked LU factorization as a broad hotspot and motivated looking for safe solver hot-path costs. That profile is qualitative attribution evidence; it did not measure this predicate change or establish causality.

## Predeclared sequence and measurements

[`predeclared-comparison-plan.json`](predeclared-comparison-plan.json) records the sequence `F7, B7, B13, F13, F64, B64, B7, F7`, with F as the finite-predicate candidate and B as baseline. It also records the fresh-run isolation, unchanged-input, exact-request, proof, physical-admission, cleanup, and early-stop controls. All eight planned runs completed.

| Run | Cold elapsed (ms) | Proof elapsed (ms) | Routing elapsed (ms) | Total work | Warm elapsed (ms) |
|---|---:|---:|---:|---:|---:|
| F7, first | 85,492 | 59,631 | 20,589 | 468 | 25,263 |
| B7, first | 87,166 | 61,344 | 20,587 | 468 | 25,265 |
| B13 | 94,229 | 67,699 | 20,817 | 464 | 27,466 |
| F13 | 91,583 | 64,986 | 20,972 | 464 | 26,059 |
| F64 | 80,587 | 62,046 | 13,006 | 446 | 18,064 |
| B64 | 83,043 | 64,700 | 12,622 | 446 | 18,239 |
| B7, repeat | 86,847 | 61,416 | 20,165 | 468 | 25,186 |
| F7, repeat | 85,514 | 59,990 | 20,358 | 468 | 25,185 |

Proof work was 390 units and diagnostic sample count was 185 in every run. Every warm report had a warm-reuse receipt and one proof-cache hit. The elapsed fields are independently reported; do not sum them. Full per-run reports, strict-wrapper records, input manifests, runner receipts, state files, and cleanup evidence are under [`runs/`](runs/) and [`comparison/`](comparison/).

All four paired proof-parity records passed exact-request and full owner/physical/proof/work/cache parity checks (timing excluded), with 37 negative corruption canaries per pair. Baseline-minus-candidate cold savings were **1,674, 2,646, 2,456, and 1,333 ms**; the median was **2,065 ms**. Proof savings were **1,713, 2,713, 2,654, and 1,426 ms**. The B7 repeat range was 319 ms and the F7 range was 22 ms. These results support the limited intermediate trial decision; they do not override the seed-13 deadline failure or establish a complete-corpus margin.

[`finalization-marker.json`](finalization-marker.json) records `COMPLETE` and `ACCEPTED_INTERMEDIATE`, while explicitly keeping Q30 `BLOCKED / NOT ACCEPTED` and normal publication false. [`collection-manifest.json`](collection-manifest.json) indexes artifact source/stored hashes and any path sanitization or compression.

## Validation and boundary

Before timing, the candidate passed the focused native four-suite gate (46.940 s), the maintained five-permutation GWT build (80.942 s), and the compiled A07 check with its strict reader and three negative canaries. The final integrated source also passed the maintained five-permutation GWT build (81.555 s); that build contains the separate, still-unaccepted plan-4/power-registration source and is retained as a distinct gate, not as evidence that Q30 normal acceptance passed.

The final-source canary bundle includes a passing A07 report and the expected normal-disabled rejection. The disabled-catalog reader and application report agree on `BLOCKED:normal-catalog-disabled`; generation, snapshot capture, and live-owner mutation were all false. This confirms rejection before mutation only. It is not a normal Q30 run. The normal catalog was not enabled, and the full normal acceptance corpus was not run.

Across the eight timing runs, strict-reader outputs passed, application reports matched wrapper receipts, before/after source and web input hashes remained stable, and owned cleanup completed. All raw application proof reports and strict-wrapper reports are preserved byte-for-byte inside deterministic gzip; wrapper receipts and path-bearing inventories are sanitized where needed.

## Re-collecting the bundle

The standard-library-only [`collector`](collect_finite_predicate_evidence.py) requires the finalization marker and verifies candidate/integrated source hashes, paired parity evidence, readers, input stability, and cleanup before writing the manifest. From this folder, supply the local scratch, candidate, and repository paths without adding them to the evidence:

```text
python -B collect_finite_predicate_evidence.py \
  --scratch-root <q30-scratch> \
  --candidate-root <q30-scratch>/finite-predicate-candidate \
  --repository-root <repository> \
  --a07-reader <q30-scratch>/finite-predicate-a07-reader.txt \
  --a07-negative-count 3
```

The bundle preserves failures if present; it does not run builds, timing trials, or acceptance gates.
