# Task 43 Recovery Assessment

Assessment date: 2026-08-20

Recovery disposition: **`PARTIAL_SALVAGE`**

This was a read-only recovery/triage assessment of the dirty Task 43 working
tree. No Task 43 recovery implementation, production-code edit, source reset,
cleanup, discard, merge, or later-roadmap work was performed.

## Executive decision

The candidate contains a worthwhile physical-package geometry core and a
mostly sound direction for footprint, installed-render, selection, and probe
consumers. Restarting from the accepted baseline would discard substantial
clean architecture without reducing the hardest remaining reasoning work.

The candidate is not safe to accept or finish with a few coordinate patches.
Acceptance-critical consumers are incomplete, lifted-lead probe semantics have
regressed, geometry identity is under-specified, the focused verifier does not
exercise the full contract, and the candidate NPN/NMOS route experiments are
deterministically invalid. The safe recovery is therefore to preserve the core
contract and bounded consumer migrations while reconstructing the incomplete
contract edges, verifiers, and fixed-layout routing work.

This is not `SALVAGE`: too many acceptance-critical portions require
reconstruction. It is not `REIMPLEMENT_FROM_BASELINE`: the clean geometry model
and consumer ownership migration are independently valuable and separable from
the failed route experiments.

## Authoritative context and baseline

The assessment read `AGENTS.md`, Task 43 in `docs/ROADMAP.md`,
`docs/ARCHITECTURE.md`, `docs/CODEX_TASK_REPORT.md`, repository history around
Task 42, the complete tracked diff and both untracked Java files, and the
relevant package, footprint, PCB layout, compaction, renderer, hit-testing,
probing, mutation, and verifier paths.

- Accepted pre-Task-43 baseline: `c0eb342b29165b8218a4b97b16fb8554fee42aff`
- Baseline subject: `Add LED diagnostic fault diversity proof`
- Baseline parent: `2ccc3b6fb3c6197b5dcadc12cdd63f6ab0ef6ca6`
- Dirty worktree branch: `master`
- Dirty worktree `HEAD`: `c0eb342b29165b8218a4b97b16fb8554fee42aff`
- `origin/master`: `c0eb342b29165b8218a4b97b16fb8554fee42aff`
- Publication branch for this report: `codex/task43-recovery-assessment`

The accepted baseline, local `HEAD`, and `origin/master` were identical during
the assessment. The uncommitted candidate is entirely a worktree diff on top
of that accepted commit.

## Current dirty-worktree inventory

### `git status --short`

```text
 M scripts/verify-browser.ps1
 M src/com/lushprojects/circuitjs1/client/CirSim.java
 M src/com/lushprojects/circuitjs1/client/NmosLowSideSwitchPcbLayoutFactory.java
 M src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchPcbLayoutFactory.java
 M src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java
 M src/com/lushprojects/circuitjs1/client/PcbComponentPlacement.java
 M src/com/lushprojects/circuitjs1/client/PcbFootprint.java
 M src/com/lushprojects/circuitjs1/client/PcbFootprintRegistry.java
 M src/com/lushprojects/circuitjs1/client/PcbPadPlacement.java
 M src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPackage.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPackages.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPartRenderContext.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPartRenderDeveloperVerifier.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPartRenderGeometry.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPartRenderRegistry.java
 M src/com/lushprojects/circuitjs1/client/PhysicalPartRenderTerminal.java
 M src/com/lushprojects/circuitjs1/client/RcDelayPcbLayoutFactory.java
 M src/com/lushprojects/circuitjs1/client/StandardPcbFootprintProviders.java
 M src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java
?? src/com/lushprojects/circuitjs1/client/PhysicalPackageGeometry.java
?? src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java
```

The status above was unchanged after the assessment build and verifier runs.

### `git diff --stat c0eb342`

