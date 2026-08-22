# Task 43R-5 completion report — RC fixed-layout reconstruction

## Roadmap milestone

Task 43 recovery milestone **43R-5 — RC fixed-layout reconstruction** is
complete. This task was limited to the fixed RC route. 43R-6 (NPN), 43R-7
(NMOS), 43R-8 (Task 43 acceptance/regression), and Task 44 were not started.
The roadmap now identifies 43R-6 as the next eligible milestone.

## Summary

`RcDelayPcbLayoutFactory` was reconstructed from the approved global route
witness for the current package geometry contract v3. The authored RC copper
route now uses all six fixed component anchors, all logical VIN/RC_OUT/GND
memberships, and the corrected package-declared escapes. The old RC copper was
not locally patched; the route was replaced globally.

The implementation covers the complete finite R1/R2 span matrix (220, 240,
260 ohms represented by the seeded axial spans), including the one direct
transition required by the 260-span branch. Existing compaction remains a
uniform rigid translation over its four seed-origin classes.

## Architectural decisions

- The live RC topology remains unchanged:
  - VIN: `J1.1`, `R1.1`, `C2.1`;
  - RC_OUT: `R1.2`, `C1.+`, `J2.1`, `R2.1`;
  - GND: `J1.2`, `C1.-`, `J2.2`, `C2.2`, `R2.2`.
- Component anchors remain J1 `(50,150)`, R1 `(200,90)`, C1 `(700,70)`,
  J2 `(900,160)`, R2 `(500,290)`, and C2 `(200,300)`.
- J2 remains `THROUGH_HOLE_OUTPUT_HEADER_2` with `DEFAULT`/`IDENTITY`
  geometry. No package, orientation, placement, or geometry-contract change
  was made.
- The route consumes pad centers and declared v3 escapes. C2 and J2 use
  their corrected 35-unit escapes; no stale v2 escape value is reintroduced.
- The generic `PcbBoardLayout` validator remains the electrical/physical
  geometry oracle. No renderer, generic validator, CircuitJS electrical,
  fault, stress/damage, NPN, NMOS, or Task 44 code was changed.

## Files changed

- `src/com/lushprojects/circuitjs1/client/RcDelayPcbLayoutFactory.java` —
  reconstructed the nine-case RC route witness.
- `docs/ARCHITECTURE.md` — documented the R5 route and validation boundary.
- `docs/ROADMAP.md` — marked 43R-5 complete and identified 43R-6 as next.
- `docs/CODEX_TASK_REPORT.md` — this handoff report.
- `docs/task-evidence/task-43/rc-production-preview.png` — final production
  preview, normal-player powered state.
- `docs/task-evidence/task-43/rc-production-power-off.png` — final production
  preview after the normal-player board-power interaction.

## Validation performed

### Implementation and finite route witness

- Approved route witness: all 9 structural R1/R2 span combinations × all 4
  compaction-origin classes = **36/36 PASS**.
- VIN, RC_OUT, and GND physical connectivity passed for every case.
- v3 endpoint escape legality passed for C2 and J2 upward escapes.
- Full-width courtyard containment, unrelated-net clearance, silkscreen
  separation, body/pad separation, route quality, and deterministic identity
  checks passed.
- Route metrics from the closed witness: trace width 9; minimum centerline
  separation 15; minimum unrelated-net visible gap 6; maximum bends 8; maximum
  detour ratio 2.5; no self-intersection, duplicate, repeated, or zero-length
  segment.
- The in-app fixed-layout developer verifier reported `PASS:layout` on the RC
  route after the final production source candidate was built.

### Build

Final command:

`scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF -Target Compile`

Result: bundled JDK 8 accepted; all five production GWT permutations compiled;
production link succeeded.

### RC, stored-energy, and adjacent regression verifiers

- The RC + stored-energy in-app developer route executed, then stopped on the
  accepted 43R-4C external renderer debt:
  `Renderer omitted disconnected component-side lead: C1.+`.
  This is the known loose/renderer validation boundary and is not an R5 route
  or geometry failure. The deferred Edge/WMI/CDP browser harness debt was not
  retried.
- The Task 40 and Task 41 corpus routes executed through their developer
  verifiers but stopped at the already-deferred NPN fixed-layout route with:
  `PCB trace passes through component routing courtyard: LOAD_SUPPLY / RLOAD
  segment 310,60 -> 310,100 keepOut=Rect(312,75,216,60) ...`.
  That is the R6 dependency boundary; no NPN source was changed during R5.
- No verifier result was fabricated or replaced with a hard-coded UI value.

### Production visual evidence

The final production build was served from `war/` and inspected in the Codex
in-app browser using normal-player mode. Both screenshots are nonblank and
show the actual RC PCB, routed copper, labeled components, J2, C2, service
ticket, and parts tray. The powered screenshot proves the final visible board
state; the power-off screenshot proves the normal board-power interaction
leaves the reconstructed copper visible and stable.

## Multi-agent implementation and review

- Coder: Copernicus (Luna MAX), delegated the single production-file change;
  returned only the authorized source modification and did not commit or push.
  Two correction rounds removed a zero-length R2.2 segment and an extra
  collinear J1.1 route vertex. The final candidate passed the coder’s build and
  layout checks.
- Independent reviewer: Godel (fresh read-only Luna MAX). Initial review
  identified the extra collinear route vertex as a deterministic-fingerprint
  blocker. After the authorized correction, the reviewer re-read the final
  diff and returned **PASS**.
- Primary architect review rounds: two implementation correction rounds,
  followed by one final acceptance review of the source, diff, witness,
  validation, and scope.
- Escalation architect: not required.
- Primary architect final result: **FINAL PASS**.

## Known limitations and follow-up

- 43R-4C’s Edge/WMI/CDP host debt remains deferred to 43R-8 and was not
  retried as R5 work.
- The RC electrical/stored-energy route is still blocked by the known R4C
  disconnected-lead renderer error.
- Task 40/41 full corpus routes remain blocked at the deferred NPN geometry
  signature; this is the reason 43R-6 is next.
- No R6, R7, R8, or Task 44 implementation was begun.

## Completion protocol handoff

- Intended commit message: `Reconstruct Task 43 RC fixed routing`
- Branch: `codex/task43-recovery-integration`
- Upstream: `origin/codex/task43-recovery-integration`
- Configured remote: `origin` (`https://github.com/dspevo-afk/TroubleshootJS.git`)
- Notification destination: `dspevock@stateofthearcelectric.com`
- Intended subject: `TroubleshootJS: Task 43R-5 RC fixed routing pushed`

The authoritative final commit SHA, push result, and notification result are
established after this report is written and are available from repository
history and the final Codex task response.
