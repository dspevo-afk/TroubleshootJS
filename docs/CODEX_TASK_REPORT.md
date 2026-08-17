# Task 35 — Generalized Physical Part Specifications

## Status

Complete. Primary architect disposition: `FINAL PASS`. Task 36 was not
started. No push was performed.

Original implementation commit message: `Task 35: generalize physical part specifications`

Correction commit message: `Task 35: preserve catalog specification ownership`

## Task 35 correction — production catalog ownership

The post-commit blocker was corrected without beginning Task 36. Resistor,
diode, and LED production catalog acquisitions now retain the exact immutable
`PhysicalSpecification` object from the selected `PhysicalCatalogEntry`.
Acquisition allocates a distinct runtime-owned physical-part ID and materializes
the selected entry's permitted visible `PhysicalNameplate` fields onto that ID
through `PhysicalNameplate.forPhysicalPartId`. Slot IDs such as R1, D1, and
LED1 are no longer used as replacement specification IDs.

The correction preserves the existing family boundaries: resistor, diode, and
LED CircuitJS element creation, orientation interpretation, generated-fault
ownership, resistor secondary-open behavior, and stress/damage behavior remain
family-owned. Original generated resistor nameplates still expose only physical
color-band markings in normal UI; catalog replacements retain their permitted
catalog markings.

### Correction protocol results

- Coder — Nietzsche: `COMPLETE`. Implemented the bounded controller,
  catalog-ID, visible-nameplate, and production-verifier correction. No commit
  or push was performed.
- Fresh reviewer — Hilbert: `PASS`. Independently verified the actual diff,
  exact production specification identity, physical identity separation,
  remove/reinstall and repeated acquisition behavior, orientation, privacy,
  future canary preservation, and family-specific electrical ownership. A
  transient concurrent browser timeout was not reproducible sequentially and
  was treated as harness flakiness, not a product finding.
- Primary architect: `FINAL PASS` after one bounded correction review round.
  Escalation-architect review was not required.

### Correction closed validation set

- JDK 8 / GWT production OBF build: PASS; all five permutations compiled and
  linked.
- Architecture verifier, including the Task 35 future-part/specification and
  package canaries: PASS.
- Renderer-provider boundary verifier: PASS.
- Procedural layout verifier, seed 3: PASS.
- Seeded LED core matrix, seeds 0/2/3: 15/15 PASS.
- Resistor replacement/identity route, seeds 0/2/3: 3/3 PASS, including the
  d4ad007 regression assertion that the acquired specification ID is the
  catalog ID rather than R1.
- Diode replacement/open route, seeds 0/2/3: 3/3 PASS.
- Diode-short route, seeds 0/2/3: 3/3 PASS.
- LED replacement/identity route, seeds 0/2/3: 3/3 PASS.
- Parallel-family route, seeds 0/2/3: 3/3 PASS.
- Wrong-repair developer and normal-player routes: PASS.
- Resistor stress/damage developer and normal-player routes: PASS, including
  solver-backed secondary failure and pause/reset checks.
- Generic normal-player replacement/privacy route: PASS; original numeric
  resistor value was absent from the player panel.
- LED, diode, and parallel normal-player routes: PASS.
- PowerShell parser checks for the verification scripts: PASS.
- `git diff --check`: PASS before staging; staged check is part of completion.
- Deterministic seeded behavior, generated-fault ownership, and completion
  checks: PASS through the existing seeded verifier routes.

### Correction visible `@Browser` gate

Using the visible in-app Browser on fresh production routes with real clicks:

- LED seed 3 rendered normally. Selecting original R1 showed only
  `Markings: Color bands`, with no numeric resistance, rating, fault, or stress
  information.
- Powering off, removing LED1, selecting the visible
  `LED1_ORIGINAL - Generic red LED` tray part, and installing it as LED1
  restored the installed state and stable A/K labels.
- Installing a catalog LED visibly retained the permitted `Generic red LED`
  marking. Diode seed 3 visibly rendered and exposed only `Generic silicon
  diode`, `D1.A`, and `D1.K` markings.
- Browser diagnostics contained no warning or error entries.

Correction screenshots captured after the final production build, visually
inspected as nonblank application states, and staged with this correction:

