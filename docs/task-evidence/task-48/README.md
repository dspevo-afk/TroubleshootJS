# Task 48 — composed controlled indicator

Status: **COMPLETE — ACCEPTED** (2026-09-06).
Base: `3928f4b8cdf633b89aeb6b78512b5cced833a774`, branch
`codex/task43p-final-recovery`. The accepted entry gate is
`623f3a74b37eec771576fe11a4d13c03a09335e7`. Entry was clean; the configured
remote/upstream matched the reviewed base. This task stops before Task 49.

## Reconciled design

All three read-only prerequisite investigations are complete. The driver uses
a series gate resistor: opening only a pulldown is not a meaningful admitted
fault because CircuitJS grounds floating nodes through its convergence shunt.
The existing assembler, namespace, preflight, runtime, mutation and fresh-owner
boundaries remain authoritative. No complete leaf generator is copied.

| Owner | Exact local content | Electrical contract / capability |
| --- | --- | --- |
| `driver`, NMOS driver contribution | RG 1 kOhm; fixed RPD 100 kOhm; fixed Q1 using the existing NMOS model, G/D/S package | CONTROL -> RG -> GATE; RPD GATE -> RETURN; Q1 G=GATE, D=SWITCHED_SINK, S=RETURN. RG is the only mutable component in this block. |
| `load`, LED-load contribution | RLOAD 330 Ohm; fixed LED1 using the existing LED model | SUPPLY -> RLOAD -> LED_NODE -> LED1 -> SWITCHED_LOAD. RLOAD is mutable. RETURN is the declared common reference, explicitly joined. |
| Device `power-adapter` | J1 two-pin connector, 5 V external load source and isolation switch | POWER_OUT -> load/SUPPLY; RETURN -> common return; external input LOAD_VIN_INPUT. |
| Device `control-adapter` | J2 two-pin connector, 5 V external control source, actual command and isolation switches | CONTROL_OUT -> driver/CONTROL; RETURN -> common return; external input CONTROL_VIN_INPUT. |

Adapter contracts are request-level device declarations, not contributed blocks
or child runtimes. Exactly two block contributions and two adapters form seven
physical components, fifteen pads and six connected board nets. The driver
declares CONTROL/GATE/SWITCHED_SINK/RETURN, the load declares
SUPPLY/LED_NODE/SWITCHED_LOAD/RETURN, and each adapter declares OUTPUT/RETURN.
Only these four explicit connections merge local nets:

1. `power-adapter/POWER_OUT` to `load/SUPPLY`.
2. `control-adapter/CONTROL_OUT` to `driver/CONTROL`.
3. `driver/SWITCHED_SINK` to `load/SWITCHED_LOAD`, with a typed low-side relation.
4. All four RETURN ports.

`BlockNamespace` resolves every component, pad, endpoint and local net, including
adapter J1/J2. Global IDs use its accepted v1 encoding with device schema
`controlled-indicator@1`. Canonical joined IDs remain the least qualified member
with explicit merge provenance. Equal labels do not merge nets. Player markings
are explicit RG/RPD/Q1/RLOAD/LED1/J1/J2 labels supplied at construction; observers
do not strip or reconstruct namespace IDs. Solver NMOS posts are G=0, S=1, D=2,
deliberately different from physical G/D/S order. The runtime receipt binds actual
board and component endpoints, including detachable resistor board wires.

The new typed low-side relationship carries the declared sink/load, supply,
control and return ports. It admits only one OUTPUT/SINK/OPEN_DRAIN and one
bounded-current switched load, supported LOW/HIGH states with active HIGH,
explicit common nonisolated reference, real supply path and separate control
connection. Bounds are 4.75–5.25 V supply within 4.5–5.5 V load acceptance,
20 mA sink capacity versus at most 16 mA load demand, ON clamp 0–0.8 V and OFF
drain allowance through 5.5 V. External control guarantees LOW <=0.1 V and HIGH
>=4.75 V; input accepts LOW <=0.2 V and HIGH >=4.0 V. Unknown or unsupported
evidence cannot become COMPATIBLE. Ordinary untyped open-drain joins stay rejected.
These are conservative preflight bounds; CircuitJS establishes actual behavior.

## Faults, physical operations and normal admission

Exactly one initial RESISTOR_OPEN fault is chosen through the accepted FAULT
named stream from stable decisions `driver-rg-open` and `load-rload-open`.
The driver and load physical owners are distinct RG and RLOAD slots. Healthy
OFF/ON behavior is solved before fault application. Both admitted faults leave
the indicator dark when commanded ON; RG open leaves the gate pulled low,
whereas RLOAD open leaves the commanded gate high. Complaints describe observed
failure to respond and do not disclose the selected owner, private effect or plan.

