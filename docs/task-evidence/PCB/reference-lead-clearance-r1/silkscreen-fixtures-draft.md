# Silkscreen fixture draft

The contextual patch adds candidate18 reference-placement fixtures. Independent `P01PhysicalPoseChecks.expectedRectangle` literals check the raised-link body and selected leads across every declared rotation and side. Literal candidate arrays pin horizontal and vertical row order. Lead-overlap rectangles are checked against the expected lead while clearing the body and every pad; exact-edge rectangles must pass the private guard. Reflected `addLabel` rejection covers `board-title`, `component:R1`, and `net:R1.1`, checking each precise `IllegalStateException` message and that no label was added.

The source-signature audit confirmed the used `BoardNet`, `BoardPad`, `BoardComponent`, `PcbPadPlacement`, board/layout, and pose constructors, plus the pad/probe accessors and private generator method signatures. The patch uses `java.lang.ReflectiveOperationException` and checks declared pose support before constructing placements.

Static applicability: **PASS** — `git apply --check docs/task-evidence/PCB/reference-lead-clearance-r1/silkscreen-fixtures.patch`. Patch is LF-only (166 added lines; SHA-256 `0930F8B05789D5B3542D65EE26DF189F97CC82F987C86316A784D53416C55B99`). Java/native execution is **NOT RUN** by this assignment. Root should apply after the candidate18 source freeze and obtain runtime proof through the maintained contract gate.
