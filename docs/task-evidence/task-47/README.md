# Task 47 — Bounded Assembler and Composed Contribution Contracts

Status: **COMPLETE — ACCEPTED** (2026-09-06).
Base: `623f3a74b37eec771576fe11a4d13c03a09335e7`, branch
`codex/task43p-final-recovery`. The separate [Composition Entry Gate](gate/README.md)
is accepted, pushed, and its completion email was sent. No prior worktree edits
existed at this phase boundary.

## Frozen ownership and executable boundary

The developer fixture is a device-owned 5 V external source and two-pin J1,
feeding a source block's series resistor and a load block's resistor. Source R1
is 100 or 220 ohms; load R1 is 1000 or 2200 ohms. Both use existing 0.25 W axial
parts. The source declares RESISTIVE_SOURCE, 3.9–5 V under a bounded 5 mA load:
`5 - 220 * 0.005 = 3.9`. This is a conservative operating guarantee, not a current
clamp. The actual CircuitJS solution must establish loaded voltage and current.

- **Block providers:** Immutable local descriptor/electrical contract, resistor
  recipe, executable healthy and observed-behavior rules, one local incorrect-
  value fault at 100000 ohms, local catalog-replacement target, and powered
  steady-state input/retest requirements. Providers own no solver elements,
  board, layout, runtime, inventory, challenge, or fault controller.
- **Request:** `BoundedAssemblyRequest` carries the Task 46 descriptor, explicit
  electrical block declarations, and device-owned `ElectricalConnection`s.
  A typed provider registry resolves the two supported versioned block types.
  It rejects unsupported schemas/parameters and noncanonical intent inputs.
- **Resolution:** `BoundedAssemblyPlan` resolves local declarations and all Task
  44 identities, requires Task 45 COMPATIBLE, and records explicit merge
  provenance before mutable runtime allocation. Every block's component, pad,
  net, endpoint, role, and port remains addressable by its exact namespace ID.
  Physical slot/part/provider/fault/repair identities retain explicit qualified
  component ownership. No ID stripping, label merge, or solver-node identity.
- **Wiring:** Source nets SUPPLY/OUT/RETURN; load nets SUPPLY/RETURN. Source OUT
  port aliases its R1 terminal-2 endpoint; load IN port aliases its R1 terminal-1
  pad. Only OUT-to-IN and RETURN-to-RETURN connections join blocks. The two
  same-named SUPPLY nets stay different real solver nodes; the explicit signal
  join maps load SUPPLY to source OUT. J1/source power and return targets are
  explicit device-owned mappings. The final graph has three board nets.
- **Private construction:** `BoundedGeneratedBoardAssembler` owns one private
  context and allocates one board, graph, binding set, physical runtime/global
  part registry, and generated instance. Per-slot typed resistor inventory views
  share that runtime's single inventory authority. Both fault candidates use
  `GeneratedFaultEngine.resistorIncorrectValue`; one deterministic candidate is
  the original challenge fault. Existing physical capabilities, mutation,
  measurements, power, lifecycle, and retest services remain authoritative.
- **Device services:** The device supplies the minimum complete validated PCB
  fixture, whole-device solved behavior and final complaint. Local healthy and
  observation rules execute against mapped actual CircuitJS observations; the
  device additionally checks loaded output/current. Both blocks' faults and
  repair targets must execute. This is no player-family registration.
- **Admission:** A new explicit developer-fixture diagnostic contract carries
  identity/candidate metrics without claiming a Task 41 plan. Only an explicitly
  developer-only generated-instance overload accepts it. Its normal diagnostic
  validation rejects it, so direct Task 41 and player admission cannot pass an
  empty proof. Existing constructors, including forced NPN diagnostic routes,
  keep their existing contract behavior.
- **Publication:** Build and validate privately, then use the accepted
  `FreshGeneratedRuntimeInstallation.installComposition` boundary. On failure,
  dispose only private candidate elements; preserve the old owner or retain the
  gate's explicit fail-closed result. No live same-owner rollback claim.