RG and RLOAD expose the full accepted resistor workflow: lift/reconnect either
lead, remove, restore/reinstall the retained part, catalog replacement, loose
probing and solved stress damage. Each has its own slot, typed inventory view,
capability, fault switch where applicable, secondary-open path and resistor
transaction. Both inventories share the single physical runtime registry.
Healthy-part operations have the same settlement/ownership rules. Original
faults remain on retained original parts; reconnecting or reinstalling them
does not repair an internal open. Catalog backings are fresh. Electrically valid
alternative values are judged by OFF/ON function and safe bounds, not a magic
catalog identity. Wrong-owner, wrong-value and incomplete repairs must fail retest.

RPD, Q1, LED1 and J1/J2 use existing fixed physical foundations with actual
CircuitJS endpoints, package geometry and probe exposure. They have no detachable
connection bindings or mutable providers. Their removal/replacement is not
implemented or claimed. The resistor-only composition provider restriction is
retained. No universal transaction or NMOS/LED mutation provider is introduced.

Normal `forGeneratedBoard` diagnostic contracts contain a real plan. Task 41
enumerates both RESISTOR_OPEN owners by stable target as well as fault type;
type-only selection cannot distinguish them. Its existing fresh-candidate route
factory calls an internal assembler proof overload selecting one of the two
declared targets. This override is not replay or player input. Proof candidates
use identical topology/layout and disjoint mutable owners. The plan executes
HIGH gate/source voltage, LOW response, power isolation, resistor resistance/
continuity signatures, physical replacement and customer retest. Executed trace
metrics remain distinct from declared capabilities. Empty developer fixtures
remain rejected by normal admission.

One device behavior service composes contribution observations and owns HIGH/LOW
operations, whole-device observation, complaint selection and customer retest.
Retest drives and solves both OFF and ON, then restores prior power/control state.
The main and control supplies are both declared external power inputs; active
measurements require both isolation switches open. Simulation pause/reset is
separate from source isolation and fresh challenge generation.

## Installation, entry and replay

The existing fresh installation transaction gains a normal composition entry.
It accepts a settled prior generated owner or a fully detached initial simulator
with no generated controller, workbench, modification owner or power binding.
A separate snapshot capture for fresh installation permits the detached case;
ordinary Task 41 capture keeps its active-owner requirement. Candidate elements
must also be disjoint from any live schematic elements. Private graph/UI
containers protect the original owner. Failure restores that owner or records
the existing isolated nonactionable failure state.

Normal publication explicitly executes live diagnostic admission after internal
settlement and before attaching the workbench/committing. It rejects developer
fixtures and an active internal proof guard. Restored developer flags cannot
remain active after publication. Existing owner/request guards protect stale
workbench handlers, repaint requests and retest completions.

The existing sidebar provides a discoverable controlled-indicator challenge
entry. The normal URL is `circuitjs.html?tsjChallenge=controlled-indicator&seed=0`;
seeds are canonical signed decimal strings and invalid input fails closed on
this route. Existing Quick Play selection and monolithic family replay stay
unchanged. Public controls operate the actual HIGH/LOW input, power isolation,
instruments, physical repair and customer retest. No new application shell is
added. Normal text, DOM, attributes and tooltips expose physical markings and
customer information, without internal namespaces, fault selection or proof data.

The new descriptor is `bounded-assembler@2` / `controlled-indicator@1` /
`controlled-indicator@1`, geometry 3. Task 47 remains `bounded-assembler@1` /
`resistive-coupling@1` / `developer-canary@1` with unchanged canonical signatures.
Component values and the bounded layout are fixed for this version; the initial
fault is the only new random decision. Only constraints containing BLOCKS=2,
COMPONENTS=7 and DOMAINS=1 are supported; other specified constraints reject.
Unknown generator, intent, profile or geometry versions reject. Replay means
descriptor -> newly assembled runtime -> actual solved behavior, including seeds
outside JavaScript Number precision and both signed-long extremes.

## PCB and closed qualification set

A device-owned fixed small-board layout uses canonical registered packages and
the existing renderer, geometry validator and hit/probe infrastructure. All six
nets require complete contained copper with no unrelated crossings. Layout
failure is repaired as a complete bounded layout, not by relaxing the validator
or starting an autorouter redesign. An independently authored literal oracle
joins raw copper, rendered pads/leads and actual solver terminals, including
deliberately wrong mappings and connected versus lifted resistor semantics.

