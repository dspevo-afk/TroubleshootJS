# TASK 43 — Physical Package and Interaction Envelope Contract

Repository:
TroubleshootJS

Starting point:
Latest master after Task 42:
`c0eb342b29165b8218a4b97b16fb8554fee42aff`
"Add LED diagnostic fault diversity proof"

## GOAL

Make every current physical package expose one authoritative geometric contract so that:

- footprint/pad positions
- rendered body
- rendered leads
- routing courtyard / keepout
- selection hit area
- drag hit area
- probe hit area

all agree in the same package-local coordinate system.

This task is architectural groundwork for Task 44 routing keepouts/corridor policy.

Do NOT change electrical behavior.
Do NOT begin Task 44 routing-policy work.
Do NOT replace existing detailed component artwork with generic rectangles.

## BEFORE IMPLEMENTING

Read and obey:

- AGENTS.md
- DEVELOPMENT.md
- docs/ARCHITECTURE.md
- docs/ROADMAP.md
- docs/CODEX_TASK_REPORT.md

Then inspect the existing implementations and registries involved in:

- PhysicalPackage
- PhysicalPackages
- footprint/provider registry
- physical-package render providers
- PCB/board renderer
- PCB placement transforms
- router geometry consumers
- selection hit testing
- drag hit testing
- probe/pad hit testing
- physical board runtime/slots
- developer verifier infrastructure

Do not create a second parallel geometry system if an existing abstraction can be cleanly extended.

Prefer extending the current package/provider boundary rather than stuffing more geometry calculations into CirSim or the board renderer.

## PRODUCTION PACKAGES THAT MUST BE COVERED

At minimum, the four current production package families must have authored package-aware geometry:

1. RESISTOR_AXIAL
2. LED_RADIAL
3. ELECTROLYTIC_RADIAL
4. CONNECTOR_2P

All other current registered packages/canary packages must also produce a valid geometry contract so the registry remains internally consistent.

Developer-only canary packages do not need elaborate artwork, but they must participate correctly in the geometry/verifier model.

## REQUIRED IMPLEMENTATION

### 1. AUTHORITATIVE PACKAGE GEOMETRY CONTRACT

Introduce or extend an appropriate immutable geometry abstraction owned by the physical-package / footprint / render-provider layer.

For each package, the authoritative geometry must expose or deterministically resolve:

- terminal/pad coordinates
- pad hit regions where appropriate
- body bounds
- lead geometry or lead bounds
- routing courtyard / keepout
- selection envelope
- drag interaction envelope if separate from selection
- probe interaction envelope

Use one explicit package-local coordinate system.

Placement on the PCB must transform that authoritative local geometry into board coordinates.

Do not duplicate magic pad offsets independently in:

- renderer
- router
- selection code
- probe code

If two subsystems need the same physical coordinate, they must ultimately derive it from the same authoritative package geometry.

### 2. PRESERVE PROVIDER OWNERSHIP

Keep responsibilities clean:

Physical package / footprint layer:
- authoritative physical geometry and package contract

Render provider:
- package-specific visual body detail
- lead/body/silkscreen artwork

Board renderer:
- consumes authoritative placement and geometry
- invokes/render providers
- does not invent alternative package dimensions

Router:
- may consume authoritative routing courtyard / keepout geometry
- must NOT own component artwork

Interaction system:
- consumes authoritative selection/probe envelopes

Do not move renderer-specific drawing logic into the router.
Do not move electrical behavior into physical geometry classes.

### 3. BODY / LEAD / SILKSCREEN CONTAINMENT

The rendered geometry of each production package must be compatible with its declared physical contract.

At minimum verify that:

- body geometry fits within the package/courtyard contract
- lead geometry reaches the intended pads
- leads do not use unrelated hard-coded pad coordinates
- silkscreen/body decoration does not imply a radically different physical position from the actual footprint
- pad locations remain the actual electrical terminal locations

A future artwork modification that puts a resistor body 40 pixels away from its real pads must fail deterministic verification instead of silently creating a graphical/electrical disagreement.

### 4. AUTHORED INTERACTION ENVELOPES

