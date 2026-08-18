# Task 37 Post-Review Correction Report

Date: 2026-08-18
Base commit: `7194bd1` (`Task 37: add NPN low-side switch family`)
Scope: Task 37 post-review correction only. Task 38/NMOS work was not started.

## Decision

`FINAL PASS`. The correction restores player-visible electrical truth for the
NPN board, keeps connector labels and the electrical graph authoritative,
makes the shared ground routing natural, preserves solver/player state across
instantaneous checks, and proves ordinary Quick Play reachability without
forced developer faults. The implementation, evidence, and this report are
intended to land as one correction commit.

## Findings addressed

1. **Connector labels:** the renderer collapsed multiple positive inputs to
   aggregate `VIN`; targeted positive pads now resolve their own authoritative
   `PowerInputNameplate`, while return pads resolve to `GND`.
2. **Duplicate seed metadata:** the PCB layout no longer maintains a second
   seed-to-nameplate table; it consumes `BoardPhysicalSpecifications`.
3. **Ground routing:** the NPN layout no longer uses the old long detour or
   ambiguous crossing; J1.2 is the shared ground trunk source.
4. **State mutation:** instantaneous compatibility and repair checks capture
   the prior commanded state and restore it in `finally`.
5. **Healthy electrical truth:** the verifier publishes full-precision values
   read from the live CircuitJS solver and checks the component rating envelope.
6. **Seed 1 lifecycle:** the natural C-E-short challenge reaches the real
   remove, loose-measurement, reinstall, replacement, power, and finish path.
7. **Original ownership:** removing and reinstalling the original Q1 preserves
   its generated fault; the fault is not stored as a global or catalog fact.
8. **Replacement identity:** catalog acquisition creates a distinct physical
   part and distinct live `NTransistorElm` backing.
9. **CircuitJS source of truth:** probes, fault symptoms, loose measurements,
   switching behavior, and completion remain solver-backed.
10. **Stable graph identity:** NPN logical nets and Q1 B/C/E pad/post mapping
    remain explicit and stable.
11. **Provider parity:** the verifier compares the registered TO-92 provider
    footprint/body/courtyard/pads/escapes and B/C/E order.
12. **Tray disjointness:** the shared tray calculation and layout invariant
    continue to reject tray/board overlap for fixed and generated boards.
13. **Route quality:** generic validation rejects duplicate points, repeated or
    overlapping segments, non-adjacent self-intersections, and backtracking;
    adjacent continuous/orthogonal segments remain legal.
14. **Ordinary Quick Play:** the verifier uses `QuickPlaySelector` and ordinary
    `selector.generate`, not a forced NPN generator or forced fault route.
15. **Natural fault mapping:** ordinary NPN seeds 0–3 resolve to the documented
    compatible fault envelope below.
16. **Legacy behavior:** legacy Quick Play families retain their validated
    `{0, 2, 3}` seed envelope and existing ordinary generation behavior.
17. **Repair completion:** success is based on live functional ON/OFF behavior,
    not component IDs, catalog IDs, or hidden fault flags.
18. **Generic Finish Job:** the NPN challenge uses the generic completion seam;
    an unrepaired board is blocked and a repaired board passes.
19. **Privacy:** the NPN report and verification attributes are gated to
    developer flags; normal player pages expose no fault/answer/electrical
    report terms.
20. **Natural seeds 0/2/3:** the remaining ordinary NPN seeds pass their live
    healthy/fault/repair checks rather than only the seed 1 scenario.
21. **Instruments:** visible voltage probing and loose Q1 resistance measurement
    use real player interactions and the existing instrument infrastructure.
22. **Player UI:** the normal board shows independent load/control labels and
    the corrected GND tree; compact NPN/B/C/E silkscreen fidelity remains a
    documented future improvement, not a hidden electrical substitute.
23. **Validation:** production build, boundary/parser checks, visible Browser
    routes, natural seed routes, forced matrix, and regression suites were run.
24. **Roadmap boundary:** only this correction was completed; Task 38 remains
    the next eligible milestone and was not begun opportunistically.

## Corrections implemented