| Gate / production path | Verifier, oracle, fixtures and dependencies | Current result |
| --- | --- | --- |
| Typed contributions, adapters, namespace, low-side preflight and rejection | Actual-source JDK8 pure contracts; fixed literal counts/IDs/bounds, reordered inputs, unsupported/unknown/mismatched relations; old Task44/45/46/47 suites | PASS: 247/202/410/269/224 assertions; 36 descriptor negatives; 12 direct low-side cases |
| Replay and named decisions | Pure Java plus independent Python seed/choice vectors, then compiled fresh runtimes at 0/1/2/3, long min/max and +/-9007199254740993; legacy canary and LED3/RC2/NPN0/NMOS0 replay | PASS: eight composed seeds, 22 seed vectors, independent Task47/48 oracles; all four legacy descriptor/replay/parity strings match the accepted baseline |
| Real circuit, fault diagnosis, repair and retest | New compiled Task48 verifier; OFF/ON, each owner, executed Task41 signatures, unrepaired/wrong/correct/alternative repairs, original versus secondary fault causality | PASS: Task48 755 assertions, both repaired owners, executed normal admission for each seed |
| Mutation and power/control lifecycle | Both composed resistor providers, healthy-part actions, applicable partial writes and exact abort, inventory/owner invariants, all-source isolation, active measurement cleanup and failures; selected composition-entry gate | PASS: 235 mutation assertions, 30 partial aborts, four lifecycle and three damage checks; six measurement cases; composition gate 113 lifecycle/180 mutation assertions |
| Initial/fresh installation, proof isolation and stale work | Detached schematic and prior generated owner, all five install stages, original-owner restoration, disjoint replay, real stale handlers/repaint/retest, empty-fixture rejection | PASS: five construction stages; five prior-owner and five detached-initial install stages; actual stale-work rejection and exact original-owner restoration |
| Bounded physical correspondence | Independent literal copper/render/solver manifest for fifteen terminals and six nets, wrong mappings, lifted/connected targets, production geometry/containment | PASS: fifteen terminals, nine copper traces, nine wrong mappings for every one of eight seeds; connected/lifted/removed targets |
| Normal player acceptance and privacy | Real visible Browser input with debug off for one deterministic case per owner: complaint, OFF/ON, left-red/right-black probes, diagnosis, isolate/repair, retest; mode exit and ordinary input; unrepaired failure; required-control disabled negative must fail without fallback | PASS: full seed0 RLOAD and seed1 RG flows; four inspected screenshots; disabled real entry fails the same public-input helper |
| Directly affected regressions | Compiled Task40 corpus, Task41 fourteen legacy routes, Task47 canary, applicable Task43P runtime oracle, six-family layout and selected challenge/replacement/NMOS/LED paths | PASS: Task40 corpus, Task41 14 routes/128 solver samples, Task47 1540 assertions, strict Task43P exit0/zero blockers, six-family layout and challenge/replacement. LED physical PASS build15; NMOS DS_OPEN/DS_SHORT/GATE_OPEN PASS build16; normal NPN/NMOS controls PASS build16 |
| Production build and error terminal | Repository build with JDK8, OBF, all five permutations; Task48 forced AssertionError emits terminal FAIL | Final build16 PASS, exit0; five permutations, compile 50.810 s / link 1.216 s. Forced AssertionError terminal FAIL confirmed on unchanged verifier inputs |
| Independent review | Fresh Luna MAX read-only integrated candidate review before live Browser, targeted deltas after repairs, final evidence reconciliation | Integrated code, every source delta and final evidence reconciliation PASS; no blocker |

Actual commands are the repository's `scripts/build.ps1`,
`scripts/verify-block-contracts.ps1`, `scripts/verify-challenge-contracts.ps1`
and `scripts/verify-assembly-contracts.ps1`, with explicit selected JDK8 and
bundled Python; any Task48-specific extension records its supported command.
Only one expensive build/browser matrix runs at a time. Exact source manifests,
compiled hashes, commands, failures, review and cleanup receipts bind final proof.
Any reuse requires an exact dependency audit; no earlier relevant PASS proves
changed implementation. A cheap anchor/apply/byte-restore preflight precedes
any required-control source mutation and its build.

Non-goals: Task49 value synthesis, further block families, multiple initial
faults, difficulty/scoring, general device generation, medium-board routing,
package redesign, broad CirSim decomposition, leaf conversion, new transaction
framework, toolchain modernization, historical CDP certification, unrelated
branch changes or cleanup.

## In-session ownership and resources

