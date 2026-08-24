# Task 43 Recovery History

This file preserves the detailed Task 43 recovery record moved out of the
active roadmap during the post-Task-43 redesign. It is historical evidence,
not a current implementation status. The final accepted implementation
baseline is `8245c79990647f6c40f53bc1dd9330ec2ccd22b4`.

## Final disposition

Task 43 — Physical Package and Interaction Envelope Contract — is complete.
The final integrated 43R-8 acceptance passed the full browser/regression
orchestration, the Task 41 and diode-short regressions, the strict forced-
negative application exit-1 contract, missing-browser infrastructure exit-2,
and natural-success exit-0 process canaries. The final post-matrix Luna MAX
review and Sol ULTRA inspection returned `PASS`.

The accepted implementation branch was
`codex/task43-recovery-integration`; the final SHA is published at
`origin/codex/task43-recovery-integration`. The historical handoff report at
the final acceptance recorded that no push was authorized while the report was
being written; later repository inspection and live `ls-remote` verification
confirmed the SHA on the remote.

## Recovery chronology

The recovery was deliberately split into bounded slices. Each slice preserved
the CircuitJS topology, stable logical identities, and provider ownership
unless the record below says otherwise.

- `faa3b5f` — froze the package geometry contract and its semantic envelope
  separation.
- `3319c9c` — closed the initial geometry/lifecycle contract.
- `8e39eff` — corrected the recovery handoff and separated accepted work from
  failed route experiments.
- `6368fea` — closed board geometry consumers, compaction, containment, and
  physical connectivity validation.
- `29724c4` — completed installed rendering, selection, hit testing, and
  board-pad/component-side probing from package-backed geometry.
- `006fa62` — unified loose-part geometry and interaction ownership.
- `9ac7473` — corrected loose-probe pagination/lifecycle invalidation.
- `1ce5682` — corrected the production package-escape geometry and established
  geometry-contract version 3 before the fixed-layout work.
- `a006a1d` — reconstructed the RC fixed-layout route from package pad
  centers/escapes while preserving the live electrical topology.
- `e7a93f4` — reconstructed the NPN fixed-layout route as a coordinated
  all-net route set; its finite verifier covered 27 resistor tuples across
  four compaction-origin classes, 108 cases total.
- `c306556` — reconstructed the NMOS fixed-layout route; its finite verifier
  covered nine resistor tuples across four compaction-origin classes, 36 cases
  total.
- `14afe84` — restored the RC fixed-layout acceptance proof after the route
  reconstruction history was reconciled.
- `d8356f9` — accepted the detached installed-lead renderer/probe closure after
  the runtime evidence and fresh independent review passed.
- `b4d67c4` — reconciled the 43R-4D acceptance state and its historical
  remediation evidence.
- `d0a1ca4` — recorded the first failed 43R-8A recovery attempt. It was not
  treated as acceptance evidence.
- `c952d8e` — completed and accepted 43R-8A, including reversed LED/diode
  replacement measurement, installed component-side probing, visible
  normal-player repair/retest flows, and independent review.
- `23330da` — corrected the 43R-3 endpoint-wrapper comparison at the physical
  binding boundary while retaining strict target, lifecycle, replacement, and
  stale-target checks.
- `5f8912d` — completed 43R-8B acceptance infrastructure: stale NPN layout
  deferrals became hard failures, and the developer-only forced-negative route
  proved application failure exit-1 versus infrastructure exit-2.
- `d7c67d9` — added the integrated Task 43 regression orchestration.
- `00004e9`, `5183616`, and `46b7ce8` — repaired bounded verifier lifecycle,
  deadline, and physical-slot-refresh issues found while integrating the
  acceptance matrix.
- `8245c79` — completed final integrated 43R-8 acceptance. The only final
  production correction remained the narrow Task 41 developer-only
  `includeDeveloperShort` propagation preserved in its earlier accepted
  checkpoint; the final integration also corrected child-wrapper status
  capture so natural fall-through maps to exit 0, explicit failures preserve
  exit 1/2, and unset failure status maps to infrastructure exit 2.

## Important intermediate failures and corrections

The recovery history intentionally retains failures rather than rewriting them
as successes:

- The original fixed NPN and NMOS authored routes contained deterministic
  crossings. Independent reachability showed the placements were feasible but
  the local route experiments were not; the routes were reconstructed as
  whole-board sets.
- The first 43R-4D runtime attempt was blocked by WMI/Edge process access and a
  blank in-app preview. Later clean compiled-preview Browser runs passed the
  required Task 43, layout, RC, stored-energy, and visible lifecycle routes,
  followed by fresh independent acceptance.
- 43R-8B was accepted only as acceptance-infrastructure closure. Its report
  explicitly kept final integrated 43R-8 unaccepted until the later matrix.
- The 43R-8B candidate was preserved in stash entry
  `stash@{0}` before the 43R-3 corrective return. The three production verifier
  files in that stash match the accepted final HEAD; its older verifier script
  is superseded by the later integration commits. The stash remains preserved
  as historical evidence and is not an unreviewed implementation baseline.

## Historical audit context

The following reports were authored against older repository states and remain
useful falsification hypotheses only:

- `STATE_LIFECYCLE_INTEGRITY_AUDIT.md` audited baseline `2ccc3b6` and found
  conditional risks around pending settlement versus `READY`, incomplete
  same-owner snapshot/restore, exception-sensitive active-measurement cleanup,
  missing request/board epochs, and causal separation of original faults from
  secondary damage. It recommended a bounded lifecycle/composition gate rather
  than claiming every risk was already open on the final SHA.
- `VERIFICATION_INTEGRITY_FALSE_PASS_AUDIT.md` audited the same older baseline
  and demonstrated controlled false-pass hypotheses: renderer-only pad
  displacement, disabling a required public action while direct controller
  dispatch remained possible, and omitted snapshot state. It also recorded
  positive local canaries that caught temporary graph leakage and a missing
  replacement binding. The final roadmap therefore requires independent
  final-SHA reconciliation before runtime composition.
- `recovery-assessment.md` recorded a read-only `PARTIAL_SALVAGE` disposition
  for the dirty pre-recovery Task 43 candidate. It identified the package
  geometry core as salvageable but required reconstruction of acceptance-
  critical consumers, lead/probe semantics, fixed layouts, and verifiers. It
  did not authorize implementation.

These reports do not establish that every finding remains open, and Task 43
does not establish that every finding is closed. Task 43P is the future
reproduce/disprove boundary.

## Preserved evidence locations

- Git commits listed above and their complete diffs.
- `docs/task-evidence/task-43/` screenshots and route evidence.
- The historical Task 43 sections in prior versions of
  `docs/ROADMAP.md` and `docs/CODEX_TASK_REPORT.md`.
- The dedicated lifecycle, verification-integrity, and recovery-assessment
  branches/worktrees.

No current roadmap milestone is authorized by this history. The next gate is
Post-Task-43 Gate A.