- **Fault identity adapter:** `GeneratedFaultLocus` now accepts exact Task 44 v1
  component-owner IDs as well as legacy simple IDs. Qualified terminal/path IDs,
  wrong entity kinds, malformed/unknown namespace encodings and noncanonical
  schema versions reject. Existing private-identity token exclusions remain.

## Version and replay policy

The only supported consumer is `bounded-assembler@1`, device intent
`resistive-coupling@1`, profile `developer-canary@1`, geometry 3. It does not
change `legacy-leaf@1`. Values use separate block VALUES/revision-1 streams
with stable source/load keys and semantic key `resistance`; initial fault uses
the device FAULT/revision-1 stream with key `selected-fault`. Stable local
decision keys map explicitly and losslessly to qualified owners; qualified IDs
are never supplied to Task 46's local candidate selector. Effective resistor
values and the selected fault are bound to the canonical seed/version request.

Only BLOCKS (2), COMPONENTS (3, including J1), and DOMAINS (1 after the explicit
return join) count constraints are supported; specified ranges must contain
those values. Every other specified count, requirement flag, or instrument
allow-list is rejected. Unknown consumer/intent/profile/geometry versions and
changed fixed wiring are rejected. Whole signed 64-bit seeds travel as decimal
strings through the browser route and canonical descriptor.

## Finite acceptance and ownership matrix

Luna leaf workers were requested at MAX; the interface has no speed control.
`gate_checks` owned the new pure contracts/tests, `gate_mutation` the assembler
and device behavior, and `gate_lifecycle` the explicit fixture admission seam.
Astra integrated the source and owns the compiled verifier, evidence and
publication. Fresh read-only Luna `gate_review` authored neither this phase's
implementation nor its test oracle.

| Gate / production boundary | Verifier and finite oracle | Result |
| --- | --- | --- |
| Pure request, providers, maps, constraints and preflight | Actual JDK8 source; eight signed seeds, exact qualified IDs, reversed input order, explicit merge provenance, duplicate/dangling identities, unsupported inputs, all four preflight outcomes, open-drain rejection; independent Python choices | PASS: 269 Java assertions, eight independent Python vectors |
| One global envelope and explicit fixture admission | Actual compiled assembler; 3 components / 6 pads / 3 nets, one graph/physical runtime/global inventory, disjoint mutable replay owners; ordinary diagnostic and Task41 admission reject the developer fixture | PASS in 1,540-assertion compiled matrix |
| Coupled solver, mapping and physical correspondence | Independent divider expectations; literal six-endpoint manifest, raw layout/copper and rendered terminals joined to actual solver posts; same-named SUPPLY nets remain separate solver nodes | PASS: eight seeds, 72 wrong-mapping negatives rejected |
| Executable local faults and physical repairs | Each block's original 100000-ohm fault; real remove/catalog-install/retest; wrong repairs rejected and supported alternative values pass; original fault remains on retained original part | PASS: both block owners; four active-measurement cases |
| Private construction and publication failures | Five private stages after actual mapping/allocation/merge/registration/validation, plus all five accepted installation stages; old-owner preservation and candidate cleanup; real duplicate/foreign ownership negatives | PASS: ten injected failure stages and cleanup |
| Owner/request succession and replay | Real captured retest completion, workbench callback and repaint command; install successor and invoke stale work; fresh mutable owners and stable canonical results | PASS; exact JVM/Python/GWT agreement for all eight seeds |
| Applicable entry, lifecycle, mutation and measurement regression | Selected composition gate; final Task43P A-I/C1 runtime evidence validated by strict PowerShell oracle | PASS: 113 lifecycle + 180 mutation assertions; strict exit 0, zero open blockers |
| Task40/41 and existing family behavior | Final physical-locus/serviceability corpus, Task41 fresh-candidate proof, challenge/replacement and all-family layout | PASS: Task40 14 routes; Task41 14 routes / 128 solver samples; layout six families at 0/2/3 |
| Legacy replay and ordinary-player privacy/input | LED3, RC2, NPN0, NMOS0; actual normal page with Task47 flags but no debug; visible left/right probe placement and mode exit | PASS: all four replay fixtures; ignored flags, visible probes, rejected unrepaired retest and normal input after mode exit |
| Explicit new-verifier error result | Debug Task47 route with forced AssertionError | PASS: `FAIL:task47:task47-explicit-assertion-canary`; no report or false PASS |
| Production build | Selected JDK8, actual repository build, OBF | PASS: build08, all five permutations, exit 0; 44.517 s compilation / 1.096 s link |
| Independent review | Fresh Luna read-only integrated review and targeted code deltas | PASS: integrated code, all targeted deltas and final evidence/dependency reconciliation |

