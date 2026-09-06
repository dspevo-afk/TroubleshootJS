# Composition Entry Gate — bounded runtime qualification

Status: ACCEPTED — complete bounded validation and independent review. Task 47 coding
remains unstarted until this gate has an accepted, separately reviewed commit.

The authorized sequence starts on `codex/task43p-final-recovery` at
`7249709e5061af6b14d4aa968fc3282a1355dac1`, with no prior worktree edits.
Accepted Task 43P `a5e8efa`, Task 44 `10dcafa`, Task 45 `134a079`, and Task 46
`7249709` are ancestors. The configured upstream is
`origin/codex/task43p-final-recovery`.

## Scope and frozen acceptance matrix

The selected dependency is a developer-only, explicitly connected two-block DC
source/resistive-load canary. Its mutable composition providers are restricted
to resistors by executable validation. Task 45 open-drain transfer remains
unsupported. This checkpoint supplies runtime prerequisites, not that assembler.
The following finite matrix was frozen after the two prerequisite investigations
and before implementation; its dispositions are updated as evidence completes.

| Gate | Production owner / entry | Fixture and independent oracle | Disposition |
| --- | --- | --- | --- |
| A | `CirSim` settled/actionable predicate; challenge, workbench, instrument and public graph/mutation entries | Compiled LED3 actual handlers; analysis/verification holds, overlay, direct rapid power/lift/remove/retest rejection, then re-enablement. Actual visible controls are a separate gate. | PASS, including visible controls |
| B | Owner-bound real repaint, retained workbench handlers, request-bound retest completion, queued measurement/power cleanup | Captured actual LED3 callbacks after NPN0 replacement; superseded NPN retest and stale completion after a new LED3 owner. No synthetic unused callback. | PASS |
| C | `FreshGeneratedRuntimeInstallation`, `Task41SimulationSnapshot`, existing installation path | Fresh detached Option A candidate, current-instance rejection, five injected stages after capabilities/power/challenge/validation/workbench writes, exact old references restored. Task41's 14-route solver corpus. | PASS |
| D | `ResistorMutationScope`, `BoardModificationController`, `ResistorSlotController`, physical slot/part/inventory and binding owners | 18 actual partial-write aborts across lift/reconnect, graph remove/restore, physical remove and catalog install; exact touched-state oracle; real overload damage and original fault preserved; failed abort stays isolated. | PASS |
| E | `CirSim.runTemporaryActiveMeasurement`, measurement adapter/session and instruments | Actual Task43P runtime D: four resistance failures and seven cleanup canaries, all solver indexes, overlay, queued power and original/suppressed error distinction. Existing meter and stored-energy lanes. | PASS |
| F | Independent manifest in `Task43PPhysicalTruthDeveloperVerifier`; logical/raw copper/render/solver sources | Resistor/connector interface triad, independently authored terminal/net expectations and nine semantic negatives including internally self-consistent wrong mapping. New assembled maps need fresh Task47 proof. | PASS within stated limits |
| G | Simulation reset, fresh installation, challenge retest owner/request, physical fault and stress owners | LED3/NPN0/RC2 reset and fresh-owner checks; stale probes/completion invalidated; original selected fault and solved overload damage remain distinct. Existing runtime G/H. | PASS |
| H | `GeneratedRuntimeInvariant` and committed mutation/installation checks | Canonical/active references, component/power/connection cross-role ownership, slot/part/provider/inventory agreement, exact resistor pad-to-installed-terminal mapping, foreign/dangling/overlay negatives. A coherently moved wrong owned lead passes the legacy geometry validator and fails this invariant. | PASS |

The invariant allows explicitly governed board endpoint aliases; mutable
component, external-power and detachable elements cannot share ownership.
Every installed component primary/auxiliary binding must reference its exact
physical part backing. Other part-specific mutation transactions are outside
the resistor-only composition boundary and are explicitly rejected there.

The remaining frozen regression set is the two focused pure suites, all five
GWT permutations, Task41's 14 routes, existing Task43P A–I, LED3 challenge /
replacement / wrong-repair / meter / stress, all-family layout, four legacy
replays (LED3, RC2, NPN0, NMOS0), normal-page privacy, and visible player input.

## Candidate and evidence

`source-manifest.json` records all 950 source/test/harness/static inputs and the
SHA256 fingerprint `a2f24ce5049aa72aa7a2594ccfa0910b61ef163d5f8200c4b5fe447748d390f9`.
The fingerprint uses sorted unique repository paths from `git ls-files -co
--exclude-standard -- src scripts tests war build.xml`, with each actual-byte
SHA256 joined as `path=sha256` separated by LF, no final LF. Documentation is
excluded. `compiled-permutations.json` binds the five final compiled outputs to
that fingerprint. `build.txt` records the final actual command, all five
permutations and exit 0:

```powershell
scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF
scripts/verify-block-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07
scripts/verify-challenge-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -PythonExe <resolved-bundled-python>
```

