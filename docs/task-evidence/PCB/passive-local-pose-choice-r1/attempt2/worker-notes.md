# Passive local pose choice worker handoff

Status: source and contract edits are complete in the isolated COPY. This note records the implementation shape and intended contract coverage; it does not claim a runtime pass.

## Scope delivered

The owned source changes add passive pose policy 4 and direct-native RB56 placement interpretation 19, mapped to `RB56_BOARD_NORMAL@5`. The new branch is eligible only on the existing candidate-1 repeated-control medium path for non-anchor items with one of the four canonical passive package identities. It retains TOP0 source geometry for demand sizing and the existing maximum-pose demand guard.

The policy-4 medium placement branch pairs TOP0 and TOP90 at each existing ring or advisory raw origin. It clamps each pose independently, reserves one evaluation before each fit attempt, uses the normal fit/access checks and the existing medium score contributions, and replaces a winner only on strict score improvement. The existing hashed fallback origin sequence is sourced from the TOP0 bounds and evaluates both poses before stopping at the first fallback origin with a winner. When another pose cannot be reserved under the existing 60,000-evaluation limit, the branch rejects with `MEDIUM_PLACEMENT_BUDGET`. The legacy medium loop remains after the one policy-4 dispatch; policies 0–3 keep their existing visit mappings. `GenerationRequest` rejects private interpretations 17, 18, and 19; no replay epoch or default interpretation was changed.

An optional package-private observer inside the new policy branch records raw origin, actual clamped origin, rotation, fit outcome, and legal-trial score. It defaults to null, and the chooser does not consume a return value or override from it. The actual-loop contract restores it to null in `finally` blocks.

## Contract coverage added

`PcbLocalPoseChoiceContractTest` invokes the actual private `PcbPlacementPlanner.mediumPlace` method reflectively. It contains five cases: a TOP90-only fit at a real source-envelope advisory vacancy; a literal hashed fallback escape where TOP90 is expected to fit while TOP0 cannot; a whole-content-score source-pose win with finite legal observations for both poses and a declared same-origin score comparison; an equal-score tie expected to retain TOP0 at literal first ring point `(930,890)`; and a budget boundary starting at 59,999 that expects one TOP0 observation followed by rejection before TOP90 fit or score.

The policy contract now recognizes policy 4 while retaining unsupported-version checks, policy 0–3 behavior assertions, and unknown policy 9 coverage. The admission contract adds version 19, policy-to-admission identity, source-size and two-layer mapping, replay-request rejection, canonical admission fields, and TOP90 admission only for canonical packages. Existing policy-3 `@4`, 40..60 / 2..7 bounds, and unknown placement checks remain.

The fallback fixture asserts its peer envelopes are inside the outline and pairwise non-overlapping. Its independent expected first legal pair is raw `(60,5010)` after 252 ring-pose evaluations, for 260 total pose evaluations. The advisory fixture checks the actual vacancy target at raw `(240,345)` and expects both clamped origins to be `(240,350)`, with TOP0 failing and TOP90 fitting there.

## Changed files

- `src/com/lushprojects/circuitjs1/client/PcbPassivePosePolicy.java`
- `src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java`
- `src/com/lushprojects/circuitjs1/client/Rb56Placement.java`
- `src/com/lushprojects/circuitjs1/client/MediumBoardNormalAdmission.java`
- `src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java`
- `src/com/lushprojects/circuitjs1/client/GenerationRequest.java`
- `tests/contracts/PcbPassivePosePolicyContractTest.java`
- `tests/contracts/PcbPassivePoseAdmissionContractTest.java`
- `tests/contracts/PcbLocalPoseChoiceContractTest.java`
- `docs/task-evidence/PCB/passive-local-pose-choice-r1/worker-notes.md`

## Validation and limits

This leaf performed source inspection only. Java contract tests, native execution, production builds, browser checks, and generation qualification were **NOT RUN** here. The fixture coordinates and expected selected outcomes are assertions in the new test, not observed runtime results. Root integration and the required gates remain necessary before any PASS can be recorded.
