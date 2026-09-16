# P08 root review

This is the implementing root's integrated-diff review, supported by independent
executable contact, clearance and solver-equivalence oracles. It is not presented
as a separate human/agent review or an audit of every unrelated subsystem.

The review checked closed-boundary candidates, negative coordinates, wide and
coincident intervals, layer masks, canonical pair order, immutable inputs, exact
stroke contacts and the unchanged global collinear-split normalization. The cache
key contains every transform scalar and uses exact immutable owner identity rather
than a hash alone. No cached result can authorize a solver reading or skip live
occlusion. Cut partitions rebuild globally, and the solver mapping is bijective
in both directions. Cache storage retains one view, not a history of boards/views.

The root inspected the clean and preserved-worktree diffs. Shared renderer/test
runner files were merged against P07; reversing only P08 restores the original
user text exactly. Other original files are checked byte-for-byte separately.
The root also reviewed real compiled browser receipts and actual screenshots.

## Four inspected preserved-worktree screenshots

`visual/01-top-via-measurement.png`: 7 V is visible, the probe contacts the via
land, and the separate crossing geometry remains visually intact.
`visual/02-bottom-trace-measurement.png`: the bottom-only bridge is visible with
7 V, while top-only copper is hidden. The runner checks all six actual readings.
`visual/03-board-front.png`: the existing normal player shell, bench multimeter,
component bodies/labels and closed bottom tray remain present.
`visual/04-copper-navigation.png`: after real face change, pan and zoom, copper
and lands remain aligned and the meter remains on the bench. Board clipping at
this zoom is intentional navigation, not evidence of a full-board fit or FPS.
