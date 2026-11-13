# E07 · 555 timer family (timers and oscillators)

Accepted 2026-11-12 for EASY on the `TIMER_555` family. Ownership is described in
[ARCHITECTURE.md](../../ARCHITECTURE.md#e07-555-timer-family-ownership).

## What a player gets

A new EASY board family, **555 timer board**. Seed parity picks one of two roles:

- **Flasher (periodic).** An astable 555 (RA, RB, C1) blinks an LED. Complaints:
  the light flashes too slowly, too fast, or not at all.
- **Pulse (triggered).** A monostable 555 (RA, C1) lights an LED for a timed
  pulse when the customer presses START. START is an off-board momentary switch
  reached through connector J2. The player presses it from the ticket or from the
  new amber toolbar button.

Faults: RA off value, RA open, C1 short. U1 is the stock CircuitJS `TimerElm` in
a new SIP-8 package, so the timing comes from the solver's comparators, latch and
discharge path, not from a model written for this family. U1 is serviceable like
every other part: eight liftable leads, remove, and a catalog "555 timer IC
(SIP-8)" replacement backed by a fresh `TimerElm`.

## Acceptance

| Criterion | Result | How |
|---|---|---|
| One triggered and one periodic role | PASS | Pulse and flasher roles; both admitted 20/20 in the quick-play physical matrix |
| Timing and frequency come from solved state | PASS | The solved U1.OUT voltage is sampled every 2 ms of solver time; period = mean rise spacing, width = rise to fall |
| Player triggers reproduce the plan | PASS | The browser START press closes the real J2 contact and produces the pulse the retest checks |
| Unsupported high frequency stays unavailable | PASS | Edges closer than 10 ms are reported as too fast with no value. The meter has no Hz function |

Negative tests in `tests/contracts/E07TimerContractTest.java`:

- **Configured value as measurement.** RB's solver value goes to 33 kΩ while its
  nameplate still says 8.2 kΩ. The board reports too slow.
- **Hidden trigger.** Power-up alone leaves OUT low. Only the START operation's
  real contact closure starts a pulse. A power cycle mid-pulse ends it and
  repower does not resume it.
- **Paint-speed timing.** The test runs with no rendering at all. The measured
  period repeats within two 2 ms solver-time slices.
- **Aliasing.** A roughly 70 Hz flasher reports too fast, with no measured period.
- **Reset erasing state.** The healthy reference survives later profiles. The
  generation dependency capture is identical before and after 0.6 s of running,
  because the OUT latch voltage is excluded from the fingerprint.

## Commands run

- `scripts/verify-current-contracts.ps1 -Suite E07TimerContractTest`: PASS, 92 assertions, about 37 s
- `-Suite QuickPlayPhysicalMatrixContractTest`: PASS (`TIMER_555` 20/20 boards, 0 rejects)
- `-Suite ProceduralFamilyContractTest`: `TIMER_555` cohorts pass. Run alone, the suite then stops at `RB56_CONTROL/0` with a `CompositeElm` NPE, a harness setup gap outside E07; the full gate is the authority
- Full native gate: PASS, 214 Java suites plus the independent oracles
- `scripts/build.ps1` (JDK 8, GWT 2.7): PASS

## Browser play (built-in browser, real clicks, 1280×800)

| Screenshot | State |
|---|---|
| `01-flasher-in-circuit-ohms.jpg` | Flasher complaint, in-circuit resistance reading on RA |
| `02-flasher-retest-passed.jpg` | RA removed, 4.7 kΩ bought and installed, customer retest PASS |
| `03-pulse-start-control.jpg` | Pulse board: toolbar START pressed, LED lit for the pulse |
| `04-pulse-retest-passed.jpg` | RA replaced with 15 kΩ, retest PASS |
| `05-flasher-unrepaired-retest-fails.jpg` | Retest without a repair fails |

Final build, no screenshot kept: flasher seed 0. Removed U1, bought a new 555
from the shop and installed it, then replaced RA with 4.7 kΩ. Customer retest
PASS on the replacement chip.

## Playability changes in the same task

- Board-owned operations (START) also appear as direct amber toolbar buttons,
  not only inside the ticket text.
- Pads on a board prepared in a zero-size viewport (hidden pane) are clickable
  once the canvas is shown.

## Limits

- The behavioral 555 ties DIS to ground through 10 Ω when unpowered, so
  in-circuit ohms across RB read low. Out-of-circuit readings are correct.
- Continuity is left out of the hidden solvability program, because on the
  pulse board RA feeds C1 and the reading would need a discharge wait.
- No frequency or scope reading is required to solve these boards. Two-channel
  scope work is U09.
- The live LED blink rate on screen follows frame rate. Measurements and retest
  use solver time.
- The empty footprint is not clickable. Install goes through the parts tray.
- Tray part numbering skips numbers, and the install dialog says "Install as
  Circuit / RA".
- Probe markers stay on the board after the player leaves meter mode.
- EASY only. MEDIUM and HARD timer content is not in scope.
