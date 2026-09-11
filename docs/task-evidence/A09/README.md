# A09 implementation checkpoint

Status: **IMPLEMENTED - VALIDATION BLOCKED**. This is not milestone closure.

Base: `667cfd16da0272ae736cd0df6961e8cb313e0f1f` on
`codex/task43p-final-recovery`. Implementation is uncommitted and unpublished.
A10 remains unstarted.

## Implementation

Normal diagnostic admission now calls `GeneratedDiagnosticProofService`, not
`Task41DeveloperVerifier`. Six leaf contributions and the composed channel
provider own their plans, deterministic hypothesis reconstruction, replacement
recipes and bounded, immutable observation programs. Production orchestration
contains no family registry/generator dispatch. The historical verifier is a
client of the same production boundary and retains independent falsifiers.

Observation programs contain no selected fault, runtime callback or solver
handle. Replay must preserve the program, canonical hypothesis population,
recipe realization, physical layout and reachable repair semantics. Actual
player input, meters, physical replacement and customer retest produce proof.
Private proof graphs cannot share mutable owners with the player or one another.

Proof receipts are immutable, controller-local, single-attempt admission results.
They are not reusable acceptance caches. Empty/stale receipts and invalid
provider contributions fail closed. Identical observations require equivalent
reachable physical repair; different mechanisms remain distinct hypotheses.

## Player-visible resistance correction

Resistance signatures now carry explicit NUMERIC or OVER_RANGE outcomes.
20 MOhm, 40 MOhm and a genuine open circuit all produce the same player-visible
OL observation. Non-equivalent repairs can no longer be justified by their hidden
numeric differences. Invalid/unavailable readings fail closed. The 10 MOhm
boundary, in-range tolerances and player meter behavior are unchanged.
The original counterexample was reproduced before the fix; 17 added native
assertions cover OL equivalence, numeric boundaries, invalid results and repairs.

## Fresh corrected-candidate evidence

- `acceptance.json`: exact gate status and explicit open gates.
- `diagnostic-report.json`: actual compiled A09 positive report, including serial
  timings, all retained leaf hypotheses, both composed driver paths, and eleven
  executed rejection cases and five real OL observations. This is not synthetic report-reader input.
- `candidate-provenance.json`: complete Java source hashes, relevant reader hashes
  and all five compiled permutation hashes for the tested uncommitted candidate.
- Native gate: 18 suites; A09 302 assertions and 40 replay cases; independent A09
  contracts 143 assertions; existing report contracts 466 assertions.
- `a08-report.json` and `task49-report.json`: fresh affected regression reports,
  independently accepted by the maintained strict readers.
- `forced-negative.json`: exact A09 forced failure, no false positive report.
- JDK 8u502/GWT: five production permutations compiled successfully.
- A09 report independently re-read successfully, 144 contract assertions.

## Resumption verification

All 610 source, five reader/test and five compiled-permutation fingerprints match
the corrected candidate. The final-code GWT/browser evidence above is retained,
not a fresh execution on resumption. Fresh 18-suite native validation and all three
actual report readers passed again, as did the final whitespace check. No production
source changes were made during this verification-only continuation.

## Open gates and limits

The most recent normal Luna and approved Reserve review attempts failed at usage
limits before reviewing the correction. On 2026-09-11 the owner explicitly authorized
publication before independent review and will review the pushed candidate afterward.
No independent review PASS is claimed.
The native player gate now passes on the normal LED seed-0 production route using
guarded Win32 mouse/keyboard input. The unrepaired customer retest failed, board
power was disconnected, R1 measured OL, the failed resistor was removed, the
player-visible catalog selected 330 Ohm +/-5%, the replacement measured 330 Ohm,
board power was restored, and the customer retest passed. Nineteen native input
records executed with zero page errors. See `player-workflow.json` and the inspected
`player-fault-ol.png`, `player-repaired-ohms.png`, and `player-retest-pass.png`.
The task-owned browser exited and its loopback server thread stopped.

Direct browser execution and report-reader acceptance do not certify the existing
CLI/CDP ownership/transport wrapper. Arbitrary untrusted executable plugins, general
rollback, staged jobs, cancellation and proof caching are not implemented here.

Full temporary logs, browser captures, reviewer transcript, process ownership and
resumption checkpoint remain in the external scratch directory recorded in
`acceptance.json`. Preserve the uncommitted worktree and pre-existing Python caches.
Publication is explicitly owner-authorized before review. Complete the post-push review
before treating A09 as milestone-accepted or starting A10.