- `docs/task-evidence/task-35-correction/led-initial.png` — fresh LED board,
  complaint, catalogs, and empty tray.
- `docs/task-evidence/task-35-correction/led-selected.png` — selected LED
  with permitted player-visible marking and stable terminal labels.
- `docs/task-evidence/task-35-correction/led-removed-tray.png` — power-off
  empty slot with the original physical LED in the loose tray.
- `docs/task-evidence/task-35-correction/led-installed.png` — catalog LED
  installed with its permitted visible marking.
- `docs/task-evidence/task-35-correction/diode-selected.png` — selected D1
  with permitted marking and stable anode/cathode labels.

## Scope and outcome

Task 35 completed the generalized physical-part/specification architecture
needed for future component families while preserving CircuitJS as electrical
truth and retaining legitimate family-specific behavior.

The common boundary now distinguishes:

- immutable `PhysicalSpecification` data and extensible ratings;
- reusable typed `PhysicalPartCatalog` entries;
- player-visible `PhysicalNameplate` markings;
- stable individual `PhysicalPart` identity;
- package, terminal, pad, and orientation metadata;
- runtime-owned inventory and loose/installed lifecycle;
- CircuitJS electrical backing; and
- family-owned electrical factories, polarity interpretation, fault bindings,
  and repair validation.

Resistor, diode, and LED catalogs use the common catalog-entry contract.
`PhysicalPartInventory<P>` is the single runtime-owned identity/inventory
mechanism; the redundant family inventory wrappers were removed. The
developer-only canary is a future-shaped, non-capacitor three-terminal part and
does not add a player-visible component family.

## Architectural decisions

### Specifications, catalogs, and privacy

`PhysicalSpecification` carries a stable specification ID and a small
extensible `Vector<PhysicalRating>` boundary. Existing resistor specifications
expose `PowerRating`; diode, LED, and basic specifications expose no ratings.
This allows future voltage/current/capacitance-specific data without teaching
generic runtime code every rating type.

`PhysicalCatalogEntry<S>` and `AbstractPhysicalCatalogEntry<S>` own common
immutable entry identity, typed specification, player-visible nameplate, and
orientation. Resistor, diode, and LED entries retain their own electrical
fields and factories where needed.

`PhysicalNameplate` remains the normal-player privacy boundary. The workbench
reads player-visible instance metadata only. Hidden original values, ratings,
generated fault ownership, stress/damage state, and private backing data are
not exposed by the generalized specification contract.

### Runtime identity and family specialization

`PhysicalBoardRuntime` remains the owner of physical identity, inventory
membership, and installed/loose association. `PhysicalPartInventory<P>` is a
typed view over that runtime storage. Removing and reinstalling a part keeps
the same physical ID; replacement acquisition creates a new physical instance.

Family controllers still own CircuitJS element construction, attachment
retargeting, diode/LED reversal interpretation, generated-fault bindings, and
resistor stress/secondary-open behavior. No generic branch was added for
resistor, diode, LED, or reference designators.

### Orientation and package identity

`PhysicalPartOrientation` carries `NON_POLARIZED`, `NORMAL`, and `REVERSED`
metadata. Generic physical/render contracts carry it; family providers
interpret its electrical and visual meaning. Terminal names and pad IDs remain
stable independent of render orientation.

Package identity is declared by stable package ID. `PhysicalPackage`
canonicalizes endpoint order and sorts normalized internal connections, making
equivalent definitions order-independent. Board-slot compatibility, footprint
lookup, and physical-render lookup use the same package-definition equivalence
rule. Conflicting same-ID definitions are rejected deterministically.

`PcbWorkbenchController.addCatalog()` now evaluates availability and the
install label for the selected catalog entry rather than deriving both from the
first entry.

## Future-shaped canary

`PhysicalSpecificationDeveloperVerifier` exercises a non-capacitor,
three-terminal future part with:

- immutable specification identity and a private technical field;
- separate player-visible nameplate metadata;
- non-polarized orientation;
- runtime-owned acquisition of distinct stable physical instances;
- loose/installed/remove/reinstall lifecycle;
- stable terminal and pad IDs;
- CircuitJS wire backing validation;
- generic capability discovery;
- typed footprint lookup;
- provider-owned installed and loose rendering; and
- equivalent package definitions with reversed connection declaration order.

