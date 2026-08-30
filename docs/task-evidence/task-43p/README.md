# Task 43P Evidence

This directory is the durable evidence namespace for the developer-only
Task 43P cross-boundary reconciliation route. It is not visible-player
`@Browser` evidence and it does not authorize Owner Review or Task 44.

## Route contract

- Positive corpus: `scripts/verify-browser.ps1 -Task43P`, covering LED, diode,
  RC, NPN, NMOS, and parallel families for the selected seeds.
- Forced negative: `scripts/verify-browser.ps1 -Task43PForcedNegative`.
  A reached forced-negative route must return application/canary exit `1`
  only after the anchored Java diagnostic is observed. Browser, CDP, timeout,
  ownership, cleanup, and unproven results are exit `2`.
- Each reached positive route publishes `data-tsj-task43p-evidence`; the
  wrapper captures the parsed payload plus baseline/current SHA and a direct
  `src`/`scripts` worktree digest in the run-owned JSON namespace.
- `Task43PPhysicalTruthDeveloperVerifier` uses an independently authored
  terminal/package manifest and in-memory negative fixtures. It does not
  mutate production source, renderer geometry, board metadata, or CircuitJS.

## Current candidate status

Candidate baseline/current HEAD: `3bfaab093f85247fc20aec068824c83dc3d214c8`.
The candidate is uncommitted. The positive/forced-negative Browser routes did
not reach the application on the current host: browser startup and cleanup
ownership proof stopped at WMI `Access denied`. Consequently no runtime JSON
is claimed here, and A-I/triad results remain `UNPROVEN` or explicitly
partial in the candidate's structured payload design. The Foreman must repeat
the routes with visible built-in `@Browser` validation and a host that can
prove exact browser/process ownership.

The static route/validation record is
[`candidate-manifest.json`](candidate-manifest.json); its runtime fields are
explicitly `UNPROVEN` where the page was not reached.

## Required interpretation

Absence of a JSON run artifact is an infrastructure limitation, not a pass.
The Java/GWT compile proves only that the developer-only route is buildable;
it does not prove the missing runtime A-I lanes, same-owner transaction
semantics, request/board/session epochs, or visible-player behavior.