Astra owns shared integration, initial/fresh publication, player entry/privacy,
compiled acceptance integration, documentation, build/Browser and publication.
Luna leaves own bounded contract, runtime, diagnostic and physical evidence work
under explicit file assignments. A fresh reviewer authors neither implementation
nor acceptance oracle. No speed selector is exposed by the worker interface.
Task scratch is uniquely owned outside the repository; existing user resources
remain untouched. The one root Browser tab is closed. Preview run
`d316bf21ba5d4abfb7682929ae320684` completed exact process/listener/lease cleanup:
Success=true, no errors, wrapper exit0; separate process/listener absence checks
also passed. All build/test commands and worker assignments are complete.
The runtime exposes no close-agent operation; completed workers own no resources.
The [cleanup receipt](cleanup.json) records the final scratch disposition: automatic
approval review blocked deletion before execution, with only "blocked by policy".
The isolated 61-file / 4,792,679-byte diagnostic directory is retained. This is a
nonblocking cleanup limitation; its deletion is not reported as PASS. The completed
verifier audit journal is also retained in its established external directory.

## Candidate binding and reproducible evidence

The accepted implementation is the commit containing this packet. The final
handoff records its exact commit and verified publication SHA. The final
[source manifest](source-manifest.json) covers 972 source/test/harness inputs at
SHA256 `cdf2bbee7afdc1af2bccfa38bbda09f3ed65a450156a9dd7793d2dec3234ec09`.
Before/after build inputs are identical. The [compiled manifest](compiled-manifest.json)
binds all five final OBF permutations and the bootstrap. Actual Browser loading
of `972C3C7155FEC8F4C95FDA7EF4E0F324.cache.js` was checked against its SHA256
`2021f679ada5b436233b3a5e04bf33ee3a2b7f5922caa173ae887a30d4c8cd6d`.

| Evidence | Candidate and exact reuse boundary |
| --- | --- |
| [Full normal-player flows](player-acceptance.json), four screenshots below | Build12 `b068f505766d676d580f421056d53e521a91ee59d9a9dde0cde0c18252fe0834`. All normal entry/control/power/instrument/repair/retest/privacy/layout and public-input helper bytes are unchanged through build16. |
| [Public-control negative](disabled-control-negative.json) | Build13 `ad308c1602274823af1216b097c98ff3ed24424e16dcecfbada95b0e0ba0ec1f`: one real entry-enable predicate forced false after exact anchor/apply/restore preflight. The same public-input helper fails. Source restored exactly. |
| [Task48 runtime](runtime.json), [Task47 regression](task47-regression.json), [compiled regression routes](browser-regressions.json) | Build14 `c4baf5856bef93edc075272161cbc928fd4a70cf8cd562559dec9d6a84731e6c`. Only three independent legacy developer-verifier files change afterward; the reused routes do not invoke them. Both source and compiled build14 manifests are retained. |
| LED physical regression | Build15 `3517c840b735d5078007e06eddb18347e7207064497e7bda30823e26f73c00ff`. Only the NMOS verifier changes afterward; LED verification and its shared helper are unchanged. Build15 manifests are retained. |
| [Final normal entry](final-entry-positive.json), [normal legacy controls](legacy-visible-controls.json), all three NMOS faults and challenge/replacement | Fresh final build16. The ordinary initial schematic uses the same public entry helper, with debug off. NPN/NMOS LOW/HIGH, all-source isolation, actual probes and unrepaired retest also pass with debug off. |
| [Pure/build output](qualification.log) and JVM receipts | Task44/45/46 ran on build05 inputs; Task47/48 and both independent assembly oracles ran on build14 inputs. Every explicit compile input and harness consumer remains byte-identical. |

The [dependency audit](dependency-audit.json) lists each changed input, pure
compilation input and consumer boundary. All four entries under its
`freshFinalRequirements` are now PASS in the receipts above. This audit reuses
qualified unchanged paths; it does not relabel failed runs as passing.
[Diagnostic history](diagnostic-history.json) preserves the actual failures,
causes, repairs and closing proof. The unrequested worker PRETTY build is
excluded; the final root OBF build supersedes its outputs.

The actual Windows toolchain was Temurin JDK8 `1.8.0_502`, repository GWT, and
bundled Python `3.12.14`. The commands below use the selected bundled Python
executable as `$task48Python` and the uniquely owned external receipt directory
as `$task48Scratch`; personal absolute paths are deliberately omitted.

