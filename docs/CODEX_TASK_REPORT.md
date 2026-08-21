# Task 43R-4C recovery correction — loose projection lifecycle invalidation

Date: 2026-08-21

Status: `CODE-LEVEL ACCEPTED — OFFICIAL BROWSER VALIDATION BLOCKED`. Commit
`006fa620846f914bde0239502f2198836003cfaa` was externally reviewed as the
43R-4 baseline, but its pagination claims were too strong: an off-page loose
probe target could remain valid electrically, disappear from the marker
projection, and revive when the player returned to the old page. That stale
held-target behavior is the blocker this correction closes, and recording the
correction restores the ordered roadmap.

The bounded correction captures a renderer-owned loose-projection epoch when a
target is acquired. Validity now requires the same generated board and current
loose projection, an existing loose part with a valid terminal, and pure
visibility on the active page. Physical endpoint lookup and target equivalence
remain keyed only by board instance, part ID, and terminal. The renderer
advances the epoch once per actual page or visible-inventory-slice transition
and notifies the instrument controller only after that transition. The
controller clears only invalid loose red/black targets, preserves unaffected
board-pad and installed targets, and resets the active reading/continuity
display without changing CircuitJS topology.

The expanded developer canary uses renderer hit acquisition and real
`InstrumentController.handlePointerInput(...)` calls across two tray pages,
including typed loose representatives and a live multi-terminal canary. It
installs its board wire and every backing `CircuitElm` into the active
`sim.elmList` exactly once, proves a renderer-acquired endpoint completes a
real DC strategy measurement, tears the fixture down on success and failure,
and retains the existing rigid-pose negative checks. Completion remains
pending because the official browser routes still need to run successfully.

The official replacement route exposed a deterministic stale expectation. Its
`verifyTrayPaginationAndProbeGeometry` path directly constructs a retained
`PhysicalResistorPartProbeTarget`, changes pages, and then requires that same
old object to regain a marker after returning. Under the required epoch
contract, the old object must remain invalid; only a fresh
`renderer.findProbeTarget(...)` acquisition may become valid. No existing
allowed production seam can mutate the verifier's retained reference into a
fresh target, and making the old object valid again would violate 43R-4C.

Dependency explanation before the verifier edit: the official replacement
route is mandatory validation, and the clarification explicitly permits a
narrow edit to `ReplacementDeveloperVerifier.java` when its canary is stale
under the required semantics. The correction therefore changes only this
canary's return-page assertion to reacquire through
`renderer.findProbeTarget(...)`, while retaining old-target invalidation and
checking stable physical/endpoint identity. No production lifecycle code is
weakened, and no route is skipped or suppressed.

Validation after this correction: the OBF JDK 8 build/link passed all five
permutations; `verify-renderer-boundary.ps1` passed; the fresh-acquisition
static canary and `git diff --check` passed. The official
`verify-browser.ps1 -Task43` route and
`verify-browser.ps1 -Route replacement -Seeds 3` route were both attempted
after the rebuild, but this host could not expose the Edge target and cleanup
failed with WMI `Access denied`. Therefore no browser `PASS:task43` or
`PASS:replacement` is claimed. A supplemental direct headless Edge attempt
also produced no DOM because the host Edge GPU process was unusable; it is not
counted as route validation.

Completion remains pending until both official browser routes can run and pass.

Current roadmap state after the code-level correction is intentionally ordered
as follows: Task 43 RECOVERY IN PROGRESS; 43R-1, 43R-2, 43R-3, and 43R-4 are
complete after accepted 43R-4C; 43R-5 is next as RC fixed-layout
reconstruction; 43R-6 is blocked by R5 as NPN fixed-layout reconstruction;
43R-7 is blocked by R6 as NMOS fixed-layout reconstruction; 43R-8 is blocked
by R5/R6/R7 as final Task 43 acceptance/regression/cleanup; and Task 44 is
BLOCKED BY TASK 43. The browser routes remain unexecuted on this host because
the Edge target is unavailable and WMI cleanup returns `Access denied`; no
browser pass is implied by the code-level state.

---

# Task 43R-4 completion report — loose package-owned pose and interaction

Date: 2026-08-21

Status: `FINAL PASS — IMPLEMENTATION, VALIDATION, AND INDEPENDENT REVIEW
COMPLETE`. The bounded recovery slice is integrated in the shared worktree;
the primary architect retains commit and publication authority until the final
staged-diff, commit, push, remote-SHA, and notification gates complete.

This is the historical externally reviewed 43R-4 baseline report. Its browser
PASS below predates 43R-4C and is not validation of the correction; the current
official Task 43 and replacement browser routes are unexecuted on the present
host because Edge/CDP is unavailable and WMI returns `Access denied`.

## Objective and bounded contract

Task 43R-4 gives every loose physical part one rigid package-owned pose. The
loose path must consume the exact bound `PhysicalGeometryRealization` when a
part has one, or the explicit package-owned default loose geometry otherwise.
The pose owns orientation/polarity metadata, any required quarter-turn, one
uniform scale, one translation, and the fixed tray cell. Body, leads, pads,
terminal markers, selection, drag, hit testing, and probes must all use that
same transformed geometry. The milestone does not change routing, board
placement, CircuitJS electrical behavior, measurement behavior, faults,
stress/damage, replacement catalogs, or future roadmap work.

