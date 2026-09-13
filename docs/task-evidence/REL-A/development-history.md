# Historical development qualification notes

These notes preserve the sequence and candidate-specific failures and passes. Some historical links now point to updated current reports; the named candidate copies and frozen manifests retain the original boundaries. Use README.md and gates.json for current status.

# U04 / U05 / Q15 admission repair / REL-A

Status: IN PROGRESS. Human EASY/MEDIUM trials and remaining final gates are open.
The current candidate includes the RC phase/cleanup corrections, real catalog
rollback, qualified RB15 selection, and the final collapsed-disclosure focus fix.
Native15 passes33 suites/oracles with cleanup; the actual five-permutation build
passes70.426s compile/1.325s link. Task43P has zero open runtime blockers; fresh
A10 passes1274 assertions/24 attempts plus707 RC and14 cleanup checks.
[Alpha38](alpha-compiled.json) passes1217 assertions/223 catalog mutation checks
in851,241ms with2ms cleanup; the strict reader and20 malformed canaries pass.
That loaded compiled execution is `35b0bbb28d03de8c828895f6d3390ec11d2ff2c32748f8fa42e4210b6d47dfa1`.
The [two-file UI/test delta](menu-focus-input-delta.json) leaves every Java/GWT,
native, model and electrical verifier input unchanged. The current
[UI preview](preview-menu-focus.json) is
`b5080dcf8a26650157498b86f5d4c3a6d88f15a57d4ba3c4e79c85f2ebda747d`.
Its49 adapter assertions and actual collapsed/expanded keyboard checks pass;
all remaining gates use this preview. [Gate status](gates.json) is authoritative.
The original [RC deadline failure](alpha-final-failure.json), later cleanup
findings and [menu focus failure](../U04/menu-focus-runtime-failure.json) remain
recorded. These deterministic corpora do not replace human trials.
Base `ec10aa8b849149c33ae00153873c94c6aa6c9ca6`, branch
`codex/task43p-final-recovery`. The authorized visual pass follows stable alpha
acceptance in a separate commit; no later roadmap milestone is included.

The support envelope is selected low-voltage 5–20-package content, including
the sixteen-package RB15 procedural control board, plus explicitly identified
three/four-package indicator practice boards. It does not qualify every circuit
or package count in that range. HARD/PSYCHOTIC, mains, general multilayer routing,
large boards, durable session saves, economy and scoring are not advertised.

Normal RB15 random selection now has the explicit qualified envelope
`0,1,2,3,17,42,101,-1,9007199254740993,-9223372036854775808,9223372036854775807`.
Quick Play, U04 New Board and the legacy New control board control use the same
Java selector. The two known failing arbitrary seeds `-4518705223253195925` and
`-5365808313541656343` cannot emerge from that selector. Selected, generated,
session and replay seeds agree exactly. Explicit developer/replay requests stay
exact and can reject; they do not remap through the random API. Neither the
eleven-seed cohort nor the independent 28-PASS/2-FAIL sample estimates a random
population success rate. No placement, route, job-time or work-unit limit was
increased.

Completed gates and retained failures (release acceptance remains pending):

- Prior RC-phase PASS: [33 maintained native suites](native-contracts.txt), including Q15's
  eleven constructions, fourteen mandatory selector inputs, both exact routing
  rejections at 80 attempts, current replay/session, A10/A11/P03/P04/E01/E03 and
  independent readers/oracles. Q15: 6,771 assertions; U04: 56; U05: 23.
- Prior RC-phase PASS: [actual JDK8/GWT build](gwt-build.txt), all five permutations,
  70.720 s compile and 1.359 s link for the RC phase candidate. Its 1,150 source/test/script/web
  inputs are frozen in [the candidate manifest](rc-phase-inputs.json); the
  separate preview execution digest includes the five compiled permutations.
- PASS: initial real player canary, using the pilot build: failed LED retest,
  power-off, Shop acquisition retaining the mounted part, OL across the original,
  separate removal and tray installation, power-on and passed customer retest.
  This is development evidence, not final-source qualification.
- FAIL: first full compiled alpha run stopped at RC seed0 after ten complete
  cases: five-second work-unit timeout in HYPOTHESES. A fresh ordinary exact RC
  seed0 route subsequently reached its customer ticket. The failed corpus and
  the isolated pass are distinct; neither is a full passing corpus.
