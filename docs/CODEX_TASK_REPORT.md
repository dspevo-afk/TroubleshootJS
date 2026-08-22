# Task 43R-5A completion report — RC fixed-layout acceptance-proof closure

## Roadmap milestone

Task 43 recovery correction **43R-5A — RC fixed-layout acceptance-proof
closure** is implemented and validated in the working tree. It does not claim
Task 43 or 43R-8 complete; 43R-8 remains the next unstarted
acceptance/regression/cleanup milestone. Task 44 was not touched.

## Starting state and scope

- Branch: `codex/task43-recovery-integration`.
- Starting handoff: `88fbe3daf007721bfdd31853fab94d5c5317f3bb`.
- Accepted production checkpoint: `c306556d3d387e4ad7d20353a73a6b703e58c477`.
- Phase A had already established that the existing RC route structurally
  supports all 3 × 3 × 4 combinations; this slice adds the acceptance proof
  and seam integration without changing that route.
- Only the requested RC factory/verifier/aggregation and three handoff
  documents were edited. No commit or push was performed.

## Implementation

`RcDelayPcbLayoutFactory.create` now normalizes its seed to variation mode and
shares one private `createLayout` with the developer-only
`createForDeveloperVerification` seam. Production passes the real seed and
null resistor keys. The developer seam passes `seed=variationMode` and
explicitly selects only canonical `SPAN_220`, `SPAN_240`, or `SPAN_260` axial
resistor geometries. The existing J1/R1/C1/J2/R2/C2 seed offsets are retained.

The production RC board dimensions, six component anchors, trace route and
copper coordinates, labels, compaction, parts-tray placement, and final
`validateGeometry` call are unchanged. R1/R2 explicit selection uses the live
`AXIAL_RESISTOR` package object and its canonical `GeometryVariant`; no second
route builder or package geometry was added.

`RcFixedLayoutDeveloperVerifier` uses one live
`RcDelayGenerator().generateForFaultVerification(0, CAPACITOR_OPEN)` fixture,
validates the board before the matrix, and executes exactly 36 cases:
`SPAN_220/SPAN_240/SPAN_260` × `SPAN_220/SPAN_240/SPAN_260` × four origin
classes. Each case checks canonical package/geometry/transform identity for
all six components, the ordered nine-trace endpoint/net witness, exact VIN,
RC_OUT, and GND logical memberships, physical endpoint representation and
rooted branches, package-declared escape metadata/directions, route quality
and clearance, deterministic full fingerprints, and normalized full geometry
across origin classes. Seam canaries reject null, `SPAN_230`, unknown keys,
and out-of-range variation modes. Production parity reconstructs seeds 0–3
from their selected live R1/R2 keys and requires full geometry and realization
parity.

`Task43DeveloperVerifier` invokes the RC proof before NPN and NMOS. The two
RC-specific deferred-failure branches were removed from
`PcbLayoutDeveloperVerifier`; both existing NPN deferrals remain untouched.

## Validation evidence

- JDK 8 production compile/link:
  `.\scripts\build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF -Target Compile`
  — passed all five GWT permutations and production linking.
- Compiled-preview Task 43 route:
  `.\scripts\verify-browser.ps1 -BaseUrl http://127.0.0.1:8898 -Task43 -TimeoutSeconds 90`
  — `PASS task43 physical package geometry contract`. This route executes
  the RC verifier first; its successful matrix evidence is
  `PASS:RC_FIXED_LAYOUT_MATRIX:cases=36/36;variantTuples=9;originClasses=4`.
- Compiled-preview general layout route:
  `.\scripts\verify-browser.ps1 -BaseUrl http://127.0.0.1:8898 -Layout -TimeoutSeconds 90 -PlayerSeed 3`
  — `PASS procedural-layout`; RC deferrals no longer absorb failures.
- Existing stored-energy route, seeds 0, 2, and 3:
  `.\scripts\verify-browser.ps1 -BaseUrl http://127.0.0.1:8898 -StoredEnergy -TimeoutSeconds 90 -Seeds 0,2,3`
  — all three routes passed.
- Existing RC behavioral route, seeds 0, 2, and 3, reaches the accepted
  43R-4C renderer boundary and reports
  `Renderer omitted disconnected component-side lead: C1.+`; no RC layout
  failure was reported. This remains outside 43R-5A.
- The in-app Browser runtime was unavailable (`agent.browsers.list()` returned
  no browser surfaces), so no normal-player visible-browser evidence is
  claimed. The successful results above are compiled-preview harness evidence.
- Final `git diff --check` is clean. The candidate remains uncommitted and
  unpushed.

## Changed files

- `src/com/lushprojects/circuitjs1/client/RcDelayPcbLayoutFactory.java`
- `src/com/lushprojects/circuitjs1/client/RcFixedLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`

---

# Task 43R-7 completion report — NMOS fixed-layout reconstruction

## Roadmap milestone

Task 43 recovery milestone **43R-7 — NMOS fixed-layout reconstruction** is
implemented and validated in the working tree. 43R-8 is now the next eligible
milestone and was not started. Task 44 was not touched.

## Starting state and scope

- Branch: `codex/task43-recovery-integration`.
- The required initial branch/worktree check was clean and matched the branch
  requested by the task. Accepted 43R-6 was `e7a93f47b5f21bb911aa902f8e03836b30e103d2`
  (HEAD and ancestor); `master` and `origin/master` remained
  `c0eb342b29165b8218a4b97b16fb8554fee42aff`.
