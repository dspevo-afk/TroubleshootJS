# Task 38 Report — NMOS Low-Side Switch Family

Date: 2026-08-18

Baseline: `ef778563ea48f3a5b2db3bf6eca1ff69cafa4c31` (`master`).

Scope: Task 38 only. The concurrent UI worktree and component-visual research
worktree were left untouched; no presentation files were changed.

## Summary and decision

Task 38 adds a solver-backed `NMOS_LOW_SIDE_SWITCH` family. The family uses a
real `NMosfetElm`, independent load/control supplies, a 100 kOhm gate
pull-down, stable board nets and G/D/S pads, typed physical NMOS identity,
provider-owned PCB geometry, solver-backed faults, normal-player scenarios,
Quick Play registration, and generic live repair completion.

The earlier primary `FINAL PASS` for commit `ee310d4` was overturned by
post-commit visible review: the player-visible PCB control/gate connectivity
did not match the solver graph. This uncommitted correction narrows the
physical boundary and is returned for primary architect re-review. No push or
new commit is performed. Task 39 is identified as the next eligible milestone
and was not started.

## Electrical and architectural decisions

- The permanent CircuitJS binding is post 0 = gate, post 1 = source, post 2 =
  drain. The generated NMOS remains three-post with the default body diode
  enabled and no body terminal.
- External control infrastructure owns the command switch. The physical path
  is `external control infrastructure -> J2.1 -> CONTROL_INPUT -> Q1.G/RPD.1`;
  J2.1 is the board-side commanded voltage after `controlCommand`. The stable
  nets are `LOAD_SUPPLY`, `CONTROL_INPUT`, `LOAD_NODE`, `DRAIN`, and `GND`;
  J2.1, RPD.1, and Q1.G share `CONTROL_INPUT`, with no `GATE_DRIVE`, `GATE`,
  TP1, or TP2 physical identity.
- Healthy proof measures live VGS, VDS, load/LED current, supply/control
  voltage, and CircuitJS gate terminal current. Gate current remains within
  the high-impedance tolerance.
- Q1 owns D-S open, D-S short, and gate-path-open effects. D-S short is a real
  0.1-ohm shunt with a solver-visible series private board-path switch. The
  original fault graph stays attached to the loose/reinstalled original; a
  catalog replacement opens that original board path and receives a distinct
  fault-free `NMosfetElm` backing.
- Physical terminal order is G/D/S and is explicitly translated to solver
  order G/S/D. No BJT base/collector/emitter behavior or fake meter values was
  added. No new meter mode was added.
- Quick Play appends NMOS at family index 5 and preserves earlier indices. Its
  normal-player seed envelope is `{0, 1, 2}`, naturally reaching D-S open,
  D-S short, and gate open.

## Implementation areas

- NMOS generator, family state, validators, scenario compatibility/presentation,
  fault effects, and developer verifier.
- Typed specification/catalog/physical part, G/D/S package, installed/loose
  renderer/probe target, replaceable Q1 slot, and distinct catalog backing
  allocator.
- Registered provider-owned TO-92-style NMOS footprint and compact one-sided
  routed layout with authoritative Q1 parity checks. Visible CONTROL_INPUT
  copper branches from J2.1 to RPD.1 and Q1.G.
- CirSim challenge/fixture routes, Quick Play registry/verifier, preview and
  browser-verifier routes, and renderer-boundary integration.
- `BoardModificationController.java` has only the harmless final-newline
  normalization reported by review.

## Validation evidence

### Build and static checks

- JDK 8/GWT PRETTY production build after this correction passed all five
  permutations, compilation, and linking:
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style PRETTY`.
- The matching JDK 8/GWT OBF production build also passed all five
  permutations, compilation, and linking:
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`.
- Renderer-provider boundary, browser-script parser, and `git diff --check`
  checks passed.
- Runtime canary execution is pending: the Edge PowerShell harness stops at
  WMI/CIM `Access denied`, and the built-in in-app Browser backend was
  unavailable after reconnect in this correction attempt.

### Visible in-app Browser acceptance

Earlier visible evidence was collected before this correction and does not
prove the corrected PCB routing. During this correction, the local in-app
Browser backend was unavailable and no new visible acceptance claim is made.
The intended acceptance flow remains:

- The rendered board shows a recognizable NMOS and labeled G, D, and S pads;
  the complaint remained symptom-only (`The controlled load does not turn
  on.` / `The controlled load remains on when control is low.`).
- The board was powered off, Q1 was selected and removed, the original appeared
  in the parts tray, the catalog NMOS was installed, and the board was powered
  back on.
- A real left-click/right-click DC probe transaction on Q1 gate/source showed
  `5 V`.
- Unrepaired Quick Play Finish Job visibly reported
  `Functional check failed. Continue troubleshooting.`
- The corrected NMOS D-S-short replacement flow visibly reached
  `Repair verified. The controlled load switches normally.`; the corrected
  Quick Play verifier route also reached the same repaired state.

Evidence images are stored in
`docs/task-evidence/task-38/`:

- `01-nmos-board-complaint.png`
- `02-q1-selected-gds.png`
- `03-nmos-repaired.png`
- `04-nmos-gate-source-probe.png`
- `05-nmos-ds-short-repaired.png`

The standalone Edge PowerShell harness was not a product pass: its Edge
process query failed with WMI/CIM `Access denied` before route execution. The
visible in-app Browser was used for the required player-facing validation.

## Review protocol

- Bounded coder Hubble implemented Task 38 and correction round 1; it remained
  uncommitted and unpushed.
- Independent reviewer Hume returned `PASS` for the initial candidate and
  `PASS` again after the D-S-short replacement-path correction.
- Post-commit primary visible review found a new real blocker: the player-visible
  PCB control/gate connectivity did not match the solver graph. The correction
  moves the command boundary outside the board, collapses the physical gate
  path to `CONTROL_INPUT`, removes TP1/TP2, and adds a family canary for solver
  voltage agreement and visible copper continuity. Primary re-review remains
  pending.

## Known limitations

- The standalone Edge/WMI harness remains unavailable under the current
  permission profile; this is recorded as unavailable, not as passing.
- Existing compact component-rendering fidelity remains bounded by the current
  renderer and is outside Task 38's electrical scope.
- The in-app Browser backend was unavailable during this correction attempt;
  visible player-flow revalidation is a handoff requirement for the primary
  reviewer. The existing player UI still has no direct control-toggle widget;
  live ON/OFF behavior is verified through the solver-backed family state and
  developer boundaries.

## Final boundary

Only Task 38 work is included. Task 39 relay-driver work, UI-shell work, and
component-visual-realism research were not integrated.

Commit message:

`Task 38: add NMOS low-side switch family`
