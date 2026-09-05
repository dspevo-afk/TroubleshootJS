# Task 43P runtime reconciliation — 2026-09-05

Task 43P's developer evidence and verifier-trust recovery are implemented.
Runtime acceptance remains blocked by the two concrete findings below. Owner
Review is incomplete and Task 44 is blocked and unstarted. This report records
the evidence boundary; it does not repair production measurement cleanup or
claim same-owner transactions or request epochs.

The tested candidate is the intended dirty worktree on
`codex/task43p-final-recovery`, based on
`a5b253873c2b25a54d7c393b118e3f2e1831a4d8`. The final source/verifier digest is
`0d4d6c5e4376ff7a865e4d3f0e6079aedb2d1dcfb26ccd21dcd518a3230ad07d`.
The A-I, physical, and first-five-source runtime digest is
`aabfeb3e4bd00fead02c33a3dd1ec0ab1a30f633650befaa90617f63903c1f38`.
The intermediate source digest is
`6b4e1541894eff75c6ff8a4b6c222e2fee5d9ff1ccf046f0299c8b2dd55f85fb` for
qualified source cases 6-10. Two bounded test corrections separate these
candidates: falsifier 6 selects a distinct solver endpoint, and the public
Remove helper reinstalls the removed original through an explicit destination
slot. Runtime collectors, physical validation, other mutation definitions,
isolation and unaffected wrapper/Gate B seams are unchanged. A subsequent
wrapper-only screenshot correction groups Base64 decoding as an expression;
the actual screenshot writer and its negative paths pass a focused Gate B
canary. Passing evidence is reused with those limits; the final Java candidate
has its own production build. Digests use the executing Windows PowerShell 5.1
record order; individual file hashes are also retained.
The final evidence index records individual source hashes, raw-artifact hashes,
commands, actual child exit codes, and run identities. Documentation and
curated evidence are added after the live runs; they do not change tested code.

## Evidence and exit-code boundary

Use [the evidence index](../task-evidence/task-43p/runtime-evidence-index.json)
and [runtime observations](../task-evidence/task-43p/runtime-observations.json)
for the retained, path-sanitized data. Historical reports and failed attempts
remain preserved. The earlier report
[POST_TASK_43_INTEGRITY_RECONCILIATION.md](POST_TASK_43_INTEGRITY_RECONCILIATION.md)
describes its dated baseline; its missing-runtime conclusions are superseded
only where current evidence below covers the same claim.

| Check | Actual result | Meaning and limit |
| --- | --- | --- |
| Final JDK 8/GWT production build | `0`, all five OBF permutations | `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`; final Java sources unchanged afterward. |
| Final complete Gate B | `0` | `scripts/verify-gate-b.ps1 -SkipJdkCheck`; JDK/GWT is covered by the separate final production build. Focused real transport, deadline, ownership, schema, and persistence-failure checks are included or recorded separately. |
| Forced-negative first | expected `1` | Final invocation/cleanup run `703cb831ebe54e71b2b806ea2f043afb`, log `task43p-forced-current-e6f5bb2f5de1466c8ecc0ebd8c8a2bf1.log`: exact forced DOM marker, anchored Java diagnostic, request/provenance binding, and complete verifier-owned cleanup. Its distinct route/request IDs are retained in the index. The preceding runtime candidate separately qualified forced-first invocation `2efbdc81cf7c4981a4ed35a685ac1c16` before A-I and triads. |
| A–I runtime collector | proven application `1` | Run `1e71c0ae2b4a40d0a2672775d3a83e57`: complete validated `OBSERVED` packet, three open observations representing two root causes. This is not a natural-success `0`. |
| Physical corpus | triad PASS in all 18 packets; overall `2` | Run `813c693b2cda48a9b980a59c147ff84b`: six families × seeds 0, 2, 3. The separate legacy A–I fields remain explicitly unproven in this route; its aggregate `2` is not relabeled PASS. |
| Eleven compiled source falsifiers | all eleven individual cases `CAUGHT`/`1` | First five qualified cases from retained invocation `45ed8048759348a1b8c6f0cb8d0352b5` (whole invocation remains `2`), plus six successful individual invocations. Each case has exact compiled/run/request-bound DOM/Java proof, source restoration, repository unchanged, complete child/harness cleanup, and a separate current absence audit. No single complete invocation with exit `1` is claimed. |
| Supplementary legacy normal-player CLI | `2`, not accepted | Reached real removal but timed out waiting for retest completion; its cleanup also failed ownership proof. Exact root-only manual cleanup subsequently proved current process/listener absence; failed profile/claim/evidence retained. This result is not replaced by the visible positive. |
| Visible built-in Browser | PASS for the LED seed-3 sequence | Real selection, red/black probes, mode exit, removal, wrong replacement/retest, correct replacement/completion, and terminal disabled controls. Five real screenshots are retained. |

