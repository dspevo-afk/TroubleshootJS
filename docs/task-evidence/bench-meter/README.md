# Draggable bench multimeter - qualified follow-up

Date: September 14, 2026. Branch: `codex/task43p-final-recovery`.
Base: `7aedfb1bb89675a4845dedcc27a6d4de54cb1e00`.
Scope: meter placement/input and the furniture-aware initial fit. P05 is unstarted.

## Behavior and coordinate contract

The native GWT meter is not cloned or reparented. Its absolute presentation is
projected from its board owner's bench-world position through the unmirrored
permanent camera and the actual canvas-to-CSS transform. PCB face mirroring and
temporary Space inspection do not mirror or magnify the meter. An opaque owner
key, not a circuit/controller/solution reference, crosses the presentation seam.

Home is 32 world units left of the PCB, with a 252 x 454 world-unit case centered
vertically where possible. Initial Fit bench includes the meter, PCB and right
Parts Tray. Top/bottom Fit bench uses the same unmirrored furniture envelope.
The contact shadow reserves a small edge margin. GWT's toolbar ScrollPanel and
its layout wrapper release overflow without acquiring a full-screen hit box.

Only the TSJ MM-90 header drags. It captures the primary pointer; window listeners
cover fast movement and capture-unavailable fallback. Cancel, release, lost
capture, blur, hidden tab, resize, overlay and owner suspension end dragging.
Header keyboard arrows move 20 CSS pixels, Shift+arrows 5; Home returns it left,
and Escape cancels a drag. The meter body continues to operate its actual controls.
A WeakMap preserves placement through menu/resume and same-owner refreshes; a
new board gets a new home. Reload/browser-restart persistence is not provided.

### Pan and visibility rule

The numeric pan rule is UNCHANGED: at least half the maximum currently visible
PCB area must remain onscreen, including diagonal pans. The fit envelope alone
is wider. Normal camera pan carries the meter at unchanged world coordinates.
At a visibility boundary, the meter is re-seated just enough in WORLD coordinates
to stay on the visible canvas below the toolbar. At unusually high zoom, its
case scale is capped to fit the visible bench. These deliberate exceptions avoid
stranding the instrument or severely restricting PCB inspection. Furniture does
not constrain circuit mutation, copper identity, solver state or pan travel.

## Fresh validation

| Check | Result |
| --- | --- |
| Final GWT OBF build | PASS, all five permutations, exit 0, 87.170 seconds |
| New jsdom meter contract | PASS, 69 checks; geometry, ownership, cancellation, header-only input |
| Existing U04 UI contract | PASS, 112 checks |
| U01 native viewport | PASS, 1,259 assertions including new furniture/flip fit regression |
| Visual workbench native | PASS, 38 assertions |
| Architecture footprint native | PASS, 46 assertions |
| Physical serviceability native | PASS, 846 assertions, all nine playable families |
| U04 native session | PASS, 76 assertions |
| Compiled U01 | PASS, 1,263 assertions, six live pad targets, identity unchanged |
| Compiled U01 forced-negative | Expected `FAIL:U01 forced-negative` observed and accepted |

The five native suites compiled the actual client sources using the maintained
runner. Their Java bytes were unchanged by the later CSS-only clipping repair.
Final browser input and final jsdom meter checks use the repaired JS/CSS.
`collected.json` hashes every published evidence file and maps copied files to
their raw receipts. Text is normalized to UTF-8/LF with trailing log whitespace removed; the build log replaces the
local checkout path with `<repo>`. Raw receipts remain unchanged in task scratch.
`source-hashes.json` records final source and compiled output hashes;
`candidate.json` records the maintained preview identity (1,463 execution files).
The raw runner's text receipt was named native.json; its correctly typed curated
copy is `native-contracts.txt`. It is stdout, not a structured JSON verdict.

## Real compiled-browser input

A fresh isolated headless Edge instance loaded the actual final GWT build.
All player actions used CDP mouse/keyboard input, not injected controller calls.
DOM queries only read public state and control geometry. On Parallel seed 0:

- Initial meter bounds: x=20, y=281.1875, 282.24 x 508.48 CSS pixels at 1440x960;
  full PCB, title and tray are visible, with the meter entirely to the left.
- Header drag moved the case +100/+80 pixels. Display/body drag did not move it.
  An ensuing middle-button pan moved it +80/-45 pixels with identical world
  coordinates. Native pointer capture was observed; a single fast move outside
  the window remained clamped and released cleanly.
- Repeated pan reached the half-board limit. Top/bottom Fit bench produced the
  same meter position. Component R1's context menu and power guard worked.
- Unrepaired customer retest failed as expected. With power isolated, actual
  probes read OL on faulty R1 and 680 Ohm on healthy R2. R1 dragged to the real
  Parts Tray; the tray selected it and offered its compatible installation target.
- The real in-game shop supplied a 330 Ohm replacement. Selecting and installing
  it through the tray, then powering the board, produced a passing customer retest.
- A moved meter survived Main menu / Resume current board without resetting.
  At 1024x768, Fit bench and higher zoom left the entire meter reachable.

`player-evidence.json` contains the numbered geometry and public UI snapshots.
`input-events.jsonl` records the actual input events. These are targeted desktop
checks, not a claim of touch-device or cross-browser qualification.

## Five inspected screenshots

1. `01-initial-left.png`: default left placement, readable board title and full tray.
2. `02-header-drag.png`: moved via label; it layers above only the board area covered.
3. `03-pan-limit.png`: actual bounded pan, meter carried with the bench, half PCB visible.
4. `05-retest-passed.png`: successful live repair/retest, modal correctly above the meter.
5. `06-small-viewport.png`: 1024x768 fit, full meter/PCB/tray and installed replacement.

## Qualification repair, limits and cleanup

The first browser screenshot exposed native toolbar clipping after switching from
fixed to absolute positioning. The exact GWT clipping containers were corrected,
a new DOM regression added, and final build/browser qualification rerun. The
failed first screenshot is preserved in raw scratch; it is not acceptance evidence.
Several exploratory selector/read attempts targeted a hidden control or assumed
a product bridge on the developer-only page; those were harness errors, not game
passes. The final positive and intentional-negative receipts are explicit.

Integrated root diff review was performed; no independent-agent review is claimed.
The complete release matrix, full Alpha/Q15 sweep, arbitrary seeds, touch hardware
and general browser support were NOT RUN for this scoped presentation change.
No old release-wide result is relabeled as a new full regression run.

Both task-owned browser/preview runs cleaned up exactly; `cleanup.json` records
success. Existing untracked evidence/cache and unrelated processes were preserved.
Raw diagnostics remain under OS-temp `TroubleshootJS-bench-meter-20260914-fkl67cei`.
Publication SHA/remote equality/email outcome are recorded separately in that
scratch directory after commit and push, not guessed before publication.