- `NpnLowSideSwitchPcbLayoutFactory.create(...)` now receives the authoritative
  `BoardPhysicalSpecifications` and derives J1/J2 labels from
  `LOAD_VIN_INPUT` and `CONTROL_VIN_INPUT` nameplates.
- `PcbWorkbenchRenderer` resolves the targeted positive connector pad to its
  own `ExternalBoardPowerInput` ID and display label. Generic single-input and
  untargeted fallbacks remain intact.
- `NpnLowSideSwitchPcbLayoutFactory` emits stable shared-trunk GND routes:
  J1.2 -> J2.2, J1.2 -> RPD.2, and J1.2 -> Q1.E through the common trunk.
- `PcbBoardLayout.validateRouteQuality` now checks segment topology rather than
  rejecting legitimate adjacent same-net route joins.
- `GeneratedScenarioLibrary.NpnLoadCompatibility` and
  `NpnLowSideSwitchRepairValidator.getRepairStatus` restore the prior
  `commandedOn` state after observational checks.
- `QuickPlayFamilyRegistry` keeps legacy families at `{0, 2, 3}` and gives NPN
  the `{0, 1, 2, 3}` natural envelope.
- `QuickPlayDeveloperVerifier` exercises the ordinary selector/generator path;
  `scripts/verify-browser.ps1 -NpnNatural` covers the four natural routes.
- `CirSim.publishNpnElectricalReportForDeveloperVerification` is gated by
  `tsjVerifyNpn`; the NPN verifier publishes live full-precision solver values
  only for developer verification.

## Natural NPN seed envelope

| Seed | Load input | Control input | Natural fault |
| ---: | ---: | ---: | --- |
| 0 | +9 V | +5 V | `TRANSISTOR_CE_OPEN` |
| 1 | +12 V | +5 V | `TRANSISTOR_CE_SHORT` |
| 2 | +5 V | +5 V | `BASE_RESISTOR_OPEN` |
| 3 | +9 V | +5 V | `LOAD_PATH_OPEN` |

The four routes pass through the ordinary selector and generator. The forced
verification matrix additionally covers all four compatible faults against all
four deterministic seeds: `16/16` pass.

## Healthy electrical envelope

These are the exact developer-only reports from the rebuilt live CircuitJS
solver. `RLOAD` is 330 ohm with a 0.5 W rating on every seed.

```text
seed=0;loadLabel=+9V;loadNominalV=9;controlLabel=+5V;controlNominalV=5;loadSolverV=9;controlSolverV=5;rloadOhms=330;rloadRatingW=0.5;loadCurrentA=0.021484060314946665;ledCurrentA=0.02148406149453198;baseCurrentA=0.004310336164274664;collectorCurrentA=0.021514795157705838;rloadPowerCalculatedW=0.15231639971336786;rloadPowerSolverW=0.15231639971336786
seed=1;loadLabel=+12V;loadNominalV=12;controlLabel=+5V;controlNominalV=5;loadSolverV=12;controlSolverV=5;rloadOhms=330;rloadRatingW=0.5;loadCurrentA=0.03044967496148918;ledCurrentA=0.030449678876901386;baseCurrentA=0.004303254838663356;collectorCurrentA=0.030449675184131785;rloadPowerCalculatedW=0.30597029273591253;rloadPowerSolverW=0.30597029273591253
seed=2;loadLabel=+5V;loadNominalV=5;controlLabel=+5V;controlNominalV=5;loadSolverV=5;controlSolverV=5;rloadOhms=330;rloadRatingW=0.5;loadCurrentA=0.009640270055906957;ledCurrentA=0.009640270080918122;baseCurrentA=0.004323251796209907;collectorCurrentA=0.009639923612649478;rloadPowerCalculatedW=0.030668486227769392;rloadPowerSolverW=0.030668486227769392
seed=3;loadLabel=+9V;loadNominalV=9;controlLabel=+5V;controlNominalV=5;loadSolverV=9;controlSolverV=5;rloadOhms=330;rloadRatingW=0.5;loadCurrentA=0.02148406031494663;ledCurrentA=0.021484061494531827;baseCurrentA=0.004310336164274802;collectorCurrentA=0.021514795157719587;rloadPowerCalculatedW=0.15231639971336736;rloadPowerSolverW=0.15231639971336736
```