The forced, A–I, physical, and successful visible-preview runs completed their
own process/listener/profile/lease cleanup. Fresh separate absence audits
returned `0`; manual cleanup was not used for those runs. The visible Browser
tab was closed before its preview ended. Earlier failed attempts remain failed:
manual cleanup after an ownership failure never qualifies verifier cleanup.
An initial root-owned visible-preview coordinator failed during metadata
serialization; its exact preview/coordinator identities were revalidated and
manually stopped. Its failed setup is distinct from the later successful
visible run `3569d61f88314fadb72d456f8cf22ece`.

The source corpus's first attempt qualified five mutations, then stopped on
`solver-detachable-c1-plus-identity-mismatch`. It selected `J2.1`, which shares
the exact `(rcOut, post 1)` endpoint with `C1.+`; therefore it injected no
identity fault and reached the generic forced marker. The parent timed out
with exit `2`; the child's browser cleanup completed but final manifest cleanup
was still pending. Exact source restoration and a separate current absence
audit passed, without manual cleanup. That failed invocation remains unproven.
The test-only correction selects the genuinely different `R1.2` endpoint first;
it changes no validator, application, marker, ownership rule, or deadline.
Corrected and remaining cases are recorded separately, reusing the five
unaffected qualified cases only with their original provenance.

The first public-Remove attempt (`1a30bb8295c141a6b5225025f42a5346`) also
remains `UNPROVEN`/`2`: its developer control removed the part but attempted
reinstallation without an explicit destination slot. Removal correctly clears
the physical part's mount. The helper was corrected to use the existing
`WorkbenchOperation.forPartAtSlot` API with the authoritative slot; production
dispatch and availability rules are unchanged. Its browser cleanup completed,
source bytes restored exactly, and current process/listener/profile/lease
absence was proven without manual cleanup; the failed source copy is retained.
Final qualification of this corrected helper is recorded separately below.

The next public-Remove attempt (`76f73a1293db4c639b6bc3054cc39f62`) reached
the screenshot boundary but could not save it: PowerShell argument mode treated
an ungrouped Base64 conversion as text for a byte-array parameter. It remains
`UNPROVEN`/`2`, with no final child proof. Browser/harness cleanup, exact source
restoration, and a separate current absence audit completed without manual
cleanup. The wrapper correction groups the actual conversion; focused tests
now execute the real screenshot writer and prove exact bytes, invalid-input
rejection, I/O-failure rejection, and overwrite refusal. Independent delta
review passed; the later live source result is separate evidence.

The final public-Remove invocation `ca1183eba8c040c392043df6c4fe98a3`
returned `CAUGHT`/`1` under the final source digest. Its child
`0721e9c274aa417d88624a1bee20e355` proves direct workbench dispatch removed,
reinstalled, and restored the original owner. A separate fresh normal-player
document received two real mouse events on the visible disabled Remove button;
the part stayed installed and the tray stayed empty. The screenshot was saved,
source bytes restored exactly, final cleanup completed, and the current
process/listener/profile/lease absence audit returned `0` without manual cleanup.
The [source evidence](../task-evidence/task-43p/source-falsification.json)
records all eleven case IDs, markers, raw-artifact hashes, and digest splits.