```text
 scripts/verify-browser.ps1                         |  13 +-
 src/com/lushprojects/circuitjs1/client/CirSim.java |  34 +++-
 .../client/NmosLowSideSwitchPcbLayoutFactory.java  | 183 +++++++++++++------
 .../client/NpnLowSideSwitchPcbLayoutFactory.java   | 188 +++++++++++--------
 .../circuitjs1/client/PcbBoardLayout.java          |  50 ++++-
 .../circuitjs1/client/PcbComponentPlacement.java   |  20 +-
 .../circuitjs1/client/PcbFootprint.java            |  49 ++++-
 .../circuitjs1/client/PcbFootprintRegistry.java    |  14 ++
 .../circuitjs1/client/PcbPadPlacement.java         |  20 +-
 .../circuitjs1/client/PcbWorkbenchRenderer.java    |  14 +-
 .../circuitjs1/client/PhysicalPackage.java         |  27 ++-
 .../circuitjs1/client/PhysicalPackages.java        | 203 ++++++++++++++++++++-
 .../client/PhysicalPartRenderContext.java          |  85 +++++++--
 .../PhysicalPartRenderDeveloperVerifier.java       |  12 +-
 .../client/PhysicalPartRenderGeometry.java         |  25 +++
 .../client/PhysicalPartRenderRegistry.java         |  11 ++
 .../client/PhysicalPartRenderTerminal.java         |  22 ++-
 .../circuitjs1/client/RcDelayPcbLayoutFactory.java | 170 +++++++++++------
 .../client/StandardPcbFootprintProviders.java      |  99 +---------
 .../StandardPhysicalPartRenderProviders.java       | 146 ++++++++-------
 20 files changed, 1002 insertions(+), 383 deletions(-)
```

Git excludes untracked files from `diff --stat`. The two additional untracked
production files are `PhysicalPackageGeometry.java` (15,622 bytes) and
`Task43DeveloperVerifier.java` (9,335 bytes).

`git diff --check c0eb342` passed; Git emitted only local LF-to-CRLF warnings.

### Changed production files

The changed production-source set is:

- `CirSim.java`
- `NmosLowSideSwitchPcbLayoutFactory.java`
- `NpnLowSideSwitchPcbLayoutFactory.java`
- `PcbBoardLayout.java`
- `PcbComponentPlacement.java`
- `PcbFootprint.java`
- `PcbFootprintRegistry.java`
- `PcbPadPlacement.java`
- `PcbWorkbenchRenderer.java`
- `PhysicalPackage.java`
- `PhysicalPackageGeometry.java` (untracked)
- `PhysicalPackages.java`
- `PhysicalPartRenderContext.java`
- `PhysicalPartRenderDeveloperVerifier.java`
- `PhysicalPartRenderGeometry.java`
- `PhysicalPartRenderRegistry.java`
- `PhysicalPartRenderTerminal.java`
- `RcDelayPcbLayoutFactory.java`
- `StandardPcbFootprintProviders.java`
- `StandardPhysicalPartRenderProviders.java`
- `Task43DeveloperVerifier.java` (untracked)

`scripts/verify-browser.ps1` is also modified as diagnostic/verification
infrastructure.

## Independently reconstructed Task 43 contract

The intended authority boundary is sound: a `PhysicalPackage` should own an
immutable package-local contract for terminal pad geometry, visible body and
leads, body keep-out, routing courtyard, selection/hit envelope, drag envelope,
and probe terminals. A placement should translate that contract; it should not
re-derive it. Footprints, compaction, validation, installed rendering,
selection, and probing should consume that same translated declaration.

The contract must keep those concepts semantically distinct even when some
rectangles happen to be equal. It must preserve logical nets, components, pads,
terminal ordering/identity, physical-part identity, CircuitJS measurement
endpoints, fault behavior, repair behavior, and retest behavior. Layout pixels
and route control points are not stable compatibility promises unless an
explicit geometry/replay version says otherwise.

## Architecture audit findings

### Clean work worth preserving

1. `PhysicalPackageGeometry` is a useful immutable aggregate. Its nested
   terminal, lead, and translated-placement views establish a coherent
   package-local coordinate system without putting electrical behavior in the
   renderer.
2. `PhysicalPackage` retaining geometry and `PcbFootprint.fromPhysicalPackage`
   translating package terminals into component pads are the correct ownership
   direction.
3. `PcbComponentPlacement` retaining the selected package geometry and
   `PcbPadPlacement` retaining declared pad/probe bounds provide the data needed
   for downstream consumers.
4. The footprint registry now rejects providers that lose or substitute the
   package declaration. Hand-built standard footprint geometry has largely
   been removed.
5. Installed renderer providers now obtain body, leads, selection, drag, and
   probe geometry through the package-backed render context. Installed hit
   testing and pad rendering/probing have been moved toward declared bounds.
6. The diff does not alter logical generator topology, component IDs, pad IDs,
   net IDs, physical-part IDs, terminal IDs, CircuitJS elements, fault
   injection, replacement, or retest logic. The observed failures are physical
   layout/consumer failures, not evidence of an electrical-model rewrite.
7. RC and existing LED physical-part routes continue to pass the focused
   checks, showing that the core migration is not globally unusable.

### Acceptance blockers and unresolved contract edges

1. **Compaction is not contract-complete.** `getOccupiedContentBounds()` still
   unions courtyards, fixed 32-pixel pad boxes, traces, and labels. It does not
   account for the declared body, lead, selection, drag, or probe envelopes.
   `compactToContent()` can therefore create a board outline that excludes
   declared installed/interactive geometry.
