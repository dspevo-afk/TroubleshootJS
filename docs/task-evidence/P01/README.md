# P01 physical poses: independently reviewed and accepted

Accepted source candidate on `codex/task43p-final-recovery`, above
`6b7e66a1edbfb8ef3d6e95967e7f7edbbc9506cf`. P02 and A10 remain unstarted.
[Acceptance](acceptance.json) and [candidate hashes](candidate-provenance.json)
record the accepted candidate. The owner authorized publication after the fresh
[independent review](independent-review.json). Publication results belong to the
containing commit and subsequent handoff, not the original pre-publication receipts.

## Implemented contract

Absolute physical coordinates are signed integer logical board units, inclusive
-100,000,000 through +100,000,000. They are not screen pixels or manufactured
millimetres. Checked displacements/local extents may span both signs; absolute
positions and rectangle extrema remain bounded. Workbench/tray display geometry
is not redefined as a physical coordinate, and existing package sizes do not scale
with the window. A later board-sizing or viewport task remains separate.

Bottom mounting reflects package-local X before the declared clockwise cardinal
rotation and board translation. The inverse reverses those operations. This
changes handedness, not terminal names. Escape vectors, every lead/probe surface,
pads, bodies and courtyards use the same pose. Viewing the underside separately
reflects board-space X about the outline; it never changes the electrical mapping.

Arrays, package catalogs and envelopes are copied. Bounds reject at construction
and both view directions. Full-footprint movement across opposite coordinate signs
works. Compaction stages all translated objects and its final outline before
publication; invalid results do not partially mutate the layout. Trace length
accumulation cannot silently wrap. Current A02 bend fixtures use the new physical
boundary, with full integer extremes retained as explicit negative cases.

## Execution evidence

| Gate | Result |
|---|---|
| Real production Java / maintained native suites | 19 suites PASS; [markers](native-suite-markers.txt) |
| P01 independent matrix/literal oracle | 8,357 assertions, 43 declared poses PASS on JVM and GWT |
| Original independent review probe, unchanged | [Before](review-probe-red.log): 8 failing observations; [after](review-probe-green.log): 14 checks PASS |
| Maintained Python numerical/seed/diagnostic oracles | [Direct execution](independent-oracles.json) PASS against fresh Java receipts |
| Report protocol | Actual PowerShell script PASS, 466 assertions |
| Production JDK 8u502 / GWT OBF | Actual repository build PASS, all five permutations and link |
| Compiled P01 render/hit/endpoint checks | [PASS](compiled-p01.json): 16 SMD poses, 316 runtime assertions, live graph unchanged |
| Forced rejection | [PASS](compiled-p01-forced-negative.json): exact failure marker, no success report |
| Normal-player debug isolation | [PASS](compiled-p01-no-debug.json): P01 URL flags do nothing without explicit debug |
| Affected A08 / Task49 | Actual compiled [A08](a08-compiled.json) and [Task49](task49-compiled.json) PASS; [maintained strict readers](actual-report-readers.log) PASS |
| Independent integrated-candidate review | PASS: fresh chat review accepted by the owner; [review and follow-ups](independent-review.json) |

The existing review probe was not rewritten to fit the repair. The new broader
matrix/literal oracle is independent of production transform helpers, but was
authored by the implementing root. Its independence as an oracle is not an
independent-person/model code-review claim.

The maintained native wrapper exited 2 at the Windows Python launch boundary:
Store alias timeout, direct Store executable access denied, then a full-run venv
launch timeout despite a passing small bounded-process preflight. The 19 Java
suites pass. All three actual Python oracles were then executed directly against
the fresh emitted Java data and passed; the report-protocol script also passed.
This separately executed evidence does not certify the failing wrapper itself.

The first browser harness incorrectly expected an uncaught error for the forced
negative. The application correctly catches it. The retained fresh negative checks
the exact controlled failure and absence of a success report. Do not relabel the
initial harness exit as success. Ordinary compiled routes have zero page errors.

## Scope and remaining acceptance

The 0805/SOT-23 fixtures are developer-only surface-pad geometry canaries. Runtime
checks exercise actual provider rendering, hit resolution, marker geometry and
exact live CircuitJS endpoints without inserting test wires. They do not implement
SMD gameplay, layer access, plated barrels, a bottom-view UI or a two-layer router.
P02 owns layer/conductor/access policy. No electrical solver or player repair
behavior was replaced.

An additional native-input player smoke was attempted. Exact window ownership was
established, but the foreground guard withheld input; zero native events occurred.
The inspected initial screenshot is not diagnosis/repair evidence. No new player
flow or side/rotation UI is introduced by P01. Actual compiled physical/probe checks
above remain separate from this unexecuted additional smoke and CLI certification.

The fresh independent review found no blocking P01 defect and additionally passed
2,288 reviewer-authored boundary/atomicity/copy assertions. All maintained native
suites, numerical oracles, production build and compiled routes were freshly rerun
by that reviewer. A closeout hash audit matched all 36 reviewed candidate files and
634 source/test/runner inputs before documentation-only reconciliation. The owner
accepted that review and authorized P01 publication, followed by P02 implementation.
Earlier quota-limited review attempts remain historical failures, not PASS.

The stale A10 prerequisite prose is corrected. The pre-existing shared-route-score
integer overflow remains a nonblocking follow-up; this is separate from P01's
checked trace-length/bounds operations. Wrapper and optional native-input limits
above are unchanged. P02 and A10 are not certified by this acceptance.

Task-owned browsers and preview listener were closed. Native/build/report/review
jobs exited; external diagnostic scratch is retained. The pre-existing Python
cache remains untouched and excluded. Exact external log locations and resource
identities belong to the private continuation checkpoint, not this repository.