- FAIL: [controlled full alpha repeat](alpha-compiled-failure-2.json) passed all
  three RC cases, then hit an NPN power-isolation guard. The [focused report](alpha-npn-forensic.json)
  showed the verifier requested power-off before preceding retest analysis settled.
  The corrected public-power sequence and session completion both pass the
  [focused NPN repair canary](alpha-npn-retest-canary.json). A MOSFET canary also
  passed. Player completion now waits for ordinary input-restoration analysis,
  with exact owner/result/session guards and a bounded failure watchdog.
- FAIL: [composed canary](alpha-composed-publication-failure.json) exposed typed
  product completion being conflated with legacy leaf Quick Play publication.
  The generation request now distinguishes those responsibilities; requalification
  [focused composed publication and repair now PASS](alpha-composed-retest-fixed.json).
  No electrical or admission budget changed.
- FAIL then PASS: a legacy Quick Play verifier flag published a developer failure
  in normal mode. A common debug gate now protects all compiled verifier dispatch;
  the maintained route helper adds explicit debug mode for verifier routes while
  preserving debug=false privacy canaries. [Normal legacy/current flags now expose
  no report or private title/ARIA data](../U04/debug-gate-canary.json). This does
  not qualify the historical Windows CDP wrapper.
- PASS before public-label correction: [integrated Alpha](alpha-compiled-before-label.json), all 38 cases in 849,794 ms,
  1,217 assertions, 110 acquisitions, 105 stale callbacks, 72 negative checks and
  223 mutation checks. The exact prior owner was restored in 1 ms. Its
  [forced failure](alpha-forced-negative.json) fails closed and restores the owner;
  the strict Python reader rejects all 20 malformed canaries.
- PASS before public-label correction: [dedicated Q15](q15/compiled-before-label.json),
  all eleven seeds in 561,586 ms, and [forced failure](q15/forced-before-label.json)
  with exact-owner restoration. This is the compiled rollback candidate.
- PASS on final public-label source: [integrated Alpha](alpha-compiled.json),
  all 38 cases in 845,279 ms, 1,217 assertions, 110 acquisitions, 105 stale
  callbacks, 72 negative checks and all 223 mutation checks. Exact-owner
  restoration took 1 ms. The fresh [forced failure](alpha-forced-negative.json)
  restored the owner in 2 ms; the strict reader rejected 20 malformed canaries.
- RUNNING/PENDING: Q15 full compiled cohort and negatives/readers, affected
  compiled regressions, final player/privacy/focus
  checks and human difficulty trials. No completion, commit or push is claimed.

The [third full alpha attempt](alpha-compiled-failure-3.json) again timed out in
RC HYPOTHESES, this time seed2 after seed0 passed with a 4,932 ms maximum unit.
The preserved candidate2 manifest identifies those inputs. A source/compiled
review located repeated emulated Java-long allocation in hot wall-clock checks.
The narrow correction uses GWT's exact numeric millisecond clock and preserves
every check, electrical step/sample and existing budget; identity longs remain
unchanged. [Compiled lowering](compiled-clock-lowering.json) records direct clock
and numeric subtraction. The [RC seed2 canary](alpha-rc-clock-canary.json) passes
admission/repair in 52,052/68,394 ms, max unit4,833 ms. This small margin is not a
population performance claim. [Compiled A07](a07-report.json) passes58 pure,
71 runtime and59 model assertions,21 model rows,16 scale fixtures and six
execution/cancellation cases. Final integrated native/corpora follow this change.

The [clock candidate passed all 38 cases](alpha-compiled-clock-before-rollback.json)
in 865,568 ms, with owner restoration in 2 ms. The three RC admissions took
50,688 / 51,579 / 52,270 ms; their maximum active units were 4,714 / 4,900 /
4,868 ms. This is retained under [its frozen inputs](clock-before-rollback-inputs.json).
It predates the acquisition review correction and is not final combined acceptance.
The read-only review found that capacitor, NPN, NMOS and LED catalog adapters
lacked partial-write compensation. Their acquisition/removal/installation paths
now use the existing declared mutation scope. The final compiled run passes
223 explicit stale-owner or compensated write cases across all seven adapters,
checked against independent live-state expectations and an exact reader matrix.
An additional [NMOS seed1 canary](alpha-rollback-nmos-canary.json) exercises the
short-fault path compensator that the full corpus's first NMOS seed does not use.
The final RC admissions take 50,648 / 51,150 / 51,630 ms; maximum units are
4,696 / 4,802 / 4,799 ms. The unchanged five-second limit still has little margin.
The bounded worker's own compile attempt used an unsuitable JDK/layout and was
BLOCKED; root's subsequent actual JDK8 native/build and compiled results above
provide runtime qualification. No worker runtime PASS is claimed.