2. **Board containment validation is incomplete.** The new package check
   verifies retained geometry, dimensions, keep-out/courtyard, and pad
   translations, but not every body/lead/probe/selection/drag envelope against
   the final board outline.
3. **Hard-coded pad consumers remain.** Component/pad overlap still uses a
   fixed 26-by-26 box, and silkscreen validation still synthesizes a fixed
   32-by-32 pad box. These paths bypass `PcbPadPlacement.getPadBounds()` and do
   not establish pad/probe-bound containment in the board outline.
4. **Package-local validation is incomplete.** It checks relationships among
   rectangles but does not establish a single explicit rule for whether all
   installed/interactive declarations must fit the nominal package dimensions.
   Generic/connector envelopes currently extend outside nominal dimensions.
5. **Lifted-lead semantics regressed.** In
   `PhysicalPartRenderContext.getComponentProbePoint`, the connected and
   disconnected branches are identical. That discards the baseline distinction
   between a board-side connected terminal and a component-side lifted lead.
6. **Geometry identity is incomplete.** Footprint/layout fingerprints omit
   package identity plus pad/probe bounds, lead geometry, and selection/drag
   envelopes. A behaviorally meaningful interaction-geometry change can retain
   the same identity.
7. **Variant ownership is based on global package ID checks.** A custom package
   reusing a built-in ID could receive or accept built-in randomized/mirrored
   variants. Variant production and acceptance need explicit package ownership,
   not ambient ID dispatch.
8. **Loose probing remains a parallel geometry rule.** The loose-part hit path
   still uses a generic radius around a point instead of the terminal's declared
   probe envelope. Task 43 must either bring loose geometry into the contract or
   explicitly and safely scope it out.
9. **The focused Task 43 verifier is structurally underpowered.** It checks raw
   package relationships and footprint translation but not actual compaction,
   board-outline containment, renderer output agreement, installed hit paths,
   lifted-lead transitions, loose probing, all randomized/mirrored variants, or
   route/silkscreen interaction.
10. **Production-package render coverage is incomplete.** Generic 3-to-6-pin
   canaries exist, but there is no guaranteed installed matrix for resistor,
   diode, LED, electrolytic, ceramic, NPN TO-92, NMOS TO-92, and connector
   variants. Electrolytic and ceramic body positions changed materially and
   lack direct visual/interaction regression evidence.
11. **Layout identity/versioning is unresolved.** RC/NPN/NMOS dimensions,
    placements, routes, and labels changed without a documented geometry
    version decision. Historical pixels are not automatically sacred, but a
    deterministic identity/replay policy must be explicit.
12. **The current verifier shell cannot be trusted by exit code alone.** Several
    invocations printed an unambiguous `FAIL ...` and nevertheless returned
    process status zero. This appears to be a pre-existing harness-flow issue,
    not a demonstrated Task 43 product regression, but acceptance automation
    must use reliable status propagation.

## Classification ledger

Each row assigns exactly one requested classification to a significant current
change or coherent change group.

