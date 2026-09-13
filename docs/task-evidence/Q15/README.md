# Q15 — small procedural control-board qualification

Status: COMPLETE — QUALIFIED. Final implementation, electrical/physical proof,
regressions and visible player checks pass. This packet identifies the accepted
candidate; Git history and the final handoff record its publication.
Base: `99a0001982ad92d245fda1e38a9c7ea1045da3a3`, branch
`codex/task43p-final-recovery`. No later milestone is included.

RB15_CONTROL implements a sixteen-package 12 V relay control board with a 5 V
external command and 180 Ohm external load. Named streams independently select
BJT/NMOS drivers, resistive termination/RC filtering, one of three serviceable
faults, placement and routing. This qualifies that family within the declared
5–20-part envelope; it does not claim arbitrary circuits at every size in it.

| Physical owners | Electrical contribution |
| --- | --- |
| J1, J2, J4 | Board supply, command and loaded output terminals |
| F1, DREV | Real I²t fuse and series reverse-polarity diode |
| C1, RBLEED | 1 uF supply storage and 2.2 kOhm discharge path |
| RIN, C2 or RBIAS | Series command impedance and driver-edge filtering or input termination |
| RDRIVE, RPD, Q1 | Drive current/impedance, driver pull-down and BJT/NMOS switching |
| D1, K1 | Inductive flyback and five-terminal relay coil/contact switching |
| RLED, LED1 | Current-limited power indication independent of the relay state |

Every pad has an actual CircuitJS endpoint. The board is declared before
electrical allocation; current footprint, placement, routing, copper and access
owners produce and validate its physical realization. All copper is on the
bottom face of through-hole parts. The nine-package E03 authored layout is a
separate reference, never counted as procedural qualification.

The frozen cohort is development seeds `0, 1, 2, 3` and regression/holdout seeds
`17, 42, 101, -1, 9007199254740993, -9223372036854775808, 9223372036854775807`.
Failures found in this cohort drove the retained fixes; it is not an independent
random-population success-rate estimate. Each final compiled case must execute
all six admission stages and all three hypotheses, then reject an unrepaired
board and a wrong replacement and accept a legal repair. The RC support checks
cover both drivers on detached copies of the actual circuit. A 100 kOhm drive
replacement legitimately works with NMOS but fails to provide BJT base drive;
the customer check follows the solved behavior.

The RC capacitor is on the driver side of RDRIVE. An earlier input-side version
retained charge when the drive resistor was open and correctly blocked OHM.
Moving it across the existing pull-down provides real discharge without a fake
reading, energy reset or relaxed 50 mV/1 uA meter threshold. Independent support
ablations check stored energy, input loading, filtering, flyback, supply KCL,
indicator limiting, persistent fuse isolation and reverse-polarity blocking.
LOW disconnects the command source. The RC flyback check observes the real
turn-off event within five 1 ms discharge time constants, retaining the same
half-initial-coil-current threshold as the unfiltered circuit. An earlier 5 us
sample correctly failed because the filtered transistor had not turned off yet.

Physical search remains bounded to 80 placements. Q15 extends the bottom-face
search to five route alternatives per placement (at most 400): nearest/random
branches, reversed order, blocked-net feedback, and an independent final
reverse/farthest tree. All alternatives start with fresh copper containers and
use unchanged connectivity/clearance validators. Top-face searches retain their
existing candidate policy. Generation yields after each placement and retains
the existing 5-second work-unit, 90-second job and 640-unit limits. Diagnostic
hypotheses reuse frozen coordinate values in fresh sealed layout containers;
their electrical graphs, physical parts, mutations and proofs remain disjoint.

Named machine: Windows 11 Home, AMD Ryzen 7 7700 (8 cores), approximately 32 GB
RAM; Temurin JDK 8u502 and GWT 2.7.0 (five permutations). Native routing timings
and compiled admission/proof timings are separate populations. Routing totals
include rejected attempts. Verifier operation time and cleanup time are separate.

Core qualification is recorded in [compiled-control-board.json](compiled-control-board.json):
eleven complete cases, 481 assertions and 80 support assertions across all four
designs. Each case proves and physically repairs all three diagnostic hypotheses,
then exercises wrong/nominal repairs on its selected fault. The original owner is
restored in 3 ms. The [forced negative](forced-negative.json) fails before admitting
a case and restores its owner. The strict Q15 reader rejects sixteen malformed
or incomplete reports.

| Compiled operation | p50 | p95 / maximum |
| --- | ---: | ---: |
| Full admission | 36.657 s | 80.627 s |
| Routing, including rejected attempts | 10.171 s | 41.667 s |
| Hypothesis proof | 21.998 s | 38.413 s |
| Largest work unit per admission | 1.990 s | 2.797 s |