## Implementation and architectural decisions

- Added the immutable package-private `LoosePartPose` carrier. Its transform
  order is package-local geometry, optional polarity mirror, optional clockwise
  quarter-turn for connector/developer-generic vertical packages, one uniform
  scale no greater than one, and one translation into the existing tray cell.
- Changed loose `PhysicalPartRenderContext` projections to consume the pose and
  the exact realization/default source; synthetic per-terminal tray positions
  are no longer used for loose geometry.
- Kept `PhysicalPartRenderGeometry` provider-owned as the common source for
  loose draw, selection, hit, marker, and probe behavior. Containment validation
  rejects visible features outside hit/envelope geometry, while pose-scaled
  loose lead strokes are clamped to transformed lead bounds.
- Updated the complete registered provider matrix, including the connector and
  multi-terminal developer canaries, and tightened pagination/selection
  invalidation for hidden loose parts.
- Preserved R3 mounted-slot, part, terminal, endpoint, replacement, and probe
  lifecycle identities. No electrical, routing, measurement, or controller
  semantics changed.

## Package matrix and negative canaries

The loose verifier enumerates every registered package/provider, covering axial
resistor, axial diode, through-hole LED, TO-92 NPN, TO-92 NMOS, radial
electrolytic capacitor, radial ceramic capacitor, connectors, and supported
multi-terminal developer canaries. Positive geometry, provider dispatch,
orientation, polarity, terminal order, stable marker/probe surfaces, and tray
projection checks pass.

The matrix also rejects, with explicit intended reasons, independently warped
terminals, non-uniform body warps, visible bodies outside hit geometry, visible
leads outside hit geometry, giant empty-tray hit regions, markers outside
declared terminal/lead surfaces, and body/terminal transform mismatches. The
verifier retains pagination, stale-selection, variant, reversal, stable
terminal identity, and generic-probe-radius checks.

## Files changed

Source scope is exactly the eight approved R4 files:

- `src/com/lushprojects/circuitjs1/client/LoosePartPose.java`
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderContext.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderGeometry.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderTerminal.java`
- `src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`

Bookkeeping updates are limited to `docs/ARCHITECTURE.md`,
`docs/ROADMAP.md`, and this report.

## Validation and review

- Official bundled JDK 8 OBF production build: passed all five GWT
  permutations and link with
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF -Target Compile`.
- Renderer boundary verifier: passed.
- `git diff --check`: passed.
- Fresh rebuilt visible in-app Browser Task 43 route:
  `data-tsj-verification=PASS:task43`; the rendered PCB/workbench screenshot
  was captured as evidence. A fresh loopback origin was used after the build
  to avoid stale browser cache.
- Loose package/provider matrix and expanded negative canaries: passed through
  the rebuilt Task 43 verifier.
- R3 installed/remove/install/pagination/probe and lifecycle identity checks:
  passed in the existing verifier coverage; installed rendering remains
  unchanged.
- Standard Edge helper invocation remains host-limited because querying Edge
  processes returns WMI `Access denied`; the required visible in-app Browser
  route passed. The pre-existing replacement verifier `NaN` lifted-measurement
  issue remains unrelated and was not changed.
- No source changes were made in route factories, board placement/netlists,
  CircuitJS electrical behavior, measurements, faults, stress/damage, or
  replacement catalog/economy code.

## Coder, review, and handoff

The delegated Luna MAX coder delivered the bounded initial implementation and
then corrected the first review findings: loose lead strokes now follow pose
scale and the negative canaries cover the registered package matrix with exact
failure reasons. The primary architect performed two review rounds (initial
candidate review and post-correction review). The first independent reviewer
found the lead-stroke containment and negative-canary coverage blockers; both
were corrected by the same delegated coder. A fresh independent read-only Luna
MAX reviewer then returned `PASS` with no blocking findings. No escalation
architect was required.

The following closing roadmap snapshot belongs to the externally reviewed
43R-4 baseline and is superseded by the 43R-4C correction above; it is
preserved as historical completion evidence.

The intended commit message is `Unify loose part geometry and interaction`.
The configured publication remote is `origin`
(`https://github.com/dspevo-afk/TroubleshootJS.git`), on branch
`codex/task43-recovery-integration` tracking
`origin/codex/task43-recovery-integration`. The post-push completion
notification destination is `dspevock@stateofthearcelectric.com` with intended
subject `TroubleshootJS: Task 43R-4 loose part geometry and interaction pushed`.
The authoritative final commit SHA, exact remote verification, and notification
result are established only after this report is written and the final
publication protocol completes; no final SHA is claimed here.

Task 44 is the next eligible roadmap milestone and remains unstarted. No
43R-5 slice is defined or started. The architect will stop after the R4
publication and notification attempt.

---

# Task 43R-3 completion report — installed rendering, selection, and probing

Date: 2026-08-21

Status: `FINAL PASS — IMPLEMENTATION, VALIDATION, AND INDEPENDENT REVIEW
COMPLETE`. The bounded recovery slice is integrated in the shared worktree;
the primary architect retains commit and publication authority.

