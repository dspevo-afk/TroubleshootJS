# A05 functional-role variants and repeated channels

Status: COMPLETE - QUALIFIED. Actual final commit, remote verification and email outcomes are recorded in the post-publication handoff.
Base: `bcf2bf8b8a3a995eaba76daefaf759e06918f4da` on
`codex/task43p-final-recovery`. The containing commit identifies the complete
implementation and this packet. A06 and later work remain unstarted.

## Delivered current envelope

The normal controlled-indicator intent now resolves two independently commanded
5 V channels, selecting real NMOS or NPN low-side implementations per stable
instance, plus a functional supply-present resistor/LED contribution. The
inventory has 15 packages, 32 physical pads, three external source/control
inputs and four serviceable OPEN-resistor fault owners. This is bounded A05
content, not Q15 relay-content or large-board qualification.

Provider-local declarations own electrical elements, models, packages,
terminals, backings and observations. Device-owned joins connect repeated
instances through explicit buses. Generic construction, materialization and
reconstruction consume those declarations; distinct private solver reservations
prevent identical local coordinates from joining different instances. The
current generator is `bounded-assembler@5`; electrical and switched-low-side
declarations are version 2. Prior generator interpretations are retired, not
kept behind adapters. Existing useful leaf content remains independent.

The current physical layout uses two copies of a bounded package-backed pattern,
not an arbitrary-size router. Model allocation knows actual CircuitJS primitive
classes, while the controlled intent owns its channel/control/support semantics.
Ordinary future variants must not add type-specific branches to generic layers.

## Frozen expectations and corpus

Supply envelope: 4.75-5.25 V with ideal bounded 5 V command sources. Source
capacity is an admission declaration, not a dynamic current limiter. ON load
current is 5-16 mA; OFF leakage is at most 1 microamp. NMOS G/D/S map to actual
CircuitJS posts 0/2/1; NPN B/C/E map to 0/1/2. The NPN uses beta 100, a real
base resistor and pulldown, VBE 0.5-0.95 V and VCE at most 0.8 V when ON.
Support uses a 1 kohm resistor and LED, powered current 2-4 mA and unpowered
current at most 1 microamp. It remains independent of both channel commands.

| Seed | A / B implementation | A / B load ohms | Ordinary fault owner |
| --- | --- | --- | --- |
| -1 | NPN / NPN | 330 / 270 | A load RLOAD |
| 0 | NPN / NMOS | 330 / 330 | A load RLOAD |
| 1 | NMOS / NMOS | 330 / 330 | B driver RG |
| 2 | NMOS / NMOS | 330 / 330 | A load RLOAD |
| signed-long MIN | NMOS / NPN | 270 / 330 | B driver RB |
| signed-long MAX | NMOS / NMOS | 330 / 330 | B driver RG |

Full solver/physical/repair cases use -1, 0, 1 and signed-long MIN. Role parity
adds 2 and MAX; value parity also covers 3 and both signs of 9007199254740993.
All four admitted fault targets in every solved composition are checked: 16
owner cases, each with wrong, correct and electrically valid alternative
repairs and peer-owner preservation. Alternative load values are 270/330 ohms;
NMOS gate 1/1.5 kohm and NPN base 2.7/3.3 kohm. These expectations were fixed
before accepting solved results. Broken support must fail healthy validation
and actual customer retest; restored support must pass both.

## Final qualification

Final source-tree digest (the maintained execution-provenance algorithm over `src/`):
`48541648277a27ee8974f9d9093a2c1281b99b8be85d7ea97a6060f84d698f2d`.
Exact script/web/execution identities are in [the dependency audit](dependency-audit.json).

| Gate | Result |
| --- | --- |
| Maintained native runner | PASS, exit 0: 15 Java suites; independent 22 seed, 9 value and 6 role vectors; 195 report assertions. A04 native construction 592 and physical/provenance 5,270 assertions. Owned scratch cleanup PASS. |
| Actual JDK8/GWT production build | PASS, exit 0: JDK 8u502/GWT 2.7.0, five OBF permutations; compile 58.307 s, link 1.306 s. Final Java source, not a prior candidate. |
| Six current Gate A05 routes | PASS: A03 identity, Task46 seed, current Task49 composition, A04 construction, expected A04 forced failure and debug-off. A forced failure is an expected negative, not a successful application result. |
| Current JVM/GWT parity | Exact: shared identity vectors, three manifests, signed seeds, six role vectors, nine value cases and their binary64 numbers. Current candidate comparison only, no historical goldens. |
| Compiled construction/lifecycle | PASS: 1,098 context and 650 runtime assertions, six current construction cases, prior owner restored and candidate cleanup. Shared-package data controls do not claim arbitrary multi-unit runtime support. |
| Compiled electrical/repair | PASS: four solved compositions, all 16 admitted fault-owner cases; wrong/correct/alternative repair, actual retest, independent controls, support failure/restoration, physical correspondence and failure isolation. |
| Ordinary player | PASS: normal visible launch, NMOS/NMOS and NPN/NPN diagnosis/repair, wrong repair rejected, valid alternatives accepted, independent controls, support, power, active-meter gating/exit and board succession. |
| Independent source review | Initial Reserve review found a verifier blocker; corrective reviews resolved it. Fresh DeepSeek V4 Flash MAX source and evidence reviews PASS with bounded follow-ups. Documentation delta reconciliation is recorded separately. |