Live CDP evidence uses the explicitly recorded 30000 ms startup condition on
Task 43P routes. The 500 ms proof budget and existing cleanup deadlines were
retained. This does not certify shorter startup conditions, every host, or
unrelated browser processes. Exit `2`, absent evidence, contradictory schemas,
timeouts, and unproven cleanup remain non-passes. Generic shell `1` is never
Java/application proof.

## Runtime A–I observations

The runtime route uses a fixed LED seed-3 entry document, then fresh detached
candidates for its bounded cases. Observations are copied before restoration;
the original owner is restored and checked after each synchronous group and
after the real asynchronous callback.

| Lane | Current observation | Disposition and boundary |
| --- | --- | --- |
| A — original fault and replacement | LED3 original-part removal/reinstallation, catalog substitution, original fault binding round trip, wrong 2200 Ω repair, and correct 1000 Ω completion assertions ran. | CLOSED for the exercised sequence. Original generated fault identity remains distinct from the installed healthy replacement. No multi-owner exception rollback is claimed. |
| B/F — replacement identity and probes | LED0 meter lifecycle helper ran both lifted leads, component-side versus board-pad resistance/DC targets, reconnection, retained-target power changes, and physical-part A/B identity. | CLOSED for these helper assertions. This is developer interaction evidence, not proof of every rendered hit region or public control. |
| C — secondary damage | LED3 stress/damage helper assertions completed with the original fault binding preserved and successful replacement/retest. | CLOSED for the existing physical-owner scenario; complete causal projections across public status, snapshots, diagnostics, and scoring remain FOLLOW-UP. |
| D — active measurement failures | Four real measurement cases: `READER` and `AFTER_STIMULUS_REMOVE`, each with and without queued power. Actual positive R2 baseline is about 680 Ω; four temporary elements and exactly one typed injection are observed. | READER cases CLOSED. Both post-removal cases are OPEN BLOCKER; details in C1. |
| E — stored energy and mutation | RC2 stored-energy/mutation helper completed on a fresh candidate and restored its owner. | CLOSED for that helper. This does not establish general re-entrant RC status/retest behavior. |
| G — paused completion and rapid actions | Removed/wrong/healthy-before-update states are READY with analysis pending and reject Retest/Finish. After natural updates, healthy completion succeeds; repeated terminal actions reject. Rapid meter/lift/reconnect/power/retest/Finish leaves no overlay, pending power, or targets. | CLOSED for this sequence. READY alone is demonstrably not a settled-state guarantee. No general epoch or pending-flag completion contract is inferred. |
| H — reset and fresh owners | LED3/NPN0/RC2 run in forward and reverse succession, with real retest/probe state before reset. Simulation reset preserves the owner; subsequent fresh installs have distinct owners/controllers/graphs, no inherited retest/probes, and equal per-case fingerprints in both orders. | CLOSED for the six-case sequence. Reset and fresh-session creation remain different operations. Snapshot calibration separately exposes C2. |
| I — real queued repaint across a switch | A real repaint is scheduled for LED3 before NPN0 is installed. Entry and exit use the current owner, stale targets clear, the detached old-owner fingerprint is unchanged, and the original owner restores. | CLOSED for this scheduled callback. `pendingAfterCallback=true` and `analyzedAfterCallback=true` are retained; this is not a generic settled/epoch proof. |

G's validator allows a functionally correct healthy completion before or after
natural updates; the actual packet rejects it before and succeeds afterward.
The synthetic healthy-before/pending combination is a bounded test-contract
FOLLOW-UP. The current task does not establish the pending flag as the sole
completion criterion, and this limitation is not used to close S:R1 or S:R10.

The existing-lane schema also permits a null `installedTargetAfter`; all four
actual observations contain the expected installed target. The runtime helper
assertions are the acceptance source for those sequences. Making the envelope
independently require the expected final target is a TEST/TOOL FOLLOW-UP, not
proof that a target disappeared in this run.

## Proven blockers and bounded correction milestones