| Current change | Classification | Recovery action |
| --- | --- | --- |
| New immutable package-local geometry, terminal, lead, and translated-placement model in `PhysicalPackageGeometry`; geometry ownership in `PhysicalPackage` | `CLEAN_TASK43_ARCHITECTURE` | Preserve the model and harden its invariants. |
| Built-in body/pad/lead/keep-out/courtyard/envelope declarations in `PhysicalPackages` | `CLEAN_TASK43_ARCHITECTURE` | Preserve as the package catalog basis. |
| Constructor validation plus legacy `PcbComponentPlacement`/`PcbPadPlacement` compatibility paths that permit null or generic fallback geometry | `UNCERTAIN_REQUIRES_INVESTIGATION` | Define nominal-bounds and explicit fallback policy; prevent bypass of package authority. |
| Randomized resistor/diode and mirrored connector variant production/acceptance keyed through global package IDs | `UNCERTAIN_REQUIRES_INVESTIGATION` | Move variant ownership/acceptance behind the package declaration. |
| Physical geometry retained by `PcbComponentPlacement`; pad/probe bounds retained by `PcbPadPlacement` | `CLEAN_TASK43_ARCHITECTURE` | Preserve. |
| `PcbFootprint.fromPhysicalPackage` plus removal of hand-authored standard footprint coordinates | `NECESSARY_CONSUMER_MIGRATION` | Preserve, then add stronger terminal-ID/variant checks. |
| `PcbFootprintRegistry` enforcement that provider output retains accepted package geometry | `CLEAN_TASK43_ARCHITECTURE` | Preserve. |
| `PcbBoardLayout` package agreement checks and compaction translation of the new geometry fields | `NECESSARY_CONSUMER_MIGRATION` | Preserve and complete full-envelope containment/identity accounting. |
| Remaining fixed 26-by-26 overlap and 32-by-32 silkscreen pad boxes in `PcbBoardLayout` | `NECESSARY_CONSUMER_MIGRATION` | Replace with declared pad bounds and add pad/probe board-containment checks. |
| Expanded trace point/error descriptions in `PcbBoardLayout` | `TEMPORARY_DIAGNOSTIC` | Retain only if still useful as developer diagnostics; do not treat as Task 43 architecture. |
| Installed package geometry accessors and package-backed body/lead/selection/drag/probe construction in render context/providers | `NECESSARY_CONSUMER_MIGRATION` | Preserve after correcting lead-state semantics. |
| Identical connected/disconnected branches in `getComponentProbePoint` | `ABANDONED_EXPERIMENT` | Reconstruct from physical lead-state semantics. |
| Added body/lead/drag/probe fields in `PhysicalPartRenderGeometry` and `PhysicalPartRenderTerminal`, plus registry agreement checks | `CLEAN_TASK43_ARCHITECTURE` | Preserve; fix defensive-copy and invariant gaps. |
| Package-bound pad drawing, installed component hit testing, and installed/pad probe bounds in `PcbWorkbenchRenderer` | `NECESSARY_CONSUMER_MIGRATION` | Preserve and add actual consumer canaries. |
| Loose-render/probe hit path retaining generic `HIT_RADIUS_SQ` and terminal points | `UNCERTAIN_REQUIRES_INVESTIGATION` | Bring it under declared probe geometry or explicitly define the loose-part scope. |
| RC conversion to provider footprints and its validated enlarged fixed layout/routes/labels | `LEGITIMATE_FIXED_LAYOUT_COMPATIBILITY_CHANGE` | Preserve provisionally; document deterministic identity policy and rerun the final matrix. |
| NPN/NMOS conversion from manual components/pads to provider footprints | `NECESSARY_CONSUMER_MIGRATION` | Preserve. |
| NPN/NMOS enlarged board outlines and package-compatible placements | `LEGITIMATE_FIXED_LAYOUT_COMPATIBILITY_CHANGE` | Preserve provisionally; independent A* evidence shows these placements are routable. |
| Current NPN/NMOS handwritten route-control-point rewrites that deterministically cross unrelated copper | `ABANDONED_EXPERIMENT` | Discard and reconstruct as an all-net routing problem. |
| Repeated endpoint diagonal auto-normalization helpers copied into RC/NPN/NMOS factories | `FIXTURE_SPECIFIC_HACK` | Remove; route construction should emit validated Manhattan geometry at a shared boundary. |
| Unused NMOS escape variables and coordinate remnants | `ABANDONED_EXPERIMENT` | Remove after the route reconstruction. |
| Dead `StandardPcbFootprintProviders.AxialProvider.visualKind` field/constructor state after geometry moved to `PhysicalPackages` | `ABANDONED_EXPERIMENT` | Remove unless a concrete non-geometry responsibility is restored. |
| Task 43 query flags, verifier scheduling, generator-failure logging, and console extraction changes in `CirSim`/`verify-browser.ps1` | `TEMPORARY_DIAGNOSTIC` | Keep only the minimal reliable developer route during recovery; retire special logging afterward. |
| `Task43DeveloperVerifier` and the modified render developer verifier | `TEMPORARY_DIAGNOSTIC` | Rebuild into an acceptance-grade consumer/variant/compaction matrix. |
| Material installed body-position changes for electrolytic/ceramic packages | `UNCERTAIN_REQUIRES_INVESTIGATION` | Require explicit visual and hit/probe regression evidence. |

No component-ID-specific package variant, fixture-specific package geometry, or
route-specific package geometry declaration was found. No significant Task 43 production
change required classification as `OUT_OF_SCOPE`; the contamination is mainly
unfinished migration, diagnostics, and route experimentation within Task 43's
physical scope.

### Suspicious or abandoned work

- The NPN and NMOS fixed copper control points are failed coordinate
  experiments with additional conflicts beyond the first validator error.
- The connected/disconnected probe branches currently collapse to the same
  result and cannot be accepted as intentional lead-state behavior.
- Unused NMOS escape variables and the unused standard-footprint `visualKind`
  parameter are residue, not architecture.
- Package-ID-based variant acceptance and implicit generic fallback are
  suspicious ownership shortcuts until their policy is explicitly settled.

### Temporary diagnostics found

