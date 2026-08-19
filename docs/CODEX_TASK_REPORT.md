# Task 38 Correction Report — NMOS Physical Gate Topology

Date: 2026-08-18

Correction baseline: `ee310d4b6bc0ed1dfeccb6a4408772075fefe9f9`
(`Task 38: add NMOS low-side switch family`, `master`).

Scope: bounded Task 38 correction only. The concurrent UI-shell worktree and
component-visual-realism research worktree were left untouched. No
presentation files, Task 39 files, or research report changes are included.

## Summary and decision

The post-commit review overturned the earlier Task 38 `FINAL PASS`: the
player-visible NMOS PCB declared separate control/gate physical nets and
rendered pseudo-headers even though CircuitJS silently bridged the same
conductor with WireElm/control-command infrastructure. The old board could
therefore show an apparently disconnected control region while the solver
treated it as connected.

This correction moves the command abstraction outside the physical board
boundary, makes the player-visible board truth match the solver graph, removes
TP1/TP2, fixes the original Q1 fault-switch attachment boundary, and reroutes
the compact one-sided layout around the drain trace.

Primary architect final result: `FINAL PASS` for the correction. The correction
is committed in the current history and is being published at the user's
request. Task 39 is the next eligible milestone and was not started.

## Corrected electrical and physical boundary

- CircuitJS remains the electrical source of truth. External control
  infrastructure contains `controlCommand`; the physical boundary begins at
  its commanded output: `external control infrastructure -> J2.1 -> real
  board gate net -> Q1.G`, with `RPD` from that same board net to ground.
- `J2.1`, `RPD.1`, and `Q1.G` share one physical `CONTROL_INPUT` BoardNet.
  `LOAD_SUPPLY`, `LOAD_NODE`, `DRAIN`, and `GND` remain distinct physical nets.
  There is no `GATE_DRIVE` or `GATE` BoardNet and no TP1/TP2 component or pad.
- Board power OFF still isolates both load and control supplies. The real
  100 kOhm pull-down holds the board control node low.
- The NMOS binding remains post 0 = gate, post 1 = source, post 2 = drain,
  with three posts, default body diode, and physical package order G/D/S.
- D-S-open and gate-open board attachments now terminate at the selected
  fault binding's public switch posts. There is no direct board-to-raw-Q1
  bypass. D-S-short retains its solver-visible private board-path switch and
  original-part ownership/isolation behavior.

## Implementation areas

- `NmosLowSideSwitchGenerator.java`: corrected control boundary, collapsed
  board net identity, removed pseudo-headers, and corrected fault-switch
  attachment endpoints.
- `NmosLowSideSwitchPcbLayoutFactory.java`: removed the fake-header area,
  added visible J2.1-rooted branches to RPD.1/Q1.G, and routed the gate branch
  around the DRAIN corridor for seeds 0–3.
- `NmosLowSideSwitchGeneratedBoardValidator.java` and
  `NmosLowSideSwitchDeveloperVerifier.java`: added family-scoped live canaries
  for BoardNet identity, solver voltage agreement, ON/OFF behavior, power-off
  isolation, pseudo-header absence, and visible copper continuity.
- `PcbLayoutDeveloperVerifier.java`: added NMOS control-routing and obsolete
  pseudo-geometry checks while preserving provider parity and global layout
  validation.
- `docs/ARCHITECTURE.md` and `docs/ROADMAP.md`: documented the corrected
  physical boundary, the overturned prior pass, and final validation.

## Validation evidence

### Builds and static checks

- JDK 8/GWT PRETTY production build passed all five permutations, compilation,
  and linking after the final route correction.
- JDK 8/GWT OBF production build passed all five permutations, compilation,
  and linking after the final route correction.
- `PASS:renderer-provider-boundary`.
- `PASS:verify-browser-parser`.
- `git diff --check` passed.
- A read-only route sweep found no unrelated CONTROL_INPUT/DRAIN crossings for
  seeds 0, 1, 2, or 3; the layout verifier also passed endpoint escapes,
  clearance, keepouts, route quality, determinism, and tray separation.