## Candidate, commands and evidence reuse

The final source candidate is build08, based on the separate gate commit
`623f3a74b37eec771576fe11a4d13c03a09335e7`, with 960-input fingerprint
`57c91b7e47598259a50504ab2d862225fdf661a75f812ca36d6e24bedf3abf7f`.
The [source manifest](source-manifest.json), [compiled hashes](compiled-permutations.json)
and [build receipt](build.txt) identify the actual final all-five-permutation build.
Documentation/evidence edits do not change those inputs.

The actual commands used JDK `1.8.0_502` and resolved Python `3.12.14`:

```powershell
scripts/verify-block-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07
scripts/verify-challenge-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -PythonExe <resolved-Python-3.12.14> -ParityOutputPath <task-owned-receipt>
scripts/verify-assembly-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -PythonExe <resolved-Python-3.12.14> -ReceiptOutputPath <task-owned-receipt>
scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF
```

All three pure harnesses returned exit 0 and removed their scoped execution
scratch. [Block contracts](block-contracts.txt) pass 247 Task44 + 202 Task45
assertions. [Challenge contracts](challenge-contracts.txt) pass 410 assertions /
36 negatives; [independent parity](challenge-parity.json) preserves 22 vectors.
[Assembly contracts](assembly-contracts.txt) and the [canonical receipt](assembly-pure.txt)
pass 269 assertions and independent eight-seed choices. These ran fresh in this
phase. The [pure dependency audit](pure-dependency-audit.json) verifies all 36
explicit sources, tests, oracles, harnesses and imported helper inputs are
unchanged from that qualified pure candidate through build08.

The [compiled assembly result](assembly-browser.json), [selected entry gate](composition-gate.json)
and [AssertionError canary](assertion-canary.json) ran fresh on build02, fingerprint
`5407512b9702225902ecc0ddb5e6c5005c66572ee3139f53b10774acea940fc1`.
Their [source manifest](assembly-validation-source-manifest.json) and
[compiled hashes](assembly-validation-permutations.json) are retained separately.
The [exact runtime reuse audit](runtime-reuse-audit.json) finds only three changed
inputs out of 960: Task40's developer verifier, the Task43P settlement diagnostic,
and the developer settlement helper. The reused paths do not call those classes
or the helper's consumers; all 957 other input bytes are identical. Fresh final
checks cover the affected helper consumers, including runtime A/B/F/C/E's real
replacement, meter lifecycle, stress/damage and stored-energy paths.

The final [runtime packet](runtime.json) contains the strict exit-0 A-I/C1 result;
[Task41 evidence](task41.json) preserves its declared-versus-executed metrics and
existing Option A limitations. [Selected regressions](regressions.json) identify
actual routes and results. Navigation-to-collection elapsed times include other
work and are not asserted to be verifier execution duration.