The selected toolchain is JDK 8 `1.8.0_502` and the resolved bundled Python.
Fresh in-session pure suites pass 247 + 202 block assertions and 410 challenge
assertions / 36 negatives, including the independent seed oracle, exit 0 and
owned-scratch cleanup. `contract-dependency-audit.json` proves all 27 explicit
sources, fixtures, oracles and harness dependencies are unchanged since the
reviewed base; those receipts remain applicable to the final candidate.

`composition-gate.json` records actual compiled GWT PASS: 113 lifecycle and 180
mutation assertions, five attachment failures, 18 partial aborts and restoration
of the original owner. Runtime markers are qualified by their evidence/oracles,
not treated as substitutes for them. Browser DOM evidence and real visible
input are separate proofs. The final browser evidence and five curated screenshots are indexed below.

Earlier diagnostic FAIL receipts remain in the task-owned external scratch and
are summarized in the final evidence index. They include newly enforced pending
settlement requiring actual update cycles in old test sequences, a meter
refresh-order repair, an alias negative that initially removed an authentic
auxiliary, and an invalid debug Task41 invocation without its required workbench.
None is claimed as PASS. The accepted electrical/identity/cleanup assertions
remain intact; adapted helpers execute the actual update/verification pipeline.

## Review, reuse and limits

Astra owns the integrated candidate, source, test/browser workload, documentation
and publication. Luna MAX leaf investigators `gate_lifecycle` and `gate_mutation`
completed and reconciled prerequisite ownership investigations before coding.
Their bounded implementation and `gate_checks` test work were handed back.
Fresh Luna MAX `gate_review` did not author implementation or test oracles. Its
H findings produced the cross-role and exact-terminal corrections and targeted
review. Full A–H review and final evidence reconciliation PASS with no concrete blocker.
The tool has no worker speed selector; no global runtime setting was changed.

Historical C1/C2 remain CLOSED only within their accepted boundaries. All affected
active-measurement runtime cases are executed again. Task41 remains fresh-owner
Option A with the original workbench detached; no deep same-owner rollback or
nested transaction claim is made. Resistor abort uses its own bounded touched-
state capture and never uses Task41 as a universal transaction mechanism.

Physical correspondence retains its independent manifest and existing semantic
negative oracle. The legacy physical packet aggregate remains UNPROVEN even
when its embedded triad passes; the broader historical 18-packet campaign and
compiled source-falsifier certification are not rerun or enlarged. These in-app
Browser results do not certify the separate Edge/CDP transport, process isolation
or 500 ms campaign. New Task47 mappings will require fresh independent proof.

Task-only preview/tab cleanup completed successfully before publication. Existing preview
resources and historical quarantines are outside this task's ownership. No Task48,
player-facing composition, universal transactions, new solver/fault/inventory
engine, router/package redesign, difficulty/scoring, transport, dependency or
configuration changes are authorized by this gate.


## Final evidence index and dependency reuse

- `composition-gate.json`: final compiled PASS (113 lifecycle / 180 mutation
  assertions), including actual `installComposition` rejection of an unsupported
  LED provider before active-owner changes.
- `task41.json`: complete 14-route corpus and 128 actual solver samples; original
  declared versus measured metrics are retained, not silently equated.
- `runtime.json`: actual A–I result plus strict PowerShell oracle (exit 0, zero
  open blockers), four active-measurement failures, seven cleanup canaries,
  original-owner restoration, and separate independent physical triad.
- `regressions.json` and `legacy-replay.json`: selected existing production
  paths, all-family layout and four versioned legacy replays.
- `visible-input.json`: ordinary-page privacy, real left/right probes and mode
  exit, wrong/correct repair retests, held/released controls, and five inspected
  screenshots. DOM inspection never injected events or private runtime state.
- `review.json`: fresh independent Luna A–H PASS and closed targeted findings.
- `cleanup.json`: all task tabs closed; owned preview cleanup Success=true,
  no errors, wrapper exit 0; no task-owned live process remains.
- `diagnostic-failures.json`: preserved failure signatures and actual resolution;
  raw receipts remain in external scratch. No failed invocation is counted PASS.

`validation-source-manifest.json` and `validation-compiled-permutations.json`
bind the runtime/regression/visible proofs to source
`469e8ce0aacae88ee319b97de62ebcf8c80e2ede3ea47989676216bb83ac0827`.
The final production delta only adds `installComposition`; its entire generic
installation source is byte-identical after removing that single new method.
The only other changed input is its new developer-gate negative. No reused
runtime, UI, rendering, fixture or oracle calls that entry. The explicit
`final-entry-dependency-audit.json` records these facts and the independent
review; the final all-five build and new gate execute the addition. The two
pure suites have their separate complete 27-input dependency audit.

The five screenshots respectively prove unrepaired probing, rejected wrong
repair, successful customer retest, disabled controls during real pending
verification, and physical removal after re-enablement. They are normal-player
states except the explicitly labeled hold/release fixture; they do not admit a
composed player challenge.