### Visible in-app Browser acceptance

The required visible local Browser was used with real navigation, clicks,
left/right probe placement, power control, component selection, removal, and
catalog replacement:

- Corrected seed 1 rendered the symptom-only stuck-active complaint, visible
  copper from J2 into the RPD/Q1 gate network, and Q1 G/D/S pads with no TP1 or
  TP2 pseudo-headers.
- In the D-S-short low-control case, a real DC V measurement showed `0 V`
  from Q1.G to Q1.S and `0 V` from J2.1 to J2.2 while the load remained
  active. This directly exercises the corrected physical control boundary.
- Corrected seed 0 rendered the symptom-only no-light complaint; a real DC V
  gate/source probe showed `5 V` with board power ON.
- With board power OFF, Q1 was visibly selected with G/D/S lead labels,
  removed through the workbench control, and shown in the parts tray.
- The catalog NMOS was installed visibly, board power was restored, and the
  Service Ticket changed to `Repair verified. The controlled load switches
  normally.`
- Visible developer routes returned `PASS:nmos` for seeds 0, 1, and 2 and
  `PASS:layout`, `PASS:npn`, and `PASS:quick-play` for the corresponding
  regression routes.
- Explicit visible NMOS Quick Play routes returned `PASS:quick-play` for
  seeds 0, 1, and 2. The family report was
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`.

Fresh screenshots are stored in
`docs/task-evidence/task-38-correction/`:

- `01-corrected-nmos-board-complaint.png`
- `02-ds-short-gate-source-0v.png`
- `03-ds-short-j2-0v.png`
- `04-gate-source-5v.png`
- `05-q1-removed-power-off.png`
- `06-repair-verified.png`

The standalone `scripts/verify-browser.ps1 -Nmos -Seeds 0,1,2` harness was
attempted but is unavailable in this environment: its Edge process query
returns WMI/CIM `Access denied` before route execution. It is recorded as
unavailable, not as a product pass. The visible in-app Browser routes above
are the player-facing evidence.

## Review protocol

- Coder Hubble implemented the correction and two targeted follow-up rounds;
  all work remained uncommitted and unpushed during delegation.
- Reviewer Hume first found a real FAIL: D-S-open and gate-open attachments
  bypassed or failed to touch their fault switches. The coder corrected the
  endpoints to the fault binding public posts; Hume then returned PASS.
- Primary visible validation found a second real blocker: the first corrected
  layout crossed CONTROL_INPUT and DRAIN for seed 1. The coder rerouted the
  gate branch; Hume independently rechecked seeds 0–3 and returned PASS.
- No escalation architect was required.

## Known limitations

- The standalone Edge/WMI harness remains unavailable under the current
  permission profile.
- Existing compact component-rendering fidelity remains bounded by the
  current renderer and is outside this electrical/topology correction.
- The player UI has no direct control-toggle widget; ON/OFF control-state
  proof is provided by the solver-backed family canary and the visible
  D-S-short low-control measurements.

## Final boundary

Only this bounded Task 38 correction is included. Task 39 relay-driver work,
UI-shell work, and component-visual-realism research were not integrated.

Correction commit message:

`Task 38: fix NMOS physical gate topology`

## Integration and publication addendum — 2026-08-18

The historical Task 38 correction record above predates the repository
integration requested after that correction. Its statements about the UI and
research branches being out of scope describe the earlier correction only.
This addendum is the current integration handoff record.

### Baseline and integrated history

- Clean integration baseline: `8c25f1abcbb61e1ba9430017031cfdd07671bf6f`
  (`origin/master`).
- Integrated `codex/component-visual-audit` at `f5407b476955672ae7181971b440d2e38a5e6801` in merge `9d47440`.
- Integrated `codex/pcb-routing-scalability-audit` at `0681cc61d2fb92d06f8c1f0a571a4160eeded824` in merge `41bd56e`.
- Integrated `codex/procedural-generation-audit` at `1eebf1a9bd7dc2ab3aac5f4278d4ae8eba03f989` in merge `6b2b065`.
- Integrated `codex/troubleshooting-realism-audit` at `1875234c46b058b9400e86ad5716d7f536f12cf7` in merge `8d828c5`.
- Integrated `codex/ui-workbench-shell` at `7bbb9de591155da0db0c173cb26c5e621a622c6a` in merge `4a1b56b`.
- `codex/prompt-dropbox` was explicitly excluded and is not an ancestor of the
  integrated history. No tracked `.worktrees` artifacts were included.

Each audit branch contributed exactly its historical report under
`docs/research/`; the four report blobs were verified unchanged against their
source branches. The UI merge retained the current master NMOS implementation.
Its `CirSim.java` change is limited to the workbench overlay hook (9 additions,
0 deletions), with the HTML include and the branch's CSS/JS assets preserved.

### Push-policy update

`AGENTS.md` now gives delegated coders, reviewers, and escalation architects no
independent push authority. The primary architect may push only the final
accepted result after review, validation, status/diff inspection, intentional
staging, cached diff checks, and commit. Failed, intermediate, unreviewed,
unvalidated, unrelated, or unfinished states must not be pushed; routine force
pushes remain prohibited without specific authorization. The completion
lifecycle now verifies branch, upstream, remote, and SHA before and after the
authorized push, and the completion report records those facts.

### Final integration validation

- JDK 8/GWT PRETTY and OBF production builds passed all five permutations and
  linking.
- Renderer-provider boundary and bundled UI JavaScript static checks passed;
  `git diff --check` passed.
- NPN developer verification passed for seeds 0–3. Natural NMOS verification
  passed for seeds 0–2, including the forced D-S-short case. Architecture and
  layout/geometry verification passed.
- NMOS Quick Play verification passed for seeds 0–2 with the report
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`.
- The visible in-app Browser showed the workbench on load with TOOLS selected,
  the SHOP mock store, RESOURCES and SETTINGS placeholders, overlay close and
  keyboard isolation, a real DC measurement after close, component selection,
  removal and replacement, board power control, NMOS Quick Play, and repair
  verification. The mock store changed only its local cart/selection state and
  did not mutate the board.