- `Task43DeveloperVerifier` and the Task 43 query/scheduling route.
- The LED generator failure wrapper and Task 43-specific browser publication.
- Broadened browser console failure extraction.
- Expanded point/segment descriptions in PCB validation errors.

These may remain during recovery, but they are not substitutes for durable
contract tests and should be minimized after the acceptance route is complete.

### Fixture-specific hacks found

The three fixed factories copy an endpoint normalization routine that silently
inserts Manhattan bends into fixture-provided points. That duplicates routing
policy in RC, NPN, and NMOS fixtures and obscures bad route input. It should not
be preserved. No fixture-specific or component-ID-specific package geometry was
found.

## Preserve/rebuild boundary

### Preserve as the recovery basis

- `PhysicalPackageGeometry.java`: preserve the type structure, immutable value
  objects, translation, mirror capability, and semantic envelope separation;
  revise validation rather than replacing the class.
- `PhysicalPackage.java` and `PhysicalPackages.java`: preserve package geometry
  ownership and built-in declarations; reconstruct global ID-based variant
  dispatch and implicit fallback behavior.
- `PcbComponentPlacement.java`, `PcbPadPlacement.java`, `PcbFootprint.java`,
  `PcbFootprintRegistry.java`, and `StandardPcbFootprintProviders.java`:
  preserve the package-to-footprint carrier/migration direction.
- `PhysicalPartRenderGeometry.java`, `PhysicalPartRenderTerminal.java`,
  `PhysicalPartRenderRegistry.java`, the installed portions of
  `PhysicalPartRenderContext.java`, `StandardPhysicalPartRenderProviders.java`,
  and `PcbWorkbenchRenderer.java`: preserve package-backed installed rendering,
  selection, and probe consumption, subject to the lead/loose-probe fixes.
- In all three fixed factories, preserve the provider-footprint integration.
  Preserve RC's currently validated physical realization provisionally.
- Preserve the current NPN/NMOS board sizes and component placements as the
  starting realization; they are not the cause of the demonstrated failures.

### Revert or reconstruct rather than incrementally patch

- Reconstruct NPN and NMOS copper as whole-board routing sets. Do not preserve
  the current route control points or continue local coordinate roulette.
- Remove the three copied diagonal endpoint-normalization helpers; introduce a
  shared validated route-construction boundary only if it is actually needed.
- Complete/rework `PcbBoardLayout` occupied-content, compaction, containment,
  and geometry-fingerprint consumers. These are mixed files: do not revert the
  retained geometry carriers while fixing the omissions.
- Reconstruct connected-versus-lifted lead/probe behavior in
  `PhysicalPartRenderContext`; do not retain the identical branches.
- Rebuild `Task43DeveloperVerifier` around actual consumers and every declared
  variant. Treat the present verifier as a diagnostic scaffold, not acceptance
  evidence.
- Reassess electrolytic/ceramic visual coordinates through explicit canaries.
- Remove temporary Task 43 generator logging, dead variables, and diagnostic
  scaffolding once the durable verifier route is established.

## Current validation matrix

All checks used the existing dirty candidate without source modification.
Pass/fail below is based on emitted verifier status and diagnostics, not merely
the unreliable shell exit status.

| Check | Result | Evidence / limitation |
| --- | --- | --- |
| JDK 8 / GWT production build | **PASS** | Temurin 8u502; all five GWT permutations compiled; compilation 41.608 s; link 1.107 s. Downloaded archive SHA-256: `3F193FE5E36409C564EB3B7668CB33CAB96AA5879D9B284F25F8653E993B1C49`. |
| `git diff --check c0eb342` | **PASS** | No whitespace errors; only working-copy line-ending warnings. |
| Focused Task 43 route | **PASS, insufficient scope** | Emitted `PASS task43 physical package geometry contract`. It checks registry alignment, base declarations, one seeded footprint translation, and a bad-courtyard canary, not the complete acceptance contract. |
| Package/footprint/render canaries | **PASS before later layout failure** | The architecture verifier runs footprint and physical-render canaries before `PcbLayoutDeveloperVerifier`; the observed architecture failure occurred at the final NPN layout stage. Task 43 also iterated registered packages. Variant/compaction coverage remains incomplete. |
| Architecture route overall | **FAIL** | `COLLECTOR` `(660,300)->(1010,300)` crossed `BASE` `(940,190)->(940,340)`. |
| Procedural/fixed layout route | **FAIL** | Same deterministic NPN crossing. |
| RC layout/electrical verifier | **PASS** | Seeds 0, 2, and 3 each emitted `PASS ... rc`. |
| LED existing physical-part verifier | **PASS** | Seeds 0, 2, and 3 each emitted `PASS ... led-parts`. |
| NPN natural verifier | **FAIL** | Seeds 0, 1, and 2 failed during initialization and timed out after a JavaScript exception; the layout/architecture route supplies the underlying deterministic NPN crossing. |
| NMOS natural verifier | **FAIL** | Seeds 0, 1, and 2 rejected `CONTROL_INPUT`/`LOAD_NODE` crossings. Seed 0: `(600,300)->(600,200)` crossed `(560,230)->(610,230)`; the geometry shifts consistently for seeds 1/2. Static geometry also exposes a later CONTROL/GND crossing. |
| Task 39 automated boundaries | **FAIL / blocked by layout** | NPN and NMOS healthy-boundary checks timed out during broken switch-board initialization. The RC boundary was blocked by the global NPN layout check. |
| Task 39 existing CDP normal-player helper | **PARTIAL** | NPN and NMOS timed out; RC emitted a pass. This headless CDP helper is supplemental diagnostic evidence, not visible built-in-browser acceptance. |
| Task 40 physical locus/serviceability | **FAIL / blocked by layout** | Timed out during NPN initialization before the Task 40 assertion. |
| Task 41 diagnostic solvability | **FAIL / blocked by layout** | Timed out during NPN initialization before the Task 41 assertion. |