- Primary production source: `NmosLowSideSwitchPcbLayoutFactory.java`.
- Authorized support changes: the NMOS fixed-layout developer verifier,
  NMOS-specific layout-verifier deferral removal, Task 43 aggregation, and the
  three 43R-7 handoff documents.
- No commit, push, merge, or completion notification was performed during the
  delegated implementation phase. The final commit SHA and publication result
  are intentionally recorded in the final task response rather than embedded
  in this pre-commit report.

## Implementation

`NmosLowSideSwitchPcbLayoutFactory` now has the frozen deterministic route for
the live version-3 package geometry. The fixed anchors remain J1 `(80+s,80)`,
J2 `(80+s,400)`, RLOAD `(350+s,200)`, RPD `(300+s,320)`, LED1 `(500+s,70)`,
and Q1 `(900+s,100)` before compaction, with `s=10*m` for four origin classes.
RLOAD and RPD are independently selected from the canonical axial variants
`SPAN_220`, `SPAN_240`, and `SPAN_260` in the developer-only overload.

The factory replaces the prior NMOS copper with exactly these eight ordered
traces:

- `LOAD_SUPPLY`: J1.1 → RLOAD.1;
- `LOAD_NODE`: RLOAD.2 → LED1.A;
- `DRAIN`: LED1.K → Q1.D;
- `CONTROL_INPUT`: J2.1 → RPD.1;
- `CONTROL_INPUT`: J2.1 → Q1.G;
- `GND`: J1.2 → J2.2;
- `GND`: J1.2 → RPD.2;
- `GND`: J1.2 → Q1.S.

The route derives pad centers and package escapes from the actual footprint
objects, keeps the dynamic DRAIN lane 20 units right of the LOAD_NODE resistor
escape lane, applies `compactToContent(40+s, 30+(m%2)*10, 26)`, positions the
parts tray, and runs the real geometry validator.

`NmosFixedLayoutDeveloperVerifier` uses one live NMOS generator fixture and
enumerates exactly 9 resistor tuples × 4 origin classes = 36 cases. Each case
checks the exact coordinate and endpoint witness, all five logical memberships,
the rooted CONTROL_INPUT copper branches and real physical-union validator,
Q1 provider/package parity, package escape directions, route quality, copper
clearance, deterministic duplicate fingerprints, and normalized geometry
equivalence across origin classes. `Task43DeveloperVerifier` aggregates this
matrix without changing RC/NPN aggregation or claiming 43R-8.

## Files changed

- `src/com/lushprojects/circuitjs1/client/NmosLowSideSwitchPcbLayoutFactory.java`
- `src/com/lushprojects/circuitjs1/client/NmosFixedLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`

No `PhysicalPackage`, `PhysicalPackageGeometry`, generic footprint/geometry,
generic validator, `SeededPcbLayoutGenerator`, RC/NPN route, NMOS electrical
generator/topology, measurement/fault/stress/replacement behavior, AGENTS.md,
or Task 44 file was changed.

## Validation evidence

Final production build command:

`.\scripts\build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF -Target Compile`

Result: bundled JDK 8 accepted; all five GWT permutations compiled; production
linking succeeded.

In-app developer validation against the compiled production preview:

- `PASS:NMOS_FIXED_LAYOUT_MATRIX:cases=36/36;variantTuples=9;originClasses=4`;
- `PASS:layout` from the general `PcbLayoutDeveloperVerifier`;
- `PASS:task43` from the Task 43 aggregate;
- `PASS:nmos` for all nine existing NMOS electrical/control/mutation cases:
  seeds 0, 2, and 3 × `NMOS_DS_OPEN`, `NMOS_DS_SHORT`, and
  `NMOS_GATE_OPEN`.

PowerShell's `System.Net.HttpListener` constructor is unsupported. A temporary
local static server was used only to serve the already-built `war/`; it is not
part of the candidate. The coder reported that the supported in-app browser
loaded the same compiled preview and produced all results listed above. In the
primary architect session, the required visible in-app Browser smoke attempt
was blocked before page load with `ERR_BLOCKED_BY_CLIENT` for loopback URLs, so
no independent normal-player screenshot is claimed. The separate Edge harness
was blocked at its first route by the host's GPU-process crash and WMI cleanup
`Access denied` behavior. These are environment limitations, not production
validation failures; no DOM shortcut or desktop automation was substituted.

The Edge run therefore did not reach the separate Task 39, Task 40, or Task 41
browser routes in this session. Their production paths were unchanged; this
limitation is recorded separately from the successful build, source review,
and coder-reported compiled-preview verifier results.

`git diff --check` was clean after the implementation and review pass.

## Multi-agent implementation and review

- Phase A investigators: Descartes, Herschel, and Einstein; parallel,
  read-only `gpt-5.6-luna` MAX workers. Their reports were reconciled into the
  frozen eight-trace witness before implementation.
- Coder: Laplace; the single write-capable `gpt-5.6-luna` MAX worker. No other
  write-capable Task 43 agent overlapped the implementation phase.
- Primary architect review: one post-coder diff/scope/route review; **PASS**.
- Independent reviewer: Popper; fresh read-only `gpt-5.6-luna` MAX worker;
  **PASS**, with the loopback/Edge host limitations above.
- Escalation architect: not required.

## Completion protocol

- Intended commit message: `Reconstruct Task 43 NMOS fixed routing`.
- Branch/upstream: `codex/task43-recovery-integration` /
  `origin/codex/task43-recovery-integration`.
- The final SHA, verified push result, and completion-notification attempt are
  established after this report is staged and committed, and are reported in
  the final task response.
- 43R-8 was marked next eligible but was not started. Task 44 was not started.
