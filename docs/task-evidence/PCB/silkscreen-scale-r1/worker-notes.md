# Silkscreen scale worker handoff

Base: `ffd02ec83ce04465447f31c2a0df568dad52a543`.

Implemented only in the assigned renderer and new contract. Top-face physical
silkscreen now draws at its projected stored baseline with the original logical
font under scoped fractional canvas scaling and board clipping. Unscaled canvas
glyph metrics produce conservative, board-clipped screen-space obstacles for
provider annotation placement; those obstacles pass through the existing frame
transform once and participate in its existing zoom/face/viewport cache key.
Provider legends retain their existing layout path. Graphics canvas and Java
font/color state are restored after physical text rendering.

The native contract emits this marker when registered and run:
`PASS: PCB silkscreen scale contracts: <count> assertions`

Source identities:

- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java` SHA-256 `ACA59F91FC318C27A59182AFFDB2D2E693FF15B6A42CDAF1579375BC885505CB`
- `tests/contracts/PcbSilkscreenScaleContractTest.java` SHA-256 `4C0AFE43724B0E8564F3E673F065C6F849F6A4539FCD59A9732BA4D154B464A5`

Static tracked `git diff --check` and a trailing-whitespace check of the new
untracked contract passed. Native contract, registry integration, production
JDK8/GWT build, and visible preview/captures were not run; root owns those gates.
The worktree also contains a concurrent `docs/CODEX_TASK_REPORT.md` edit and
pre-existing untracked evidence/cache paths; they were left untouched.