The final release corpus declares 38 family/seed pairs: all currently advertised
random seeds across nine families. Its compiled verifier requires complete
admission, exact replay, real loose acquisition, unchanged installed bindings,
separate repair and real customer retest. Negative cases cover stale session and
catalog leases, foreign providers, unknown catalog entries, mismatched profiles
and cancellation retaining the exact previous owner. A strict independent reader
rejects incomplete/pilot/rounded/duplicate/over-budget reports.

Human calibration is separate from agent-operated acceptance. The user has
been asked to try EASY indicator seed3 and MEDIUM two-channel controller seed3,
reporting outcome, elapsed time, measurements/actions and usability issues.
No human result has been received or inferred.

Final visible MEDIUM seed0 work found a Shop heading displaying the opaque
realization/component ID. The public projection now supplies the physical region
and component name; UI acquisition retains the exact opaque ID. The focused DOM
contract passes 45 checks including display/transport separation. Source/build
and applicable production evidence must be refreshed after this presentation fix.
The earlier 38-case PASS remains the pre-label candidate's result.

A normal RC New board attempt selected seed3 and failed preparation while the
dedicated Q15 corpus was also running. Two Browser Cancel clicks timed out at the
3,000 ms command boundary; neither is counted as cancellation. The normal error
screen retains the previous repaired MEDIUM owner and permits recovery. Its
[screenshot](rc-concurrent-preparation-failure.png) and [interaction record](../U04/player-input.json)
preserve this failure. Normal mode does not expose the private failure phase;
concurrent verifier load is a possible cause, not a proved diagnosis. A fresh
final-candidate RC seed3 replay with no competing corpus reached its ticket in
an observed upper bound of 54,157 ms. A subsequent visible pointer cancellation
retained that exact replay. Its 1,029 ms Browser round trip is not a coordinator
cleanup measurement. Final Settings/Resources checks passed both keyboard focus
wraps, exact draft and return focus, preference persistence/restoration, inert
background and normal DOM privacy. [Final input evidence](../U04/final-player-input.json).

Retained limits include A11-D1 and the historical Windows browser-wrapper
qualification boundary. Real Browser interaction does not qualify that wrapper.
The current task report tracks owned processes, preserved data and publication.
Next unstarted roadmap milestone after acceptance: P05.

The [legacy Quick Play compiled failure](quick-play-unsettled-failure.json)
reported the resistor mutation guard. Its synchronous verifier combined removal
and installation without ordinary runtime settlement and relied on a single
update for public power changes. The verifier now uses the existing bounded
settlement owner between dependent public actions and asserts actual isolation;
electrical, mutation and completion expectations remain intact. The terminal
error does not identify which conjunct of the shared guard rejected. Several
Browser status reads hit their 3-second deadline during the preceding synchronous
selection corpus; these are transport failures, distinct from the terminal
application FAIL. Native33 and the next five-permutation build passed; current
qualification and a final retest-helper entry settlement are tracked in the
checkpoint. Prior passing Alpha evidence remains tied to its own frozen inputs.


The affected compiled A08 run on execution digest `35a16e27c22c18c17357c6f51d8736a648ee543a4a06506899579cc13707a2da`
failed its historical assertion that the LED catalog must be excluded from
composition. The current LED adapter now supplies an owned scoped declaration.
The [retained failure](a08-mutable-led-failure.json) led to a developer-fixture
correction: accept exact owned R1/LED declarations, retain independent unscoped
and foreign-provider rejection checks, and assert no live-state change. The
[one-file source delta](a08-fixture-delta.json) changes no product guard or other
verifier. Native run10 then caught a local variable collision in that fixture;
the [compile failure](a08-fixture-native-compile-failure.txt) is retained, the
local was renamed, and requalification follows. Task41, Task49, E01/E03, A10,
A07 and U01 passed on the immediately preceding build. Their unchanged consumed
source boundary is identified by the before-A08 and current manifests.