### Task 43P-C1 — exception-safe temporary measurement cleanup

**Classification:** OPEN BLOCKER / CODE. **Owner:** CircuitJS adaptation and
instrument lifecycle owner. **Affected path:**
`CircuitJsSimulationAdapter` → `CirSim.runTemporaryActiveMeasurement()`;
`Task43PRuntimeLifecycleDeveloperVerifier` provides the reproduction.

Reproduce with `scripts/verify-browser.ps1 -Task43P -Task43PRuntime
-Task43PStartupSettleMilliseconds 30000 -TimeoutSeconds 90`. In both
`AFTER_STIMULUS_REMOVE` cases, elements leave `elmList` but remain referenced by
voltage-source/node-link structures, the measurement overlay remains active,
and both solver-restoration flags are false. With queued power, the requested
POWERED state remains pending and the board is still UNPOWERED. Both READER
cases clean up correctly. A deferred resistance refresh in the powered reader
case is legitimate cache state and is not counted as temporary graph residue.

The bounded correction must make each cleanup stage exception-safe while
preserving CircuitJS as electrical truth, the current owner, the user's latest
power request, and the original exception. It must not add a transaction
framework or repair another owner. Acceptance requires the same four real
injected cases to close through independent checks of all graph indexes,
solver state, overlay, power, and identity; normal measurement/mode-switch/
mutation/reset checks; final JDK8/GWT build, fresh independent review, and
qualified forced-first/runtime cleanup. No production repair is implemented
in this reconciliation.

### Task 43P-C2 — snapshot assertion completeness for the supported boundary

**Classification:** OPEN BLOCKER / TEST/TOOL. **Owner:** Task 41 verifier and
snapshot-proof owner. **Affected path:**
`Task41SimulationSnapshot.assertRestored()` and its field inventory.

The snapshot captures and exactly restores `lastResistanceTestCurrent`.
After a valid restore, changing that field to the deterministic sentinel is
accepted by `assertRestored()`. The Task 43P collector's direct field comparison
detects the discrepancy; the existing Task 41 assertion does not. This is one
assertion gap, separate from C1's two runtime observations.

The bounded correction must check this captured mutable field in the actual
restoration assertion and audit the supported snapshot field inventory for
the same omission pattern. Acceptance requires rejection of the post-restore
sentinel and compiled omitted-restore canary, positive non-default round trips,
unchanged original-owner identity/targets, fresh independent review, build,
and affected runtime gates. It must not claim complete same-owner transaction
support or broaden snapshot ownership silently.

Both correction milestones precede relevant runtime-composition acceptance.
Owner Review remains incomplete and Task 44 must not start automatically.

## Historical finding reconciliation

Keys reference the preserved reports listed in the earlier reconciliation:
S = lifecycle audit; V = false-pass audit; A = Task43 recovery assessment;
VR/RR/PG/TR = visual, routing, generation, and troubleshooting audits.
Every row below applies to the candidate identified above. CLOSED is scoped
to the stated reproduced claim; FOLLOW-UP is not evidence of a production bug
or a grant to start another milestone.