Update component selection and dragging so supported generated-board parts use the package's authored interaction envelope rather than a generic package rectangle wherever practical.

Update probe hit testing so package-aware pad/probe geometry is authoritative.

Requirements:

- visible component body should be selectable where expected
- selectable space must remain reasonably close to the visible body
- probe hit areas must remain reasonably close to the actual visible pad/lead location
- invisible giant clickable rectangles are not acceptable
- clicking near one pad must not silently resolve to an unrelated terminal
- probing a pad must still resolve to the same stable BoardPad ID and electrical binding as before
- selection geometry must not change electrical identity

A clearly documented generic fallback may remain only for genuinely unsupported/legacy cases.

Current generated production boards must not depend on a generic fallback for the four required production package families.

### 5. ROUTING COURTYARD / KEEPOUT CONTRACT

Expose package routing courtyard / keepout information in a form that the router can consume.

Task 43 should establish the contract and validate it.

Do NOT implement Task 44's routing corridor policy, obstacle negotiation, or unrouted-net failure behavior yet.

Do not substantially rewrite the router merely to consume this data.

Existing routing behavior should remain stable unless a currently incorrect component/pad coordinate must be corrected.

### 6. STABLE IDENTITY MUST NOT CHANGE

This is physical geometry work only.

Preserve:

- BoardComponent IDs
- BoardPad IDs
- BoardNet IDs
- GeneratedComponentBindings
- physical slot ownership
- replacement semantics
- probe electrical bindings
- CircuitJS elements/bindings except where a purely geometric endpoint correction is genuinely required

Never use CircuitJS analyzed node numbers as persistent geometry or interaction identity.

CircuitJS remains the electrical source of truth.

### 7. REMOVE / INSTALL / REPLACE MUST STILL WORK

The geometry changes must not break physical modification flows.

Verify current supported behavior including:

- select component
- remove component
- inspect loose component
- reinstall original component
- install catalog replacement
- probe installed component pads
- probe loose-component terminals where currently supported

Task 42's LED fault ownership behavior must remain intact.

Do not special-case Task 42 faults inside the geometry system.

### 8. DETERMINISTIC GEOMETRY VERIFICATION

Add a Task43DeveloperVerifier or equivalent deterministic verifier coverage.

It must inspect the registered package/provider geometry rather than merely checking that classes exist.

For every current registered package, verify appropriate invariants such as:

- valid finite dimensions
- valid nonempty geometry
- expected terminal count
- every terminal has a resolvable pad position
- body bounds are sane
- routing courtyard is sane
- selection envelope is sane
- probe/pad envelopes are sane
- body/lead geometry satisfies declared containment rules
- interaction envelopes are not absurdly displaced from the visible package/pads

The verifier must contain at least one deliberate synthetic BAD geometry canary proving that it detects contract violations.

Examples of intentional verifier failures:

- body placed outside declared courtyard
- terminal/pad placed outside the package contract
- selection envelope displaced far away from body
- probe envelope displaced far away from its terminal
- lead terminating somewhere other than its declared pad

Do not write a verifier that merely confirms the production providers return whatever values they themselves declared. It must enforce meaningful cross-property invariants.

### 9. SEEDED / PLACEMENT STABILITY

Verify that package-local geometry is deterministic.

For identical package + placement inputs, geometry must resolve identically.

Board placement transforms may translate/rotate geometry if supported, but must not alter:

- terminal identity
- relative pad geometry
- package dimensions
- interaction semantics

If current boards do not support rotation, do not invent a major rotation system merely for this task.

## REQUIRED REGRESSION VALIDATION

Run the normal JDK8 build:

`.\scripts\build.ps1`

Run the existing generated-board/seed verification suite.

Run the currently relevant developer verifiers, especially the diagnostic/repair paths touched by Tasks 40-42.

At minimum confirm that the existing generated LED and RC board families still:

- generate deterministically
- render
- route
- select components
- accept probe placement
- remove parts
- reinstall parts
- install replacements
- preserve stable pad identities
- preserve measurement behavior
- complete their existing diagnostic/repair verification

Task 42 LED_OPEN must still pass its explicit forced diagnostic/repair route.