No enormous unrelated matrix was run. The smallest seed/family checks needed to
separate core migration health from fixed-layout failure were used.

## NPN feasibility assessment

### Current authored route

The current route is invalid for every seed shift. It contains:

- `COLLECTOR`: `(660+s,300) -> (1010+s,300)`
- `BASE`: `(940+s,190) -> (940+s,340)`

They necessarily intersect at `(940+s,300)`. This is a bad authored route, not
a verifier expectation tied to obsolete geometry and not evidence that the
TO-92 package contract is defective.

An independent reachability check also held every other current authored trace
fixed and searched from Q1.B's legal escape to RPD.1 at 10-, 5-, 2-, and
1-pixel resolution. It found no path, including when the existing same-net
BASE-to-RB copper was reusable. The collector span plus LED/load and GND/Q1
return corridors make a one-segment or one-lane BASE correction topologically
insufficient. This explains why repeated local coordinate edits did not
converge.

### Placement/reachability proof

A read-only independent reproduction of the existing private deterministic A*
router was run against the actual current NPN board outline, package routing
courtyards, pad coordinates, legal endpoint escape directions/lengths, other
pads, 10-pixel grid, and one-grid-cell unrelated-net clearance. It routed every
required logical net using the current board dimensions and component
placements for seeds 0, 1, 2, and 3. A separate exact crossing/segment-distance
post-check covered every generated branch and reported a minimum unrelated-net
centerline distance of 20 pixels in every seed, exceeding the authoritative
15-pixel minimum.

The successful order was a **custom independent witness-search order**:

```text
BASE, CONTROL_INPUT, LOAD_NODE, LOAD_SUPPLY, GND, COLLECTOR
```

It is not the production procedural router order. Production `routeNets()`
sorts the NPN nets as `BASE, COLLECTOR, CONTROL_INPUT, GND, LOAD_NODE,
LOAD_SUPPLY`; the fixed NPN factory currently invokes neither order because it
uses handwritten routes. The custom order proves spatial feasibility, not that
the current production router API will produce the witness unchanged.

A seed-0 witness includes:

```text
BASE Q1.B -> RPD.1:
970,190 -> 750,190 -> 750,350 -> 260,350 -> 260,380 -> 280,380

COLLECTOR LED1.K -> Q1.C:
660,140 -> 660,170 -> 710,170 -> 710,80 -> 1100,80 ->
1100,250 -> 1010,250 -> 1010,190
```

The proof rerouted the entire copper set; it did not pretend that the failed
BASE segment could be corrected while freezing every experimental control
point. This complements the fixed-copper no-path result: the placement is
feasible, but the current authored copper topology is not locally repairable.
The proof did not validate silkscreen placement, so it is feasibility evidence,
not acceptance of a replacement layout.

**NPN conclusion:** the current board outline and component placement are
routable. The defect is the authored copper strategy. No package variant,
component move, or board expansion is required by the evidence, but a local
BASE-only patch is not enough. No placement/package physical change is
demonstrated necessary. The smallest demonstrated implementation change is
coordinated all-net rerouting (with labels adjusted after routing if necessary).

## NMOS feasibility assessment

### Current authored route

The current validated failure is not a mysterious package or legacy-verifier
problem. For seed 0, the `CONTROL_INPUT` vertical at `x=600` crosses the
`LOAD_NODE` horizontal at `y=230`. Seeds 1 and 2 reproduce the same construction
after their deterministic shifts.

