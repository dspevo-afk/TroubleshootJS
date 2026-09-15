# Multimeter world-lock correction

Scope: meter-only correction above d1745961a7f626aea9c830422583b2b96a10c705.
The user's latest instruction supersedes the earlier always-visible requirement.

## Behavior

Camera projection no longer changes the stored world position. Pan, zoom,
viewport resizing, menu/resume and optional drawer visibility cannot re-seat
or shrink the meter. Its case follows the unmirrored permanent camera scale.
Header dragging, keyboard movement, initial placement and explicit Home reset
are the only position-changing operations. The numeric board pan bounds stay
unchanged. CSS clip-path trims paint and pointer hits at protected boundaries;
it does not move the case. The visible contact shadow is retained.

## Fresh qualification

The new zoom regression fails on published d174596: the old code changes
the dropped world position from (-184, 110) to (376.5, 251.5). The corrected
meter DOM suite passes 358 checks, including 90 camera/resize stress iterations,
pointer lifecycle, offscreen dragging and optional drawer clipping. Existing
UI contracts pass 112 checks. These DOM shims are not browser-input evidence.

Actual JDK8/GWT production build passes all five permutations. Build and browser
qualification use a task-owned isolated worktree at d174596 plus this meter
patch. The unfinished performance/packing/drawer/Shop work is not part of this
candidate or certified by these results. provenance.json records the final
source/test and compiled-permutation hashes; production input recheck has no drift.

Actual compiled Edge mouse/key input, normal RB15_CONTROL seed 3:
Header drag changes placement; body drag does not. Five wheel-zoom events move
the entire meter offscreen, then the opposite wheel events restore the exact
same screen bounds and world coordinates. Its right edge reaches -2913.65 px
without an automatic recovery. Pan/return, both PCB faces, 1024x768 resize/restore,
menu/resume, explicit Home, backlight and mode controls also pass. Dragging the
header behind the toolbar clips the case: the hit at (100,80) reaches Board Power,
not an invisible meter surface. No application exception was observed.

Five inspected screenshots show initial, dropped, zoomed-away, returned and
partially clipped states. browser-result.json, player-states.json and the actual
Input.dispatch event transcript record observations and actions. Screenshots
and receipts are from the final candidate, not an injected controller simulation.

## Limits and resources

No full electrical/release matrix, touch-device or cross-browser qualification
was repeated. Drawer opening is a DOM contract here, not a fresh acceptance of
the larger unfinished drawer implementation. Root reviewed the final diff; no
separate agent review was run. The initial browser harness waited on about:blank
before navigation and timed out; explicit normal navigation resolved that setup
mistake. A documentation-write preflight was blocked once, then an identical
retry succeeded. Neither event is an application failure or silently counted PASS.

Exact task browser/preview cleanup passed. Raw logs remain in OS Temp; unrelated
worktree changes and processes are preserved. See the current task report for
the separate four-item polish work still awaiting completion. P05 is unstarted.
