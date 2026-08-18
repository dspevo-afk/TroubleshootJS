# Task 37 — NPN Low-Side Switch Family + Parts Tray Layout Correction

## Final disposition

Status: `FINAL PASS`

Task 37 is complete. Task 38 — NMOS Low-Side Switch Family is the next
eligible roadmap milestone and was not started.

The implementation remains solver-backed: player actions mutate the generated
CircuitJS graph, CircuitJS solves the result, and the meter, behavior, and
repair validators consume live electrical state. No hard-coded meter readings,
fault-identity completion shortcut, or Q1-specific Finish Job exception was
added.

## Implemented scope

- Added the seeded `NPN_LOW_SIDE_SWITCH` family with separate load and control
  supplies.
- Preserved stable logical nets: `LOAD_SUPPLY`, `CONTROL_INPUT`, `BASE`,
  `LOAD_NODE`, `COLLECTOR`, and `GND`.
- Used a real CircuitJS `NTransistorElm` with explicit post mapping:
  post 0 = base, post 1 = collector, post 2 = emitter.
- Added solver-backed faults: Q1 C-E open, Q1 C-E short, base resistor open,
  and load-path open.
- Added immutable TO-92 NPN specification/nameplate/physical identity,
  provider-owned footprint/render/probe geometry, loose-part inspection, and
  replaceable Q1 lifecycle.
- Original Q1 removal/reinstallation retains its private generated fault;
  catalog replacements allocate a distinct live transistor backing and do not
  inherit that fault.
- Added generic solver-backed NPN healthy/faulted/repaired validators and
  normal Quick Play registry support.
- Added a generic `PcbBoardLayout` board/tray disjointness invariant and
  corrected fixed RC, seeded LED/diode/parallel, and NPN layouts.
- Added exact Q1 parity verification against the registered TO-92 footprint,
  including placement, body, courtyard, pad coordinates, escape vectors,
  escape lengths, and B/C/E ordering.

## Architecture decisions

- CircuitJS remains the electrical source of truth; the PCB layout never owns
  electrical behavior.
- Stable board/component/pad/net IDs remain separate from analyzed solver node
  numbers.
- Fault infrastructure is private to the original physical part and detaches
  with that part; replacements have independent identity and backing.
- Provider registries own TO-92 footprint, installed/loose rendering, and
  probe geometry. The NPN family factory consumes the provider rather than
  duplicating package geometry.
- Generic repair completion requires the live board to switch correctly in
  both command states with no active overlay, unaddressed modification, or
  remaining installed fault.
- The parts tray is workbench chrome outside the board outline. Shared layout
  validation rejects tray/board intersection, while compaction excludes tray
  chrome from the board outline calculation.

## Files and areas changed

New NPN foundation/family files:

`DynamicNpnBackingAllocator.java`, `NpnCatalogEntry.java`,
`NpnComponentSlot.java`, `NpnLowSideSwitchDeveloperVerifier.java`,
`NpnLowSideSwitchFamilyState.java`, `NpnLowSideSwitchFaultValidator.java`,
`NpnLowSideSwitchGeneratedBoardValidator.java`,
`NpnLowSideSwitchGenerator.java`, `NpnLowSideSwitchPcbLayoutFactory.java`,
`NpnLowSideSwitchRepairValidator.java`, `NpnPartLocation.java`,
`NpnReplacementCatalog.java`, `NpnSlotController.java`,
`NpnSpecification.java`, `PhysicalNpnPart.java`,
`PhysicalNpnPartProbeTarget.java`, and `ReplaceableNpnBoardCapability.java`.

Existing integration areas:

- CircuitJS generated-board routing and developer verification in `CirSim.java`.
- Generated fault effects/types/scenarios and component connection ownership.
- Physical package, footprint, definition, render, and probe registries.
- `PcbBoardLayout`, `PcbLayoutDeveloperVerifier`, seeded layout generation,
  and `RcDelayPcbLayoutFactory`.
- Quick Play registry/verifier and browser/renderer boundary scripts.

No files were removed. No unrelated roadmap family was implemented.

## Validation evidence

### Automated/build validation

- `& .\scripts\build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`
  — PASS; all five JDK 8/GWT production permutations compiled and linked.
- `& .\scripts\verify-renderer-boundary.ps1` — PASS.
- `git diff --check` — PASS; Git reported only normal LF/CRLF conversion
  notices.