Independent source/geometry analysis found another latent current conflict:
the gate branch `(200+s,440)->(600+s,440)` crosses the GND-to-RPD.2 vertical at
`x=540+s`, `560+s`, or `580+s` for the supported resistor spans. By contrast,
the current LOAD_SUPPLY lane at `x=330+s` and CONTROL inner lane at `x=350+s`
are 20 pixels apart and satisfy the 15-pixel rule. A diagnostic naming
`LOAD_SUPPLY` versus `CONTROL_INPUT` would therefore be stale or mislabeled for
this source; the actual conflicts are CONTROL versus LOAD_NODE and GND.

A fixed-copper BFS at 1-, 2-, and 5-pixel resolution found no legal J2.1-to-Q1.G
path while all other current authored copper was retained. Omitting LOAD_NODE
opened the reachable region. Thus a local gate-lane patch is not sufficient.

### Placement/reachability proof

The same independent A* reproduction routed all current NMOS logical nets in
the actual board outline and current component placements for seeds 0, 1, 2,
and 3, including randomized resistor spans, package courtyards, endpoint escape
corridors, and unrelated-net clearance. The exact all-branch post-check found
no crossing and a 20-pixel minimum unrelated-net centerline distance in every
seed.

The successful order was a **custom independent witness-search order**:

```text
CONTROL_INPUT, LOAD_NODE, LOAD_SUPPLY, GND, DRAIN
```

Production `routeNets()` would sort these nets as `CONTROL_INPUT, DRAIN, GND,
LOAD_NODE, LOAD_SUPPLY`; the fixed NMOS factory currently uses handwritten
routes instead. The order above is feasibility evidence, not a claim about the
unchanged production router's ordering behavior.

A seed-0 witness includes:

```text
CONTROL_INPUT J2.1 -> Q1.G:
170,440 -> 900,440 -> 900,190 -> 920,190

LOAD_NODE LED1.A -> RLOAD.2:
520,140 -> 520,200 -> 580,200 -> 580,230 -> 560,230

LOAD_SUPPLY J1.1 -> RLOAD.1:
170,120 -> 360,120 -> 360,230 -> 380,230
```

Again, this is route-set feasibility, not a substitute for running the final
production layout validator and silkscreen checks after implementation. In
combination with the fixed-copper BFS, it proves that the placement has room but
the current authored route topology must be coordinated rather than patched.

**NMOS conclusion:** the board outline and component placement are routable.
The failure is an authored route/corridor problem, not placement-induced
infeasibility and not a package-contract defect. Reconstruct the coordinated
all-net route set/order; no fake package variant or component relocation is
currently justified by the evidence.

## Ordered recovery plan (do not implement under this assessment)

### 1. Harden package-contract and variant ownership — `LUNA_MAX_REQUIRED`

- **Purpose:** settle nominal bounds, explicit geometry fallback, randomized
  variant ownership, connector mirroring, terminal ordering, and immutability.
- **Likely files/systems:** `PhysicalPackageGeometry.java`,
  `PhysicalPackage.java`, `PhysicalPackages.java`.
- **Invariants:** no electrical/topology change; package/terminal IDs and
  measurement endpoints remain stable; concepts remain semantically distinct.
- **Acceptance:** positive/negative tests for every built-in package, every
  resistor/diode span, both connector orientations, custom-ID collision
  canary, deep immutability, and same-seed determinism.

### 2. Complete footprint, containment, compaction, and identity consumers — `LUNA_MAX_REQUIRED`

- **Purpose:** make board containment and deterministic identity consume the
  entire declared contract.
- **Likely files/systems:** `PcbFootprint.java`, `PcbFootprintRegistry.java`,
  `PcbComponentPlacement.java`, `PcbPadPlacement.java`, `PcbBoardLayout.java`,
  footprint providers.
- **Invariants:** stable board/component/pad/net identity and valid endpoint
  escape semantics; no hidden duplicate geometry authority.
- **Acceptance:** compaction contains body/leads/pads/probes/selection/drag and
  courtyards for all variants; deliberate escape canaries fail; fingerprints
  are deterministic and respond to interaction-geometry changes or an explicit
  documented geometry-version policy.

### 3. Correct renderer, hit, probe, and lead-state migration — `LUNA_MAX_REQUIRED`

- **Purpose:** make actual installed and in-scope loose consumers agree with
  package geometry while restoring connected/lifted terminal semantics.
- **Likely files/systems:** `PhysicalPartRenderContext.java`,
  `PhysicalPartRenderGeometry.java`, `PhysicalPartRenderTerminal.java`,
  `PhysicalPartRenderRegistry.java`, `StandardPhysicalPartRenderProviders.java`,
  `PcbWorkbenchRenderer.java`.
