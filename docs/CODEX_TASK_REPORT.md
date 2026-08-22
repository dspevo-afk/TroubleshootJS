# Task 43R-6 completion report — NPN fixed-layout reconstruction

## Roadmap milestone

Task 43 recovery milestone **43R-6 — NPN fixed-layout reconstruction** is
complete. This task was limited to the NPN fixed route and its NPN-specific
developer evidence. 43R-7 (NMOS), 43R-8 (final Task 43 acceptance), and Task
44 were not started. The roadmap now identifies 43R-7 as the next eligible
milestone.

## Starting point and scope

- Branch: `codex/task43-recovery-integration`
- Starting accepted head: `a006a1d6be8728f48af8a94b35abe88ab43b7716`
- Starting state: clean and equal to
  `origin/codex/task43-recovery-integration`.
- Allowed production source: `NpnLowSideSwitchPcbLayoutFactory.java`.
- Allowed support changes: NPN-specific developer verification, Task 43 NPN
  aggregation, and the three handoff documents.
- RC/NMOS factories, package/geometry contract, generic validators, renderer,
  measurement/fault/stress/replacement systems, and Task 44 were untouched.

## Summary

`NpnLowSideSwitchPcbLayoutFactory` now uses one approved global all-net route
set for the current version-3 package geometry. The route keeps the live NPN
topology, component anchors, canonical package objects, stable electrical
node identities, and compaction behavior. It consumes pad centers and
declared package escapes instead of relying on the stale authored route.

The un-compacted anchors are J1 `(170,150)`, J2 `(170,570)`, RLOAD
`(330,100)`, RB `(530,530)`, RPD `(280,380)`, LED1 `(620,140)`, and Q1
`(970,190)`. The nine trace memberships are:

- `LOAD_SUPPLY`: J1.1 → RLOAD.1;
- `CONTROL`: J2.1 → RB.1;
- `LOAD_NODE`: RLOAD.2 → LED1.A;
- `COLLECTOR`: LED1.K → Q1.C;
- `BASE_RPD`: Q1.B → RPD.1;
- `BASE_RB`: Q1.B → RB.2;
- `GND_J1_J2`: J1.2 → J2.2;
- `GND_J1_RPD`: J1.2 → RPD.2;
- `GND_J1_Q1`: J1.2 → Q1.E.

The three canonical axial span variants are independently selected for
RLOAD, RB, and RPD: 27 structural tuples total. Four existing compaction
origin classes produce the closed 27 × 4 = **108-case** witness. The base
routes use the y=36 upper lane with y=330 and y=470 branches. The ground
tree uses the x=60/y=430 trunk, the y=440 RPD detour, and the separated
emitter branch. The resulting routes have no duplicate, zero-length,
self-intersecting, or courtyard-crossing segment; minimum distinct-net
centerline separation is 16 units.

`NpnFixedLayoutDeveloperVerifier` uses the live generator fixture, canonical
package objects, exact trace endpoint and membership assertions, the real
`PcbBoardLayout.validateGeometry` oracle, deterministic duplicate
fingerprints, and normalized rigid-translation equivalence. The existing NPN
ground-tree checks were made translation-relative to the compacted J1.2/J2.2
pads.

## Files changed

- `src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchPcbLayoutFactory.java`
  — reconstructed the global nine-trace NPN route and added the bounded
  developer matrix factory overload.
- `src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchDeveloperVerifier.java`
  — updated ground-tree checks to use the placed compacted pads.
- `src/com/lushprojects/circuitjs1/client/NpnFixedLayoutDeveloperVerifier.java`
  — added the explicit 27-tuple × 4-origin NPN witness.
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java` —
  included the NPN fixed-layout matrix in the Task 43 developer aggregation.
- `docs/ARCHITECTURE.md` — documented the R6 route and validation boundary.
- `docs/ROADMAP.md` — marked R6 complete and identified R7 as next.
- `docs/CODEX_TASK_REPORT.md` — this handoff report.

## Validation performed

### Build and developer verifiers

Final build command:

`.\scripts\build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF -Target Compile`

Result: bundled JDK 8 accepted; all five GWT permutations compiled;
production linking succeeded.

The cache-busted compiled production preview ran the in-app developer route
with `tsjVerifyLayout=true`, `tsjVerifyNpn=true`, and
`tsjVerifyTask43=true`. Observed results:

- `PASS:NPN_FIXED_LAYOUT_MATRIX:cases=108/108;variantTuples=27;originClasses=4`;
- `PASS:task43`;
- NPN electrical report published solver-backed +9 V/+5 V rails, 330 Ω
  RLOAD, 21.48 mA load/LED current, 4.31 mA base current, and 0.1523 W
  RLOAD power;
- the same run reached the expected deferred NMOS courtyard frontier and
  did not claim NMOS success.

### Normal-player validation

The visible in-app browser production page rendered the actual NPN PCB with
board traces, components, service ticket, power control, meter controls, and
parts tray. Through normal visible interaction, the check clicked `Set
control HIGH`, `Set control LOW`, and `Retest Customer`. The rendered result
was `Customer retest did not pass. Continue troubleshooting.`, which is the
expected unrepaired challenge behavior.

The first preview attempts were blank because the browser had a stale GWT
selection/bootstrap cache: it requested old missing permutation hashes and,
on one origin, a stale Super Dev Mode recompile hook. Super Dev Mode was not
needed or enabled. A temporary no-cache local preview with a cache-busted
`circuitjs1.nocache.js` URL served the current compiled `war/` and rendered
the page; no product source or preview script was changed for this fix.
The separate external `scripts/verify-browser.ps1 -Npn -Layout` harness
still ended with a WebSocket close and Edge-process cleanup `Access denied`
from WMI, so that host limitation remains recorded separately from the
successful visible in-app check.

## Multi-agent implementation and review

- Phase A investigators: Bacon, Gibbs, and Anscombe; read-only, parallel,
  `gpt-5.6-luna` MAX reasoning. Their route reports were reconciled before
  implementation and the verifier-compatible witness was frozen.
- Coder: Tesla; single write-capable `gpt-5.6-luna` MAX worker. Changed only
  the authorized NPN/developer files and returned the build/verifier evidence.
- Independent reviewer: Averroes; fresh read-only `gpt-5.6-luna` MAX reviewer.
  Re-read the final diff and returned **PASS** for route membership, 108-case
  matrix coverage, identity, translation, and scope.
- Primary architect review: one final diff/scope review after the coder and
  before the independent review; **FINAL PASS**.
- Escalation architect: not required.

## Completion protocol handoff

- Intended commit message: `Reconstruct Task 43 NPN fixed routing`
- Branch: `codex/task43-recovery-integration`
- Upstream: `origin/codex/task43-recovery-integration`
- Configured remote: `origin` (`https://github.com/dspevo-afk/TroubleshootJS.git`)
- Notification destination: `dspevock@stateofthearcelectric.com`
- Intended subject: `TroubleshootJS: Task 43R-6 NPN fixed routing pushed`

The authoritative final commit SHA, verified push result, and notification
result are established after this report is written and are available from
repository history and the final Codex task response.
