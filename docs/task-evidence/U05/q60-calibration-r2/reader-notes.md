# U05 Q60 calibration feature reader

This reader independently checks the frozen U05 calibration captures and recomputes
the bounded adaptive measurement reduction from exported values. It reports
feature evidence only. It does not assign EASY/MEDIUM/HARD, alter a profile rule,
choose or replace seeds, run a native verifier, or claim a global optimum over
all tools and actions.

The inputs are pinned by baseline-inputs.json, the amended scope.md, and the
final cohort-plan.json. The plan fixes development seeds -1017, -1067, -1022,
then regression seeds 9007199254741579, -1004, -1028, with the existing
90,000 ms / 640 work / 5,000 ms limits. Captures must use the final build and
64-hex source binding placed in the plan before the cohort is captured. Each capture
is a tsj-u05-calibration-capture/2 envelope containing the canonical signed-long
seed, build, source binding, and qualification report. The reader pins the
source-exported family RB56_CONTROL, plan version 1, source epoch
tsj-generation-dependencies-v18, placement 15, and physical admission
RB56_BOARD_NORMAL@1. It requires all success and cleanup flags and validates
the actual successful receipt against the unchanged time, work, unit, and
six-stage limits. Failure-only job timing fields are checked when present.

Run after root has frozen the final plan and captured the complete requested
partition:

    python docs/task-evidence/U05/q60-calibration-r2/read_features.py --plan docs/task-evidence/U05/q60-calibration-r2/cohort-plan.json --partition development --reports capture-development-1.json capture-development-2.json capture-development-3.json --output development-assessment.json

Use --partition regression and the three preassigned regression captures only
after a proposed rule has been frozen from development. The reader requires all
three seeds in the exact planned order; it has no partial-cohort mode. --self-test
runs bounded synthetic positive and negative checks for minimax reduction,
tolerance overlap, equivalent repair classes, over-range privacy, source/build/
epoch binding, cleanup, caps, and strict observation shape.

The observation parser accepts five ordered hypotheses and the actual 12- or
13-measurement program inventory. Numeric readings must carry the exported
max(0.01, abs(value)*0.02) tolerance. Only DRIVER_A_RESISTANCE may be
OVER_RANGE; that form has exactly its identity and outcome and cannot carry a
hidden number. Resistance values must be nonnegative and within the 10 MΩ meter
range. Opaque repair-class ordinals must follow first-seen order, and identical
complete observations cannot claim different classes.

For each measurement, the reader treats two numeric outcomes as observationally
equal when their difference is no greater than the larger exported tolerance;
two over-range outcomes are equal. It independently computes the minimum
worst-case adaptive readings needed to reduce the observation population to one
repair class and the best single-reading worst-case remaining class count. It
also reports the Ω-only worst-case remaining classes and numeric/over-range
counts. The algorithm is bounded to five hypotheses and the exported
measurement program. The latter is not a minimum over all possible probe pairs.

The result explicitly records profileAssignment: NONE and
globalToolsetOptimumClaimed: false. It does not infer repair success, the owner's
played strategy, a population holdout, or a subjective difficulty label. Any
profile rule and regression interpretation remain root-owned and must preserve
all failed cases and cleanup evidence.

Root's source audit distinguishes the automatic proof from the optional retained
view. Automatic qualification must report ordinaryCustomerRetestPassed:false:
the D01 proof completed each repair, while no ordinary player view was completed.
A retained view additionally requires its actual ordinary customer retest PASS.
The first retained development attempt was ended unrepaired and remains FAIL;
it cannot be accepted by this reader. Root added positive/negative fixtures for
this distinction before the automatic cohort was accepted. Successful reports
do not require the failure-only job-cost fields.

No captures have been read by the leaf, and it did not execute the self-test.
Root owns syntax/runtime canaries, actual native captures, final
build/source binding, cohort assessment, and acceptance.
