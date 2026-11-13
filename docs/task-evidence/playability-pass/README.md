# Playability pass · stabilize what is playable

ROADMAP Next up item 2, started 2026-11-13. I played a fresh relay output board
(EASY) in the built-in browser from the ticket to a repair, and fixed what got in
the way. Ownership is described in [ARCHITECTURE.md](../../ARCHITECTURE.md) under
the E07 section.

## Defects found and fixed

| Defect | Fix |
|---|---|
| "Set control HIGH" and "Set control LOW" looked the same whichever was applied, so you could not tell the input state | The applied input button is filled amber with a dot, and has `aria-pressed`. Families with latched inputs report this through `GeneratedActiveInputOperations`. Momentary START stays a plain button |
| Red and black probe markers stayed on the board after the meter was turned off | Markers are drawn only while a probing mode is selected. Probe positions are kept, so turning the meter back on resumes the same reading |
| Clicking a part in the tray drawer only selected it. Install needed a right-click or a drag, and the hint did not say where the Install button was | A plain click on a loose tray part opens its menu with "Install as …". The shop and help text say so |
| A removed part and an identical shop replacement looked the same in the tray | Each tray part is captioned "Removed part" or "New part" |
| The install button read "Install as Circuit / K1" | Boards with one layout region show just "Install as K1" |

## Checks

| Check | Result |
|---|---|
| `scripts/build.ps1` (JDK 8, GWT 2.7) | PASS |
| 23 focused Java suites touching the changed classes (Task43, physical serviceability, U06 session save, REL-B identity, E07, Q15, Q30 service flow, RB56 temporal, sensor control, A09, D01, U03, U05 and others) | PASS. The first run stopped on Q15's 60 s child budget while a relay board was simulating in the browser; the six suites it had not reached passed with the browser idle |
| Full native gate (no `-Suite`), browser idle | PASS: 215 Java suites plus independent oracles, 52 min; RB56 matrix 138 s |
| `tests/contracts/u04_ui_contract.mjs` (hint wording updated) | NOT RUN: jsdom is not installed on this machine |
| Browser, real clicks, production build | PASS: relay board HIGH/LOW toggle, meter off clears probes, remove R2, buy a resistor, captions, click-to-install R2. Sensor board LOW/MID/HIGH toggle |

## Screenshots

| File | State |
|---|---|
| `01-active-input-high.jpg` | Relay board after accepting the ticket: "Set control HIGH" shown as applied |
| `02-meter-off-no-probes.jpg` | Probes were on K1 A1/A2; meter turned off, board clear |
| `03-tray-captions.jpg` | Tray drawer with a new 100 Ω resistor and the removed R2 |
| `04-click-to-install.jpg` | Plain click on the removed resistor opens "Install as R2" |

## Not fixed

- Tray part numbers still skip ("Part 6") because they count installed parts too.
- Single-sided boards show no copper on the top view; the bottom copper is under
  View → "View bottom copper" or by holding Tab. Not changed.
- The empty footprint is still not a click target; install goes through the tray.