```powershell
./scripts/verify-block-contracts.ps1 -JavaHome '.tools/jdk8-download/jdk8u502-b07'
./scripts/verify-challenge-contracts.ps1 -JavaHome '.tools/jdk8-download/jdk8u502-b07' -PythonExe $task48Python -ParityOutputPath "$task48Scratch/task46-parity.txt"
./scripts/verify-assembly-contracts.ps1 -JavaHome '.tools/jdk8-download/jdk8u502-b07' -PythonExe $task48Python -ReceiptOutputPath "$task48Scratch/task47-assembly-build14.txt" -ControlledReceiptOutputPath "$task48Scratch/task48-assembly-build14.txt"
./scripts/build.ps1 -JavaHome '.tools/jdk8-download/jdk8u502-b07' -Target Compile -Style OBF
& $task48Python tests/contracts/task48_assembly_reference.py docs/task-evidence/task-48/runtime.json
& $task48Python tests/contracts/task47_assembly_reference.py docs/task-evidence/task-48/task47-compiled-receipt.txt
. ./scripts/Task43PRuntimeEvidence.ps1
Assert-Task43PRuntimeEvidence (Get-Content -Raw docs/task-evidence/task-48/task43p-runtime.json | ConvertFrom-Json) task48-final14 task48-runtime
```

All scripts above returned exit0. The strict Task43P oracle returned exit0 and
zero blockers across A, B/F, C, E, D, G, H and I; its
[raw packet](task43p-runtime.json) and [actual outcome](task43p-runtime-outcome.json)
are separate from the browser's OBSERVED marker. Exact supported query strings,
terminal values and developer attributes are in `browser-regressions.json`.
The normal-player cases used actual visible Browser clicks and canvas input.

Task41's [14-route receipt](task41-regression.txt) retains 128 solver samples;
the [composition entry receipt](composition-entry-regression.txt) retains 113
lifecycle / 180 mutation assertions and 18 partial aborts. Task48 adds 30 partial
aborts across both resistor owners. The [legacy replay audit](legacy-replay-audit.json)
compares all four descriptor/replay/parity strings byte-for-byte with the accepted
Task47 baseline; JVM/GWT parity and both eight-seed oracle receipts are retained.

## Curated visible evidence

All four images are real, inspected, nonblank normal-player screenshots. The
associated full interaction sequence is in `player-acceptance.json`.

| Image | What it proves |
| --- | --- |
| [Load fault: high gate, dark LED](load-fault-gate-voltage.png) | Seed0: ordinary red/black physical probes read 4.95 V gate-to-source with HIGH commanded while the load remains dark. |
| [Load repair: customer pass](load-repair-customer-pass.png) | RLOAD physically replaced using the catalog; visible customer retest passes the complete OFF/ON requirement. |
| [Driver fault: low gate](driver-fault-gate-voltage.png) | Seed1: HIGH commanded with an actual low gate, distinguishing the RG path from the load fault. |
| [Driver repair: customer pass](driver-repair-customer-pass.png) | RG physically replaced using the catalog; visible customer retest passes. |

## Review, limits and publication

A fresh Luna MAX read-only reviewer authored neither implementation nor oracle.
The integrated review and all targeted source deltas passed before their live
Browser work. The last source delta settles the solver-backed repair-status
observation before the legacy NMOS public retest; it retains the status assertion,
snapshots, public completion/latch and leakage checks. Final evidence review
PASS: all 972 source inputs, six compiled assets, both independent Python oracles,
raw proof, four screenshots and exact dependency reuse were independently checked.
Its disposition is recorded in [review.json](review.json).

There are no outstanding implementation or validation blockers. This version
fixes the two-block topology, values and small-board layout. Only RG and RLOAD
have physical mutation workflows; RPD, Q1, LED1 and connectors remain fixed.
Task41 retains its accepted fresh-owner Option A and declared-versus-executed
metrics; no arbitrary same-owner or nested rollback is claimed. The historical
CDP/source-falsifier campaign is not rerun or recertified, and its legacy physical
aggregate remains UNPROVEN. The independent Task48 physical triad is separately
qualified. Task47's redundant numeric physical-report seed retains its documented
Number limitation; new Task48 seeds, including physical evidence, are exact strings.

Nonblocking follow-ups remain bounded: the legacy Task46 AssertionError-to-RUNNING
verifier issue; the single-result resistor capability convenience lookup is not
a multi-owner API (all qualified paths use the target component); and the broader
typed relation can describe a bidirectional load whereas this template separately
requires its exact INPUT load contract. None bypasses this challenge's admission
or affects its qualified player paths. No follow-up is implemented by this task.

The candidate is accepted for publication. Astra commits only these Task48 files,
pushes the configured branch normally, verifies its exact remote SHA, and then
attempts the authorized Gmail notification. The final handoff records the actual
outcomes and exact SHA. G5 is satisfied; Task49 is the next unstarted milestone
and needs separate authorization.