- **Invariants:** physical-part/terminal identity and CircuitJS measurement
  endpoints do not change; lifting a lead changes only the intended connection;
  board-side and component-side targets remain distinguishable.
- **Acceptance:** installed package matrix for every production package,
  selection-edge and probe-envelope clicks, connected/lifted/unlifted tests,
  loose-part scope test, and explicit electrolytic/ceramic visual canaries.

### 4. Reconstruct fixed RC/NPN/NMOS physical layouts — `LUNA_MAX_REQUIRED`

- **Purpose:** retain provider footprints and feasible placements while
  replacing route experimentation with whole-board, clearance-aware routing.
- **Likely files/systems:** `RcDelayPcbLayoutFactory.java`,
  `NpnLowSideSwitchPcbLayoutFactory.java`,
  `NmosLowSideSwitchPcbLayoutFactory.java`, and only a shared route helper if
  justified.
- **Invariants:** logical topology, component/pad/net IDs, faults, customer
  behavior, repair/retest behavior, and electrical simulation remain unchanged.
- **Acceptance:** production validators for RC/NPN/NMOS across the closed seed
  set; no crossings/clearance/courtyard/silkscreen failures; deterministic
  fingerprints; directly relevant Task 39/40/41 routes restored. Record the
  geometry identity/version decision rather than preserving obsolete pixels.

### 5. Build an acceptance-grade Task 43 verifier — `LUNA_MAX_REQUIRED`

- **Purpose:** test the contract through real consumers rather than only raw
  value-object relationships.
- **Likely files/systems:** `Task43DeveloperVerifier.java`,
  `PhysicalPartRenderDeveloperVerifier.java`, `CirSim.java`,
  `scripts/verify-browser.ps1`.
- **Invariants:** assertions are strengthened, never bypassed; developer routes
  do not change normal player behavior.
- **Acceptance:** reliable nonzero shell status on failure; package/variant,
  compaction, renderer, selection, probe, lift, layout, and negative canaries;
  fresh JDK 8/GWT build plus the bounded LED/RC/NPN/NMOS and Task 39/40/41 set.

### 6. Mechanical cleanup after architecture and validation settle — `SPARK_SAFE`

- **Purpose:** remove only proven-dead experimental residue.
- **Likely files/systems:** unused NMOS locals/imports, redundant branches after
  their behavior is already implemented/tested, retired temporary generator
  logging, and duplicate diagnostic text.
- **Invariants:** zero behavior, geometry, identity, or verifier-semantic change.
- **Acceptance:** intended-only diff inspection, `git diff --check`, fresh JDK 8
  build, and unchanged closed validation results. If any cleanup requires a
  design or route decision, it leaves this chunk and becomes
  `LUNA_MAX_REQUIRED`.

## Key risks for the recovery

- Several mixed files contain both clean migration and failed experiments;
  whole-file revert or whole-file acceptance would each lose important intent.
- Package-ID-based variant dispatch can silently violate custom package
  ownership and make a verifier pass for the wrong reason.
- Changing the interaction envelope without updating compaction/fingerprints
  can create off-board clickable/probeable regions and replay ambiguity.
- A visually plausible lead can still target the wrong electrical endpoint;
  lifted-lead tests must prove endpoint identity, not just pixels.
- The A* feasibility result proves space exists but does not accept a final
  route, label layout, or route-order API. Production validators remain the
  authority after implementation.
- Current verifier shell status propagation can convert printed failures into
  apparent CI success if callers inspect only the process exit code.
- Historical fixed-layout pixels should not drive fake package variants, but a
  deliberate deterministic layout-version decision is still required.

## Investigator cross-checks

Two distinct read-only investigators were requested at Luna Max: one audited
geometry ownership/consumer architecture, and one independently examined
NPN/NMOS feasibility. Both completed without editing the worktree. The
architecture investigator independently recommended `PARTIAL_SALVAGE` and
identified the same compaction, lead-state, fingerprint, variant-ownership, and
verifier gaps. The layout investigator proved both frozen authored copper sets
are locally infeasible while confirming that courtyards, placements, outlines,
and legal escapes themselves are valid. The primary architect independently
reproduced whole-board A* reachability and remains responsible for this
disposition.

## Explicit no-implementation statement

**NO recovery implementation was performed during this assessment.** The dirty
Task 43 production worktree remains on `master` at the accepted baseline commit
with the same 20 modified tracked files and two untracked Java files. This
document was authored separately from the accepted baseline solely for review
on the dedicated diagnostic branch.
