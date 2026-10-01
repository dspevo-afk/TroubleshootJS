# Q30 scale plan-4 manifest proposal

**Proposal only; not qualification evidence.** Seed selection uses no routing,
timing, solver, proof, normal-admission, or compiled-receipt results.

## Frozen input and source recipe

All 51 seeds from the normal-admission README are retained: 24 Representative
matrix rows, 24 Held-out matrix rows, and three exact signed-long replay roots.
Each matrix row keeps its original epoch-3 cohort/support/topology metadata and
adds a separate plan-version-4 expected identity. Historical epoch-3 evidence
is not relabeled or replaced. Plan-4 grammar comes from the scale-candidate
Rb30Plan.java; named-stream arithmetic mirrors the independent Task 46 Python
reference. Source hashes and every plan identity are in the JSON.

## Append rule

Scan Representative roots upward from 10000 and stop before 20000; scan
Held-out roots upward from 20000. First append the first unused root filling
each missing package count in 20..40. Then rescan each root range from its
start, skipping used roots, and append the first seed filling a missing valid
topology axis or missing present/absent state of an eligible support feature.
Topology requires all valid one/two-channel, reference, and active driver axes.
Channel-B features are checked only among two-channel plans. Fault choices are
reported but do not drive extension.

| Cohort | Rows | Appended | Package counts | Topology axes | Missing support states |
| --- | ---: | ---: | --- | ---: | --- |
| Representative | 35 | 11 | all 21 (20–40) | 18/18 | none |
| Held-out | 39 | 15 | all 21 (20–40) | 18/18 | none |

The JSON records exact per-count totals, topology axes, support present/absent
coverage, selected fault values, signed-long layout/routing seeds, all 51
preserved plan-4 identities, and each appended root with its rule/reason.
Total proposed rows: 77 (preserved 51, appended 26; Representative 11, Held-out 15).

## Limits

This oracle predicts immutable plan structure only. It does not construct a
board or establish placement, routing, solver proof, normal admission, or
timing. Differential comparison with Java plan-4 outputs and all qualification
gates remain required before adoption.