## Objective and bounded contract

Task 43R-3 unified the installed-board rendering, selection, hit-testing, and
board/component-side probing consumers around the exact immutable package
placement and physical-part projection established by 43R-1 and 43R-2. It
does not implement 43R-4, Task 44, loose-part pose/rendering, routing changes,
electrical changes, measurement changes, controller changes, or fixed-route
repairs.

The installed state contract is now explicit:

- connected leads expose the exact board-pad surface and board-pad target;
- lifted leads preserve the board pad and expose a separate detached
  component-lead surface and target;
- physical removal and replacement invalidate installed rendering/selection
  and stale component-lead targets;
- same-part reinstall preserves stable part, terminal, and CircuitJS endpoint
  identity while creating a fresh renderer projection/target epoch; and
- replacement parts have distinct physical identity.

## Implementation

`PcbWorkbenchRenderer` now uses provider-owned installed geometry for drawing,
selection, hit testing, and marker/probe placement. `PhysicalPartRenderContext`
and `PhysicalPartRenderTerminal` carry the exact placed board-pad and
component-side surfaces. `ComponentLeadProbeTarget` validates mounted slot,
part/package/terminal/binding/endpoint identity and the renderer projection
epoch. Renderer selection and component-side lookup require the exact physical
part to remain mounted in its slot. `PhysicalPartRenderGeometry` returns deep
defensive copies for mutable lead rectangles.

The focused developer verifier adds provider-dispatch and terminal-count
canaries, connected/lifted surface checks, exact selection/hit/probe agreement,
same-part remove/reinstall lifecycle checks, replacement identity invalidation,
and board/runtime identity preservation. The implementation changed only the
following eight authorized source files:

- `src/com/lushprojects/circuitjs1/client/ComponentLeadProbeTarget.java`
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderContext.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderGeometry.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderTerminal.java`
- `src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`

## Validation and review

- Bundled JDK 8 OBF production build: passed all five permutations and link.
- Renderer boundary verifier: `PASS:renderer-provider-boundary`.
- Visible in-app Browser Task 43 route: `PASS:task43`; no console warnings or
  errors observed.
- `git diff --check`: passed.
- Fresh independent read-only Luna MAX review: `FINAL PASS`; no blockers.
- The standalone Edge helper remains host-limited by WMI `Access denied` when
  querying Edge processes. This is a harness limitation; the required visible
  in-app Browser route passed.
- The pre-existing architecture routing negative canary remains outside this
  R3 scope and was not changed.

## Review and handoff

The fresh Luna MAX coder corrected exact mounted-slot validation for installed
component-lead lookup without reverting the accepted candidate or changing
unrelated systems. The fresh independent reviewer confirmed provider-owned
rendering/hit/probe geometry, lifecycle invalidation and selection clearing,
immutable geometry copies, R3 canary coverage, eight-file source scope, and
the absence of routing/electrical/measurement/controller/future-milestone
changes.

The primary architect will now stage only the eight source files and these
three documentation updates, run cached checks, commit the accepted R3 result,
push the current recovery branch, verify the remote SHA, and attempt the
required post-push Gmail notification. No later milestone will be started.

# Task 43R-2 completion report — board geometry consumers and physical connectivity

Date: 2026-08-20

Status: `FINAL PASS — IMPLEMENTATION, VALIDATION, AND INDEPENDENT REVIEW
COMPLETE`. The bounded recovery slice is integrated in the shared worktree;
publication remains the primary architect's responsibility.

## Objective and bounded contract

Task 43R-2 closes the board-side consumers of the Task 43R-1 physical package
contract: layout realization, checked compaction, full-stroke containment,
surface/courtyard validation, and physical net connectivity. It does not
implement Task 43R-3 or 43R-4 and does not touch Task 44, fixed route
coordinates, renderer/tray consumers, CircuitJS electrical behavior, fault or
stress behavior, or player probe/mutation behavior.

The implementation DAG was: preserve the R-1 realization carrier and
placement translation identity; make the board geometry validator authoritative
for all installed surfaces and trace strokes; add a physical DSU over pad and
trace-segment contacts plus transitive package-internal connectivity; mirror
the stroke envelope in seeded routing; add focused positive/negative
canaries; then run the official build and visible developer verification
routes.

## Research, delegation, and reconciliation

Three read-only Luna investigations established the R-2 contract, current
consumer call paths, and the fixed-route/deferred-consumer boundary before
implementation. The primary architect reconciled the proposed centerline
trace-clearance interpretation against the existing package courtyard
contract and the generic generated families. The accepted result uses the
full `TRACE_WIDTH` stroke with a narrow, explicit endpoint escape for a trace
leaving its own endpoint pad; generic LED, diode, and parallel layouts remain
strictly validated.

The bounded implementation was delegated to `gpt-5.3-codex-spark` coders with
Extra High (`xhigh`) reasoning. Each Spark coder received three separate
chances to correct its own work. The board coder exhausted the Spark quota
after the initial attempt and two correction attempts, so subsequent
corrections used Luna. The Spark verifier work was completed in the same
bounded scope; no coder pushed or published changes.

## Implementation and correction record

The integrated production changes preserve exact
`PhysicalGeometryRealization` identity through `translatedTo`/
`translatedBy`, validate canonical package-object identity, and carry all
installed surfaces into containment and occupied bounds. `PcbBoardLayout`
builds physical connectivity from pad nodes and absolute trace-stroke segment
nodes, unions trace-pad/trace-trace/pad-pad contacts on matching nets, rejects
cross-net contacts, applies transitive package internal connectivity, and
requires every logical multi-pad net to be physically connected. The seeded
router uses the same full-stroke collision envelope.

The focused verifier and correction rounds addressed the following findings:

- exact placement realization identity after translation;
- absolute trace segment node offsets;
- full-width courtyard/silkscreen containment while preserving generic-family
  validity;
- two-pad package-backed fixture coverage and legal `LEAD_NET` connectivity;
- no-argument connected component-lead probe coverage;
- canonical package-object identity and pad-pad DSU coverage;
- exact fixed-layout deferrals, including the RC `VIN / C2` courtyard
  signature exposed by the final full-width check.

The exact deferred fixed-layout signatures retained or exposed by this strict
check are:

- RC `component:C2 / GND` silkscreen;
- RC `VIN / C2` courtyard segment `296,210 -> 296,370`;
- NPN `LOAD_SUPPLY / RLOAD` courtyard;
- NPN zero-length segment at `1050,230`;
- NMOS `LOAD_SUPPLY / RLOAD` courtyard.

The existing RC silkscreen, NPN courtyard, and NMOS courtyard records remain
the previously reserved R-5/R-6/R-7 fixed-route deferrals; the RC courtyard
and NPN zero-length records are the R-2 recovery observations. These are
explicit later-recovery records, not catch-all acceptance. Any other
generated layout or geometry error remains a verifier failure.

Jason's final coder accounting reported all five original reviewer blockers
fixed. He estimated approximately 1,635 added lines in the total candidate:
about 639 lines of final production architecture and 996 lines of permanent
developer verifier/canary coverage, with zero temporary diagnostics or
experiments remaining.

## Validation evidence

- `git diff --check`: passed.
- Official JDK8 OBF compile/link passed across all five permutations with
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF
  -Target Compile`.