- The normal-player NMOS flow visibly repaired the board and reported
  `Repair verified. The controlled load switches normally.` The D-S-short,
  low-control flow produced real `0 V` gate/source and J2.1/J2.2 readings.

Fresh Browser evidence is stored in
`docs/task-evidence/integration-ui/`:

- `initial-workbench.png`
- `mock-parts-store.png`
- `nmos-complaint.png`
- `nmos-low-control-0v.png`
- `nmos-repaired.png`

The repository's standalone preview launcher could not perform its WMI/CIM
process check under the current permission profile (`Access denied`). The
project preview itself was started directly and the required visible in-app
Browser flows and read-only verifier routes completed. The WMI limitation is a
harness/environment limitation, not a product pass or failure.

### Review and completion state

- Coder Peirce changed only `AGENTS.md`, returned without staging, committing,
  or pushing, and passed its diff checks.
- Reviewer Copernicus performed the independent integration review and returned
  `PASS`. The primary architect independently reviewed the final tree and
  returned `FINAL PASS`. No escalation architect was required.
- Task 38 remains the accepted completed milestone. Task 39 (Relay Driver) is
  the next eligible roadmap milestone and was not started.

Final accepted integration commit SHA, push result, remote, and branch/upstream
are recorded below after the final commit and remote verification:

- Final accepted integration commit SHA: `32940dc4c9de773eb4a7af663b1889d122bb1a3c`.
- Push result: `succeeded; the accepted integration commit was pushed to
  `origin/master` as part of the final fast-forward publication`
- Remote: `origin`
- Branch/upstream: `master` -> `origin/master`
