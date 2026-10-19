# Domain and occupancy cause evidence

Baseline: `e0b3052fd030bb30359471bd217a673854612b18`, accepted private13.
This packet adds developer-only native diagnostics. It does not alter generation,
electrical behavior, player UI, placement interpretation or saved sessions.

The predeclared scope is in `scope.json`. The new native contract exports the
actual routed geometry of roots42 and-1022, including per-package access and
conductive domain courtyards, union bounds, declared isolation strips, both
copper faces, pads, vias and silk bounds. Root42 uses placement attempt1;
root-1022 uses attempt0. These are distinct placement paths, not proof that their
remaining empty space has distinct causes.

`render_overlays.py` consumes the native receipts, checks exact accepted13
geometry identities and the existing whitespace measurements, and renders
`index.html` with whole-board and pocket-detail SVGs. The domain union rectangles
are bounds of actual conductive courtyards. They are not hard allowed-placement
regions. The green rectangle uses the existing conservative GRID10 metric,
including package access, copper clearance on both faces, vias, silk and declared
isolation. It is not proof that a complete package can fit or be routed there.

The maintained JDK8 native diagnostic suite passed30 assertions in59.5718274s,
including terminal wrapper cleanup (cleanup duration was not separately timed).
The initial compile failed on a missing fixture helper and is retained as
`native-attempt1.txt`. Root review also corrected first-field JSON formatting and
kept declared pad order when constructing footprints before the successful run.
Exact accepted13 hashes, selected attempts and protected void areas match.
`audit_boundary.py` passes all1,440 prior frozen inputs except the intended
one-line native suite registration. `compiled-web-boundary-audit.json` separately
proves unchanged production source and entire compiled web digests. Full GWT and
player flows are NOT RUN for this native-only change; prior evidence is retained
at that audited unchanged production boundary.

Four final SVGs were rasterized locally with installed PyMuPDF and all four PNGs
were inspected. Initial SVG class styling was unsupported by the local renderer;
explicit attributes corrected its black fills. A browser file-URL attempt was
blocked by protocol policy; no alternate browser route was attempted. These are
local artifact renderings, not production-preview or player-input evidence.

Root42 has a810×220 pocket at(40,1480). Its right sampled boundary is isolation,
its top is D4 access, and left/bottom are the sampled board edge. DA, KA, QA and
RPIN_A access envelopes overlap its Y span elsewhere on the board, preventing
removal of that whole horizontal strip. Root-1022 has a760×960 pocket at(290,980):
JAC access bounds the left, UFB access the top, isolation the right and the board
edge the bottom. Both pockets extend outside the isolation strip. Neither the
metric nor these bounds proves a proposed component move legal or routable.
`geometry-assessment.json` retains every matching obstacle on neighboring sampled
cells, plus package projection witnesses. A full-axis compactor cannot simply
delete either entire pocket span while preserving those other packages.

Next bounded experiment: vacancy advice in repeated-control placement attempt0,
which currently lacks it, reusing its duplicate ring-zero slots. Preserve all
six placement candidates, evaluation caps,3+3 route caps and exact physical
admission. This is a hypothesis for testing, not a qualified size improvement.

No main merge or email. Existing physical, search, routing and time limits stay.

Root reviewed the integrated native/export/render/registration diff. A separate
static reviewer checked the diagnostic sources and relevant owners, identified
the missing displayed guard layer, and confirmed the correction. That reviewer
did not run gates or inspect the images. Root ran the native gate and inspected
all four final renders. `resources-cleanup.json` records verified cleanup.