- The visible in-app Browser developer routes reported `PASS:task43` for LED,
  diode, and parallel seeds 0, 2, and 3, and `PASS:layout` for the strict
  layout route after the final RC deferral correction. Browser diagnostics
  showed no product console logs on those routes.
- The Task 43R-2 verifier includes positive and negative canaries for package
  identity, two-/three-pad and transitive package connectivity, branches,
  perpendicular traces, compaction identity, full-stroke edge containment,
  surfaces, lifted/connected leads, endpoint escape, crossing/clearance,
  orphan/disconnected nets, and generic generated LED/diode/parallel layouts.
- A host limitation prevented the standard preview helper's Edge WMI process
  query from completing; the already-running local server and visible
  browser route were used instead. This is a harness limitation, not a
  product failure.

The fresh independent read-only Luna reviewer, explicitly run at Max
reasoning, returned exactly `PASS` with no unresolved R-2 blocker. The primary
architect's final staged-diff inspection and publication gate remain.

---

# Task 43R-1 correction handoff — package realization verifier and boundary

Date: 2026-08-20

Status: `FINAL PASS — PRIMARY ARCHITECT VALIDATION AND INDEPENDENT REVIEW
COMPLETE`. The bounded correction is integrated in the shared worktree. No push
was performed; the primary architect retains publication authority.

## Correction design and research handoff

The integrated correction preserves the selected-geometry ownership contract:
`PhysicalPackage` owns package definition/catalog/default,
`PcbComponentPlacement` exposes the immutable `PhysicalGeometryRealization`,
`PhysicalBoardSlot` retains the final layout carrier across removal, and each
`PhysicalPart` binds that carrier once. Geometry contract version 2 is checked
through explicit connected and detached lifted lead surfaces.

Luna's research/review summary identified the stale verifier use of the removed
`Lead.getPadPoint()` API, the missing selected-realization lifecycle proof, and
the need for generated runtime identity to cover carriers, parts, terminals,
and CircuitJS endpoint identities. It also confirmed that renderer/tray
consumers are deferred implementation slices rather than a verifier failure
classification, and that legacy `PcbPadPlacement` compatibility must remain
untouched.

Spark A integrated the package geometry/catalog/placement contract. Spark B
integrated the slot/runtime/physical-part carrier ownership and one-time
binding lifecycle. Spark C owns this bounded correction: verifier API and
surface assertions, synthetic SPAN_260 lifecycle canary, generated identity
snapshot/canaries, and the Task 43R-1 architecture and handoff wording.

## Scope and behavior implemented by this candidate

The verifier now checks connected endpoints against terminal pad centers;
detached lifted endpoints against pad centers and board-pad probe bounds; shared
lifted/connected body attachments; lead/probe containment in selection and drag
envelopes; lifted lead/probe separation from board-pad probes; existing terminal
order, translation, mirroring, and cross-terminal probe matrix checks; and
negative canaries for malformed detached endpoints, bounds, and probes.