The canonical assembly corpus is `0`, `1`, `2`, `3`, signed-long minimum and
maximum, and `-9007199254740993` / `9007199254740993`. The compiled route requested
the negative seed beyond JavaScript's exact integer range as a string. Raw report
strings were parsed with Python integer semantics; all eight quoted case-seed
strings and canonical requests, selected values/faults and independent divider
fault voltages agree with the JVM/Python receipt. No rounded JavaScript number
was used as the transport or the oracle. The reused physical oracle also emits a
redundant `physicalCorrespondence.seed` as an unquoted JSON number. Its raw
integer text is preserved here, but JavaScript Number parsing/serialization can
round unsafe values. That metadata field is not the canonical replay seed; use
the quoted case seed and descriptor. This remains a bounded evidence-format
limitation, not a runtime or replay failure.

## Surgical test-tool repairs and limitations

The [diagnostic history](diagnostic-failures.json) retains failed signatures,
operations, timings where measured, cleanup and the closing proof separately.
The Task47 reader-failure test initially hit a Task43P-only seam guard; the
reviewed change admits the already debug-gated Task47 verifier while retaining
the running-verifier guard. Task40's older workflow needed actual settlement
between dependent power/mutation/observation/retest operations and a null-safe
failure diagnostic. Its original fault and repair assertions remain intact.

The synchronous developer settlement helper exposed CircuitJS's ordinary UI
wall-clock throttle: all 20 updates could finish before a solver step. The narrow
repair requests the existing actual `runCircuit(true)` step only for the exact
ready owner awaiting analyzed verification, with all mutation/measurement/
installation/power guards clear. A subsequent ordinary update must still satisfy
the unchanged settled predicate. It does not write solver time, pending flags,
physical readings or the 20-attempt limit. Final runtime checks pass. Browser
focus timeouts are recorded as transport failures; delayed collection or a fresh
owned tab establishes the separate application result.

Task48 and player-facing composition remain unstarted. The supported fixture is
only the explicitly compatible resistive source/load connection. Open-drain
transfer remains rejected. No general device generator, leaf conversion, router
or package redesign, new difficulty/scoring, second solver/inventory/fault engine,
universal transaction, nested proof or deep same-owner rollback is claimed.
Task41's declared complexity exceeds some executed dimensions; its raw evidence
retains those limits. The historical physical aggregate remains UNPROVEN even
where its bounded independent triad passes. No full historical CDP/isolation/
source-falsifier campaign or change to its 500 ms budgets is claimed. The existing
Task46 AssertionError-to-RUNNING follow-up remains outside this milestone; the
new Task47 failure boundary is explicitly tested.

## Visible evidence and resource closure

The [fresh legacy replay packet](legacy-replay.json) passes LED3, RC2, NPN0 and
NMOS0. Every final descriptor, replay summary and 22-vector parity string is
byte-identical to the separately accepted gate packet. The actual compiled
verifier also proves paired generated snapshots equal with fresh mutable owners.

[Visible input/privacy](visible-input.json) records the ordinary page with Task47
verification/failure/seed flags but no debug flag. No verification marker,
assembly report, root data-tsj attributes or composed fault/identity details are
exposed. All input was real visible Browser interaction.

- [Unrepaired probes and retest](01-normal-probes-unrepaired.png): left red / right
  black produce 10.653 V; the actual customer retest refuses the unrepaired board.
- [Mode exit and ordinary selection](02-normal-mode-exit.png): clicking active
  DC V again yields --- V, then clicking R1 selects the physical part normally.

Astra inspected both 1280-by-720 screenshots; neither is blank or fabricated.
These are legacy player-flow regressions, not a playable composed challenge.
The [cleanup receipt](cleanup.json) records exact-owned preview cleanup
Success=true, no errors and wrapper exit 0. All task Browser tabs closed and no
owned live build/server/test process remains. Scoped diagnostic receipts remain
outside the repository; existing user resources and historical quarantines were
preserved. [Independent review](review.json) and its narrow evidence delta both
PASS. Astra accepted the candidate for the separate Task47 commit and normal
push; the exact implementation SHA is the commit containing this checkpoint.
The authorized completion notification follows verified publication. Task48
requires separate authorization and remains unstarted.