The new geometry verifier must also pass.

## BROWSER / PRODUCTION UI SMOKE TEST

Use the real production board UI, not only internal object tests.

Exercise representative generated LED and RC boards.

Visually/behaviorally confirm:

- resistor body aligns with its pads/leads
- LED body aligns with its pads/leads
- electrolytic body aligns with its pads/leads
- connector body aligns with its terminals
- traces terminate at the intended visible pads
- selecting the visible body selects the intended part
- dragging/selecting does not depend on a large invisible offset box
- probing visible pads resolves to the intended terminal
- remove/reinstall/replace still operates from the visible component
- no obvious artwork jump or trace/pad disconnect was introduced

Record specific seeds and observations in CODEX_TASK_REPORT.md.

## ARCHITECTURAL CONSTRAINTS

Preserve all existing project rules, particularly:

- CircuitJS remains simulation source of truth.
- Stable board identities never use analyzed solver node numbers.
- Physical package geometry must remain separate from electrical simulation behavior.
- GeneratedBoardInstance remains family-agnostic.
- Family-specific behavior stays with its family.
- External simulation infrastructure is not a PCB component.
- Seeded generation remains reproducible.
- Avoid broad upstream CircuitJS refactors.
- Keep this incremental and modular.

## NON-GOALS

Do NOT:

- build production/manufacturing DRC
- implement Task 44 routing corridor policy
- implement unrouted-net UI/failure policy
- replace detailed vector component rendering with generic rectangles
- change component electrical models
- change generated fault behavior
- change meter electrical behavior
- add new component families
- implement SMD
- implement multilayer routing
- redesign the PCB UI
- perform a large CirSim refactor

## ACCEPTANCE CRITERIA

Task 43 is complete only if:

1. RESISTOR_AXIAL, LED_RADIAL, ELECTROLYTIC_RADIAL, and CONNECTOR_2P expose package-aware authoritative geometry covering:
   - pads
   - body
   - leads
   - routing courtyard/keepout
   - selection envelope
   - probe envelope

2. All current registered packages have a valid geometry contract.

3. Renderer, interaction logic, and future router consumers derive physical coordinates from that authoritative contract rather than independently duplicated generic rectangles/offsets.

4. Body/lead geometry is validated against the package/courtyard contract.

5. Selection and probe areas remain physically close to the visible body/pads.

6. Deterministic verifier coverage catches deliberately malformed package/interaction geometry.

7. Existing generated LED and RC boards still render, route, select, probe, remove, reinstall, replace, inspect, and diagnose correctly.

8. Task 42 LED_OPEN electrical ownership and repair behavior remains unchanged.

9. All required builds/verifiers pass.

10. No Task 44 routing policy has been implemented prematurely.

## TASK COMPLETION PROTOCOL

When implementation is complete:

1. Run all required builds and verifiers.
2. Perform the production UI smoke tests above.
3. Inspect git diff and git status carefully.
4. Overwrite docs/CODEX_TASK_REPORT.md with:
   - task
   - summary
   - architectural decisions
   - geometry contract introduced/extended
   - packages covered
   - files changed
   - validation commands/results
   - tested seeds
   - production UI observations
   - verifier canaries used
   - known limitations/concerns
   - recommended next step
   - intended commit message
5. Update docs/ROADMAP.md to mark Task 43 complete only if all acceptance criteria genuinely pass.
6. Preserve Task 44 as the next roadmap task:
   "Routing Keepouts, Corridor Policy, and Unrouted-Net Failure Contract"
7. Stage only intended changes.
8. Run:
   `git diff --cached --check`
9. Commit with a concise descriptive message.
10. Push the completed task commit to the current remote working branch.
11. Verify the remote branch points at the exact completed commit.
12. Do not begin Task 44 or any later roadmap milestone.

Standing project rule: after every successfully completed future task, commit and push the validated task unless the user explicitly instructs otherwise. A task is not considered fully complete until the push has been verified.

If any required validation fails:
- do not commit
- do not push
- leave the worktree intact
- report the failure precisely in your final response
- do not claim Task 43 complete.