The canary also proves deterministic rejection of a conflicting same-ID
package definition and never registers a player-visible future component.

## Files changed

Product/runtime and developer verification:

- `src/com/lushprojects/circuitjs1/client/AbstractPhysicalCatalogEntry.java`
- `src/com/lushprojects/circuitjs1/client/ArchitectureDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/BasicPhysicalSpecification.java`
- `src/com/lushprojects/circuitjs1/client/DiodeCatalogEntry.java`
- `src/com/lushprojects/circuitjs1/client/DiodeNameplate.java`
- `src/com/lushprojects/circuitjs1/client/DiodeProtectedIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/DiodeReplacementCatalog.java`
- `src/com/lushprojects/circuitjs1/client/DiodeReplacementInventory.java` (removed)
- `src/com/lushprojects/circuitjs1/client/FixedPhysicalPart.java`
- `src/com/lushprojects/circuitjs1/client/LedCatalogEntry.java`
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/LedNameplate.java`
- `src/com/lushprojects/circuitjs1/client/LedReplacementCatalog.java`
- `src/com/lushprojects/circuitjs1/client/LedReplacementInventory.java` (removed)
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/PcbFootprintRegistry.java`
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchController.java`
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalBoardRuntime.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalBoardSlot.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalDiodePart.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalLedPart.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPackage.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPackages.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPart.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartCatalog.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartInventory.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartOrientation.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderMetadata.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderRegistry.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalResistorPart.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalSpecification.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/ReplaceableDiodeBoardCapability.java`
- `src/com/lushprojects/circuitjs1/client/ReplaceableLedBoardCapability.java`
- `src/com/lushprojects/circuitjs1/client/ReplaceableResistorBoardCapability.java`
- `src/com/lushprojects/circuitjs1/client/ReplacementDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/ResistorCatalogEntry.java`
- `src/com/lushprojects/circuitjs1/client/ResistorNameplate.java`
- `src/com/lushprojects/circuitjs1/client/ResistorReplacementCatalog.java`
- `src/com/lushprojects/circuitjs1/client/ResistorReplacementInventory.java` (removed)
- `src/com/lushprojects/circuitjs1/client/StandardPcbFootprintProviders.java`
- `src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java`

Documentation and visual evidence:

- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`
- `docs/task-evidence/task-35/led-initial.png`
- `docs/task-evidence/task-35/led-selected.png`
- `docs/task-evidence/task-35/led-removed-tray.png`
- `docs/task-evidence/task-35/diode-initial.png`
- `docs/task-evidence/task-35/diode-selected.png`

## Multi-agent protocol results

### Coder — Nietzsche

Initial implementation: `COMPLETE`. The coder implemented the bounded
catalog/specification, runtime inventory, orientation, package identity,
selected-entry UI, and future-canary work. It reported the required build,
verifiers, regressions, and diff checks passing, with no commit or push.

Correction round 1: `COMPLETE`. The coder resolved the reviewer blocker by
sorting canonical package connections and expanding the canary to exercise
reordered package definitions through slot installation, footprint lookup,
installed rendering, loose rendering, removal, and reinstall identity. It
reported a fresh JDK 8/GWT build and affected regressions passing, with no
commit or push.

### Reviewer — Fermat

Initial review: `FAIL` with one `BLOCKER`. `PhysicalPackage` normalized each
connection endpoint but did not sort the connection set, so equivalent
definitions in different declaration order could disagree across slot,
footprint, and render boundaries.

The primary architect classified this as a genuine blocker because it violated
the Task 35 package-identity acceptance criterion and returned only this
precise correction to the coder.

Correction review: `PASS`. The blocker was resolved. The reviewer found these
non-blocking items:

- `FOLLOW-UP`: the legacy `PcbFootprintRegistry.register(String, ...)`
  compatibility overload does not retain a package definition for future
  conflict validation; current production registration uses the typed overload;
- `FOLLOW-UP`: strengthen direct future-canary assertions for every rendered
  pad ID/draw-loose path if that evidence boundary becomes necessary;
- `FOLLOW-UP`: add a fresh visible-browser trace to the correction-specific
  review record if future process evidence needs it; this is not a product
  defect.

No reviewer `BLOCKER` remains. These follow-ups do not reopen Task 35.

### Primary architect

The primary architect independently inspected the actual corrected diff and
relevant execution paths, including the runtime inventory, catalog entries,
nameplate privacy boundary, family electrical parts, package registries,
workbench catalog selection, orientation, render providers, and the canary.
No additional blocker was found. Primary final disposition: `FINAL PASS`.

Primary architect review rounds: one, with one bounded coder/reviewer
correction pass inside that round. Escalation architect review was not
required.

## Closed validation set

All checks below were run after the correction; the production build was also
rerun by the primary architect with JDK 8.

- `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile
  -Style OBF`: PASS; all five GWT permutations compiled and linked.
- `scripts/verify-browser.ps1 -Architecture`: PASS; includes the Task 34(A)
  architecture checks and the Task 35 future-part/specification canary.
- `scripts/verify-renderer-boundary.ps1`: PASS.
- `scripts/verify-browser.ps1 -Layout -PlayerSeed 3`: PASS.
- Seeded LED core matrix, seeds 0/2/3: 15/15 PASS, covering resistance,
  continuity/meter, challenge/completion, replacement, and combined repair.
- Resistor replacement/identity: 3/3 PASS through the replacement route.
- Diode replacement/identity and generated diode behavior: 3/3 PASS;
  diode-short: 3/3 PASS.
- LED parts/replacement/identity: 3/3 PASS.
- Parallel family regression: 3/3 PASS.
- Wrong-repair developer and normal-player routes: PASS.
- Resistor stress/damage developer and normal-player routes: PASS, including
  solver-backed secondary-open behavior, pause/resume safety, reset, and no
  diagnostic UI.
- LED, diode, parallel, and wrong-repair normal-player routes: PASS.
- PowerShell parser checks for `scripts/verify-browser.ps1`: PASS.
- `git diff --check`: PASS; only expected Windows line-ending warnings were
  emitted.
- Deterministic seeded values and generated-fault ownership/completion checks:
  PASS through the seeded verifier matrices and stress report.

### Visible `@Browser` gate

After the final primary JDK 8/GWT build, the primary architect used the
visible in-app Browser with real visible clicks and typed/semantic controls on
fresh routes:

- LED seed 3 rendered normally. LED1 selection exposed only `Generic red LED`,
  `LED1.A`, and `LED1.K` markings. Power-off removal moved
  `LED1_ORIGINAL - Generic red LED` to the visible loose tray; reinstalling
  the selected tray part returned it to the installed slot without changing
  its visible identity.
- Diode seed 3 rendered normally. D1 selection exposed only `Generic silicon
  diode`, `D1.A`, and `D1.K` markings.
- The visible browser log check returned no error or warning entries.

Committed screenshots, all captured from the final production-browser
viewport and visually inspected as nonblank application states:

- `docs/task-evidence/task-35/led-initial.png` — fresh LED board, catalogs,
  complaint, and empty parts tray.
- `docs/task-evidence/task-35/led-selected.png` — selected LED1 with permitted
  nameplate and stable terminal labels.
- `docs/task-evidence/task-35/led-removed-tray.png` — power-off empty slot and
  original physical LED in the loose tray.
- `docs/task-evidence/task-35/diode-initial.png` — fresh diode board and
  replacement catalog.
- `docs/task-evidence/task-35/diode-selected.png` — selected D1 with permitted
  nameplate and stable anode/cathode labels.

## Known non-blocking follow-ups

The reviewer’s typed-registry compatibility-overload and stronger direct
canary pad-assertion items are carried forward as `FOLLOW-UP`. No capacitor
ratings, capacitor faults, RC circuits, capacitance measurement, transistors,
relays, SMD gameplay, or broad PCB/CircuitJS work was implemented.

## Handoff

Next eligible roadmap milestone: Task 36 — Capacitor Foundation and RC Family.
It is identified only; it was not begun. Adding that family will require
capacitor-specific specification, electrical model, render provider, and
scenario code, but not a copied resistor identity/inventory foundation.

Original implementation commit message: `Task 35: generalize physical part specifications`
Correction commit message: `Task 35: preserve catalog specification ownership`

No push was performed. The task stops here as required.