- In-app Browser NPN matrix — PASS for all 12 combinations:
  `TRANSISTOR_CE_OPEN`, `TRANSISTOR_CE_SHORT`, `BASE_RESISTOR_OPEN`, and
  `LOAD_PATH_OPEN` at seeds 0, 2, and 3.
- In-app Browser `tsjVerifyLayout` — PASS.
- In-app Browser `tsjVerifyArchitecture` — PASS.
- In-app Browser Quick Play selector/NPN Finish Job route — `PASS:quick-play`.
- In-app Browser RC plus stored-energy route — `PASS:rc`.
- In-app Browser LED, diode, and parallel regression routes —
  `PASS:challenge`, `PASS:diode`, and `PASS:parallel`.

The PowerShell Edge harness was attempted with
`& .\scripts\verify-browser.ps1 -Npn -Seeds @(0,2,3)`, but this environment
denied its WMI Edge-process query (`Access denied`). This did not weaken the
assertions: the equivalent developer routes were run through the required
visible in-app Browser, and the browser returned the verifier attributes listed
above. `start-preview.ps1` has the same WMI limitation, so the local production
preview was started directly with the existing `preview.ps1` server.

### Visible in-app Browser validation

The built-in Browser was made visible and used for real player interactions.
No DOM mutation, JavaScript-triggered click, synthetic event, or Windows
desktop automation was used as a substitute.

Directly observed on the NPN challenge:

1. Vague service complaint: “The controlled load does not switch on.”
2. Real PCB with visible TO-92-style Q1, B/C/E markings, copper traces, and a
   parts tray outside the board.
3. DC voltage mode selected through the visible meter button.
4. Real left/right probe placement on control/ground and load/ground points;
   the control probe displayed `5 V` and the faulted load probe remained
   unresolved, matching the open-switch symptom.
5. Board power switched off before modification.
6. Q1 selected, removed, shown as `Q1_ORIGINAL - Generic NPN transistor` in
   the visible tray, and shown as `State: Loose`.
7. Catalog NPN replacement installed while the original remained in the tray.
8. Board powered back on; the complaint changed to “Repair verified. The
   controlled load switches normally.”

Normal Quick Play was reloaded visibly. The fresh session showed a new RC
  challenge with a visible `Finish Job` control and a tray outside the board;
  no prior NPN modifications persisted. The RC tray separation was also
  inspected in that fresh screenshot.

Evidence files, captured from the final production preview and visually
inspected as nonblank application states:

- `docs/task-evidence/task-37/npn-initial.png` — initial NPN complaint,
  board, TO-92 Q1, and tray outside the board.
- `docs/task-evidence/task-37/npn-probed.png` — DC voltage mode with visible
  red/black probe markers and the control/load measurement interaction.
- `docs/task-evidence/task-37/npn-loose-part-tray.png` — power-off removed Q1,
  selected loose original, visible `State: Loose`, and tray.
- `docs/task-evidence/task-37/npn-repaired.png` — replacement installed,
  original retained in the tray, board powered, and repair verified.
- `docs/task-evidence/task-37/quickplay-reload.png` — fresh normal Quick Play
  RC board, visible `Finish Job`, and tray outside the board.

## Multi-agent protocol record

- Primary architect read AGENTS.md, ROADMAP.md, ARCHITECTURE.md, and the prior
  task report before implementation and selected only Task 37.
- Coder round 1 returned `COMPLETE` with the candidate uncommitted.
- Reviewer round 1 returned `FAIL` for one substantive provider-ownership
  blocker: Q1 layout had been manually duplicated in the NPN factory.
- Correction round 1 was delegated to the same coder. It routed Q1 through
  the registered TO-92 provider and added exact provider-parity verification;
  coder returned `COMPLETE`.
- Reviewer correction round returned `PASS`.
- Primary architect independently reviewed the corrected diff, ran the final
  build and focused routes, completed visible Browser validation, and returns
  `FINAL PASS`.
- No escalation architect was required. No push was performed.

## Handoff

Roadmap status is updated: Task 37 is complete and Task 38 is identified as
the next eligible milestone only. Task 38 was not started.

Final commit message: `Task 37: add NPN low-side switch family`

The task stops after the single final commit as required.