The maximum calculated/solver `RLOAD` power is
`0.30597029273591253 W`, below the `0.5 W` rating. Calculated and solver
power agree for every seed within floating-point precision, and all currents
remain in the intended sane envelope.

## Seed 1 player lifecycle

The visible ordinary NPN route presents the complaint: “The controlled load
stays active when control is low.” The player can power the board off, select
Q1 on the rendered board, remove it, and inspect the loose original
`Q1_ORIGINAL - Generic NPN transistor`. In OHM mode, visible probes on the
loose collector/emitter terminals show `100 mOhm` through the real loose-part
solver path, consistent with the C-E short.

Reinstalling the original preserves the fault. The developer lifecycle check
rejects the wrong catalog identity, then confirms that the correct catalog
replacement has a distinct physical identity and distinct `NTransistorElm`
with stable B/C/E mapping and no original fault binding. With the replacement
installed, powering the board on and exercising the real solver-backed board
produces normal load switching; generic `Finish Job` reports the repair
verified.

## Routing and state policy

The shared GND geometry starts at J1.2, reaches a common trunk near the left
side of the board, then branches to J2.2, RPD.2, and Q1.E. Stable route IDs,
pad ownership, and node mapping are unchanged. The generic route validator
detects duplicate/overlapping segments and non-adjacent crossings without
mistaking adjacent endpoints or intentional same-net joins for defects.

NPN instantaneous compatibility and repair validation is observational. It
temporarily commands the real graph when needed, then restores the captured
prior state in `finally`. RC validation is intentionally different: it is a
temporal, stored-energy family and advances real solver time to test charge and
discharge behavior.

## Validation evidence

- JDK 8/GWT production build: `scripts/build.ps1 -Target Compile -Style OBF`
  passed (`Compiling 5 permutations`, compile/link succeeded).
- `scripts/verify-renderer-boundary.ps1`: `PASS:renderer-provider-boundary`.
- PowerShell AST parse: `PASS:verify-browser-parser`.
- Visible in-app Browser natural NPN routes: seeds 0, 1, 2, and 3 each report
  `PASS:npn`.
- Visible in-app Browser ordinary Quick Play route for NPN seed 1:
  `PASS:quick-play`, with
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`.
- Fresh normal Quick Play session has no developer report attribute and no
  hidden fault/electrical terms; it exposes the real board, tray, complaint,
  and generic `Finish Job` UI only.
- Coder/reviewer verification: forced NPN matrix `16/16`, provider parity,
  route/layout, lifecycle, architecture, renderer, Quick Play, RC/stored
  energy, LED/diode/parallel regression suites pass.
- `git diff --check`: passed before commit.

The standalone `scripts/verify-browser.ps1 -NpnNatural` attempt was not used
as player-facing evidence: in the current sandbox, headless Edge exited before
route execution with `GPU process isn't usable`/fatal GPU errors. Host
WMI/CIM access also returned `Access denied` during harness process inspection.
The required visible in-app Browser interaction was therefore kept visible and
used for the player-facing observations above; the limitation does not change
the passing in-app route results.

## Evidence files

- [NPN seed 1 initial board](task-evidence/task-37-correction/npn-seed1-initial.png)
- [NPN seed 1 loose original](task-evidence/task-37-correction/npn-seed1-loose-original.png)
- [NPN seed 1 loose C-E measurement](task-evidence/task-37-correction/npn-seed1-loose-ce-measurement.png)
- [NPN seed 1 correct replacement and verified repair](task-evidence/task-37-correction/npn-seed1-correct-replacement.png)
- [NPN seed 2 labels and probes](task-evidence/task-37-correction/npn-seed2-probes.png)
- [Fresh normal Quick Play session](task-evidence/task-37-correction/quickplay-fresh-session.png)

## Known fidelity debt

The current compact TO-92 silkscreen visibly uses `NPN` and B/C/E markings.
More realistic transistor-body and silkscreen rendering can be addressed in a
future scoped task. It does not replace or falsify the electrical model, pad
mapping, nameplates, or player-visible measurements validated here.

## Final boundary

Only the Task 37 post-review correction is included. Task 38 remains the next
eligible roadmap milestone and was not started. The final handoff is one local
correction commit; publication is a separate explicit user-authorized step.