| Finding | Current evidence and remaining boundary | Disposition |
| --- | --- | --- |
| S:R1 READY before settlement | G records READY while pending/unanalysed after mutation; invalid completion rejects and healthy completion follows updates. No general settled/actionable guarantee is established. | FOLLOW-UP |
| S:R2 incomplete snapshot/transaction | H restores detached owners and exact field values; the assertion accepts a changed resistance-current sentinel (C2). Full same-owner transaction support remains unsupported. | OPEN BLOCKER / TEST/TOOL for C2; broader transaction claim remains FOLLOW-UP |
| S:R3 multi-owner mutation rollback | A/B/F prove successful bounded identity transitions; current slot/mutation paths remain sequential and no multi-owner commit-failure rollback canary was added. | FOLLOW-UP |
| S:R4 cleanup exception safety | Real D post-removal failures preserve graph/solver/overlay residue in both power cases. | OPEN BLOCKER / CODE, C1 |
| S:R5 re-entrant RC status/retest | E exercises stored energy; H exercises RC2 session succession. Fingerprints deliberately avoid a stateful status query after restoration. Arbitrary repeated/re-entrant RC queries are not certified. | FOLLOW-UP |
| S:R6 fault versus secondary-damage causality | A/C preserve original fault identity while replacing physical parts; C runs the existing damage assertions. Complete UI/snapshot/diagnostic/scoring causal projection is not proven. | FOLLOW-UP |
| S:R7 reset versus fresh session | H proves reset keeps owners and fresh creation replaces them with clean retest/probe state in both orders. It does not change reset semantics. | CLOSED for the owner distinction and six-case contamination check |
| S:R8 complete mutation invariant | A/B/F, D/G/H/I and triads cover selected boundaries; no universal post-commit multi-owner invariant is asserted. | FOLLOW-UP |
| S:R9 renderer/instrument snapshot coverage | Original detached-owner restoration is checked; arbitrary renderer strategy and nested same-owner instrument state are outside Option A. | FOLLOW-UP |
| S:R10 missing epochs | I proves one actual queued repaint safely uses the new owner. No board/session/request epoch was introduced, and generic stale work is not certified. | FOLLOW-UP |
| V:R-01/M1 physical correspondence false pass | Eighteen independent physical triads and compiled producer-path falsifiers catch pad/lead displacement, copper/net, solver endpoint, package, manifest, and internally consistent wrong mappings. Body pixels are outside this proof. | CLOSED for the bounded correspondence corpus; broader geometry FOLLOW-UP |
| V:R-02/M4 disabled public Remove/direct dispatch | Ordinary visible Remove succeeds. The compiled disabled-Remove case independently proves successful workbench dispatch/reinstall/owner restoration, then unchanged installed/tray state after real input on a visible disabled button. | CLOSED for the exercised public Remove boundary |
| V:R-03/M5 snapshot false-pass canary | Current code captures/restores resistance current, but the actual Task41 assertion still accepts a changed post-restore sentinel. A working assignment does not disprove the historical detector gap. The new independent Task43P field comparison exposes it. | OPEN BLOCKER / TEST/TOOL, C2; supersedes the earlier report's stale classification |
| V:M2 retained stimulus canary | D now reproduces actual failure residue, including solver indexes. The exact historical source mutation was not rerun; its lifecycle concern maps to C1. | OPEN BLOCKER / CODE for the reproduced cleanup slice; exact legacy canary NOT RUN |
| V:M3 omitted NPN replacement binding | A/B/F exercise LED replacement/identity, and physical source cases challenge endpoint binding. The exact historical `replaceSingleElement` omission is not among the eleven cases. | FOLLOW-UP |
| V:R-04 shared metadata oracle | Independent manifest is joined to raw logical nets/copper, rendered terminals, packages, and exact retained/live solver endpoints. Producer-path changes, a self-consistent wrong mapping, and omitted manifest terminal were each caught. | CLOSED for the independent physical corpus; broader rendering FOLLOW-UP |
| V:R-05 diode-short admission route | Ordinary diode seeds 0/2/3 pass physical checks; the distinct historical `-DiodeShort -Seeds @(0)` route is not covered by those packets. | FOLLOW-UP |
| V:R-06 RC/fixed-CDP repeatability | Current exact isolated runs work at the recorded startup condition; H includes RC2 in both orders. Repeated external QuickPlay RC or shared fixed-port concurrency is not certified. | FOLLOW-UP |
| V:R-07 finite corpus | Six explicit families × three seeds, one selected fault per seed, and eleven falsifiers are a bounded corpus. | FOLLOW-UP for wider seed/fault coverage |
| V:R-08 visible/helper coverage gap | Five real LED3 screenshots document unrepaired, measured, removed, wrong-repair, and completed states. Other family/player flows remain helper-only. | CLOSED for the requested LED player sequence; broader coverage FOLLOW-UP |
| V:R-09 privacy/accessibility | Ordinary LED3 DOM and accessible attributes were inspected at material states; no hidden-fault/topology answer appeared. This is not every family or channel. | FOLLOW-UP for broader privacy coverage |
| A:#1 compaction | Current physical triads verify terminal/copper joins, not the complete body/lead/selection/drag/probe envelope used by compaction. | FOLLOW-UP |
| A:#2 board containment | No complete body/lead/probe/selection/drag versus outline matrix was run. Current visible board and terminal geometry are bounded evidence. | FOLLOW-UP |
| A:#3 fixed pad consumers | Endpoint falsifiers do not certify every overlap/silkscreen sizing consumer. | FOLLOW-UP |
| A:#4 nominal package dimensions | Current package/variant/terminal identity is checked; full nominal-envelope policy remains outside the corpus. | FOLLOW-UP |
| A:#5 lifted-lead transition | B/F actually exercise both R1 leads, separate component/board targets, OL across the gap, DC/power changes, and reconnection. This corrects the old missing-runtime statement. | CLOSED for LED0 transition assertions; pixel/hit-envelope coverage FOLLOW-UP |
| A:#6 geometry identity | H semantic fingerprints and physical observations do not prove every interaction-geometry change alters a persisted digest. | FOLLOW-UP |
| A:#7 package-ID variant ownership | Selected packages and mirror mutation are tested; arbitrary custom-ID collisions and all variants are not. | FOLLOW-UP |
| A:#8 loose probe radius | Visible tray selection occurs, but no independent loose-component radius/envelope canary is included. | FOLLOW-UP |
| A:#9 verifier structural coverage | Physical/source tests now cover independent terminal geometry and mappings; compaction, all hit envelopes, routes, and silkscreen remain outside that proof. | FOLLOW-UP |
| A:#10 installed rendering | Six families have terminal/package correspondence evidence; screenshots show the LED flow. No all-package body-pixel oracle is claimed. | FOLLOW-UP |
| A:#11 layout/replay version | Current contract-version matching and H fingerprints are observed; no new persisted geometry/replay policy is defined. | FOLLOW-UP |
| A:#12 shell exit trust | Focused negative canaries, forced proof, strict packet validation, child-code capture, and exact cleanup distinguish the selected application and infrastructure paths. | CLOSED for Task43P tested paths; unrelated shell wrappers FOLLOW-UP |
| VR — visual envelopes/pixels | Terminal correspondence and real screenshots supplement the existing renderer boundary check; no independent body-pixel/envelope matrix exists. | FOLLOW-UP |
| RR — routing density/scale | Current small boards pass raw-copper checks; dense multi-board/rip-up scaling is not implemented or benchmarked here. | FOLLOW-UP |
| PG — composed generation | Current leaf families are tested. Block composition is the unstarted future roadmap boundary. | FOLLOW-UP |
| TR — distinguishability/input/leakage | Actual wrong/correct repair and normal-player privacy evidence is bounded to LED3; all-fault separating sequences and public NPN input remain future work. | FOLLOW-UP |
| Gate B isolation | Current verifier-owned exact cleanup and fresh independent absence audits cover forced, runtime, physical, each accepted source case, and the successful visible preview. Earlier failed/manual cleanup and retained source copies are kept separate. | CLOSED for individually qualified runs; failed legacy NormalPlayer CLI remains UNPROVEN/2 |

## Task 41 ownership decision and review

Option A remains authoritative: evaluate a fresh candidate with the original
workbench detached, then restore that original owner. The observed exact
restoration and clean fresh-session succession do not establish Option B
(complete same-owner, nested transaction/snapshot support). C2 strengthens
only Option A's real assertion boundary.

Fresh independent Luna reviews covered the integrated code and repaired
deltas. The reviewer did not author the implementation or its test oracle.
The final record identifies current hashes, reused unchanged reviews, runtime
packet review, and the final documentation/evidence review. Root inspected
the integrated result and retained C1/C2 as blockers. No Foreman/Inspector
hierarchy is claimed under the current flat Astra/Luna repository rules.

Publication and final review results are recorded in the current task report.