A verifier-local synthetic axial-resistor board binds a real SPAN_260 footprint
through `PcbBoardLayout` and `PhysicalBoardRuntime`, installs a real
`PhysicalResistorPart` backed by a `ResistorElm`, removes/reinstalls the same
part, checks stable part/terminal/endpoint/carrier identity, and rejects a
SPAN_220 rebind. The synthetic element is not added to a live generated board.
Generated identity proof now requires non-empty family/topology IDs and
agreement with `GeneratedChallengeDefinition` and
`GeneratedDiagnosticSolvabilityContract`; snapshots include sorted board
component/pad/net mappings, semantic operation IDs, runtime slot/part/terminal
IDs, endpoint identities, and realization fingerprints.

The architecture section now records contract version 2, explicit carrier
ownership, one-time binding, detached lifted geometry, and the corrected
boundaries: R-2 is board/layout/compaction/containment/connectivity; R-3 is
installed rendering/selection/probing; R-4 is loose pose/render/hit/probe and
physical-part realization consumer lifecycle. These consumers remain deferred
and are not claimed implemented here.

## Checks and remaining validation

- The correction intentionally touched the narrow geometry-lifecycle production
  layer required by the approved R-1 ownership design: placement, package
  geometry, slot/runtime binding, physical-part realization carriers, and
  geometry versioning, alongside `Task43DeveloperVerifier.java` and the two
  documentation files. It did not touch PCB route factories, renderer
  implementation, tray implementation, CircuitJS electrical topology,
  measurement endpoint behavior, fault behavior, stress/damage behavior, or
  Task 44.
- `git diff --check`: passed after the correction.
- Task 43 verifier compile/link: passed across all five OBF GWT permutations
  with `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07`.
- Task 43 browser route: passed through the visible Codex in-app Browser after
  the prescribed `scripts/start-preview.ps1 -Port 8899` helper was shown to be
  unavailable on this host because `System.Net.HttpListener` reports
  “Operation is not supported on this platform”. A temporary loopback-only
  static server served the already-built `war` output, and the Browser loaded
  `circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyTask43=true&running=true`.
  The rendered application published `data-tsj-verification=PASS:task43`; no
  Browser console errors or warnings were observed. No normal-player screenshot
  is claimed because this is a developer-verifier route, not a player-flow
  acceptance test.
- Exact integrated allowed-file scope check: passed; only the authorized
  Task 43R-1 source, verifier, and documentation files are present.
- Fresh independent read-only Luna reviewer: returned exactly `PASS` with no
  blockers against the frozen R-1 contract and current integrated diff.
- Stale `Lead.getPadPoint()` search: passed with no remaining verifier use.
- No renderer, tray, routing, electrical, fault, mutation, or Task 44 consumer
  migration was invoked.

Known limits are the intentionally deferred R-2/R-3/R-4 consumer slices. No
Task 43R-2 work was started.

---

# Task 42 — Existing-Family Diagnostic Diversity Proof

Date: 2026-08-19

Status: `FINAL PASS`. Task 42 only was implemented and validated; Task 43 was
not started.

## Scope and behavior

Task 42 extends the existing LED indicator family with a real solver-backed
`LED_OPEN` fault. The generated graph inserts a private open switch owned by
the physical `LED1`, while the board still exposes authentic LED terminals
through that switch. The fault therefore persists through removal and
reinstallation of the original physical part. Only a distinct correct LED
catalog installation clears the fault. The existing R1 open and R1 incorrect
routes remain intact.

The LED family now admits three normal candidates: R1 open, R1 incorrect, and
LED open. The full normal corpus is 14 routes: LED 3, diode 1, parallel 2, RC
2, NPN 3, and NMOS 3. Task 40 serviceability proves LED-open removal,
catalog-install repair, and customer retest, with negative proofs for R1
replacement/removal/reinstall and original bad-LED reinstall. Task 41
solver-backed signatures distinguish LED open from both R1 faults and retain
the wrong-owner repair negatives in the opposite direction.

Owner diversity is derived from admitted physical repair-owner IDs rather than
from a hand-maintained UI or difficulty table:

| Family | Admitted routes | Distinct owners | Classification |
| --- | ---: | ---: | --- |
| LED | 3 | 2 (`LED1`, `R1`) | `MULTI_OWNER_DIAGNOSTIC` |
| diode | 1 | 1 (`D1`) | `GUIDED_EASY_SINGLE_OWNER` |
| parallel | 2 | 1 (`R1`) | `GUIDED_EASY_SINGLE_OWNER` |
| RC | 2 | 1 (`C1`) | `GUIDED_EASY_SINGLE_OWNER` |
| NPN | 3 | 2 (`Q1`, `RB`) | `MULTI_OWNER_DIAGNOSTIC` |
| NMOS | 3 | 1 (`Q1`) | `GUIDED_EASY_SINGLE_OWNER` |

The LED Quick Play seed envelope is `{0,2,3,4}` so the new fault is
reachable without changing the shared legacy family envelope `{0,2,3}`.

## Validation evidence

- Synced with `git pull --ff-only origin master` before implementation; the
  starting master commit was `7abba4a` and the pulled baseline was `2ccc3b6`.