These are nearest-rank quantiles over eleven sequential first admissions after
the LED bootstrap and routing-cancellation canary; they are not cold-process
measurements or an arbitrary-seed success-rate estimate. Work uses at most
120 of 640 units. Cancellation took 0 ms at millisecond resolution after entering
its handler; that does not bound queued player input. [Performance data](performance.json).
Native construction ranges from 0.325–4.075 s, separately recorded in the
[native contract receipt](native-contracts.txt).

The final native command passes 30 Java suites, Q15's 6,713 assertions, current
independent seed/value/role oracles and report protocol. The [actual JDK8/GWT build](gwt-build.txt)
passes all five permutations (81.304 s compile, 1.412 s link). Fresh native
[Quick Play canaries](quickplay-native.txt) execute the changed eight-family
registry, deterministic construction and exact selection envelopes. The
[candidate provenance](candidate-provenance.json) binds 1,076 source/test/script
inputs and five compiled permutations; the restarted preview matched the selected
source/web identity before final browser qualification.

All required final gates passed:

- `./scripts/verify-current-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07`
- `./scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07`
- Actual production Browser Q15, forced-negative and affected regression routes.
- `python tests/contracts/q15_evidence_contract.py docs/task-evidence/Q15`
- `./scripts/verify-q15-evidence.ps1` for Q15 and the maintained A10/E03/A08/Task41/Task49 readers.
- Actual normal-player power, probe, removal/catalog repair, HIGH/LOW and retest
  input with 2–5 inspected production screenshots.

Fresh compiled regressions pass: E03 46 electrical and 113 mutation assertions
across six cases, A08 1,103 assertions with 90 compensated writes and five fresh
failure cases, Task49 397 assertions/four solved cases, Task41 twenty routes and
182 solver samples, and A10 1,273 assertions/24 attempts. The isolated A10 run
includes both RC admissions (59.191/59.333 s, largest units 4.739/4.746 s), profile
boundary cancellation, stale-dependency rejection and cleanup. Its ordinary
benchmark first/repeat p95 values are 1.833/1.811 s. The final maintained readers
and negative canaries pass in [evidence-readers.txt](evidence-readers.txt).

[Normal player checks](player-flow.md) freshly cover NMOS and BJT admission,
unrepaired failure, partial/all source isolation, genuine parallel-path OHM
readings, removal/tray/catalog replacement, HIGH/LOW measurements and passed
customer retest. A 100 kOhm alternative drive resistor passes on NMOS; an
underrated 5 V relay fails on BJT before the 12 V replacement passes. Five
inspected production screenshots show these states, bottom copper and direct
left/right probe reversal (+12 V to -12 V). Visible cancellation restores the
prior board; exact owner identity is independently checked by the compiled
seed3 canary. Debug verifier flags without the debug gate expose no reports or
answer keys. Browser warning/error logs are empty for the accepted flows.

Earlier failures are retained in the task-owned OS-temp directory
`TroubleshootJS-Q15-9de3ad5ef0374dcc80777a0afcb25547`. They include bounded routing
rejections, unavailable GWT `Collections.shuffle`, missing fuse dependency capture,
oversized temporal metadata, verifier replacement/terminal-order issues, a
work-unit timeout, shared-layout rejection, and the RC discharge failure. None is
substituted for a final PASS. Screenshot capture initially timed out even on an
empty tab; it recovered after the user reconnected/unlocked Windows.
[Retained failures](retained-failures.json) preserve the principal intermediate
reports. Two renderer crashes had no captured terminal proof. The first final A10
run also failed its RC profile-boundary assertion; an isolated ordinary RC route
subsequently admitted, and the unchanged final candidate passed the full A10
retry and strict reader after completed simulator tabs were closed.
That failed assertion does not expose a nested job outcome, so its precise cause
has not been established. Long synchronous verifier operations can time out a DOM
poll; only a subsequently captured complete report can establish a passing gate.
Two earlier visible-cancel input attempts lacked a confirmed cancellation result;
the final observed DOM-node click explicitly reported cancellation. They remain
separate from that PASS in the player receipt.

No mains, two-layer gameplay, broader package counts, new instruments, scoring or
advanced profiles are claimed. Existing A11-D1 and historical Windows wrapper
qualification limits remain. Root integrated review covers generation/resumption,
electrical/physical identities, bottom-layer routing, fresh-layout ownership,
meter cleanup, causal support, repair behavior and report/privacy boundaries;
no unresolved blocker remains. Independent agent review was NOT RUN (optional).
The [final input audit](final-input-audit.json) rechecks every consumed input and
compiled permutation, supporting reuse of the already passing final-source gates
after documentation-only edits. Cleanup is recorded separately in
[resource-cleanup.json](resource-cleanup.json). Next unstarted milestone: U04.