[Native/build receipt](native-build.json), [compiled summary](compiled-summary.json),
[player results](player-results.json) and [independent review](independent-review.md)
record scope and limits. Native Task35 uses the existing fail-closed JVM stub;
it does not substitute for the actual production GWT compile.

## Verification corrections, not suppressed failures

The inherited last compiled failure used a broken-support negative that removed
a declared element. It now models a finite high resistance while keeping exact
graph membership, verifies the actual failed function/retest, and restores the
real model. Mutation verification now settles after clearing faults before
checking dependent instrument operations and uses the private proof installer
while mutating a candidate, rather than completing and retiring it too early.

Compiled A04 negatives now use exact mutable component IDs, choose a foreign
pad with genuinely different electrical backing, identify the actually selected
channel's fault switch, settle power changes before dependent checks, and prove
that an aborted receipt rejects rather than reading a revoked registry as though
it were live. These fixtures retain actual production ownership checks.

The scratch evidence collector was also corrected to preserve a one-item parsed
JSON array and decode integer-versus-floating numeric tokens by token kind.
This retained strict shape/status/case/seed checks and exact binary64 comparison;
no tolerance, historical report substitution or false-positive acceptance was
introduced. Earlier failing invocations are not accepted results.

## Visible player evidence and reuse

The root used a task-owned visible Chrome window. Win32 SendInput supplied
actual mouse/keyboard events; Playwright was used for read-only inspection,
report capture and screenshots, not injected player/controller operations.
The normal URL and visible Open controlled indicator control both reached the
current route without debug flags. Changing the input seed selected the actual
variant; no developer-only variant override qualified normal play.

| Screenshot | What it proves |
| --- | --- |
| [01-npn-diagnosis.png](01-npn-diagnosis.png) | NPN repeated board, power off and ordinary probes observing the open A-load resistor. |
| [02-wrong-repair-rejected.png](02-wrong-repair-rejected.png) | A wrong 100 kohm replacement does not restore the customer function. |
| [03-npn-alternative-repair.png](03-npn-alternative-repair.png) | A 270-ohm replacement for the original 330-ohm NPN-channel load passes actual customer retest. |
| [04-nmos-alternative-repair.png](04-nmos-alternative-repair.png) | A 1.5 kohm replacement for the original 1 kohm NMOS gate resistor passes actual customer retest. |
| [05-independent-channels-support.png](05-independent-channels-support.png) | Channel A LOW, channel B HIGH and support still powered on the repeated NPN board. |

Player evidence predates final developer-verifier corrections. The dependency
audit shows the only source changes during takeover were the three named
developer-only verifiers; normal generation/solver/physical/instrument/UI code
is unchanged. Current compiled gates were nevertheless rerun from final Java.
This reuses unchanged player evidence, not earlier failing compiled results.

## Limits and resources

No A06 onward, arbitrary netlists, transistor-fault mutation, save UI, new
router/layers, MCU/importer or broad legacy rewrite is claimed. The four OPEN
resistor targets and the two-channel physical envelope are deliberately bounded.
Manifests remain capped at 2,048 choices and 262,144 characters with boundary
checks; this is not evidence that larger future boards fit that bound.

The worker runtime could not execute some host dependencies or expose a working
built-in Browser route. Root Desktop Commander execution used the real installed
JDK8/CPython and owned visible Chrome instead. The unchanged strict CLI wrapper's
ownership-deadline limitation remains separate; no timeout, ownership rule or
core isolation implementation was weakened, and no wrapper certification is
claimed. Current product evidence is not a full historical Task43/Gate B matrix.

Task-owned browser, preview and launcher are closed. Standard exact stop released
the preview port. Completed Reserve workers and gate subprocesses have exited;
the root's final documentation/reconciliation session is separate. Unrelated
older resources, including the ambiguous old 8899 preview, were not touched.
Final documentation delta review PASS. Runtime resources are closed; retained scoped recovery evidence is listed in the final handoff. Normal publication follows the final Git audit.

## DeepSeek retry verification

The latest owner instruction changed new workers to DeepSeek V4 Flash. Two
read-only MAX reviewers independently examined the integrated source and raw
qualification records. The runtime recorded provider `deepseek` and model
`deepseek-v4-flash`; previous Reserve author/review results are not relabeled.
The retry root reran the maintained native suite (15 suites, 5,270 physical
assertions, 195 protocol assertions; exit 0 and cleanup), recomputed all four
execution digests, and rechecked the six captured compiled reports against the
new native receipts: exact current identity, three manifests, seed/role and
binary64 value parity passed. This is a fresh native/reader check, not a new
browser playthrough or production build. All 915 accepted source-file hashes
remain unchanged.

The action journal is recovered operator-recorded evidence, not independent OS
input telemetry. Screenshot bytes/timestamps and the normal UI states were
checked; the retry root visually inspected the repaired NMOS/NPN boards and
wrong-repair state. No stronger physical-input certification is claimed.

Unreachable singleton convenience helpers remain a nonblocking cleanup follow-up
when their owning boundary is next changed. They are not an enabled historical
generator. The channel-operation catalog check remains bounded to the current
provider, whose emitted operations are exercised by the compiled corpus. Do not
expand A06/A09 or restart a historical qualification campaign for these notes.

Final independent documentation delta review: PASS. It verified the corrected
four-file hashes, 915 source hashes, accepted build timings and current receipts.
No new source, native, build or browser execution was claimed by that reviewer.