- JDK8 OBF compile/link passed with bundled JDK 8u502. `git diff --check`
  passed, and the architecture verifier passed.
- Task 40 reported `PASS:task40`; Task 41 reported `PASS:task41` for all 14
  routes with 128 solver samples. The live Task 41 evidence included
  `routes=14`, `declaredDepth=4..6`, `measuredDepth=4..9`,
  `declaredTemplates=6`, `declaredMeterModes=43`,
  `executedMeterModes=41`, `declaredTransitions=54`,
  `executedTransitions=30`, `declaredIsolation=16`,
  `executedIsolation=0`, `declaredTemporal=26`, `executedTemporal=8`,
  `declaredRailsDomains=11`, `parallelAmbiguity=1`, and `retest=1`.
- Task 39 reported all six existing NPN, NMOS, and RC routes passing. Legacy
  LED seed 0, 2, and 3 resistance/meter/challenge/replacement routes all
  passed sequentially; LED physical-part verification and the Task 40/41
  seed-4 proofs passed.
- Visible in-app Browser validation used real player input on LED seed 4:
  power off, select LED1, remove the installed part, install the correct
  catalog LED, power on, and retest. The rendered UI reported
  `Repair verified. Indicator operating normally.` and
  `Customer retest passed. The reported behavior is resolved.` No browser
  console errors or warnings were observed.
- Evidence screenshots are `docs/task-evidence/task-42/led-open-initial.png`,
  `led-removed-tray.png`, and `led-repaired-retest.png`.

The aggregate Quick Play helper still has a pre-existing RC finish-route
cancellation, and its complete LED seed-4 finish canary remains follow-up
work; the explicit LED seed-4 player workflow and Task 40/41 proofs passed.
This is recorded as a harness/coverage limitation, not a Task 42 product
failure.

## Review and handoff

The delegated coder returned the bounded implementation with the JDK8/GWT
build, Task 40/41, physical LED, legacy, replacement, and stress checks
passing; it did not commit, push, or edit documentation. An independent
read-only reviewer returned `PASS` with no blockers or backlog findings. One
primary-architect review round confirmed the private switch graph, public
terminal mapping, persistent original-fault identity, derived owner counts,
route totals, and the absence of Task 43 work. No escalation architect was
needed.

The intended commit message is `Add LED diagnostic fault diversity proof` on
`master`, with only the Task 42 source, documentation, and browser evidence
files staged. After final validation, the primary architect will verify the
remote commit and attempt the required Gmail notification to
`dspevock@stateofthearcelectric.com` with subject
`TroubleshootJS: Task 42 LED diagnostic diversity proof pushed`.

Task 43 is the next eligible roadmap milestone and remains unstarted.

---

# Task 41 — Diagnostic Solvability Verifier v1 and Complexity Evidence

Date: 2026-08-19

Status: `FINAL PASS` for implementation, validation, independent review, and
publication. Task 42 is identified as the next eligible milestone but was not
started.

## Scope and behavior

Task 41 adds a family-agnostic diagnostic solvability contract and a live
pre-READY admission boundary. The developer proof enumerates the compatible
fault candidates for one unchanged topology/layout, then exercises rendered
PCB probe endpoints, CircuitJS-backed DC voltage/resistance/continuity/diode
measurements, board power, player-controlled inputs, temporal waits,
isolation, repair, and customer retest. It records numeric solver samples and
tolerances, candidate separation, explicit equivalent-repair classes, and
deterministic rejection reasons.

The normal admitted corpus is 13 routes: LED 2, diode 1, parallel 2, RC 2,
NPN 3, and NMOS 3. `DIODE_SHORT` remains developer-only. NPN
`LOAD_PATH_OPEN` is excluded from normal admission and is checked only as a
developer-only same-layout comparison against C-E open. The proof rejects
unsupported player operations and requires the unaffected-function retest.

Developer candidate boards use detached real workbench renderers, so proof
execution cannot append duplicate player panels. `Task41SimulationSnapshot`
restores the exact generated owner and CircuitJS graph object identity/content
without re-analysis, plus board power bindings, instruments and continuity
feedback counters, physical modification state, runtime/render counters and
static CircuitElm render state. Transactional rollback has best-effort
exception recovery and eight injected restore-failure stages.

## Validation evidence

- JDK8 OBF compile/link passed with the bundled JDK 8u502.
- PowerShell parser check returned `PS_PARSE_OK`; `git diff --check` passed.
- Visible in-app Browser Task 41 route reported `PASS:task41` with three
  rendered component panels, no measurement overlays, and evidence metrics:
  `routes=13`, `minDepth=4`, `maxDepth=6`, `templates=6`,
  `solverSamples=121`, `transitions=51`, `isolation=15`, `meterModes=4`,
  `temporal=25`, `railsDomains=11`, `parallelAmbiguity=1`, and `retest=1`.
  The evidence attribute contains actual numeric sample/tolerance pairs.
- Visible Task 40 reported `PASS:task40`. Visible Task 39 NPN, NMOS, and RC
  routes each reported `PASS:task39` with three rendered component panels.
- The fresh independent read-only reviewer returned `FINAL PASS` after the
  exact-runtime and continuity-counter correction round. The initial review
  blockers were corrected and the corrected build/routes were rerun.

