# Task 37 Correction Report — NPN Silkscreen Truth and Repair-State Preservation

Date: 2026-08-18

Original Task 37 commit: `7194bd19a2b0eb1ca95eba9e4e457438a277bb9`

Correction baseline in this checkout: `8752612` (`Task 37: fix NPN player truth and routing`)

Scope: Task 37 only. Task 38/NMOS was not started.

## Summary and decision

`FINAL PASS`. This correction closes two Task 37 proof gaps:

1. The developer verifier now proves that NPN J1.1/J2.1 raw and rendered
   silkscreen text follows the generated physical power-input nameplates for
   ordinary Quick Play seeds 0, 1, 2, and 3. Production label ownership remains
   `NpnLowSideSwitchGenerator -> BoardPhysicalSpecifications ->
   PowerInputNameplate -> NpnLowSideSwitchPcbLayoutFactory/renderer`; no seed
   mapping was duplicated in production layout code.
2. `getRepairStatus()` now has focused live-state preservation coverage. The
   real CircuitJS ON/OFF functional test still runs, and the prior command is
   restored through the family-state switch path on every exit, including early
   precondition returns. The verifier compares live control voltage, load/base/
   collector currents, and collector voltage before and after faulted/wrong and
   correctly repaired status queries.

The latest user instruction authorizes pushing after the final commit. The
correction is therefore intended to land as one new local commit and then be
pushed on the existing `codex/task-37-npn-player-truth-routing` branch.

## Acceptance criteria and implementation

### Silkscreen/nameplate truth

`NpnLowSideSwitchPcbLayoutFactory` already consumed the authoritative
`BoardPhysicalSpecifications` from the prior correction. This task adds the
missing focused proof rather than adding a second value source. For each seed,
`NpnLowSideSwitchDeveloperVerifier.verifyDeterministicNameplateEnvelope()` uses
the ordinary `QuickPlaySelector` and `selector.generate()`, reads the generated
physical nameplates and actual `PcbSilkscreenLabel` objects, then compares both
raw text and renderer-targeted text.

| Seed | LOAD_VIN_INPUT nameplate | J1.1 text | CONTROL_VIN_INPUT nameplate | J2.1 text |
| ---: | ---: | --- | ---: | --- |
| 0 | 9 V | `+9V` | 5 V | `+5V` |
| 1 | 12 V | `+12V` | 5 V | `+5V` |
| 2 | 5 V | `+5V` | 5 V | `+5V` |
| 3 | 9 V | `+9V` | 5 V | `+5V` |

The normal-player NPN envelope remains 0, 2, and 3; seed 1 is included only
for the deterministic boundary proof and is not added to the legacy-family
envelope. No fault, answer, seed, or developer report metadata is added to
normal player UI.

### Repair-status state preservation

`NpnLowSideSwitchRepairValidator.getRepairStatus()` captures the current
command before its precondition gates, runs the real healthy ON and OFF
CircuitJS profile, and restores the prior command in `finally` through
`NpnLowSideSwitchFamilyState.setCommandedOn(CircuitElm.sim, ...)`. The early
non-powered, overlay, incomplete-modification, and incomplete-installation
returns are inside that `try/finally`, so they cannot skip restoration.

The verifier covers these meaningful states across the forced NPN routes:

| Scenario | Entry command | Live entry expectation | Status proof |
| --- | --- | --- | --- |
| C-E open, faulted/wrong replacement | ON/high | control about 5 V, load inactive, base drive present | non-success and all five live observations unchanged |
| C-E short, faulted/wrong replacement | OFF/low | control about 0 V, load active, collector near ground | non-success and all five live observations unchanged |
| C-E open, correct replacement | ON/high | healthy load/base/collector behavior | `CORRECTLY_RESTORED`, command and live state unchanged |
| C-E short, correct replacement | OFF/low | healthy load/base-off behavior | `CORRECTLY_RESTORED`, command and live state unchanged |

The five live observations are J2.1-J2.2 control voltage, RLOAD load current,
base current, collector current, and Q1.C-Q1.E collector voltage. Wrong
replacement setup also synchronizes the actual control switch through the same
solver-backed family-state method before taking its baseline; it does not only
assign the boolean command field.

## Files changed