## Limitations and handoff

The standalone `scripts/verify-browser.ps1 -Task41 -Seeds @(0)` helper remains
environment-limited on this host by the Edge process/WebSocket boundary and
Win32 process-query access. Required player-facing evidence was obtained with
the visible in-app Browser; the helper limitation is not an application
failure. The final publication SHA and remote verification are reported in
the primary-architect handoff and post-push notification.

---

# Task 40 — Physical Fault Locus and Serviceability Admission

Date: 2026-08-19

Status: `FINAL PASS` for implementation, validation, and independent review;
ready for the primary-architect commit/push gate.

## Scope and behavior

Task 40 adds family-agnostic hidden metadata connecting each normally admitted
solver fault candidate to a stable physical locus, legal observation and
isolation actions, a supported repair path, and the Task 39 customer retest.
Loci are semantic component-internal, terminal/lead attachment, connector
contact, or trace-segment identities; private CircuitJS switches never become
physical owners. Admission now rejects unknown observation, isolation, and
repair IDs before candidate selection or physical-owner metrics. The verifier
also proves that a bogus `BOGUS_REPAIR` candidate is rejected.

The generated board retains the full admitted candidate set for owner metrics
and keeps selected-binding integrity separate. Runtime admission checks the
installed original physical part, stable terminal/connection bindings, probe
exposure, replacement provider, operation catalog, and family controller
providers. `Task40DeveloperVerifier` then executes real remove/lift/reconnect
and catalog-install operations through the existing workbench controller,
repowers the board, and invokes customer retest. Connector and trace candidates
remain incompatible for normal play. NPN `LOAD_PATH_OPEN` is a forced
developer-only fixture; NMOS public Q1 terminals and RC C1 positive-lead
attachment remain owned by the original physical part.

The source-of-truth normal corpus is 13 routes: LED 2, diode 1, parallel 2,
RC 2, NPN 3, and NMOS 3. The roadmap’s previous estimate of 14 was stale:
normal `DIODE_SHORT` is developer-only and NPN `LOAD_PATH_OPEN` was removed
from normal admission under the option-B resolution.

## Validation evidence

- JDK8 OBF compile/link passed with the bundled JDK 8u502 after the final
  action-whitelist correction.
- PowerShell parser check returned `PS_PARSE_OK`; `git diff --check` passed.
- The focused Task 40 route in the visible in-app Browser completed the real
  generated-candidate verifier and reported `PASS:task40`.
- Independent review reran/confirmed Task 40, Task 39, NPN, and NMOS browser
  verifiers and found no blocker.
- The standalone Edge/CDP helper remains environment-limited on this host:
  its cleanup/process snapshot can fail before app execution with Edge/WMI
  access denied or a WebSocket cancellation. This does not invalidate the
  visible in-app Browser result.

## Review and limitations

The first independent review found metadata-only Task 40 verification and
selected-owner-only metrics; both were corrected. A fresh independent review
then found the stale 14-route documentation and the missing admission-time
action whitelist; the roadmap/report/architecture notes and source verifier
were corrected. The fresh review returned `FINAL PASS`. Task 41 is identified
as the next milestone but has not been implemented.

---

# Task 39 — Player-Operable Functional Inputs and Customer Retest Contract

Date: 2026-08-19

Status: `FINAL PASS` for implementation, validation, and independent review;
ready for the primary-architect commit/push gate.

## Scope and behavior

Task 39 adds a family-neutral `GeneratedBoardOperationCatalog` with stable
semantic IDs (`CONTROL_INPUT_HIGH`, `CONTROL_INPUT_LOW`, and
`CUSTOMER_RETEST`). NPN and NMOS HIGH/LOW operations dispatch their existing
external CircuitJS command switches; customer profiles validate the resulting
J2.1/gate and load behavior from the solved circuit and restore command, power,
and physical state with nested `finally` cleanup. All six current families own
profiles, and RC uses a real board-power cycle plus natural stored-energy
discharge.

The workbench exposes the operation/retest contract to a normal player. Live
repair status, retest result, Finish Job, and latched `COMPLETED` are distinct.
After completion, board power, instruments, PCB selection, and physical
mutation are disabled; NPN/NMOS semantic operation controls remain live and
solver-backed. Legacy replacement and stress verifiers now finish all physical
checks before the public retest.

## Validation evidence

- JDK8 GWT OBF compile/link passed with the bundled JDK 8u502.
- PowerShell parser check: `PS_PARSE_OK`; `git diff --check` passed.
- `-Task39 -TimeoutSeconds 120`: six routes passed (NPN, NMOS, RC boundary
  plus three visible normal-player routes).
- Seeded matrices passed: NPN 16/16, NMOS 12/12, RC 4/4; natural NPN 4/4,
  natural NMOS 4/4, stored-energy 3/3.
- Affected legacy normal-player flows passed: resistor, diode, parallel, LED,
  and RC terminal-state checks. Replacement seed 3, stress/damage, LED parts,
  diode, parallel, and wrong-repair routes also passed; the two routes that
  initially collided under concurrent Edge processes passed when rerun serially.