- `src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchDeveloperVerifier.java`
  - ordinary generated seed/nameplate assertions;
  - faulted and repaired live-state snapshots;
  - solver-backed wrong-replacement setup synchronization;
  - CE-open/CE-short command-state coverage.
- `src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchRepairValidator.java`
  - moves precondition exits inside the state-restoring `try/finally` while
    preserving the functional ON/OFF proof.
- `docs/ARCHITECTURE.md`
  - documents the final state-restoration and generated-nameplate proof seam.
- `docs/ROADMAP.md`
  - records the accepted Task 37 correction while keeping Task 38 next and
    unstarted.
- `docs/task-evidence/task-37-correction/npn-seed2-final-normal-silkscreen.png`
  - rebuilt normal-player seed-2 (+5 V) board evidence.

## Validation evidence

### Production/build and source checks

- JDK 8/GWT production build:
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`
  passed all five permutations, compilation, and linking.
- `scripts/verify-renderer-boundary.ps1`: `PASS:renderer-provider-boundary`.
- `scripts/verify-browser.ps1` PowerShell AST parse: `PASS:verify-browser-parser`.
- Final source diff checks were clean before staging; staged diff check is run
  as part of completion.

### Visible in-app Browser

The rebuilt local production preview was opened in the visible Codex in-app
Browser. Normal-player seed 2 visibly showed `+5V` at J1.1 and J2.1, `GND` at
J1.2 and J2.2, the real complaint, board power, and an empty parts tray. The
normal page had no developer electrical-report attribute. The screenshot is
stored in the evidence directory above.

Developer routes were exercised through the same visible Browser after the
production rebuild:

- Natural NPN seeds 0, 1, 2, and 3: each `PASS:npn`, with reports showing
  `+9V`, `+12V`, `+5V`, and `+9V` load labels respectively and `+5V` control.
- Forced matrix for seeds 0, 2, and 3 crossed with all four NPN faults:
  `12/12` `PASS:npn` routes.
- Ordinary Quick Play NPN seed 1: `PASS:quick-play`; report
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`;
  normal-player privacy remained clean and `Finish Job` remained generic.
- Architecture: `PASS:architecture`.
- PCB layout: `PASS:layout`.
- RC: `PASS:rc`.
- Stored energy: `PASS:stored-energy`.
- LED: `PASS:resistance`.
- Diode: `PASS:diode`.
- Parallel: `PASS:parallel`.

One in-app Browser route read encountered a transient CDP timeout/closed-tab
condition during the adjacent regression pass. The page had loaded; a fresh
visible tab and separate stabilized reads completed the affected checks. This
was a browser-harness interruption, not a product assertion failure.

The standalone PowerShell Edge harness was not reported as passing. Its host
process inspection encountered WMI/CIM `Access denied` before route execution;
the visible in-app Browser was used for the required player-facing and route
evidence instead.

## Coder and reviewer protocol

- Coder Galileo delivered the first bounded candidate with generated
  nameplate checks and solver-state observations.
- Fresh reviewer Poincare returned `FAIL` with a `BLOCKER`: wrong-replacement
  checks lacked live before/after observations.
- Coder James added the five live observations around wrong-replacement status.
- Fresh reviewer Carson returned `FAIL` with a `BLOCKER`: the verifier setup
  had synchronized only bookkeeping, not the actual CircuitJS switch.
- Coder Hooke corrected setup by calling `setCommandedOn(sim, ...)` before the
  wrong-replacement baseline.
- Fresh reviewer Raman independently returned `PASS` after inspecting the
  corrected diff and validation evidence.
- Primary architect review rounds: three candidate reviews, including two
  blocker correction rounds.
- Escalation architect: not required.
- Primary architect final result: `FINAL PASS`.

## Known limitations

The compact TO-92 renderer still uses the existing physical `NPN` body and
B/C/E markings. That is previously documented physical-fidelity debt and is
outside this state/nameplate correction. It does not replace the electrical
model, nameplate ownership, or solver-backed measurement behavior.

## Final boundary

Only this Task 37 correction is included. Task 38/NMOS was not started. The
commit message is:

`Task 37: fix NPN board state and labels`

The latest user instruction authorizes pushing this one correction commit after
the final checks; no further milestone work is permitted in this task.