- Visible built-in Browser interaction on the rebuilt preview exercised NPN
  `Set control HIGH`, `Set control LOW`, and `Retest Customer`, producing the
  expected solver-backed unrepaired retest message. A visible RC
  `Power-cycle and Retest Customer` action likewise produced the expected
  unrepaired message. Screenshots were captured during both flows.
- Visible Quick Play NPN seed 3 reached the completed report
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`;
  physical controls were disabled while HIGH/LOW semantic controls remained
  enabled.

## Review and limitations

The first independent review found post-completion physical checks in the
replacement and stress verifiers; those checks were moved before completion,
and both routes were rerun successfully. The second independent review
returned `FINAL PASS`. The standalone full `-QuickPlay` harness still
reports an Edge/CDP WebSocket cancellation when successful Finish Job reloads
the document; the direct visible Quick Play route verifies the product state
before that expected target replacement. This is recorded as harness evidence,
not a product failure.

---

# Roadmap Completion-Workflow Consistency Correction Report

Date: 2026-08-18

Task type: bounded documentation/process correction only.

Baseline: clean local `master` synchronized with `origin/master` at
`a1e1957bd574c731b9dedcd33e0e53fcee1f89f4`
(`Fix roadmap and publishing workflow`).

Scope: `docs/ROADMAP.md` and this rolling report only. Task 39 and every
implementation milestone remain untouched.

## Contradiction corrected

`AGENTS.md` already defined the normal successful primary-architect workflow as:

    validation
      → final diff/status inspection
      → report and documentation
      → intended-only staging and cached checks
      → one final commit
      → push to the configured upstream
      → verify the remote contains the exact final SHA
      → attempt the post-push Gmail completion notification when available
      → STOP without beginning another milestone

The roadmap still said both “After a milestone is completed and committed,
STOP” and “Stop after the commit.” Those statements could end a task before
publication, remote verification, and notification.

The roadmap now:

- preserves one milestone as one bounded task;
- requires the full permanent `AGENTS.md` Task Completion Protocol before
  stopping;
- states that the configured upstream must contain the exact final SHA;
- includes the post-push Gmail notification attempt and truthful result
  reporting in its Definition of Done;
- updates the roadmap-maintenance stop rule to the same boundary; and
- continues to prohibit automatically beginning the next milestone.

No detailed process law was duplicated beyond the short sequence needed to
remove ambiguity; `AGENTS.md` remains authoritative.

## Files changed

- `docs/ROADMAP.md`
  - corrects the opening milestone boundary;
  - replaces milestone-selection step 6's stop-after-commit instruction;
  - aligns the Definition of Done with verified publication and notification;
  - aligns the roadmap-maintenance stop rule.
- `docs/CODEX_TASK_REPORT.md`
  - overwrites the previous correction handoff with this task report.

`AGENTS.md` was reviewed and intentionally unchanged. Its normal primary
workflow is already consistent, and its delegated coder/reviewer/escalation
prohibitions against committing, pushing, publishing, or notifying remain
intentional.

## Validation

- Initial `git status`: clean on `master` with local HEAD and `origin/master`
  both at `a1e1957bd574c731b9dedcd33e0e53fcee1f89f4`.
- Allowed-path inspection: only `docs/ROADMAP.md` and this report are intended
  to differ.
- Re-read the roadmap Purpose/task-boundary statement, Roadmap Rules →
  Milestone selection, Definition of Done, Roadmap Maintenance Rules, and
  Immediate Next Milestone after editing.
- Searched `AGENTS.md` and `docs/ROADMAP.md` for normal primary-workflow
  stop-after-commit, do-not-push, and commit-as-final-boundary language.
- No stale normal-workflow stop-after-commit instruction remains in the
  roadmap.
- The primary sequence is unambiguous: commit → push → verify → notification
  attempt → stop.
- Delegated subagents remain prohibited from publishing; those rules were not
  treated as contradictions.
- Exactly one immediate `[>]` milestone remains Task 39.
- Task 39's milestone text, scope, dependencies, and status were not modified,
  and no Task 39 implementation was started.
- No roadmap ordering, Task 64/65/66 dependency, difficulty migration, or
  production/architecture content changed.
- Working-tree `git diff --check`: passed.
- Final intended-path, diff, status, and staged-diff checks: passed; exactly the
  two authorized files are staged and `git diff --cached --check` is clean.
- Production JDK/GWT and browser validation were intentionally not run because
  this correction is documentation/process only.

## Review and acceptance

Coder/reviewer subagents: not used; this was a narrow primary-architect wording
correction.

Primary architect result: `FINAL PASS` subject to the required final staged
check, verified push, and notification sequence.

Escalation architect: not required.

## Known limitations

- The report is written before its own commit, push, and email action exist, so
  it cannot contain its authoritative final commit SHA, push result, or
  notification result. Those exact results are available from repository
  history and the final Codex task response.
- This task provides no production-runtime validation evidence and starts no
  implementation milestone.

## Completion intent

Intended commit message: `Align roadmap completion workflow`

Configured publication target: `master -> origin/master`

Notification recipient: `dspevock@stateofthearcelectric.com`

Intended notification subject:
`TroubleshootJS: Roadmap completion workflow correction pushed`
